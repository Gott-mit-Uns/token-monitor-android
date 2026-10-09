package io.github.theminionooo.tokenmonitor

import android.graphics.Bitmap
import android.util.SizeF
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.ui.*
import io.github.theminionooo.tokenmonitor.widget.*
import org.junit.*
import java.io.File

class UnpricedUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun chinese() { io.github.theminionooo.tokenmonitor.localization.LanguagePreferences.set(context,"zh-CN") }
    @After fun cleanup() { io.github.theminionooo.tokenmonitor.localization.LanguagePreferences.set(context,"system") }
    private val stats = HubProtocolParser.decodeStats("""{"updatedAt":"2026-10-09T12:00:00Z","periods":{"today":{"totalTokens":1234567,"costUsd":1.25,"unpricedTokens":40000,"clients":{"codex":1234567},"clientUnpricedTokens":{"codex":40000},"models":{"gpt-test":1234567},"modelUnpricedTokens":{"gpt-test":40000},"projects":{"p":{"label":"NAS 项目","totalTokens":1234567,"costUsd":1.25,"unpricedTokens":40000}},"sessions":{"s":{"client":"codex","totalTokens":1234567,"costUsd":0,"unpricedTokens":40000,"usageSource":"codex-dots-local","usageCoverage":"observed-only"}}}}}""")
    @Test fun lightRows() = rows(InterfaceTheme.Porcelain,1f,"light")
    @Test fun darkLargeRows() = rows(InterfaceTheme.Obsidian,2f,"dark-large")
    private fun rows(theme: InterfaceTheme,scale:Float,name:String) {
        val palette=Palette.from(theme);val p=stats.periods.getValue("today")
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides palette,LocalDensity provides Density(LocalDensity.current.density,scale)) {
                MaterialTheme(colorScheme=tokenMonitorColors(palette),typography=tokenMonitorTypography(1)) {
                    LazyColumn(Modifier.width(360.dp).fillMaxHeight().background(palette.shell),contentPadding=PaddingValues(12.dp)) {
                        item { ProjectUsageRow(p.projects.single(),1f) }
                        item { SessionUsageRow(p.sessions.single(),"Codex Dots","合成示例",1f) }
                    }
                }
            }
        }
        compose.onNodeWithText("40,000 Token 未定价，费用仅含已知小计").assertExists()
        compose.onNodeWithText("Dots · 仅观测用量",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("— (?)").assertExists()
        compose.onRoot().captureToImage().asAndroidBitmap().let { b -> File(context.filesDir,"unpriced-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) } }
    }
    @Test fun deckAccessibilityAndBothThemes() {
        val data=prepareWidgetDeck(HubSnapshot(stats=stats),WidgetSession(),0)
        for(theme in listOf(InterfaceTheme.Obsidian,InterfaceTheme.Porcelain)) for(page in listOf(WidgetDeckPage.Overview,WidgetDeckPage.Breakdown,WidgetDeckPage.Activity)) {
            val summary=widgetDeckPageSummary(page,data)
            Assert.assertTrue(summary.contains("40,000"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val remote=WidgetDeckRenderer.render(context,page,data,theme,SizeF(360f,180f))
                val view=remote.apply(context,FrameLayout(context))
                Assert.assertNotNull(view)
                val bitmap=WidgetDeckRenderer.renderBitmap(context,page,data,Palette.from(theme),SizeF(360f,180f))
                File(context.filesDir,"unpriced-widget-${theme.isLight}-${page.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        }
    }
}
