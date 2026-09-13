package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.network.HermesJson
import com.uzairansar.hermex.ui.sessions.collapseCompressionSegments
import org.junit.Assert.*
import org.junit.Test

class CompressionReportTest {
    private val parent = SessionSummary(sessionId="old",profile="default",rawSource="desktop")
    private val child = SessionSummary(sessionId="new",profile="default",rawSource="webui",parentSessionId="old",relationshipType="child_session",createdAt=100.0)
    private val report = """{"found":true,"session_id":"old","manual_review":false,"total_segments":1,"segments":[{"session_id":"old","source":"desktop","end_reason":"compression","active":false,"updated_at":100.08}],"children":[{"session_id":"new","source":"webui","role":"child_session","started_at":100.0}]}"""
    @kotlinx.serialization.Serializable
    private data class Fixture(val rows: List<SessionSummary>, val reports: Map<String,CompressionReport>)
    @Test fun observedLifecycleWithMissingRedirectsProducesTwoConversations() {
        val text = javaClass.getResource("/compression-lifecycle.json")!!.readText()
        val fixture = HermesJson.decodeFromString<Fixture>(text)
        val enriched = fixture.rows.map { row -> fixture.reports[row.sessionId]?.let { confirmCompressionReport(row,fixture.rows,it) } ?: row }
        assertEquals(8,enriched.size)
        assertEquals(2,enriched.collapseCompressionSegments().size)
    }
    @Test fun malformedAmbiguousOrOrdinaryReportsNeverHideRows() {
        val valid = HermesJson.decodeFromString<CompressionReport>(report)
        val variants = listOf(valid.copy(found=false),valid.copy(sessionId="wrong"),valid.copy(manualReview=true),
            valid.copy(children=valid.children+valid.children.single().copy(sessionId="another")),
            valid.copy(segments=listOf(valid.segments.single().copy(endReason="idle_timeout"))),
            valid.copy(children=listOf(valid.children.single().copy(startedAt=20.0))),
            valid.copy(children=listOf(valid.children.single().copy(source="telegram"))))
        variants.forEach { assertNull(confirmCompressionReport(parent,listOf(parent,child),it).continuationSessionId) }
        listOf(child.copy(sessionSource="fork"),child.copy(profile="other"),child.copy(relationshipType="branch"),child.copy(sourceTag="subagent")).forEach {
            assertNull(confirmCompressionReport(parent,listOf(parent,it),valid).continuationSessionId)
        }
    }
    @Test fun earlierIndependentChildDoesNotPreventCompressionHandoff() {
        val branched = report.replace("100.08", "101.6").replace("\"children\":[", "\"children\":[{\"session_id\":\"fork\",\"source\":\"webui\",\"role\":\"child_session\",\"started_at\":10},")
        val fork = child.copy(sessionId="fork",sessionSource="fork",createdAt=10.0)
        val confirmed = confirmCompressionReport(parent,listOf(parent,child,fork),HermesJson.decodeFromString(branched))
        assertEquals("new",confirmed.continuationSessionId)
        assertEquals(setOf("new","fork"),listOf(confirmed,child,fork).collapseCompressionSegments().map { it.sessionId }.toSet())
    }
    @Test fun compressedParentWithSlightlyEarlierChildCollapsesAcrossDesktopWebui() {
        val confirmed = confirmCompressionReport(parent,listOf(parent,child),HermesJson.decodeFromString(report))
        assertEquals("new",confirmed.continuationSessionId)
        assertEquals(listOf("new"),listOf(confirmed,child).collapseCompressionSegments().map { it.sessionId })
    }
}
