package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** Real Compose popup layout/edge touch; synthetic choices, no Hub access. */
class CompactSettingsDropdownScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private var minRowPx=0
    private var minControlPx=0
    private fun content(block:@Composable ()->Unit) {
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark,LanguageChoice.CHINESE,AppearancePreferences(reduceMotion="on")) {
                val d=LocalDensity.current
                SideEffect {minRowPx=with(d) {36.dp.roundToPx()};minControlPx=with(d) {48.dp.roundToPx()}}
                Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().padding(8.dp)) {block()}}
            }
        }}
    }
    private fun labelFits(tag:String) {
        val node=compose.onNodeWithTag("$tag-label",useUnmergedTree=true)
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val layout=layouts.single()
        assertFalse("Complete option text must be visible",layout.hasVisualOverflow)
        assertFalse("Option text must not be ellipsized",(0 until layout.lineCount).any(layout::isLineEllipsized))
        val row=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val text=node.fetchSemanticsNode().boundsInRoot
        assertTrue(text.top>=row.top && text.bottom<=row.bottom)
    }
    private fun save(name:String)=saveVisualEvidence(compose.activity,compose.onNodeWithTag("compact-menu",useUnmergedTree=true),"settings-dropdown-$name-${if(dark) "dark" else "light"}.png")

    @Test fun settingSelectorUsesCompactRowsAndAdjacentTouchEdgesSelectExactlyOnce() {
        val selected=mutableStateOf("zh");var changes=0
        content {DesktopSettingSelect("语言",listOf("zh" to "中文","en" to "English","system" to "跟随系统"),selected.value,"compact") {selected.value=it;changes++}}
        val control=compose.onNodeWithTag("compact-select").fetchSemanticsNode().boundsInRoot
        assertTrue("Collapsed setting target remains 48dp",control.height.roundToInt()>=minControlPx)
        compose.onNodeWithTag("compact-select").performClick()
        val tags=listOf("compact-zh","compact-en","compact-system")
        val rows=tags.map {tag->
            labelFits(tag)
            compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.also {
                assertTrue(it.height.roundToInt()>=minRowPx)
                if(compose.activity.resources.configuration.fontScale==1f) assertEquals("Short standard-font row is exactly 36dp",minRowPx,it.height.roundToInt())
            }
        }
        rows.zipWithNext().forEach {(a,b)->assertEquals("No row gap or overlapping targets",a.bottom,b.top,0f)}
        compose.onNodeWithTag("compact-zh").assertIsSelected()
        save("language")
        for((index,tag) in tags.withIndex()) {
            if(index>0) compose.onNodeWithTag("compact-select").performClick()
            val expected=listOf("zh","en","system")[index]
            compose.onNodeWithTag(tag).performTouchInput {click(Offset(width/2f,1f))}
            assertEquals(expected,selected.value);assertEquals(index*2+1,changes)
            compose.onNodeWithTag("compact-menu").assertDoesNotExist()
            compose.onNodeWithTag("compact-select").performClick()
            compose.onNodeWithTag(tag).performTouchInput {click(Offset(width/2f,height-1f))}
            assertEquals(expected,selected.value);assertEquals(index*2+2,changes)
        }
    }

    @Test fun longLabelsGrowNaturallyAndConstrainedListScrollsWithAccessibleSelection() {
        val expanded=mutableStateOf(true);val selected=mutableStateOf("0")
        val options=(0..11).map {DesktopMenuOption("$it",if(it==0) "这是一个需要完整显示的很长设置选项，保留所有文字并允许自然换行。This setting label remains complete." else "选项 $it","compact-$it")}
        content {Box(Modifier.size(48.dp)) {DesktopOptionMenu(expanded.value,options,selected.value,"compact-menu",width=140.dp,maxHeight=180.dp,compactSettings=true,dismiss={expanded.value=false}) {selected.value=it;expanded.value=false}}}
        labelFits("compact-0")
        val row=compose.onNodeWithTag("compact-0",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Long label grows beyond compact minimum",row.height.value>36f)
        assertTrue(compose.onNodeWithTag("compact-menu").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()>0f)
        save("long-label")
        compose.onNodeWithTag("compact-11").performScrollTo().performClick()
        assertEquals("11",selected.value);compose.onNodeWithTag("compact-menu").assertDoesNotExist()
        compose.runOnIdle {expanded.value=true}
        compose.onNodeWithTag("compact-0").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) {assertTrue(it())}
        assertEquals("0",selected.value);compose.onNodeWithTag("compact-menu").assertDoesNotExist()
    }

    @Test fun popupRetainsKeyboardAndOutsideDismissalAndOtherMenusKeep48dp() {
        val expanded=mutableStateOf(true);val compact=mutableStateOf(true);val selected=mutableStateOf("a")
        content {Box(Modifier.size(48.dp)) {DesktopOptionMenu(expanded.value,listOf(DesktopMenuOption("a","中文","compact-a"),DesktopMenuOption("b","English","compact-b")),selected.value,"compact-menu",compactSettings=compact.value,dismiss={expanded.value=false}) {selected.value=it;expanded.value=false}}}
        compose.onNodeWithTag("compact-a").performKeyInput {pressKey(Key.DirectionDown);pressKey(Key.Enter)}
        assertEquals("b",selected.value);compose.onNodeWithTag("compact-menu").assertDoesNotExist()
        compose.runOnIdle {expanded.value=true}
        compose.onNodeWithTag("compact-b").performKeyInput {pressKey(Key.Escape)}
        compose.onNodeWithTag("compact-menu").assertDoesNotExist()
        compose.runOnIdle {expanded.value=true;compact.value=false}
        assertTrue("Non-settings menus retain 48dp",compose.onNodeWithTag("compact-a").fetchSemanticsNode().boundsInRoot.height.roundToInt()>=minControlPx)
        val device=InstrumentationRegistry.getInstrumentation().uiAutomation
        val screen=compose.activity.resources.displayMetrics
        val now=android.os.SystemClock.uptimeMillis()
        val x=screen.widthPixels-4f;val y=screen.heightPixels/2f
        val down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0)
        val up=android.view.MotionEvent.obtain(now,now+30,android.view.MotionEvent.ACTION_UP,x,y,0)
        try {device.injectInputEvent(down,true);device.injectInputEvent(up,true)} finally {down.recycle();up.recycle()}
        compose.onNodeWithTag("compact-menu").assertDoesNotExist()
    }
}
