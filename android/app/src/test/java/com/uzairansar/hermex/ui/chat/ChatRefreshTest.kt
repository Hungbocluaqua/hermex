package com.uzairansar.hermex.ui.chat

import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.network.*
import com.uzairansar.hermex.data.db.ServerCacheOwnership
import com.uzairansar.hermex.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import mockwebserver3.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRefreshTest {
    @Test fun continuationDraftNeverOverwritesAnExistingDraft() {
        val original=PersistedChatPendingState(draft="source")
        assertEquals("source",mergeContinuationDraft(original,PersistedChatPendingState()).draft)
        assertEquals("target\n\nsource",mergeContinuationDraft(original,PersistedChatPendingState(draft="target")).draft)
        assertEquals("source",mergeContinuationDraft(original,original).draft)
    }

    @Test fun compressionEventRetainsAttestedContinuation() {
        assertNotEquals(SseEvent.Ignored,SseEventDecoder.decode("compressed", """{"new_session_id":"next"}"""))
    }
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())
    @Test fun visibleIdleRefreshDiscoversExternalMessageAndPreservesDraft() = exerciseRefresh()
    @Test fun discoversNewStreamAndHandlesCompressedDoneWithoutTranscript() = exerciseRefresh(compressed=true)
    @Test fun discoversNewStreamAndHandlesCompressedMissingDone() = exerciseRefresh(compressed=true,missingDone=true)
    private fun exerciseRefresh(compressed:Boolean=false,missingDone:Boolean=false) = runTest {
        val body=AtomicReference("old")
        val server=MockWebServer()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                assertEquals("GET",request.method)
                if(request.url.encodedPath == "/api/chat/stream") {
                    assertEquals("new-stream",request.url.queryParameter("stream_id"))
                    return MockResponse.Builder().code(200).addHeader("Content-Type","text/event-stream")
                        .body("event: compressed\ndata: {\"new_session_id\":\"next\"}\n\n" +
                            if(missingDone) "" else "event: done\ndata: {\"session_id\":\"s\"}\n\n").build()
                }
                val active = if(compressed && body.get() != "old") ",\"active_stream_id\":\"new-stream\"" else ""
                val json=when(request.url.encodedPath) {
                    "/api/session" -> """{"session":{"session_id":"s"$active,"messages":[{"id":"m","role":"assistant","content":"${body.get()}"}]}}"""
                    else -> "{}"
                }
                return MockResponse.Builder().code(200).body(json).build()
            }
        }
        server.start()
        val http=OkHttpClient()
        val vm=ChatViewModel("s",ChatRepository(HermesApiClient(server.url("/"),http),RecordingCacheDao(),ServerCacheOwnership(),SseStreamClient(server.url("/"),http){emptyList()}))
        val store=androidx.lifecycle.ViewModelStore().also { it.put("fixture",vm) }
        try {
            withContext(Dispatchers.Default) { withTimeout(5000){vm.state.first{!it.isLoading}} }
            vm.updateDraft("keep this")
            body.set("external message")
            vm.refreshVisibleConversation()
            if(compressed) withContext(Dispatchers.Default) {
                withTimeout(10000) { vm.state.first { it.openSessionId == "next" } }
            } else assertEquals("external message",vm.state.value.messages.single().displayText)
            assertEquals("keep this",vm.state.value.draft)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); server.close() }
    }
}
