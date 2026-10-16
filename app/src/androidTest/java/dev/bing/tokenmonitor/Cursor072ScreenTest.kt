package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class Cursor072ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun includedAndPrepaidRemainDistinctAcrossThemesAndLargeFonts() {
        val now=Instant.parse("2026-10-16T00:00:00Z")
        val limits=HubParser.parse("""{"periods":{},"limits":{"providers":[{"provider":"cursor","status":"ok","accountKey":"sha256:synthetic","accountEmail":"long-synthetic-account@example.invalid","planLabel":"Ultra","updatedAt":"2026-10-16T00:00:00Z","windows":[{"kind":"weekly","label":"Included usage","usedPercent":25,"showMeter":true},{"kind":"billing","label":"Prepaid balance","metric":"credits","remaining":12.34,"currency":"USD","showMeter":false}]}]}}""").limits
        val theme=mutableStateOf(false);val font=mutableStateOf(1f);val compact=mutableStateOf(false)
        val prefs=LimitDisplayPreferences(homeBars=true)
        val home=HomeQuotaLayout.project(limits,prefs,now=now).single()
        compose.activity.runOnUiThread {compose.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font.value)) {
                TokenTheme(theme.value,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion="on")) {
                    CompositionLocalProvider(LocalLimitDisplay provides prefs) {
                        Surface {Column(Modifier.widthIn(max=320.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            if(compact.value) HomeQuotaRow(home,open={}) else DesktopAccount(limits,now=now)
                        }}
                    }
                }
            }
        }}
        for(dark in listOf(false,true)) for(scale in listOf(1f,2f)) for(homeMode in listOf(false,true)) {
            compose.runOnIdle {theme.value=dark;font.value=scale;compact.value=homeMode}
            for(text in listOf("Included usage","75% left","Prepaid balance","$12.34","Reset time unknown")) {
                val node=compose.onNodeWithText(text,useUnmergedTree=true).performScrollTo().assertIsDisplayed()
                val layouts=mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
                val layout=layouts.single()
                assertFalse("$text height",layout.didOverflowHeight)
                for(line in 0 until layout.lineCount) {
                    assertFalse("$text ellipsis",layout.isLineEllipsized(line))
                    assertTrue("$text right edge",layout.getLineRight(line)<=layout.size.width+.5f)
                }
            }
            if(homeMode) compose.onNodeWithTag("home-quota-meter-0-1").assertDoesNotExist()
            compose.mainClock.advanceTimeByFrame();compose.waitForIdle()
            saveVisualEvidence(compose.activity,compose.onRoot(),"cursor-072-${if(homeMode) "home" else "limits"}-${if(dark) "dark" else "light"}-$scale.png")
        }
    }
}
