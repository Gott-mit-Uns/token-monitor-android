package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fixture-only Android rendering and expansion; no production Hub or phone claims. */
class SessionMetrics072ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun screenshot(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val destination=File(compose.activity.getExternalFilesDir(null),"sessions072-$name.png")
        destination.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun assertCompleteActivity(id:String,expected:String) {
        val tag="session-activity-$id"
        val node=compose.onNodeWithTag(tag,true).assertIsDisplayed().assertTextEquals(expected)
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val layout=layouts.single()
        val visible=node.fetchSemanticsNode().boundsInRoot
        val lineMeasurements=(0 until layout.lineCount).joinToString {line->
            "line$line(left=${layout.getLineLeft(line)},right=${layout.getLineRight(line)},top=${layout.getLineTop(line)},bottom=${layout.getLineBottom(line)},baseline=${layout.getLineBaseline(line)},start=${layout.getLineStart(line)},end=${layout.getLineEnd(line)},visibleEnd=${layout.getLineEnd(line,visibleEnd=true)},ellipsis=${layout.isLineEllipsized(line)})"
        }
        val detail="$tag text=${layout.layoutInput.text}, size=${layout.size}, lines=${layout.lineCount}, "+
            "rawHasVisualOverflow=${layout.hasVisualOverflow}, overflowWidth=${layout.didOverflowWidth}, overflowHeight=${layout.didOverflowHeight}, "+
            "paragraphWidth=${layout.multiParagraph.width}, paragraphHeight=${layout.multiParagraph.height}, "+
            "didExceedMaxLines=${layout.multiParagraph.didExceedMaxLines}, constraints=${layout.layoutInput.constraints}, "+
            "density=${layout.layoutInput.density}, maxLines=${layout.layoutInput.maxLines}, softWrap=${layout.layoutInput.softWrap}, "+
            "overflow=${layout.layoutInput.overflow}, style=${layout.layoutInput.style}, visibleBounds=$visible, $lineMeasurements"
        val diagnosticId=id.replace(Regex("[^A-Za-z0-9_-]"),"_")
        File(compose.activity.getExternalFilesDir(null),"sessions072-layout-$diagnosticId.txt").writeText(detail)
        // Capture the actual Compose scene before a failing assertion tears down the Activity.
        screenshot("diagnostic-$diagnosticId")
        // Observed on the unchanged f7 product: the pre-layout paragraph retains the parent's
        // 765px max width while Text shrink-wraps to 275px. The line right/visible bounds are
        // both 275px and all 25 characters are visible, so the raw width flag is not clipping.
        // Verify painted line bounds and ancestor bounds below instead of that container flag.
        assertFalse("Activity metrics must fit vertically: $detail",layout.didOverflowHeight)
        assertFalse("Activity metrics must not exceed the line budget: $detail",layout.multiParagraph.didExceedMaxLines)
        assertEquals("Every activity character must remain visible: $detail",expected.length,
            layout.getLineEnd(layout.lineCount-1,visibleEnd=true))
        repeat(layout.lineCount) {line->
            assertFalse("Activity metrics must not ellipsize: $detail",layout.isLineEllipsized(line))
            assertTrue("Activity line must fit horizontally: $detail",layout.getLineLeft(line)>=-.5f && layout.getLineRight(line)<=layout.size.width+.5f)
            assertTrue("Activity line must fit vertically: $detail",layout.getLineBottom(line)<=layout.size.height+.5f)
        }
        assertTrue("Activity node must not be clipped by its ancestors: $detail; visible=$visible",
            visible.width>=layout.size.width-1f && visible.height>=layout.size.height-1f)
    }
    private fun check(dark:Boolean) {
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":302,"sessions":{
          "codex:p":{"client":"codex","sessionId":"p","title":"Family Main","totalTokens":100,"messageCount":2,"inputTokens":10,"cacheReadTokens":90,"cacheWriteTokens":0,"outputTokens":10,"timedOutputTokens":1000,"timedDurationMs":1000},
          "codex:c":{"client":"codex","sessionId":"c","title":"Family Child","parentSessionId":"p","totalTokens":200,"messageCount":8,"inputTokens":900,"cacheReadTokens":100,"cacheWriteTokens":0,"outputTokens":100,"timedOutputTokens":100,"timedDurationMs":9000},
          "codex:unknown":{"client":"codex","sessionId":"unknown","title":"Legacy Hub","totalTokens":1},
          "codex:missing":{"client":"codex","sessionId":"missing","parentSessionId":"unknown","totalTokens":1}
        }}}}""")
        val actions=Actions();val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,prefs.language,prefs.appearance) {HubScreen(state,actions,preferences=prefs)}}}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
        val id="session-group:subagents:codex:p"
        show("session-row-$id")
        assertCompleteActivity(id,"10 calls · 17% · 11 tok/s")
        screenshot(if(dark) "dark-group" else "light-group")
        compose.onNodeWithTag("session-row-$id").performClick()
        show("session-row-codex:p")
        assertCompleteActivity("codex:p","2 calls · 90% · 10 tok/s")
        compose.onNodeWithTag("session-role-codex:p",true).assertTextEquals("Main conversation / continuation")
        show("session-row-codex:c")
        assertCompleteActivity("codex:c","8 calls · 10% · 11 tok/s")
        screenshot(if(dark) "dark-members" else "light-members")
        show("session-return-list");compose.onNodeWithTag("session-return-list").performClick()
        val unknown="session-group:subagents:codex:unknown"
        show("session-row-$unknown")
        assertCompleteActivity(unknown,"Calls unknown · —% · — tok/s")
        screenshot(if(dark) "dark-missing" else "light-missing")
        compose.onNodeWithTag("session-row-$unknown").performClick()
        show("session-row-codex:unknown")
        assertCompleteActivity("codex:unknown","Calls unknown · —% · — tok/s")
        screenshot(if(dark) "dark-missing-member" else "light-missing-member")
        assertEquals(0,actions.reads)
        assertEquals(302L,snapshot.periods.getValue("today").tokens)
    }
    @Test fun lightGroupAndExpandedMembersKeepOwnMetrics()=check(false)
    @Test fun darkGroupAndMissingLegacyFieldsStayHonest()=check(true)
}
