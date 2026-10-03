package io.github.theminionooo.tokenmonitor.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseUpdatesTest {
    @org.junit.Test fun cloudflareEditionCannotUseUpstreamInstaller() {
        org.junit.Assert.assertFalse(ReleaseUpdates.automaticUpdatesEnabled)
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { ReleaseUpdates.requireAutomaticUpdates() }
    }

    @Test fun comparesDesktopVersionsAndAndroidRevisions() {
        assertEquals(1, ReleaseUpdates.compareTags("android-v0.61.0-r1", "android-v0.60.0-r7"))
        assertEquals(1, ReleaseUpdates.compareTags("android-v0.62.0-r1", "android-v0.61.0-r2"))
        assertEquals(1, ReleaseUpdates.compareTags("android-v0.61.0-r2", "android-v0.61.0-r1"))
        assertEquals(0, ReleaseUpdates.compareTags("android-v0.61.0-r1", "android-v0.61.0-r1"))
        assertEquals(-1, ReleaseUpdates.compareTags("android-v0.60.0-r7", "android-v0.61.0-r1"))
    }

    @Test fun acceptsOnlyMatchingPublishedReleaseAssets() {
        val candidate = ReleaseUpdates.parseManifest(release(), manifest(), 610001)
        assertEquals("v0.62.0", candidate.versionName)
        assertEquals(620001, candidate.versionCode)
        assertEquals("token-monitor-android-v0.62.0-r1.apk", candidate.apkName)
    }

    @Test fun rejectsWrongPackageAndRollback() {
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseUpdates.parseManifest(release(), manifest().with("packageName", JsonPrimitive("other.app")), 610001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseUpdates.parseManifest(release(), manifest(), 620001)
        }
    }

    @Test fun rejectsUnpublishedReleaseAndWrongDownloadLocation() {
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseUpdates.parseManifest(release().with("draft", JsonPrimitive(true)), manifest(), 610001)
        }
        assertThrows(IllegalStateException::class.java) {
            ReleaseUpdates.parseManifest(release().with("assets", JsonArray(emptyList())), manifest(), 610001)
        }
    }

    private fun release(): JsonObject = Json.parseToJsonElement(
        """{
          "tag_name":"android-v0.62.0-r1",
          "draft":false,
          "prerelease":false,
          "name":"Android v0.62.0 r1",
          "body":"## What changed\\n- Update checking",
          "html_url":"https://github.com/The-Minion-oOo/token-monitor-android/releases/tag/android-v0.62.0-r1",
          "assets":[{
            "name":"token-monitor-android-v0.62.0-r1.apk",
            "browser_download_url":"https://github.com/The-Minion-oOo/token-monitor-android/releases/download/android-v0.62.0-r1/token-monitor-android-v0.62.0-r1.apk"
          }]
        }""",
    ).jsonObject

    private fun manifest(): JsonObject = Json.parseToJsonElement(
        """{
          "schemaVersion":1,
          "packageName":"io.github.theminionooo.tokenmonitor",
          "tag":"android-v0.62.0-r1",
          "versionName":"v0.62.0",
          "versionCode":620001,
          "apk":"token-monitor-android-v0.62.0-r1.apk",
          "sizeBytes":10000000,
          "sha256":"${"a".repeat(64)}"
        }""",
    ).jsonObject

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))
}
