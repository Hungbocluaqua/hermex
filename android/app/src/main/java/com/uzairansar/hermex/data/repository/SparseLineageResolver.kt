package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlin.math.abs

/** Hidden nodes are proof only. Mirrors the server's legacy null-profile = default contract. */
internal class SparseLineageResolver {
    private data class Proof(val child: SessionSummary?, val checkedAt: Long)
    private val proofs=mutableMapOf<Pair<String,String>,Proof>()
    private val mutex=Mutex()
    private fun profile(row:SessionSummary)=row.profile?.takeIf { it.isNotBlank() } ?: "default"
    private fun eligible(row:SessionSummary)=row.archived != true && !row.isDelegatedSubagentSession &&
        !row.sessionSource.equals("fork",true) && row.relationshipType?.lowercase() !in setOf("fork","branch","subagent") &&
        row.rawSource in setOf("desktop","webui")
    suspend fun enrich(rows:List<SessionSummary>,metadata:suspend(String)->SessionSummary?,report:suspend(String)->CompressionReport):List<SessionSummary> {
        if(!mutex.tryLock()) return rows
        try {
            val now=System.currentTimeMillis()
            proofs.entries.removeAll { now-it.value.checkedAt>60_000 }
            val byId=rows.associateBy { it.sessionId }
            suspend fun childOf(parent:SessionSummary):SessionSummary? {
                val id=parent.sessionId ?: return null
                if(!eligible(parent)) return null
                val key=profile(parent) to id
                proofs[key]?.let { return it.child }
                val r=report(id)
                val ended=r.segments.singleOrNull()
                val end=ended?.updatedAt
                if(r.found!=true || r.sessionId!=id || r.manualReview!=false || r.totalSegments!=1 ||
                    ended?.sessionId!=id || ended.endReason!="compression" || ended.active!=false ||
                    end==null || !end.isFinite() || end<=0) { proofs[key]=Proof(null,now);return null }
                val candidate=r.children.filter { c -> c.startedAt?.let { it.isFinite() && it>0 && abs(it-end)<=5 }==true }.singleOrNull()
                val nextId=candidate?.sessionId
                if(nextId==null || candidate.role!="child_session" || nextId==id) { proofs[key]=Proof(null,now);return null }
                val child=byId[nextId] ?: metadata(nextId)
                if(child?.sessionId!=nextId || !eligible(child) || profile(child)!=profile(parent) ||
                    (child.parentSessionId!=null && child.parentSessionId!=id)) { proofs[key]=Proof(null,now);return null }
                // The report attests direct parentage when legacy metadata omits it.
                val normalizedParent=parent.copy(profile=profile(parent))
                val normalizedChild=child.copy(parentSessionId=id,profile=profile(child))
                val confirmed=confirmCompressionReport(normalizedParent,listOf(normalizedParent,normalizedChild),r)
                val result=normalizedChild.takeIf { confirmed.continuationSessionId==nextId }
                proofs[key]=Proof(result,now)
                return result
            }
            withTimeoutOrNull(6_000) {
                for(row in rows) {
                    var current=row
                    val seen=mutableSetOf<String>()
                    try {
                        while(seen.size<128) {
                            val id=current.sessionId ?: break
                            if(!seen.add(id)) break
                            current=childOf(current) ?: break
                        }
                    } catch(e:CancellationException) { throw e }
                    catch(_:Exception) { current.sessionId?.let { proofs[profile(current) to it]=Proof(null,now) } }
                }
            }
            return rows.map { row ->
                var current=row
                val seen=mutableSetOf<String>()
                while(eligible(current) && current.sessionId!=null && seen.add(current.sessionId!!)) {
                    val next=proofs[profile(current) to current.sessionId!!]?.child ?: break
                    val fresh=byId[next.sessionId] ?: next
                    if(!eligible(fresh) || profile(fresh)!=profile(row) ||
                        (fresh.parentSessionId!=null && fresh.parentSessionId!=current.sessionId)) break
                    current=fresh
                }
                if(current.sessionId!=row.sessionId && current.sessionId !in seen && !current.sessionId.isNullOrBlank())
                    row.copy(lineageRootId=current.sessionId)
                else if(current.sessionId!=row.sessionId && proofs[profile(current) to current.sessionId]?.child==null)
                    row.copy(lineageRootId=current.sessionId)
                else row
            }
        } finally { mutex.unlock() }
    }
}
