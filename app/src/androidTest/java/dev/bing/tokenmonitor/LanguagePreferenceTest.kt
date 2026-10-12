package dev.bing.tokenmonitor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class LanguagePreferenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun languageSwitchPersistsAndRestoresAfterActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DisplayPreferenceStore(context)
        val previous = store.load()
        try {
            compose.activityRule.scenario.onActivity { ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(previous.copy(language = LanguageChoice.CHINESE)) }
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"general")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-language-select"));compose.onNodeWithTag("settings-language-select").performClick()
            compose.onNodeWithTag("settings-language-ENGLISH").performClick()
            compose.onNode(hasContentDescription("Settings") and hasClickAction()).assertIsDisplayed()
            compose.waitUntil(5000) { store.load().language == LanguageChoice.ENGLISH }
            compose.activityRule.scenario.recreate()
            compose.onNode(hasContentDescription("Settings") and hasClickAction()).assertIsDisplayed()
            compose.onNodeWithTag("hub-content").performScrollToNode(hasText("Hub credential"))
            compose.onNodeWithText("Hub credential").assertIsDisplayed()
            openSettingsSection(compose,"general")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-language-select"));compose.onNodeWithTag("settings-language-select").performClick()
            compose.onNodeWithTag("settings-language-CHINESE").performClick()
            compose.onNode(hasContentDescription("设置") and hasClickAction()).assertIsDisplayed()
            compose.waitUntil(5000) { store.load().language == LanguageChoice.CHINESE }
        } finally { store.save(previous) }
    }
    @Test fun rapidDisplayChoicesDoNotOverwriteEachOtherBeforeRecomposition() {
        val store = DisplayPreferenceStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = store.load()
        try {
            compose.activityRule.scenario.onActivity {
                val model = ViewModelProvider(it)[HubViewModel::class.java]
                model.updateDisplay(DisplayPreferences())
                model.changeDisplay(DisplayChange.Theme(ThemeChoice.DARK))
                model.changeDisplay(DisplayChange.Language(LanguageChoice.ENGLISH))
                model.changeDisplay(DisplayChange.HideTitles(true))
                model.changeDisplay(DisplayChange.ToolPin("hermes"))
                model.changeDisplay(DisplayChange.ToolVisibility("claude"))
            }
            compose.waitUntil(5000) { store.load() == DisplayPreferences(ThemeChoice.DARK, true, LanguageChoice.ENGLISH, hiddenTools = setOf("claude"), pinnedTools = listOf("hermes")) }
        } finally { store.save(previous) }
    }
    @Test fun oldDisplayRecordRetainsThemeAndPrivacyWhenLanguageIsAdded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-language-${UUID.randomUUID()}.bin"
        val file = File(context.noBackupFilesDir, name)
        val store = DisplayPreferenceStore(context, name)
        try {
            // The actual v1 format from 0.1.3: version, dark theme, hidden titles.
            file.writeBytes(byteArrayOf(1, 2, 1))
            val legacy = store.load()
            assertEquals(ThemeChoice.DARK, legacy.theme)
            assertTrue(legacy.hideTitles)
            assertEquals(LanguageChoice.CHINESE, legacy.language)
            store.save(legacy.copy(language = LanguageChoice.ENGLISH))
            assertEquals(legacy.copy(language = LanguageChoice.ENGLISH), DisplayPreferenceStore(context, name).load())
        } finally { store.clear() }
    }
    @Test fun v2RecordMigratesAndHomeOrderSurvivesReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-home-order-${UUID.randomUUID()}.bin"
        val store = DisplayPreferenceStore(context, name)
        try {
            File(context.noBackupFilesDir, name).writeBytes(byteArrayOf(2, 2, 1, LanguageChoice.ENGLISH.ordinal.toByte()))
            val old = store.load()
            assertEquals(DisplayPreferences(ThemeChoice.DARK, true, LanguageChoice.ENGLISH,homeModules=defaultHomeModules), old)
            val reordered = DisplayChange.HomeModules(listOf("trends", "devices", "tools")).apply(old)
            store.save(reordered)
            assertEquals(reordered, DisplayPreferenceStore(context, name).load())
            assertEquals(listOf("activity", "devices", "tools"), store.load().homeModules)
        } finally { store.clear() }
    }
    @Test fun v3RecordAndV4ToolsRetainAllChoicesAcrossNewInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-tool-order-${UUID.randomUUID()}.bin"
        val store = DisplayPreferenceStore(context, name)
        val file = File(context.noBackupFilesDir, name)
        try {
            file.writeBytes(byteArrayOf(3, 2, 1, 1, 3, 5, 3, 1))
            val old = store.load()
            assertEquals(DisplayPreferences(ThemeChoice.DARK, true, LanguageChoice.ENGLISH, listOf("trends", "devices", "tools")), old)
            val customized = old.copy(hiddenTools = setOf("claude"), pinnedTools = listOf("hermes", "codex"))
            store.save(customized)
            assertEquals(16, file.readBytes()[0].toInt())
            assertEquals(customized, DisplayPreferenceStore(context, name).load())
            val manual = DisplayChange.ToolMove("opencode", -1).apply(customized)
            store.save(manual)
            assertEquals(manual, DisplayPreferenceStore(context, name).load())
            val goodBytes = file.readBytes()
            assertTrue(runCatching { store.save(manual.copy(pinnedTools = listOf("unknown-tool"))) }.isFailure)
            assertTrue(goodBytes.contentEquals(file.readBytes()))
            file.writeBytes(goodBytes + 0.toByte())
            assertTrue(runCatching { store.load() }.isFailure)
        } finally { store.clear() }
    }
}
