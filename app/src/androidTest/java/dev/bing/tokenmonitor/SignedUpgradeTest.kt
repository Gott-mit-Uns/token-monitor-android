package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Separate processes on either side of a signed APK replacement; isolated public stores only. */
class SignedUpgradeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val hubName = "test-signed-upgrade-hub.bin"
    private val prefsName = "test-signed-upgrade-preferences.bin"
    private val uidName = "test-signed-upgrade-uid.txt"
    private val body = """{"periods":{"today":{"totalTokens":1234}},"devices":[]}"""
    private val history = """{"daily":[{"date":"2026-10-12","tokens":1234}],"monthly":[]}"""
    private fun savedHub() = SavedHub("https://example.invalid", "public-upgrade-fixture", body, 1,
        alternate = "http://192.168.1.1", history = history, historySignature = "public-history",
        deviceAliases = mapOf("public-device" to "办公 Public Device 龘𠮷"), observedModelIds = listOf("public-model")).also {
        it.lanEnabled = false
        it.endpointsAssociated = true
    }
    private fun profiles() = linkedMapOf(
        "primary" to DisplayPreferences(theme = ThemeChoice.DARK, hideTitles = true, language = LanguageChoice.ENGLISH,
            currency = "CNY", currencyRates = mapOf("CNY" to "7"), lastPeriod = "last30", monthMode = "last30",
            sessionTitlesEnabled = false, appearance = AppearancePreferences(textSize = "largest",
                colors = mapOf("bg" to "#0b0c0e", "chart" to "#123456"), reduceMotion = "on")),
        "system" to DisplayPreferences(theme = ThemeChoice.LIGHT, appearance = AppearancePreferences(textSize = "larger",
            interfaceFont = DesktopAppearance.systemFont, displayFont = DesktopAppearance.systemFont)),
        "mono" to DisplayPreferences(theme = ThemeChoice.SYSTEM, appearance = AppearancePreferences(textSize = "largest",
            interfaceFont = DesktopAppearance.defaultInterfaceFont, displayFont = DesktopAppearance.defaultInterfaceFont)),
        "custom" to DisplayPreferences(theme = ThemeChoice.DARK, appearance = AppearancePreferences(textSize = "largest",
            interfaceFont = "\"TokenMonitorMissingPublicFont, monospace\", serif", displayFont = "sans-serif-condensed"))
    )
    private fun preferenceFile(profile: String) = if (profile == "primary") prefsName else "test-signed-upgrade-preferences-$profile.bin"
    private fun checkVersion() = assertEquals(InstrumentationRegistry.getArguments().getString("expectedVersion"),
        context.packageManager.getPackageInfo(context.packageName, 0).versionName)
    private fun fingerprint(name: String): String = MessageDigest.getInstance("SHA-256")
        .digest(File(context.noBackupFilesDir, name).readBytes()).joinToString("") { "%02x".format(it) }
    private fun receipt() {
        println("PUBLIC_UPGRADE_PROFILE ${fingerprint(hubName)} ${fingerprint(prefsName)}")
        println("PUBLIC_UPGRADE_UID ${context.applicationInfo.uid}")
        profiles().keys.forEach { profile -> println("PUBLIC_UPGRADE_FONT_PROFILE $profile ${fingerprint(preferenceFile(profile))}") }
    }

    @Test fun seedIsolatedPublicStoresInInstalledVersion() {
        checkVersion()
        HubStore(context,hubName).save(savedHub())
        profiles().forEach { (profile, preferences) -> DisplayPreferenceStore(context,preferenceFile(profile)).save(preferences) }
        File(context.noBackupFilesDir,uidName).writeText(context.applicationInfo.uid.toString())
        receipt()
    }
    @Test fun upgradedVersionDecryptsOldHistoryAndRetainsPreferences() {
        checkVersion()
        assertEquals("Signed replacement must retain the installed application's UID",
            File(context.noBackupFilesDir,uidName).readText().toInt(), context.applicationInfo.uid)
        val files = listOf(hubName) + profiles().keys.map(::preferenceFile)
        val beforeRead = files.associateWith(::fingerprint)
        val saved=HubStore(context,hubName).load()!!
        val expected = savedHub()
        assertTrue("Encrypted connection and associated LAN configuration survive replacement",
            saved.address == expected.address && saved.alternate == expected.alternate &&
                saved.credential == expected.credential && saved.lanEnabled == expected.lanEnabled &&
                saved.endpointsAssociated == expected.endpointsAssociated)
        assertEquals(body,saved.snapshot)
        assertEquals(history,saved.history)
        assertEquals("public-history",saved.historySignature)
        assertEquals(expected.savedAt,saved.savedAt)
        assertEquals(expected.deviceAliases,saved.deviceAliases)
        assertEquals(expected.observedModelIds,saved.observedModelIds)
        profiles().forEach { (profile, preferences) ->
            assertEquals("Retain $profile fonts, App text size, theme and presentation choices exactly",
                preferences, DisplayPreferenceStore(context,preferenceFile(profile)).load())
        }
        assertEquals("Cache and preference reads must not rewrite the inherited files", beforeRead, files.associateWith(::fingerprint))
        receipt()
    }
}
