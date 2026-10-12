package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class HubLayoutScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun responsiveNavigationAndReadOnlyPricingRemainUsable() {
        val args = InstrumentationRegistry.getArguments()
        val wide = args.getString("expectWide") == "true"
        val twoColumns = args.getString("expectTwoColumns") == "true"
        val dark = args.getString("visualDark") == "true"
        val suffix = args.getString("visualSuffix") ?: "phone"
        val actions = object : HubUiActions {
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
        val period = Period(5000000123, "1.25", emptyMap(), emptyMap(), emptyList(), emptyList(), null,
            TokenComponents(1000, 100, 10, 20, 4999998993))
        val state = HubState(snapshot = Snapshot("2026-10-09T00:00:00Z", mapOf("today" to period), emptyList(), emptyList(), false),
            pricing = PricingParser.parse("""{"ok":true,"version":1,"revision":1,"value":[{"modelId":"demo-model","inputPerM":0,"outputPerM":1.25,"cacheWrite1hPerM":2.5}]}"""))
        compose.activity.runOnUiThread { compose.activity.setContent { TokenTheme(dark) { HubScreen(state, actions) } } }
        compose.onNodeWithTag("hub-view-selector").assertIsDisplayed()
        compose.onNodeWithTag("hub-settings-toggle").assertIsDisplayed()
        compose.onNodeWithTag("hub-total-tokens").assertIsDisplayed()
        compose.onNodeWithText("5,000,000,123").assertIsDisplayed()
        File(compose.activity.getExternalFilesDir(null), "visual-layout-$suffix.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        openSharedSettings(compose,"pricing")
        compose.onNodeWithTag("hub-content").performScrollToNode(hasText("demo-model"))
        compose.onNodeWithText("demo-model").assertIsDisplayed()
        compose.onNodeWithTag("shared-price-demo-model").performClick()
        compose.onNodeWithText("输入(未命中缓存)：0（明确免费）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("输入(命中缓存)：未设置").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("缓存写入（1 小时）：2.5").performScrollTo().assertIsDisplayed()
        File(compose.activity.getExternalFilesDir(null), "visual-pricing-$suffix.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
