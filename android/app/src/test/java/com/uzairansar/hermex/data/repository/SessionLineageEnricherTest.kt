package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SessionLineageEnricherTest {
    private fun sessions() = listOf(SessionSummary(sessionId="old"),SessionSummary(sessionId="tip",parentSessionId="old"))
    @Test fun loadsOnlyMetadataForParentsAndMemoizes() = runTest {
        val enricher=SessionLineageEnricher()
        val requests=mutableListOf<String>()
        val fetch: suspend (String)->SessionSummary? = { id -> requests.add(id); SessionSummary(sessionId=id,preCompressionSnapshot=true,continuationSessionId="tip") }
        val rows=enricher.enrich(sessions(),now=0,fetch=fetch)
        assertEquals(listOf("old"),requests)
        assertEquals("tip",rows.first().continuationSessionId)
        assertNull(rows.first().activeStreamId)
        enricher.enrich(sessions(),now=10,fetch=fetch)
        assertEquals(1,requests.size)
    }
    @Test fun ignoresMismatchedIdentitiesAndProfiles() = runTest {
        val other=SessionLineageEnricher().enrich(sessions(),now=0) { SessionSummary(sessionId="wrong",preCompressionSnapshot=true,continuationSessionId="tip") }
        assertNull(other.first().continuationSessionId)
        val profile=SessionLineageEnricher().enrich(sessions().map {it.copy(profile="one")},now=0) { SessionSummary(sessionId=it,profile="two",preCompressionSnapshot=true,continuationSessionId="tip") }
        assertNull(profile.first().continuationSessionId)
    }
    @Test fun endpointFailureKeepsEveryRowVisible() = runTest {
        val result=SessionLineageEnricher().enrich(sessions(),now=0) { throw java.io.IOException("offline fixture") }
        assertEquals(2,result.size)
        assertNull(result.first().continuationSessionId)
    }
    @Test fun timeoutIsBoundedAndDoesNotFailTheList() = runTest {
        val result=SessionLineageEnricher().enrich(sessions(),now=0) { awaitCancellation() }
        assertEquals(2,result.size)
        assertEquals(6_000,testScheduler.currentTime)
    }
    @Test fun externalCancellationPropagates() = runTest {
        try {
            SessionLineageEnricher().enrich(sessions(),now=0) { throw CancellationException("cancel fixture") }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
    @Test fun requestFanoutIsBoundedAndOrdinaryRowsNeedNoRequests() = runTest {
        var concurrent=0; var peak=0; var count=0
        val many=(1..30).flatMap { listOf(SessionSummary(sessionId="p$it"),SessionSummary(sessionId="c$it",parentSessionId="p$it")) }
        SessionLineageEnricher().enrich(many,now=0) { id ->
            count++; concurrent++; peak=maxOf(peak,concurrent)
            delay(10); concurrent--; SessionSummary(sessionId=id)
        }
        assertEquals(20,count)
        assertEquals(2,peak)
        SessionLineageEnricher().enrich(listOf(SessionSummary(sessionId="ordinary")),now=0) { fail("Unexpected detail request");null }
    }
}
