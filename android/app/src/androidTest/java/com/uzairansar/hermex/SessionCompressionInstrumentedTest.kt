package com.uzairansar.hermex

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.ui.sessions.SessionListRoute
import com.uzairansar.hermex.ui.sessions.SessionListUiState
import com.uzairansar.hermex.ui.theme.HermexTheme
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SessionCompressionInstrumentedTest {
    private var server: MockWebServer? = null
    private val compose = createComposeRule()
    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun after() { server?.close() }
    }).around(compose)

    @Test fun rendersOneLinePreservesLiveNavigationAndCanRevealHistory() = runScenario(false)
    @Test fun missingRedirectAndCrossSurfaceLifecycleStillGroup() = runScenario(true)
    @Test fun inconclusiveSuccessfulReportFallsBackToMetadataRedirect() = runScenario(false,true)
    private fun runScenario(lifecycle: Boolean, inconclusive: Boolean = false) {
        val requests=CopyOnWriteArrayList<String>()
        val mock=MockWebServer().also { server=it }
        fun json(body:String)=MockResponse.Builder().code(200).body(body).build()
        mock.dispatcher=object : Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request.method+" "+request.url.encodedPath)
                return when(request.url.encodedPath) {
                    "/api/projects" -> json("""{"projects":[]}""")
                    "/api/profiles" -> json("""{"profiles":[],"single_profile_mode":true}""")
                    "/api/sessions" -> json("""{"sessions":[
                        {"session_id":"old","title":"Workshop discussion","last_message_at":1,"raw_source":"desktop"},
                        {"session_id":"middle","title":"Workshop discussion","parent_session_id":"old","last_message_at":2,"raw_source":"webui"},
                        {"session_id":"tip","title":"Workshop discussion","parent_session_id":"middle","last_message_at":3,"is_streaming":true,"active_stream_id":"fixture-stream","raw_source":"webui"},
                        {"session_id":"independent","title":"Independent conversation","last_message_at":4}
                    ],"archived_count":0}""")
                    "/api/session/lineage/report" -> {
                        if (inconclusive) json("""{"found":true,"session_id":"${request.url.queryParameter("session_id")}","manual_review":false,"total_segments":1,"segments":[],"children":[]}""")
                        else if (!lifecycle) MockResponse.Builder().code(404).body("{}").build() else {
                            val id=request.url.queryParameter("session_id")
                            val next=if(id=="old") "middle" else "tip"
                            val source=if(id=="old") "desktop" else "webui"
                            json("""{"found":true,"session_id":"$id","manual_review":false,"total_segments":1,"segments":[{"session_id":"$id","source":"$source","end_reason":"compression","active":false,"updated_at":100.2}],"children":[{"session_id":"$next","source":"webui","role":"child_session","started_at":100.0}]}""")
                        }
                    }
                    "/api/session" -> {
                        assertEquals("0",request.url.queryParameter("messages"))
                        assertEquals("0",request.url.queryParameter("resolve_model"))
                        if(request.url.queryParameter("session_id")=="old") json("""{"session":{"session_id":"old","pre_compression_snapshot":true,"continuation_session_id":"tip"}}""")
                        else json("""{"session":{"session_id":"middle"}}""")
                    }
                    else -> MockResponse.Builder().code(404).body("{}").build()
                }
            }
        }
        mock.start()
        val app=ApplicationProvider.getApplicationContext<Application>()
        val container=AppContainer(app)
        val url=mock.url("/")
        val opened=AtomicReference<String>()
        val account=ServerAccount(id=url.toString(),urlString=url.toString(),displayName="Fixture",initials="FX")
        compose.setContent {
            HermexTheme {
                SessionListRoute(
                    authState=AuthState.LoggedIn(url,account),container=container,
                    onOpenChat={opened.set(it)},onOpenVoiceChat={},onOpenSharedDraft={},
                    onOpenPanels={},onOpenKanban={},onOpenSettings={},onNeedsOnboarding={},
                )
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("session_row_tip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("session_row_tip").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Live").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("session_row_old").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("session_row_middle").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("session_row_independent").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val screenshot=compose.onNodeWithTag("session_list").captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),"compression-grouped.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
        screenshot.recycle()
        compose.onNodeWithTag("session_row_tip").performScrollTo().performClick()
        assertEquals("tip",opened.get())
        compose.onNodeWithText("Afficher les segments de compression").performScrollTo().performClick()
        compose.onNodeWithTag("session_row_old").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("old",opened.get())
        compose.onNodeWithText("Regrouper les conversations").performScrollTo().performClick()
        assertTrue(compose.onAllNodesWithTag("session_row_old").fetchSemanticsNodes().isEmpty())
        runBlocking {
            val cached=container.sessionRepository(url).loadCachedSessions()!!.sessions
            assertEquals(4,cached.size)
            assertEquals(2,SessionListUiState(sessions=cached).visibleSessions.size)
        }
        assertTrue(requests.all { it.startsWith("GET ") })
    }
}
