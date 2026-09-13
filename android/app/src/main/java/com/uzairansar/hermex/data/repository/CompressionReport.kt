package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
internal data class CompressionReport(
    val found: Boolean? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("manual_review") val manualReview: Boolean? = null,
    @SerialName("total_segments") val totalSegments: Int? = null,
    val segments: List<CompressionReportRow> = emptyList(),
    val children: List<CompressionReportRow> = emptyList(),
)
@Serializable
internal data class CompressionReportRow(
    @SerialName("session_id") val sessionId: String? = null,
    val source: String? = null,
    val role: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    val active: Boolean? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("started_at") val startedAt: Double? = null,
)

/** Recover only an unbranched compression handoff at its observed lifecycle boundary. */
internal fun confirmCompressionReport(parent: SessionSummary, rows: List<SessionSummary>, report: CompressionReport): SessionSummary {
    if (report.found != true || report.sessionId != parent.sessionId || report.manualReview != false ||
        report.totalSegments != 1 || report.segments.size != 1) return parent
    val ended = report.segments.single()
    if (ended.sessionId != parent.sessionId || ended.endReason != "compression" || ended.active != false) return parent
    val end = ended.updatedAt ?: return parent
    if (!end.isFinite() || end <= 0) return parent
    // updated_at is ended_at in this report. Child creation precedes parent closure
    // during compression; bound the handoff to five seconds and refuse competing children.
    val started = report.children.filter {
        val start = it.startedAt
        start != null && start.isFinite() && start > 0 && abs(start-end) <= 5.0
    }.singleOrNull() ?: return parent
    if (started.role != "child_session") return parent
    val child = rows.singleOrNull { it.sessionId == started.sessionId &&
        it.parentSessionId == parent.sessionId && it.profile == parent.profile } ?: return parent
    if (child.sessionId != started.sessionId || child.sessionId == parent.sessionId ||
        parent.isDelegatedSubagentSession || child.isDelegatedSubagentSession ||
        listOf(parent,child).any { it.sessionSource.equals("fork",true) || it.relationshipType?.lowercase() in setOf("fork","branch","subagent") }) return parent
    val surfaces = setOf("desktop","webui")
    if (ended.source !in surfaces || started.source !in surfaces ||
        (!parent.rawSource.isNullOrBlank() && parent.rawSource != ended.source) ||
        (!child.rawSource.isNullOrBlank() && child.rawSource != started.source)) return parent
    return parent.copy(preCompressionSnapshot=true,continuationSessionId=child.sessionId)
}
