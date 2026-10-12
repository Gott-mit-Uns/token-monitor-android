package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** Component matrix is synthetic; actual system font acceptance runs separately. */
class Desktop069UiScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark=mutableStateOf(false)
    private val language=mutableStateOf(LanguageChoice.ENGLISH)
    private val scale=mutableFloatStateOf(1f)
    private fun heading()=if(language.value==LanguageChoice.CHINESE) "显示偏好设置" else "Display preferences"
    private fun summary()=if(language.value==LanguageChoice.CHINESE) "中文 · 深色 · 减少动态效果" else "English · Dark · Reduced motion"
    private fun choices()=if(language.value==LanguageChoice.CHINESE) listOf("跟随系统","浅色","深色") else listOf("System default","Light","Dark")
    private fun complete(text:String) {
        val node=compose.onNodeWithText(text,useUnmergedTree=true)
        node.assertIsDisplayed()
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val result=layouts.single()
        assertFalse("$text height",result.didOverflowHeight)
        for(line in 0 until result.lineCount) {
            assertFalse("$text line $line ellipsis",result.isLineEllipsized(line))
            assertTrue("$text right glyph bound",result.getLineRight(line)<=result.size.width+.5f)
            assertTrue("$text left glyph bound",result.getLineLeft(line)>=-.5f)
        }
    }
    @Test fun narrowSettingsLabelsAndChoicesRemainComplete() {
        compose.activity.runOnUiThread {compose.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.floatValue)) {
                TokenTheme(dark.value,language.value) {
                    Column(Modifier.width(180.dp).background(MaterialTheme.colorScheme.background)) {
                        DesktopSettingSection("matrix",heading(),R.drawable.desktop_view_tool,summary(),false,"matrix-section",toggle={}) {}
                        DesktopSettingInlineChoices("Appearance",listOf("system","light","dark").zip(choices()),"system","matrix-choice") {}
                    }
                }
            }
        }}
        for(theme in listOf(false,true)) for(lang in listOf(LanguageChoice.ENGLISH,LanguageChoice.CHINESE)) for(font in listOf(1f,2f)) {
            compose.runOnIdle {dark.value=theme;language.value=lang;scale.floatValue=font}
            complete(heading());complete(summary());choices().forEach(::complete)
            val bounds=listOf("system","light","dark").map {compose.onNodeWithTag("matrix-choice-$it").getUnclippedBoundsInRoot()}
            bounds.forEach {val density=compose.activity.resources.displayMetrics.density
                assertTrue("48dp must occupy the required physical pixels: $it",(it.height.value*density).roundToInt()>=(48*density).roundToInt())}
            bounds.zipWithNext().forEach {(a,b)->assertTrue("choices overlap",a.right<=b.left || a.bottom<=b.top)}
        }
    }
    @Test fun switcherAdaptsAndKeepsBothTouchTargetsAndActions() {
        var cycles=0;var menus=0
        compose.activity.runOnUiThread {compose.activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.floatValue)) {
                TokenTheme(dark.value,language.value) {
                    Box(Modifier.width(128.dp)) {DesktopViewSwitcher(HubPage.SESSIONS,HubPage.DEVICES,{cycles++},{menus++})}
                }
            }
        }}
        for(theme in listOf(false,true)) for(lang in listOf(LanguageChoice.ENGLISH,LanguageChoice.CHINESE)) for(font in listOf(1f,2f)) {
            compose.runOnIdle {dark.value=theme;language.value=lang;scale.floatValue=font}
            val current=compose.onNodeWithTag("hub-view-current");val menu=compose.onNodeWithTag("hub-view-selector")
            for(node in listOf(current,menu)) {val b=node.getUnclippedBoundsInRoot();assertTrue(b.width>=48.dp);assertTrue(b.height>=48.dp)}
            assertTrue(current.getUnclippedBoundsInRoot().right<=menu.getUnclippedBoundsInRoot().left)
            current.performClick();menu.performClick()
        }
        assertEquals(8,cycles);assertEquals(8,menus)
    }
    @Test fun actualSystemAboutLabelsRemainCompleteAndPackageVersionIsReal() {
        var observed:android.content.res.Resources?=null
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark.value,language.value) {observed=LocalResources.current;Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(8.dp)) {
                val colors=MaterialTheme.colorScheme
                assertTrue("Primary text contrast",androidx.core.graphics.ColorUtils.calculateContrast(colors.onSurface.toArgb(),colors.background.toArgb())>=4.5)
                assertTrue("Secondary text contrast",androidx.core.graphics.ColorUtils.calculateContrast(colors.onSurfaceVariant.toArgb(),colors.background.toArgb())>=4.5)
                AboutSettings()
            }}
        }}
        for(theme in listOf(false,true)) for(lang in listOf(LanguageChoice.ENGLISH,LanguageChoice.CHINESE)) {
            compose.runOnIdle {dark.value=theme;language.value=lang}
            compose.waitForIdle()
            val strings=observed!!
            complete(strings.getString(R.string.desktop_reference_version,"0.69.0"))
            complete(strings.getString(R.string.desktop_parity_partial))
            complete(strings.getString(R.string.hub_version_unknown))
            compose.onNodeWithTag("about-android-version").assertTextContains("0.2.51-dev (60)",substring=true)
            saveVisualEvidence(compose.activity,compose.onRoot(),"desktop069-about-${theme}-${lang.name}.png")
        }
    }

}
