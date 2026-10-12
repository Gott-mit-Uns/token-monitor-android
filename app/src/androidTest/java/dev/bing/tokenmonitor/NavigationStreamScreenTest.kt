package dev.bing.tokenmonitor

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real HubViewModel HTTP/SSE over a synthetic peer; no production endpoint is contacted. */
class NavigationStreamScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun allEightPagesAndSettingsKeepOneForegroundStream() {
        val app=compose.activity.application as Application
        val generated=UUID.randomUUID().toString()
        val peer=NetworkFixtureHub(generated)
        val store=HubStore(app,"test-navigation-${UUID.randomUUID()}.bin")
        val owner=ViewModelStore()
        val client=HubViewModel.defaultClient().newBuilder().addInterceptor {chain->
            val request=chain.request()
            chain.proceed(request.newBuilder().url(request.url.newBuilder().host("127.0.0.1").build()).build())
        }.build()
        lateinit var model:HubViewModel
        try {
            compose.runOnUiThread {model=HubViewModel(app,store,client);owner.put("navigation",model)}
            compose.waitUntil(10000) {!model.state.value.loading}
            compose.runOnUiThread {model.updateNetworkScope(fixtureLocalScope());model.setForeground(true);model.connect("http://192.168.1.1:${peer.port}",generated,"")}
            compose.waitUntil(10000) {model.state.value.live && peer.streams.get()==1}
            val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
            compose.activity.runOnUiThread {compose.activity.setContent {val state by model.state.collectAsState();TokenTheme(false,prefs.language,prefs.appearance) {HubScreen(state,model,preferences=prefs)}}}
            for(id in MainDisplay.ids) {
                compose.onNodeWithTag("hub-view-selector").performClick()
                compose.onNodeWithTag("view-$id").performScrollTo().performClick()
                compose.waitForIdle()
                assertEquals("Navigation must not replace the live stream",1,peer.streams.get())
                assertEquals(0,peer.closedStreams.get())
            }
            compose.runOnUiThread {model.setForeground(false)}
            compose.waitUntil(10000) {peer.closedStreams.get()==1}
            compose.runOnUiThread {model.setForeground(true)}
            compose.waitUntil(10000) {peer.streams.get()==2 && model.state.value.live}
            assertEquals(0,peer.badAuthentication.get())
        } finally {
            compose.runOnUiThread {owner.clear()};peer.close();store.clear();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()
        }
    }
}
