package com.uzairansar.hermex.ui.sessions

import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.network.HermesJson
import com.uzairansar.hermex.data.db.CachedSessionEntity
import org.junit.Assert.*
import org.junit.Test

class SessionLineageProjectionTest {
    private fun rows(json: String): List<SessionSummary> = HermesJson.decodeFromString(json)
    private fun chain(): List<SessionSummary> = rows("""[
        {"session_id":"old","title":"Original","pre_compression_snapshot":true,"continuation_session_id":"tip","last_message_at":1},
        {"session_id":"middle","title":"Original","parent_session_id":"old","relationship_type":"child_session","last_message_at":2},
        {"session_id":"tip","title":"Current","parent_session_id":"middle","relationship_type":"child_session","last_message_at":3}
    ]""")

    @Test fun compressedSegmentsRenderAsOneConversation() {
        val sessions = chain()
        assertEquals(listOf("tip"), SessionListUiState(sessions = sessions).visibleSessions.map { it.sessionId })
        assertEquals(3, sessions.size)
    }
    @Test fun sameTitlesWithoutProofStaySeparate() {
        val sessions = listOf(SessionSummary(sessionId="a", title="Same"), SessionSummary(sessionId="b", title="Same", parentSessionId="a", relationshipType="child_session"))
        assertEquals(2, sessions.collapseCompressionSegments().size)
    }
    @Test fun manualForkAndSubagentAreNeverHidden() {
        val sessions = chain() + listOf(
            SessionSummary(sessionId="fork", parentSessionId="old", sessionSource="fork", lineageRootId="old"),
            SessionSummary(sessionId="worker", parentSessionId="old", sourceTag="subagent", lineageRootId="old"),
        )
        assertEquals(setOf("tip","fork","worker"), sessions.collapseCompressionSegments().map { it.sessionId }.toSet())
    }
    @Test fun profileAndSourceBoundariesArePreserved() {
        assertEquals(3, chain().mapIndexed { index, row -> row.copy(profile="profile$index") }.collapseCompressionSegments().size)
        val sessions = chain().mapIndexed { index, row -> row.copy(rawSource=if(index==2) "telegram" else "webui") }
        assertTrue(sessions.collapseCompressionSegments().any { it.sessionId=="tip" })
        assertTrue(sessions.collapseCompressionSegments().any { it.sessionId!="tip" })
    }
    @Test fun missingParentOrMissingRedirectDoesNotHideRows() {
        assertEquals(2, chain().drop(1).collapseCompressionSegments().size)
        assertEquals(3, chain().map { it.copy(continuationSessionId=null) }.collapseCompressionSegments().size)
        assertEquals(1, listOf(chain().first()).collapseCompressionSegments().size)
    }
    @Test fun streamingParentIsOpenedUntilRotationCompletes() {
        val sessions = chain().map { if(it.sessionId=="old") it.copy(isStreaming=true,activeStreamId="parent-stream") else it }
        val row = sessions.collapseCompressionSegments().single()
        assertEquals("old",row.sessionId)
        assertEquals("parent-stream",row.activeStreamId)
        val rotated = sessions.map { it.copy(isStreaming=it.sessionId=="tip",activeStreamId=if(it.sessionId=="tip") "tip-stream" else null) }.collapseCompressionSegments().single()
        assertEquals("tip",rotated.sessionId)
        assertEquals("tip-stream",rotated.activeStreamId)
    }
    @Test fun searchMatchesHistoricalSegmentButOpensCurrentConversation() {
        val local = SessionListUiState(sessions=chain(),searchQuery="original").visibleSessions
        assertEquals(listOf("tip"),local.map { it.sessionId })
        val remote = SessionListUiState(sessions=chain(),searchQuery="secret phrase",remoteSearchQuery="secret phrase",remoteContentSearchSessionIds=listOf("old","middle")).visibleSessions
        assertEquals(listOf("tip"),remote.map { it.sessionId })
    }
    @Test fun historicalSegmentsRemainAvailableExplicitly() {
        assertEquals(3,SessionListUiState(sessions=chain(),showCompressionSegments=true).visibleSessions.size)
    }
    @Test fun cacheRoundTripPreservesProjectionAndAllSegments() {
        val cached = chain().map { CachedSessionEntity.from("https://example.invalid/",it,1)!!.toSummary() }
        assertEquals(3,cached.size)
        assertEquals(listOf("tip"),cached.collapseCompressionSegments().map { it.sessionId })
        assertNotEquals(CachedSessionEntity.cacheKey("server-a","old"),CachedSessionEntity.cacheKey("server-b","old"))
    }
    @Test fun serverRootGroupsWhenRootIsNotInThePage() {
        val sessions = listOf(SessionSummary(sessionId="a",lineageRootId="root",lastMessageAt=1.0),SessionSummary(sessionId="b",lineageRootId="root",lastMessageAt=2.0))
        assertEquals(listOf("b"),sessions.collapseCompressionSegments().map { it.sessionId })
    }
    @Test fun cyclesTerminateAndUnknownFieldsDecodeSafely() {
        val sessions=rows("""[{"session_id":"a","parent_session_id":"b","unknown":true},{"session_id":"b","parent_session_id":"a"}]""")
        assertEquals(2,sessions.collapseCompressionSegments().size)
    }
    @Test fun archivedAndProjectFiltersRemainRespected() {
        val sessions=chain().map { it.copy(projectId="selected") } + SessionSummary(sessionId="other",projectId="other")
        assertEquals(listOf("tip"),SessionListUiState(sessions=sessions,selectedProjectId="selected").visibleSessions.map { it.sessionId })
        assertEquals(listOf("archived"),SessionListUiState(sessions=sessions+SessionSummary(sessionId="archived",archived=true),showArchived=true).visibleSessions.map { it.sessionId })
    }
}
