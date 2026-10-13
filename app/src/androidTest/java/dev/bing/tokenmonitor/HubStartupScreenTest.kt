package dev.bing.tokenmonitor

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class HubStartupScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val actions = object : HubUiActions {
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
    @Test fun iconThenHomeAndColdProcessDoesNotRestoreSettings() {
        val startup = mutableStateOf(HubStartup.LOADING)
        val state = mutableStateOf(HubState(snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":123}},"devices":[]}""")))
        val session = AtomicReference("first-process")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            key(session.get()) { HubScreen(state.value, actions, startup = startup.value) }
        }
        compose.onNodeWithTag("hub-startup-icon").assertIsDisplayed()
        compose.onNodeWithTag("hub-settings-toggle").assertDoesNotExist()
        compose.onNodeWithTag("hub-content").assertDoesNotExist()
        saveVisualEvidence(compose.activity, compose.onRoot(), "startup-icon.png")
        compose.runOnIdle { startup.value = HubStartup.HOME }
        compose.onNodeWithTag("hub-startup").assertDoesNotExist()
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed()
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("connection-controls").assertExists()
        // A later refresh and ordinary Activity state restoration retain Settings.
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("connection-controls").assertExists()
        compose.onNodeWithTag("hub-startup").assertDoesNotExist()
        // A new ViewModel/process has a new key, ignoring the previous route.
        session.set("second-process")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed()
        compose.onNodeWithTag("connection-controls").assertDoesNotExist()
    }
    @Test fun firstInstallShowsConnectionConfiguration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { HubScreen(HubState(), actions, startup = HubStartup.SETTINGS) }
        compose.onNodeWithTag("connection-controls").assertExists()
        compose.onNodeWithTag("hub-startup").assertDoesNotExist()
    }
}
