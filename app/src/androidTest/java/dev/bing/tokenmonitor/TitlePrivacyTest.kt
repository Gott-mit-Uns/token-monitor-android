package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TitlePrivacyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun hiddenTitlesRemainHiddenInDesktopListsAndSummaries() {
        val actions = object : HubUiActions {
            override fun connect(address: String, credential: String, alternate: String) {}
            override fun refreshSaved() {}
            override fun switchEndpoint() {}
            override fun hasAlternate() = false
            override fun endpointType() = "https"
            override fun loadModelNames() {}
            override fun loadHistory() {}
            override fun loadSubscriptions() {}
            override fun forget() {}
            override fun renameDevice(id: String, input: String) {}
        }
        val project = UsageRow("project-id", "Synthetic private project", 123, "0.25", "Codex", null, null)
        val session = HubParser.parse("""{"periods":{"today":{"sessions":{"session-id":{"title":"Synthetic shared session","tokens":123,"costUsd":"0.25","client":"codex"}}}}}""").periods.getValue("today").sessions.single()
        val period = Period(123, "0.25", emptyMap(), emptyMap(), listOf(project), listOf(session), null,
            TokenComponents(null, null, null, null, null))
        val state = HubState(snapshot = Snapshot(null, mapOf("today" to period), emptyList(), emptyList(), false))
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme { HubScreen(state, actions, preferences = DisplayPreferences(hideTitles = true)) }
        } }
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-projects").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("项目 摘要"))
        compose.onNodeWithText(project.label).assertDoesNotExist()
        compose.onNodeWithText("项目 摘要").performTouchInput {longClick()}
        compose.onNodeWithText("用量摘要").assertIsDisplayed()
        compose.onNodeWithText(project.label).assertDoesNotExist()
        compose.onNodeWithText("已知估算费用：$0.2500").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("session-row-session-id"))
        compose.onNodeWithTag("session-title-session-id",true).assertTextEquals("Codex")
        compose.onNodeWithText(session.label).assertDoesNotExist()
        compose.onNodeWithTag("session-row-session-id").performClick()
        compose.onNode(hasText("Codex") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText(session.label).assertDoesNotExist()
    }

    @Test fun revokedSharedTitleDisappearsFromOpenDetailsAndReloadedCache() {
        val context = compose.activity
        val name = "title-revocation-${java.util.UUID.randomUUID()}.bin"
        val store = HubStore(context, name)
        val sharedTitle = "Synthetic revocable title"
        fun body(title: String) = """{"periods":{"today":{"totalTokens":123,"sessions":{"session-id":{"sessionId":"source-session","title":"$title","tokens":123,"costUsd":"0.25","client":"codex"}}}}}"""
        val original = body(sharedTitle)
        val revoked = body("")
        val state = mutableStateOf(HubState(snapshot = HubParser.parse(original)))
        val actions = object : HubUiActions {
            override fun connect(address: String, credential: String, alternate: String) {}
            override fun refreshSaved() {}
            override fun switchEndpoint() {}
            override fun hasAlternate() = false
            override fun endpointType() = "https"
            override fun loadModelNames() {}
            override fun loadHistory() {}
            override fun loadSubscriptions() {}
            override fun forget() {}
            override fun renameDevice(id: String, input: String) {}
        }
        try {
            val record = SavedHub("https://example.invalid", java.util.UUID.randomUUID().toString(), original, 123)
            store.save(record)
            compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme { HubScreen(state.value, actions) } } }
            compose.onNodeWithTag("hub-view-selector").performClick()
            compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasText(sharedTitle))
            compose.onNodeWithText(sharedTitle).performClick()
            compose.onNode(hasText("$0.2500",substring=true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            // The production snapshot path replaces the encrypted raw stats; it never merges old rows.
            store.save(record.copy(snapshot = revoked))
            compose.runOnIdle { state.value = state.value.copy(snapshot = HubParser.parse(revoked)) }
            compose.onNodeWithText(sharedTitle).assertDoesNotExist()
            compose.onNode(hasText("source-session") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText("$0.2500",substring=true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            val restored = HubStore(context, name).load()!!
            assertFalse(restored.snapshot.contains(sharedTitle))
            assertEquals("source-session", HubParser.parse(restored.snapshot).periods.getValue("today").sessions.single().label)
        } finally { store.clear() }
    }
}
