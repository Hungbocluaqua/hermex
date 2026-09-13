package com.uzairansar.hermex

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.ui.chat.ChatRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import mockwebserver3.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ChatRefreshInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val server=MockWebServer()
    @After fun close() { server.close() }
    @Test fun openChatRefreshesWithoutNavigationAndTechnicalHistoryIsReversible() {
        val fresh=AtomicInteger(0)
        val methods=CopyOnWriteArrayList<String>()
        server.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                methods.add(request.method)
                val body=when(request.url.encodedPath) {
                    "/api/session" -> """{"session":{"session_id":"fixture-refresh","messages":[
                        {"id":"1","role":"user","content":"Workshop question"},
                        {"id":"2","role":"assistant","content":"Initial answer"},
                        {"id":"3","role":"user","content":"Internal event","display_kind":"process_wakeup"},
                        {"id":"4","role":"assistant","content":"Technical acknowledgement","_source":"process_wakeup"}
                        ${if(fresh.get()>0) ",{\"id\":\"5\",\"role\":\"user\",\"content\":\"External question\"},{\"id\":\"6\",\"role\":\"assistant\",\"content\":\"${if(fresh.get()==1) "External answer received" else "Foreground answer restored"}\"}" else ""}
                    ]}}"""
                    else -> "{}"
                }
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
        val app=ApplicationProvider.getApplicationContext<Application>()
        val container=AppContainer(app)
        val owner=object: androidx.lifecycle.LifecycleOwner {
            val registry=androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get()=registry
        }
        compose.runOnUiThread { owner.registry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) { HermexTheme { ChatRoute(sessionId="fixture-refresh",serverId=server.url("/").toString(),repository=container.chatRepository(server.url("/")),onBack={},onOpenWorkspace={},onOpenGit={}) } } }
        compose.waitUntil(20_000){compose.onAllNodesWithText("Initial answer").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(compose.onAllNodesWithText("Internal event").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Technical acknowledgement").fetchSemanticsNodes().isEmpty())
        fresh.set(1)
        compose.waitUntil(20_000){compose.onAllNodesWithText("External answer received").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("External answer received").assertIsDisplayed()
        val bitmap=compose.onNodeWithTag("chat_transcript").captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null),"chat-refresh.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        compose.onNodeWithText("Afficher les événements techniques").performScrollTo().performClick()
        compose.onNodeWithText("Technical acknowledgement").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState=androidx.lifecycle.Lifecycle.State.CREATED }
        fresh.set(2)
        compose.runOnIdle { owner.registry.currentState=androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitUntil(20_000){compose.onAllNodesWithText("Foreground answer restored").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(methods.all { it=="GET" })
    }
}
