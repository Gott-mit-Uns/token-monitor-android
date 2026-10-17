package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real window/font configuration is set by the runner; no replacement density. */
class FontRowAlignmentScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val args get()=InstrumentationRegistry.getArguments()
    private fun bounds(tag:String)=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun complete(tag:String) {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val text=layouts.single()
        assertEquals("No font shrinking",11f*DesktopAppearance.scale(args.getString("appTextSize") ?: "standard"),text.layoutInput.style.fontSize.value,.01f)
        assertFalse("Complete text must fit its natural layout: $tag",text.hasVisualOverflow)
        repeat(text.lineCount) {assertFalse(text.isLineEllipsized(it))}
    }
    private fun verify() {
        val a=bounds("appearance-interface-select");val b=bounds("appearance-display-select")
        val row=bounds("appearance-interface-row")
        val x=bounds("appearance-interface-reset");val y=bounds("appearance-display-reset")
        assertEquals("Dropdown left edges",a.left.value,b.left.value,.5f)
        assertEquals("Dropdown right edges",a.right.value,b.right.value,.5f)
        assertEquals("Reset reaches content edge",row.right.value,x.right.value,.5f)
        assertEquals(x.right.value,y.right.value,.5f)
        assertEquals("Gap before reset",8f,x.left.value-a.right.value,.5f)
        for(target in listOf(a,b,x,y)) {
            assertTrue("48dp touch width",target.width.value>=47.5f)
            assertTrue("48dp touch height",target.height.value>=47.5f)
        }
        val ta=bounds("appearance-interface-title");val tb=bounds("appearance-display-title")
        assertEquals(ta.left.value,tb.left.value,.5f)
        val stacked=ta.bottom<=a.top
        assertEquals("Both rows use the same breakpoint",stacked,tb.bottom<=b.top)
        if(!stacked) assertEquals("Shared label column width",ta.width.value,tb.width.value,.5f)
        listOf("appearance-interface-title","appearance-display-title","appearance-interface-value","appearance-display-value").forEach(::complete)
    }
    private fun mount(long:Boolean):MutableState<DisplayPreferences> {
        val preferences=mutableStateOf(DisplayPreferences(appearance=AppearancePreferences(reduceMotion="on",
            interfaceFont=if(long) DesktopAppearance.bundledUiFont else "",
            displayFont=if(long) DesktopAppearance.bundledDataFont else DesktopAppearance.defaultDisplayFont)))
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(args.getString("visualDark")=="true",LanguageChoice.CHINESE,AppearancePreferences(reduceMotion="on",textSize=args.getString("appTextSize") ?: "standard")) {
                Surface { Column(Modifier.fillMaxWidth().padding(12.dp).testTag("font-rows")) {
                    Text("高级自定义文字大小",style=quotaTextStyle(11,true))
                    FontSettingsControls(preferences.value) {preferences.value=it.apply(preferences.value)}
                } }
            }
        } }
        return preferences
    }
    private fun save(kind:String)=saveVisualEvidence(compose.activity,compose.onNodeWithTag("font-rows"),"font-rows-${args.getString("matrixCase") ?: "current"}-$kind.png")
    @Test fun defaultsShareColumnsAndKeepResetAtTheRightEdge() {mount(false);verify();save("default")}
    @Test fun longNamesWrapWithoutShrinkingAndResetStillRestoresEachOriginalDefault() {
        val preferences=mount(true);verify();save("long")
        compose.onNodeWithTag("appearance-interface-reset").assertContentDescriptionEquals("重置界面字体").performClick()
        compose.runOnIdle {assertEquals("",preferences.value.appearance.interfaceFont);assertEquals(DesktopAppearance.bundledDataFont,preferences.value.appearance.displayFont)}
        compose.onNodeWithTag("appearance-display-reset").assertContentDescriptionEquals("重置总量数字字体").performClick()
        compose.runOnIdle {assertEquals(DesktopAppearance.defaultDisplayFont,preferences.value.appearance.displayFont)}
        verify()
    }
}
