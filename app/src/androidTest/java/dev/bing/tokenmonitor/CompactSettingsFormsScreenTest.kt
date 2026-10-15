package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Real Compose bounds and public fixtures; no production configuration is loaded. */
class CompactSettingsFormsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val arguments get()=InstrumentationRegistry.getArguments()
    private val dark get()=arguments.getString("visualDark")!="false"
    private val fontScale get()=arguments.getString("compactFontScale")?.toFloatOrNull() ?: 1f
    private val width get()=arguments.getString("compactWidth")?.toIntOrNull() ?: 320
    @Composable private fun Surface(content:@Composable ColumnScope.()->Unit) {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
            TokenTheme(dark,LanguageChoice.CHINESE) {
                Column(Modifier.width(width.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(12.dp).testTag("compact-settings-fixture"),content=content)
            }
        }
    }
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"compact-$name-${if(dark) "dark" else "light"}-$width-$fontScale.png")
    private fun fullText(tag:String) {
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(results)}
        assertTrue("Text must wrap naturally without clipping: $tag; "+results.joinToString {"text=${it.layoutInput.text}, size=${it.size}, constraints=${it.layoutInput.constraints}, lines=${it.lineCount}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}, paragraphWidth=${it.multiParagraph.width}, paragraphHeight=${it.multiParagraph.height}, lineBounds="+(0 until it.lineCount).joinToString {line->"${it.getLineLeft(line)}..${it.getLineRight(line)}"}},results.isNotEmpty() && results.none {it.hasVisualOverflow})
    }
    @Test fun fontRowsShareColumnsKeepValuesAndResetOnlyTheirOwnRole() {
        val preferences=mutableStateOf(DisplayPreferences(language=LanguageChoice.CHINESE))
        compose.activity.runOnUiThread {compose.activity.setContent {Surface {FontSettingsControls(preferences.value) {preferences.value=it.apply(preferences.value)}}}}
        val first=compose.onNodeWithTag("appearance-interface-select").getUnclippedBoundsInRoot()
        val second=compose.onNodeWithTag("appearance-display-select").getUnclippedBoundsInRoot()
        assertEquals(first.left.value,second.left.value,.5f);assertEquals(first.width.value,second.width.value,.5f)
        val firstLabel=compose.onNodeWithTag("appearance-interface-title",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val secondLabel=compose.onNodeWithTag("appearance-display-title",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertEquals(firstLabel.left.value,secondLabel.left.value,.5f)
        if(fontScale==1f) {assertTrue(first.top<firstLabel.bottom && first.bottom>firstLabel.top);assertTrue(second.top<secondLabel.bottom && second.bottom>secondLabel.top)}
        listOf("interface","display").forEach {role->
            fullText("appearance-$role-value")
            val reset=compose.onNodeWithTag("appearance-$role-reset").getUnclippedBoundsInRoot()
            assertTrue(reset.width>=48.dp && reset.height>=48.dp)
        }
        compose.onNodeWithTag("appearance-interface-reset").assertContentDescriptionEquals("重置界面字体")
        compose.onNodeWithTag("appearance-display-reset").assertContentDescriptionEquals("重置总量数字字体")
        compose.onNodeWithTag("appearance-interface-select").performClick();compose.onNodeWithTag("appearance-interface-mono").performClick()
        val changed=preferences.value.appearance.interfaceFont
        assertEquals(DesktopAppearance.fontFor("mono",display=false),changed)
        compose.onNodeWithTag("appearance-display-select").performClick();compose.onNodeWithTag("appearance-display-system").performClick()
        compose.onNodeWithTag("appearance-display-reset").performClick()
        assertEquals(changed,preferences.value.appearance.interfaceFont)
        assertEquals(DesktopAppearance.defaultDisplayFont,preferences.value.appearance.displayFont)
        compose.onNodeWithTag("appearance-interface-reset").performClick();assertEquals("",preferences.value.appearance.interfaceFont)
        save("fonts")
    }
    @Test fun customFontDraftSurvivesSavedStateWithoutPrematurelyChangingPreferences() {
        val preferences=mutableStateOf(DisplayPreferences(language=LanguageChoice.CHINESE))
        compose.activityRule.scenario.onActivity {activity->
            val content=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            repeat(content.childCount) {index->(content.getChildAt(index) as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()}
            content.removeAllViews()
        }
        val tester=StateRestorationTester(compose)
        tester.setContent {Surface {FontSettingsControls(preferences.value) {preferences.value=it.apply(preferences.value)}}}
        compose.onNodeWithTag("appearance-interface-select").performClick();compose.onNodeWithTag("appearance-interface-custom").performClick()
        compose.onNodeWithTag("appearance-interface-custom-field").performScrollTo().performTextReplacement("Public font family")
        assertEquals("",preferences.value.appearance.interfaceFont)
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("appearance-interface-custom-field").assertTextEquals("Public font family")
        compose.onNodeWithTag("appearance-interface-apply").performScrollTo().performClick()
        assertEquals("Public font family",preferences.value.appearance.interfaceFont)
        save("font-draft")
    }
    @Test fun pricingExpandsInPlacePagesSearchesAndTracksAuthoritativeUpdates() {
        val original=HubPricing(true,7,"2026-10-16T00:00:00Z",(0..40).map {ModelPrice("public-${it.toString().padStart(2,'0')}",mapOf("inputPerM" to BigDecimal("2.5"),"outputPerM" to BigDecimal.ZERO))})
        val prices=mutableStateOf<HubPricing?>(original);var reads=0
        compose.activityRule.scenario.onActivity {activity->
            val content=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            repeat(content.childCount) {index->(content.getChildAt(index) as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()}
            content.removeAllViews()
        }
        val tester=StateRestorationTester(compose)
        tester.setContent {Surface {InlinePricingSettings(prices.value,false) {reads++}}}
        compose.onNodeWithTag("settings-open-pricing").performClick();compose.onNodeWithTag("settings-open-pricing-details").assertExists()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithTag("shared-pricing-search").performScrollTo().performTextReplacement("public")
        compose.onNodeWithTag("shared-pricing-page").assertTextEquals("1/2")
        compose.onNodeWithTag("shared-pricing-next").performScrollTo().performClick()
        compose.onNodeWithTag("shared-pricing-page").assertTextEquals("2/2")
        compose.onNodeWithTag("shared-price-public-40").performScrollTo().performClick()
        compose.onNodeWithTag("shared-price-outputPerM").performScrollTo().assertTextContains("0（明确免费）",substring=true)
        tester.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settings-open-pricing-details").assertExists()
        compose.onNodeWithTag("shared-pricing-search").performScrollTo().assertTextEquals("public")
        compose.onNodeWithTag("shared-pricing-page").assertTextEquals("2/2")
        compose.onNodeWithTag("shared-price-inputPerM").performScrollTo().assertTextEquals("输入(未命中缓存)：2.5")
        compose.runOnIdle {prices.value=original.copy(models=original.models.map {if(it.model=="public-40") it.copy(rates=it.rates+("inputPerM" to BigDecimal("3.5"))) else it})}
        compose.onNodeWithTag("shared-price-inputPerM").performScrollTo().assertTextEquals("输入(未命中缓存)：3.5")
        compose.runOnIdle {prices.value=original.copy(models=emptyList())};compose.onNodeWithTag("shared-price-details").assertDoesNotExist()
        compose.runOnIdle {prices.value=original};compose.onNodeWithTag("shared-price-details").assertDoesNotExist()
        compose.onNodeWithTag("shared-pricing-search").performScrollTo().performTextReplacement("public-40")
        compose.onNodeWithTag("shared-price-public-40").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("shared-price-public-00").assertDoesNotExist()
        compose.onNodeWithTag("shared-pricing-refresh").performScrollTo().performClick();assertEquals(1,reads)
        assertEquals("2.5",original.models.last().rates.getValue("inputPerM").toPlainString())
        save("pricing")
        compose.activity.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("settings-open-pricing-details").assertDoesNotExist()
    }
    @Test fun quotaDescriptionBelongsToToggleAndSmallCheckboxKeepsFullTouchTarget() {
        val preferences=mutableStateOf(DisplayPreferences(language=LanguageChoice.CHINESE))
        compose.activity.runOnUiThread {compose.activity.setContent {Surface {
            LimitDisplayToggle("Weekly / Resets — 长说明会自然换行","compact-quota",true) {}
            LimitDisplaySettings(preferences.value,{preferences.value=it.apply(preferences.value)},embedded=true)
        }}}
        val target=compose.onNodeWithTag("compact-quota").getUnclippedBoundsInRoot()
        val visual=compose.onNodeWithTag("compact-quota-visual",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue(target.height>=48.dp);assertEquals(14f,visual.width.value,.5f)
        assertEquals((target.top.value+target.bottom.value)/2,(visual.top.value+visual.bottom.value)/2,.5f)
        compose.onNodeWithTag("limit-mask-emails").performScrollTo().performClick();assertTrue(preferences.value.limits.maskEmails)
        val row=compose.onNodeWithTag("limit-mask-emails").getUnclippedBoundsInRoot()
        val note=compose.onNodeWithTag("limit-mask-emails-description",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue(note.top>=row.top && note.bottom<=row.bottom)
        val title=compose.onNode(hasText("遮罩账户邮箱") and hasAnyAncestor(hasTestTag("limit-mask-emails")),useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertEquals("Title and description use the shared 2dp gap",2f,(note.top-title.bottom).value,.6f)
        compose.onNodeWithText("额度条显示").performScrollTo()
        val next=compose.onNodeWithText("额度条显示").getUnclippedBoundsInRoot()
        val visibleNote=compose.onNodeWithTag("limit-mask-emails-description",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Next item must be visually separate from the description",next.top-visibleNote.bottom>=8.dp)
        save("quota-before-text-check")
        fullText("limit-mask-emails-description")
        save("quota")
    }
}
