package dev.bing.tokenmonitor

import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Explicitly opted-in acceptance on a task-owned emulator, using the production default stores.
 * Run each method separately: first the unconfigured test, then seed, then configured tests.
 * External force-stop/process-death evidence remains separate from Activity recreation evidence.
 * No test clears a store, uninstalls an app, or replaces MainActivity's production content.
 */
private object ColdProcessPublicProfile {
    const val snapshot = """{"periods":{"today":{"totalTokens":123,"cost":0.25},"allTime":{"totalTokens":123,"cost":0.25}},"devices":[]}"""
    const val address = "https://example.invalid"
    const val savedAt = 1234L

    fun requireOwnedEmulator() {
        val args = InstrumentationRegistry.getArguments()
        // Skip during general suites; this helper must never seed an ordinary device.
        assumeTrue("Requires explicit task-owned emulator opt-in", args.getString("ownedEmulator") == "true")
        assertTrue("Only a task-owned Android emulator may use the production-store fixture",
            Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") ||
                Build.PRODUCT.startsWith("sdk_gphone") || Build.PRODUCT.startsWith("sdk_google"))
    }

    fun isOurRecord(record: SavedHub): Boolean = record.address == address && record.credential.isEmpty() &&
        record.snapshot == snapshot && record.savedAt == savedAt && record.alternate == null

    fun preferences(): DisplayPreferences {
        val args = InstrumentationRegistry.getArguments()
        val theme = when (args.getString("coldTheme", "dark")) {
            "light" -> ThemeChoice.LIGHT
            "dark" -> ThemeChoice.DARK
            else -> error("coldTheme must be light or dark")
        }
        val language = when (args.getString("coldLanguage", "zh")) {
            "zh" -> LanguageChoice.CHINESE
            "en" -> LanguageChoice.ENGLISH
            else -> error("coldLanguage must be zh or en")
        }
        return DisplayPreferences(theme = theme, language = language,
            appearance = AppearancePreferences(reduceMotion = "on"))
    }
}

/** Has no Activity rule: seed finishes before a later invocation creates the real ViewModel. */
class ColdProcessAcceptanceSeedTest {
    @Test fun seedActualProfile() {
        ColdProcessPublicProfile.requireOwnedEmulator()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HubStore(context)
        val existing = store.load()
        assertTrue("Refusing to overwrite a production default store outside this public fixture",
            existing == null || ColdProcessPublicProfile.isOurRecord(existing))
        val record = SavedHub(ColdProcessPublicProfile.address, "", ColdProcessPublicProfile.snapshot,
            ColdProcessPublicProfile.savedAt,
            history = """{"daily":[],"monthly":[]}""",
            subscriptions = """{"subscriptions":[]}""",
            modelNames = """{"ok":true,"value":{"modelAliases":{},"modelAliasGrouping":"off"}}""",
            pricing = """{"ok":true,"version":1,"revision":1,"value":[]}""",
            historySignature = FixedPeriods.signature(HubParser.parse(ColdProcessPublicProfile.snapshot)))
        record.lanEnabled = false
        store.save(record)
        val preferences = ColdProcessPublicProfile.preferences()
        DisplayPreferenceStore(context).save(preferences)
        val loaded = HubStore(context).load()
        assertNotNull(loaded)
        assertTrue("Public fixture survives authenticated default-store round trip",
            ColdProcessPublicProfile.isOurRecord(loaded!!))
        assertEquals(123L, loaded.parsedSnapshot!!.periods.getValue("today").tokens)
        assertFalse(loaded.lanEnabled)
        assertEquals(preferences, DisplayPreferenceStore(context).load())
        assertTrue(File(context.noBackupFilesDir, "hub-state.bin").isFile)
        assertTrue(File(context.noBackupFilesDir, "display-preferences.bin").isFile)
    }
}

class ColdProcessAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun taskOwnedEmulatorOnly() { ColdProcessPublicProfile.requireOwnedEmulator() }

    private fun requirePublicProfile() {
        val saved = HubStore(compose.activity).load()
        assertNotNull("Seed the public default-store fixture in a separate invocation first", saved)
        assertTrue("Only this public fixture may be captured",
            saved != null && ColdProcessPublicProfile.isOurRecord(saved))
    }

    private fun awaitHome() {
        compose.waitUntil(45_000) {
            compose.onAllNodesWithTag("hub-total-tokens").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed().assertTextEquals("123")
        compose.onNodeWithTag("settings-general-toggle").assertDoesNotExist()
        compose.onNodeWithTag("hub-startup").assertDoesNotExist()
        compose.onNodeWithTag("hub-refresh").assertIsDisplayed()
        compose.onNodeWithTag("hub-settings-toggle").assertIsDisplayed()
    }

    private fun awaitSettings() {
        compose.waitUntil(45_000) {
            compose.onAllNodesWithTag("settings-general-toggle").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settings-general-toggle").assertIsDisplayed()
        compose.onNodeWithTag("hub-startup").assertDoesNotExist()
        compose.onNodeWithTag("hub-total-tokens").assertDoesNotExist()
    }

    private fun capture(scene: String) {
        val preferences = DisplayPreferenceStore(compose.activity).load()
        val suffix = "${preferences.theme.name.lowercase()}-${preferences.language.name.lowercase()}"
        val basename = "cold-process-actual-$scene-$suffix"
        saveVisualEvidence(compose.activity, compose.onRoot(), "$basename-public.png")
        val report = JSONObject().put("scenario", scene).put("pid", Process.myPid())
            .put("packageName", compose.activity.packageName).put("sdkInt", Build.VERSION.SDK_INT)
            .put("theme", preferences.theme.name).put("language", preferences.language.name)
            .put("productionMainActivity", true).put("contentReplaced", false)
            .put("defaultEncryptedStoreExists", File(compose.activity.noBackupFilesDir, "hub-state.bin").isFile)
            .put("defaultPreferenceStoreExists", File(compose.activity.noBackupFilesDir, "display-preferences.bin").isFile)
            .put("scope", "Task-owned emulator with public synthetic data; not physical phone evidence")
        File(compose.activity.getExternalFilesDir(null), "$basename-public.json").writeText(report.toString(2))
    }

    @Test fun verifyUnconfiguredColdSettings() {
        assertFalse("Run before seeding on the fresh task-owned emulator; do not clear an existing store",
            File(compose.activity.noBackupFilesDir, "hub-state.bin").exists())
        awaitSettings()
        compose.onNodeWithTag("connection-controls").assertExists()
        capture("unconfigured-settings")
    }

    @Test fun verifyActualColdHomeWithoutSettings() {
        requirePublicProfile()
        awaitHome()
        capture("home")
        // Failed public DNS/offline requests must leave the accepted encrypted cache intact.
        val retained = HubStore(compose.activity).load()
        assertTrue(retained != null && ColdProcessPublicProfile.isOurRecord(retained))
    }

    @Test fun navigateToSettingsAndRecreateRetainsSettings() {
        requirePublicProfile()
        awaitHome()
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        awaitSettings()
        capture("settings-before-recreate")
        compose.activityRule.scenario.recreate()
        awaitSettings()
        capture("settings-after-recreate")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitSettings()
        capture("settings-after-background-return")
    }
}
