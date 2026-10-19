package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class Desktop075ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun wire():String {
        val at=Instant.now().minusSeconds(1)
        return """{"devices":[],"periods":{"today":{"sessions":{"cursor:bot":{"client":"cursor","sessionId":"bot","title":"Bot","totalTokens":10,"lastUsedAt":"$at","models":{"claude":10},"contextTokens":50,"contextWindow":100},"cursor:ordinary":{"client":"cursor","sessionId":"ordinary","title":"Ordinary","totalTokens":20,"lastUsedAt":"$at"}}},"month":{"sessions":{"cursor:bot":{"client":"cursor","sessionId":"bot","totalTokens":10,"lastUsedAt":"$at","models":{"grok-bot-default":10}},"cursor:ordinary":{"client":"cursor","sessionId":"ordinary","title":"Ordinary","totalTokens":20,"lastUsedAt":"$at"}}}}}"""
    }
    @Test fun homeDoesNotCountRecentBotAsRunningAndListKeepsItsGroup() {
        val state=HubState(snapshot=HubParser.parse(wire()),live=true,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"),appearance=AppearancePreferences(reduceMotion="on"))
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state,Actions(),preferences=prefs)}}}
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("home-sessions-running"))
        compose.onNodeWithTag("home-sessions-running",true).assertTextEquals("1 running")
        compose.onNodeWithTag("home-session-context-cursor:bot").assertDoesNotExist()
        saveVisualEvidence(compose.activity,compose.onRoot(),"desktop075-home-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        val bot="session-row-session-group:cursor-grok-bot"
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(bot))
        compose.onNodeWithTag(bot).assertExists()
        saveVisualEvidence(compose.activity,compose.onRoot(),"desktop075-sessions-${if(dark) "dark" else "light"}.png")
    }
    @Test fun encryptedColdRestoreRebuildsIdentityFromSameSnapshotOnly() {
        val name="test-desktop075-${UUID.randomUUID()}.bin"
        val store=HubStore(compose.activity,name)
        try {
            val body=wire()
            store.save(SavedHub("https://example.invalid","public-fixture",body,1,null))
            val loaded=HubStore(compose.activity,name).load()!!
            assertEquals(body,loaded.snapshot)
            val row=HubParser.parse(loaded.snapshot).periods.getValue("today").sessions.first {it.id=="cursor:bot"}
            assertTrue(SessionDisplay.isGrokBot(row));assertEquals("idle",SessionDisplay.activity(row,System.currentTimeMillis()))
            assertEquals(10L,row.tokens)
        } finally {store.clear()}
    }
}
