package dev.bing.tokenmonitor

import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DisplayPreferencesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun presentationSettingsSurviveActivityRecreation() {
        val store = DisplayPreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = store.load()
        val baseline=previous.copy(language=LanguageChoice.CHINESE)
        try {
            compose.activityRule.scenario.onActivity {ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(baseline)}
            compose.waitUntil(5000) {store.load()==baseline}
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"appearance")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-theme-DARK"));compose.onNodeWithTag("settings-theme-DARK").performClick()
            compose.waitUntil(5000) { store.load().theme == ThemeChoice.DARK }
            compose.onNodeWithTag("settings-theme-DARK").assertIsSelected()
            openSettingsSection(compose,"main")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasContentDescription("隐藏项目与会话标题"))
            compose.onNodeWithContentDescription("隐藏项目与会话标题").performClick()
            compose.waitUntil(5000) { store.load() == DisplayChange.HideTitles(!baseline.hideTitles).apply(DisplayChange.Theme(ThemeChoice.DARK).apply(baseline)) }
            compose.activityRule.scenario.recreate()
            openSettingsSection(compose,"main")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasContentDescription("隐藏项目与会话标题"))
            compose.onNodeWithContentDescription("隐藏项目与会话标题").let { if (previous.hideTitles) it.assertIsOff() else it.assertIsOn() }
            openSettingsSection(compose,"appearance")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-theme-DARK"));compose.onNodeWithTag("settings-theme-DARK").assertIsSelected()
            compose.onNodeWithTag("settings-theme-LIGHT").performClick()
            compose.waitUntil(5000) { store.load().theme == ThemeChoice.LIGHT }
        } finally { store.save(previous) }
    }
}
