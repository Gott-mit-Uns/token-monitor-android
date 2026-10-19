package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class EnglishScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun englishDetailsAndErrorsTranslateWithoutLosingAnalysisSelection() {
        val args = InstrumentationRegistry.getArguments()
        val dark = args.getString("visualDark") == "true"
        val suffix = args.getString("visualSuffix") ?: "phone"
        val preferences = mutableStateOf(DisplayPreferences(language = LanguageChoice.ENGLISH,homeModules=defaultHomeModules))
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
        val period = Period(5000000123, "1.25", emptyMap(), emptyMap(), emptyList(), emptyList(), null,
            TokenComponents(1000, 100, 10, 20, 4999998993))
        val state = HubState(error = R.string.error_revision_changed,
            snapshot = Snapshot("2026-10-09T00:00:00Z", mapOf("today" to period), listOf(Device("demo", "示例工作站", false, null, null, null,mapOf("today" to DeviceUsage(5000000123,"1.25",emptyMap(),null,null,null)),hubDisplayName="示例工作站")), emptyList(), false),
            pricing = PricingParser.parse("""{"ok":true,"version":1,"revision":1,"value":[{"modelId":"demo-model","inputPerM":0,"outputPerM":1.25,"cacheWrite1hPerM":2.5}]}"""),
            history = HubHistory(listOf(HistoryRow("2026-10-09", 100, "1.25")), emptyList()))
        compose.activity.runOnUiThread { compose.activity.setContent {
            TokenTheme(dark, preferences.value.language) { HubScreen(state, actions, preferences = preferences.value) }
        } }
        compose.onNodeWithText("Hub data changed. Load the details again.").assertIsDisplayed()
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed()
        compose.onNodeWithText("5,000,000,123").assertIsDisplayed()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("示例工作站"))
        compose.onNodeWithText("示例工作站").assertIsDisplayed()
        openSharedSettings(compose,"pricing")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("demo-model"))
        compose.onNodeWithTag("shared-price-demo-model").performClick()
        compose.onNodeWithText("Input (cache miss): 0 (explicitly free)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Input (cache hit): Unset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cache write (1 hour): 2.5").performScrollTo().assertIsDisplayed()
        File(compose.activity.getExternalFilesDir(null), "visual-english-pricing-$suffix.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { preferences.value = preferences.value.copy(language = LanguageChoice.CHINESE) }
        compose.onNodeWithTag("hub-settings-panel-motion").assertExists()
        
        compose.onNodeWithText("输入(未命中缓存)：0（明确免费）").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { preferences.value = preferences.value.copy(language = LanguageChoice.ENGLISH) }
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-open-pricing"));compose.onNodeWithTag("settings-open-pricing").performClick()
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.onNodeWithTag("view-trends").performScrollTo().performClick()
        compose.onNodeWithTag("trends-main-open-history").performClick()
        compose.onNodeWithTag("dashboard-open-records").performScrollTo().performClick()
        compose.onNodeWithText("7 days").assertDoesNotExist()
        compose.onNodeWithTag("records-day-grid").assertExists()
        File(compose.activity.getExternalFilesDir(null), "visual-english-history-$suffix.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        openSettingsSection(compose,"general")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Source and open-source licenses"))
        compose.onNodeWithText("Source and open-source licenses").performClick()
        compose.onNodeWithText("Source and licenses").assertIsDisplayed()
        compose.onNodeWithText("Token Monitor Android — source and open-source notices").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { preferences.value = preferences.value.copy(language = LanguageChoice.CHINESE) }
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("来源与开源许可"))
        compose.onNodeWithText("来源与开源许可").performClick()
        compose.onNodeWithText("来源与许可").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
    }
}
