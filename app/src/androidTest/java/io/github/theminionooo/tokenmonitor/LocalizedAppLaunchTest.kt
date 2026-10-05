package io.github.theminionooo.tokenmonitor

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.localization.LanguagePreferences
import org.junit.Rule
import org.junit.Test

/** Launch the actual app, including translated Context and ActivityResult permission registry. */
class LocalizedAppLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun chineseColdLaunchPreservesPermissionActivityOwner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        SecureConnectionStore(context).clear()
        SnapshotCache(context).clear()
        LanguagePreferences.set(context, "zh-CN")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitForIdle()
                compose.onNodeWithText("连接你的 Hub").assertExists()
            }
        } finally { LanguagePreferences.set(context, "system") }
    }
}
