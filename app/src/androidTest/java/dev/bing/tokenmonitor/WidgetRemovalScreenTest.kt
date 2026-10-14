package dev.bing.tokenmonitor

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Removal is verified against the installed package and real General section, not deleted classes. */
class WidgetRemovalScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val dark get() = InstrumentationRegistry.getArguments().getString("visualDark") != "false"
    private val clock = DesktopAnimationClock { compose.mainClock.currentTime * 1_000_000L }

    @Suppress("DEPRECATION", "DiscouragedApi")
    @Test fun installedPackageHasNoWidgetProvidersConfigurationResourcesOrBootPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES or
                PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
        val oldProviders = listOf("dev.bing.tokenmonitor.HubWidgetProvider", "dev.bing.tokenmonitor.HubQuotaWidgetProvider")
        val oldConfiguration = "dev.bing.tokenmonitor.WidgetConfigurationActivity"
        val receivers = info.receivers.orEmpty().map { it.name }.toSet()
        oldProviders.forEach { name ->
            assertFalse("The installed manifest must remove $name, including disabled components", name in receivers)
        }
        assertFalse("Widget configuration must no longer resolve as an installed Activity",
            info.activities.orEmpty().any { it.name == oldConfiguration })
        (oldProviders + oldConfiguration).forEach { name ->
            assertTrue("Removed component $name must also be absent from the installed application code",
                runCatching { context.classLoader.loadClass(name) }.exceptionOrNull() is ClassNotFoundException)
        }
        assertFalse("Widget boot restoration must not leave RECEIVE_BOOT_COMPLETED permission",
            Manifest.permission.RECEIVE_BOOT_COMPLETED in info.requestedPermissions.orEmpty())

        val manager = AppWidgetManager.getInstance(context)
        assertTrue("The system widget picker must not list this package",
            manager.installedProviders.none { it.provider.packageName == context.packageName })
        oldProviders.forEach { name ->
            assertTrue("Removed provider $name must retain no system widget IDs after replacement",
                manager.getAppWidgetIds(ComponentName(context.packageName, name)).isEmpty())
        }
        legacyResources.forEach { (kind, names) ->
            names.trim().split(Regex("\\s+")).forEach { name ->
                assertEquals("The installed resource table must remove $kind/$name", 0,
                    context.resources.getIdentifier(name, kind, context.packageName))
            }
        }
    }

    @Test fun actualGeneralSectionOmitsWidgetEntriesAndKeepsAboutActionsAndCompleteLicenses() {
        val preferences = mutableStateOf(DisplayPreferences(language = LanguageChoice.CHINESE, homeModules = listOf("devices")))
        val state = HubState(snapshot = HubParser.parse("""{"periods":{"today":{"totalTokens":1234}},"devices":[]}"""),
            history = HubHistory(emptyList(), emptyList()), modelNames = ModelNames(emptyMap(), "off"))
        val actions = Actions()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                TokenTheme(dark, preferences.value.language, preferences.value.appearance) {
                    CompositionLocalProvider(LocalDesktopAnimationClock provides clock) {
                        key(preferences.value.language) {
                            HubScreen(state, actions, preferences = preferences.value,
                                updateDisplay = { preferences.value = it.apply(preferences.value) })
                        }
                    }
                }
            }
        }
        for (language in listOf(LanguageChoice.CHINESE, LanguageChoice.ENGLISH)) {
            compose.runOnIdle { preferences.value = preferences.value.copy(language = language) }
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose, "general")
            compose.onNodeWithTag("settings-details-general").assertExists()
            compose.onNodeWithTag("settings-language").assertExists()
            listOf("小组件", "widget", "Hub 用量", "Hub usage", "缓存额度", "Cached quotas").forEach { text ->
                compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true)
                    .assertCountEquals(0)
            }
            listOf("android-update-check", "android-update-current-notes", "android-update-releases", "about-open-licenses")
                .forEach { tag ->
                    compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
                    compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
                }
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("about-android-version"))
            compose.onNodeWithTag("about-android-version").assertIsDisplayed()
            saveVisualEvidence(compose.activity, compose.onRoot(),
                "settings064-general-no-widgets-${language.name.lowercase()}-${if (dark) "dark" else "light"}.png")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("about-open-licenses"))
            compose.onNodeWithTag("about-open-licenses").performClick()
            compose.onNodeWithTag("about-licenses-content").performScrollToNode(hasText("MIT License"))
            compose.onNodeWithText("MIT License").assertExists()
            compose.onNodeWithText(if (language == LanguageChoice.CHINESE) "关闭" else "Close").performClick()
        }
        assertEquals("Opening General and licenses must not request Hub data", 0, actions.reads)
    }

    private class Actions : HubUiActions {
        var reads = 0
        override fun connect(address: String, credential: String, alternate: String) { reads++ }
        override fun refreshSaved() { reads++ }
        override fun switchEndpoint() { reads++ }
        override fun hasAlternate() = false
        override fun endpointType() = "https"
        override fun loadHistory() { reads++ }
        override fun loadModelNames() { reads++ }
        override fun loadPricing() { reads++ }
        override fun loadDeviceHistory() { reads++ }
        override fun loadSubscriptions() { reads++ }
        override fun forget() {}
        override fun renameDevice(id: String, input: String) {}
    }

    // Inventory of the 0.2.63 candidate widget resources. String lookup survives their deletion from R.
    private val legacyResources = mapOf(
        "xml" to "hub_widget_info hub_quota_widget_info",
        "layout" to "hub_widget hub_quota_widget hub_dashboard_widget",
        "drawable" to "widget_dark_background widget_light_background",
        "dimen" to "widget_corner_radius",
        "color" to """
            widget_dark_accent widget_dark_secondary widget_dark_surface widget_dark_text
            widget_light_accent widget_light_secondary widget_light_surface widget_light_text
        """,
        "id" to """
            quota_widget_account_1 quota_widget_account_2 quota_widget_account_3 quota_widget_configure
            quota_widget_more quota_widget_open quota_widget_root quota_widget_time quota_widget_title
            widget_activity widget_activity_detail widget_activity_section widget_activity_title
            widget_bar_1 widget_bar_2 widget_bar_3 widget_bar_4 widget_bottom_sections widget_breakdown_section
            widget_configure widget_cost widget_detail_1 widget_detail_2 widget_detail_3 widget_detail_4
            widget_detail_title widget_divider_1 widget_divider_2 widget_icon_1 widget_icon_2 widget_icon_3 widget_icon_4
            widget_metric_section widget_open widget_period widget_quota_1 widget_quota_2 widget_quota_name_1
            widget_quota_name_2 widget_quota_row_2 widget_quota_section widget_quota_title widget_root
            widget_row_1 widget_row_2 widget_row_3 widget_row_4 widget_time widget_title widget_tokens
            widget_trend widget_trend_change widget_value_1 widget_value_2 widget_value_3 widget_value_4
        """,
        "string" to """
            quota_widget_add quota_widget_cache_age quota_widget_compact_age quota_widget_compact_more
            quota_widget_compact_open quota_widget_compact_stale quota_widget_compact_title quota_widget_description
            quota_widget_empty quota_widget_missing quota_widget_more_accounts quota_widget_more_periods
            quota_widget_name quota_widget_open quota_widget_settings_note quota_widget_source_stale
            quota_widget_stale_age quota_widget_title widget_add widget_add_manually widget_align_account_missing
            widget_align_activity widget_align_activity_accessible widget_align_activity_known widget_align_age_unknown
            widget_align_automatic widget_align_breakdown widget_align_cache widget_align_cancel
            widget_align_compact_cache widget_align_compact_stale widget_align_compact_unknown widget_align_custom
            widget_align_dashboard widget_align_edit widget_align_edit_short widget_align_empty_short
            widget_align_expand widget_align_expand_short widget_align_instance widget_align_instances
            widget_align_missing_history widget_align_mode widget_align_models widget_align_month
            widget_align_no_accounts widget_align_no_breakdown widget_align_note widget_align_period
            widget_align_quota widget_align_quota_global widget_align_quota_note widget_align_save widget_align_scope
            widget_align_settings_note widget_align_stale widget_align_summary widget_align_title widget_align_today
            widget_align_tools widget_align_total widget_align_trend_accessible widget_align_unreadable_short
            widget_brand widget_cost widget_cost_formatted widget_cost_partial widget_cost_partial_formatted
            widget_cost_unknown widget_description widget_empty widget_name widget_no_value widget_open
            widget_period widget_settings_note widget_times widget_tokens_accessible widget_unreadable
        """
    )
}
