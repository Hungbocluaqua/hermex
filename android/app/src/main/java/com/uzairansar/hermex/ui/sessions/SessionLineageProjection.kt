package com.uzairansar.hermex.ui.sessions

import com.uzairansar.hermex.core.model.SessionSummary

/** Display-only projection. Never uses titles or ordinary parenthood as identity. */
internal fun List<SessionSummary>.collapseCompressionSegments(matchingIds: Set<String>? = null): List<SessionSummary> {
    val groups = mutableListOf<List<SessionSummary>>()
    groupBy { it.profile.orEmpty() to (it.archived == true) }.values.forEach { scoped ->
        val rows = scoped.filter { !it.sessionId.isNullOrBlank() }.associateBy { it.sessionId!! }
        val roots = rows.keys.associateWith { it }.toMutableMap()
        fun root(id: String): String {
            var current = id
            val seen = mutableSetOf<String>()
            while (roots[current] != null && roots[current] != current && seen.add(current)) {
                current = roots.getValue(current)
            }
            return current
        }
        fun protected(row: SessionSummary): Boolean = row.isDelegatedSubagentSession ||
            row.sessionSource.equals("fork", true) ||
            row.relationshipType?.lowercase() in setOf("fork", "branch", "subagent")
        fun join(a: String, b: String) {
            val first = rows[a] ?: return
            val second = rows[b] ?: return
            if (protected(first) || protected(second)) return
            if (!first.rawSource.isNullOrBlank() && !second.rawSource.isNullOrBlank() &&
                !first.rawSource.equals(second.rawSource, true) &&
                !(first.rawSource in setOf("desktop", "webui") && second.rawSource in setOf("desktop", "webui"))) return
            roots[root(a)] = root(b)
        }
        // A server-projected root is evidence; a plain parent_session_id is not.
        rows.values.filter { !protected(it) && !it.lineageRootId.isNullOrBlank() }
            .groupBy { it.lineageRootId }.values.forEach { linked ->
                linked.forEach { row ->
                    join(linked.first().sessionId!!, row.sessionId!!)
                    row.lineageRootId?.let { join(row.sessionId!!, it) }
                }
            }
        rows.values.forEach { old ->
            if (old.preCompressionSnapshot != true || protected(old)) return@forEach
            val target = rows[old.continuationSessionId] ?: return@forEach
            if (protected(target)) return@forEach
            // A confirmed snapshot redirect can span intermediate segments whose
            // generic relationship_type is child_session (older WebUI servers).
            val path = mutableListOf(target.sessionId!!)
            val seen = mutableSetOf(target.sessionId)
            var current = target
            while (current.sessionId != old.sessionId) {
                val parent = rows[current.parentSessionId] ?: break
                if (!seen.add(parent.sessionId!!) || protected(parent) || protected(current)) break
                path.add(parent.sessionId!!)
                current = parent
            }
            join(old.sessionId!!, target.sessionId!!)
            if (current.sessionId == old.sessionId) path.forEach { join(old.sessionId, it) }
        }
        groups.addAll(scoped.groupBy { row -> row.sessionId?.let(::root) ?: row.stableId }.values)
    }
    val matchOrder = matchingIds?.withIndex()?.associate { it.value to it.index }.orEmpty()
    return groups.filter { members -> matchingIds == null || members.any { it.stableId in matchingIds } }
        .sortedBy { members -> members.minOf { matchOrder[it.stableId] ?: Int.MAX_VALUE } }.map { members ->
        // Open the actual active row. Never copy another row's stream ID onto it.
        val representative = members.maxWithOrNull(
            compareBy<SessionSummary> { it.isStreaming == true || !it.activeStreamId.isNullOrBlank() }
                .thenBy { it.preCompressionSnapshot != true }
                .thenBy { maxOf(it.lastMessageAt ?: 0.0, it.updatedAt ?: 0.0, it.createdAt ?: 0.0) }
                .thenBy { it.sessionId.orEmpty() },
        )!!
        representative
    }
}
