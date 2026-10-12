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
    private val body = """{"periods":{"today":{"totalTokens":1234}},"devices":[]}"""
    private val history = """{"daily":[{"date":"2026-10-12","tokens":1234}],"monthly":[]}"""
    private fun checkVersion() = assertEquals(InstrumentationRegistry.getArguments().getString("expectedVersion"),
        context.packageManager.getPackageInfo(context.packageName, 0).versionName)
    private fun fingerprint(name: String): String = MessageDigest.getInstance("SHA-256")
        .digest(File(context.noBackupFilesDir, name).readBytes()).joinToString("") { "%02x".format(it) }
    private fun receipt() = println("PUBLIC_UPGRADE_PROFILE ${fingerprint(hubName)} ${fingerprint(prefsName)}")

    @Test fun seedIsolatedPublicStoresInInstalledVersion() {
        checkVersion()
        HubStore(context,hubName).save(SavedHub("https://example.invalid","public-upgrade-fixture",body,1,
            history=history,historySignature="public-history"))
        DisplayPreferenceStore(context,prefsName).save(DisplayPreferences(language=LanguageChoice.ENGLISH))
        receipt()
    }
    @Test fun upgradedVersionDecryptsOldHistoryAndRetainsPreferences() {
        checkVersion()
        val saved=HubStore(context,hubName).load()!!
        assertEquals(body,saved.snapshot)
        assertEquals(history,saved.history)
        assertEquals("public-history",saved.historySignature)
        assertEquals(LanguageChoice.ENGLISH,DisplayPreferenceStore(context,prefsName).load().language)
        receipt()
    }
}
