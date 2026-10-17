package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Actual product surfaces using only public fixtures; baseline mode asserts the old rendering. */
class Desktop073ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val args get()=InstrumentationRegistry.getArguments()
    private val before get()=args.getString("desktop073Baseline")=="true"
    private val dark get()=args.getString("visualDark")!="false"
    private val prefix=String(Character.toChars(0xF066E))+" "
    private val rawTitle=prefix+"PLAN: Review mobile layout"
    private val shownTitle get()=if(before) rawTitle else "PLAN: Review mobile layout"
    private val actions=object:HubUiActions {
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {}
        override fun loadModelNames() {}
        override fun loadSubscriptions() {}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    private fun body(tokens:Int=200)=buildJsonObject {
        put("periods",buildJsonObject {put("today",buildJsonObject {
            put("totalTokens",tokens);put("outputTokens",tokens);put("timedOutputTokens",if(tokens>0) 100 else 0);put("timedDurationMs",1000)
            put("sessions",buildJsonObject {put("omp:p",buildJsonObject {
                put("client","omp");put("sessionId","p");put("title",rawTitle);put("totalTokens",tokens)
                put("outputTokens",tokens);put("timedOutputTokens",if(tokens>0) 100 else 0);put("timedDurationMs",1000)
                put("messageCount",2);put("lastUsedAt",Instant.now().minusSeconds(30).toString())
            })})
        })})
    }.toString()
    private fun state(body:String)=HubState(snapshot=HubParser.parse(body),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
    private fun screenshot(stage:String)=saveVisualEvidence(compose.activity,if(stage.endsWith("summary")) compose.onNodeWithTag("session-summary-details") else compose.onRoot(),
        "desktop073-${if(before) "before" else "after"}-${if(dark) "dark" else "light"}-$stage.png")
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    @Test fun homeListAndSummaryUseSameDisplayTitleWithoutChangingStoredTitle() {
        val raw=body();val current=mutableStateOf(state(raw))
        val preferences=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=listOf("sessions"),appearance=AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,preferences.language,preferences.appearance) {HubScreen(current.value,actions,preferences=preferences)}}}
        show("home-session-omp:p")
        compose.onNodeWithTag("home-session-title-omp:p",true).assertTextEquals(shownTitle)
        screenshot("home")
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        show("session-row-omp:p")
        compose.onNodeWithTag("session-title-omp:p",true).assertTextEquals(shownTitle)
        compose.onNodeWithTag("session-activity-omp:p",true).assertTextContains("100 tok/s",substring=true)
        screenshot("sessions")
        compose.onNodeWithTag("session-row-omp:p").performClick()
        compose.onNodeWithTag("session-summary-details-title",true).assertTextEquals(shownTitle)
        screenshot("summary")
        assertEquals(rawTitle,current.value.snapshot!!.periods.getValue("today").sessions.single().session!!.title)
    }
    @Test fun longTitleCanBeReadCompletelyInSummary() {
        val title="PLAN: Inspect matched timing, zero corrections and long mobile session titles without losing the original Hub metadata"
        val raw=body().replace(rawTitle,prefix+title)
        val preferences=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion="on"))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,preferences.language,preferences.appearance) {HubScreen(state(raw),actions,preferences=preferences)}}}
        compose.onNodeWithTag("hub-view-selector").performClick();compose.onNodeWithTag("view-sessions").performScrollTo().performClick()
        show("session-row-omp:p");compose.onNodeWithTag("session-row-omp:p").performClick()
        val heading=compose.onNodeWithTag("session-summary-details-title",true)
        heading.assertTextEquals(if(before) prefix+title else title)
        val layouts=mutableListOf<TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        val layout=layouts.single()
        assertFalse("Summary title must fit vertically",layout.didOverflowHeight)
        assertEquals(layout.layoutInput.text.length,layout.getLineEnd(layout.lineCount-1,visibleEnd=true))
        repeat(layout.lineCount) {
            assertFalse("Summary title must not ellipsize",layout.isLineEllipsized(it))
            assertTrue("Summary title line must fit",layout.getLineLeft(it)>=-.5f && layout.getLineRight(it)<=layout.size.width+.5f)
        }
        screenshot("long-summary")
    }
    @Test fun signedEncryptedColdCachePreservesRawTitleAndExactZeroTiming() {
        val context=compose.activity
        val name="public-desktop073-cache.bin"
        val store=HubStore(context,name)
        try {
            for(tokens in listOf(200,0)) {
                val raw=body(tokens)
                store.save(SavedHub("https://example.invalid","public-fixture",raw,1))
                val restored=HubStore(context,name).load()!!
                assertEquals(raw,restored.snapshot)
                val snapshot=HubParser.parse(restored.snapshot)
                val row=snapshot.periods.getValue("today").sessions.single()
                assertEquals(rawTitle,row.session!!.title)
                assertEquals(shownTitle,SessionDisplay.title(row))
                assertEquals(tokens.toLong(),row.tokens)
                assertEquals(if(tokens>0) 100.0 else 0.0,SessionListDisplay.rate(row),0.0)
            }
        } finally {store.clear()}
    }
}
