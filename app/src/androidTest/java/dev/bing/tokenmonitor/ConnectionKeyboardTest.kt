package dev.bing.tokenmonitor

import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** Empty connection form only: no addresses or credentials are entered or captured. */
class ConnectionKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun connectButtonRemainsReachableWithSoftwareKeyboardOpen() {
        val displayStore=DisplayPreferenceStore(compose.activity)
        val previous=displayStore.load()
        val baseline=previous.copy(language=LanguageChoice.CHINESE)
        compose.activityRule.scenario.onActivity {ViewModelProvider(it)[HubViewModel::class.java].updateDisplay(baseline)}
        compose.waitUntil(5000) {displayStore.load()==baseline}
        val animating = AtomicBoolean()
        compose.runOnIdle {
            ViewCompat.setWindowInsetsAnimationCallback(compose.activity.window.decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animating.set(true)
                    }
                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animating.set(false)
                    }
                    override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>) = insets
                })
        }
        try {
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("connection-secret"))
        compose.onNodeWithTag("connection-secret").performClick()
        compose.waitUntil(5000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true && !animating.get() }
        compose.waitForIdle()
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("连接并保存入口"))
        compose.onNodeWithText("连接并保存入口").assertIsDisplayed()
        // The button and focused field fit above the keyboard, without submitting the form.
        compose.waitUntil(5000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        } finally {
            compose.runOnIdle { ViewCompat.setWindowInsetsAnimationCallback(compose.activity.window.decorView, null) }
            displayStore.save(previous)
        }
    }
}
