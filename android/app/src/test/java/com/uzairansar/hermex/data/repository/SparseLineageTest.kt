package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.ui.sessions.collapseCompressionSegments
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SparseLineageTest {
    @Test fun refusesCrossProfileAndExplicitForkEvenWithWarmProof() = runTest {
        val old=SessionSummary(sessionId="old",profile="default",rawSource="webui")
        val tip=SessionSummary(sessionId="tip",profile="default",rawSource="webui",parentSessionId="old")
        val r=CompressionReport(found=true,sessionId="old",manualReview=false,totalSegments=1,
            segments=listOf(CompressionReportRow(sessionId="old",source="webui",endReason="compression",active=false,updatedAt=100.2)),
            children=listOf(CompressionReportRow(sessionId="tip",source="webui",role="child_session",startedAt=100.0)))
        val resolver=SparseLineageResolver()
        resolver.enrich(listOf(old,tip),{null},{r})
        for(other in listOf(tip.copy(profile="other"),tip.copy(relationshipType="fork"),tip.copy(parentSessionId="unrelated"))) {
            assertEquals(2,resolver.enrich(listOf(old,other),{null},{r}).collapseCompressionSegments().size)
        }
    }

    @Test fun legacyHiddenNodesUseServerDefaultProfileAndReportParentage() = runTest {
        val old=SessionSummary(sessionId="old",profile="default",rawSource="webui")
        val tip=SessionSummary(sessionId="tip",profile="default",rawSource="webui",updatedAt=2.0)
        val hidden=SessionSummary(sessionId="hidden",rawSource="webui")
        val output=SparseLineageResolver().enrich(listOf(old,tip),{ hidden },{ id ->
            CompressionReport(found=true,sessionId=id,manualReview=false,totalSegments=1,
                segments=listOf(CompressionReportRow(sessionId=id,source="webui",endReason=if(id=="tip")"idle_timeout" else "compression",active=false,updatedAt=100.2)),
                children=if(id=="tip")emptyList() else listOf(CompressionReportRow(sessionId=if(id=="old")"hidden" else "tip",source="webui",role="child_session",startedAt=100.0)))
        })
        assertEquals(1,output.collapseCompressionSegments().size)
    }
    @Test fun traversesLongMissingChainWithoutAddingHiddenRows() = runTest {
        val all=(0..23).map { SessionSummary(sessionId="s$it",parentSessionId=if(it==0)null else "s${it-1}",profile="p",rawSource="webui",updatedAt=it.toDouble()) }
        val visible=listOf(all[0],all[5],all[23],SessionSummary(sessionId="independent",title="same",profile="p"))
        val resolver=SparseLineageResolver()
        val output=resolver.enrich(visible,{ id -> all.first { it.sessionId==id } },{ id ->
            val i=id.removePrefix("s").toInt()
            CompressionReport(found=true,sessionId=id,manualReview=false,totalSegments=1,
                segments=listOf(CompressionReportRow(sessionId=id,source="webui",endReason="compression",active=false,updatedAt=100.2)),
                children=listOf(CompressionReportRow(sessionId="s${i+1}",source="webui",role="child_session",startedAt=100.0)))
        })
        assertEquals(4,output.size)
        assertEquals(2,output.collapseCompressionSegments().size)
        assertEquals("s23",output.collapseCompressionSegments().first().sessionId)
    }
}
