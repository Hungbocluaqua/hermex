package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Optional read-only enrichment for servers omitting snapshot redirects from /api/sessions. */
internal class SessionLineageEnricher {
    private data class Entry(val value: SessionSummary?, val checkedAt: Long)
    private val memo = mutableMapOf<Pair<String?, String>, Entry>()
    private val permits = Semaphore(2)

    suspend fun enrich(
        sessions: List<SessionSummary>,
        now: Long = System.currentTimeMillis(),
        fetch: suspend (String) -> SessionSummary?,
    ): List<SessionSummary> {
        val keys = sessions.mapNotNull { it.sessionId?.let { id -> it.profile to id } }.toSet()
        memo.keys.retainAll(keys)
        val parentIds = sessions.mapNotNull { it.parentSessionId }.toSet()
        val candidates = sessions.filter { row ->
            val id = row.sessionId
            id != null && (id in parentIds || row.preCompressionSnapshot == true) &&
                row.continuationSessionId.isNullOrBlank() && !row.isDelegatedSubagentSession &&
                (memo[row.profile to id]?.let { now - it.checkedAt >= 60_000 } ?: true)
        }.sortedBy { memo[it.profile to it.sessionId!!]?.checkedAt ?: Long.MIN_VALUE }.take(20)
        // Old servers / slow or unavailable detail endpoints must not block the list.
        withTimeoutOrNull(6_000) {
            coroutineScope {
                candidates.map { row ->
                    async {
                        permits.withPermit {
                            try {
                                val detail = fetch(row.sessionId!!)
                                if (detail?.sessionId == row.sessionId &&
                                    (detail.profile == null || detail.profile == row.profile)) {
                                    memo[row.profile to row.sessionId] = Entry(detail, now)
                                } else {
                                    memo[row.profile to row.sessionId!!] = Entry(null, now)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                // Preserve prior verified metadata on transient failure.
                                val key = row.profile to row.sessionId!!
                                memo[key] = Entry(memo[key]?.value, now)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        return sessions.map { row ->
            val detail = row.sessionId?.let { memo[row.profile to it]?.value }
            row.copy(
                preCompressionSnapshot = row.preCompressionSnapshot == true || detail?.preCompressionSnapshot == true,
                continuationSessionId = row.continuationSessionId ?: detail?.continuationSessionId,
                lineageRootId = row.lineageRootId ?: detail?.lineageRootId,
            )
        }
    }
}
