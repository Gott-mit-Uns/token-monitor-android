package dev.bing.tokenmonitor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

/** Source settings has one open section. Navigate the real header before its controls. */
internal fun openSettingsSection(compose:AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>,id:String) {
    val tag=when(id) {"main"->"main-display-toggle";"appearance"->"appearance-toggle";"limits"->"limit-settings-toggle";else->"settings-$id-toggle"}
    if(compose.onAllNodesWithTag("settings-details-$id").fetchSemanticsNodes().isEmpty()) {
        compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
}

/** Shared read-only data belongs to settings, outside the main-view menu. */
internal fun openSharedSettings(compose:AndroidComposeTestRule<ActivityScenarioRule<MainActivity>,MainActivity>,page:String) {
    compose.onNodeWithTag("hub-settings-toggle").performClick()
    openSettingsSection(compose,if(page=="pricing") "tools" else "subscriptions")
    compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-open-$page"))
    compose.onNodeWithTag("settings-open-$page").performClick()
}
