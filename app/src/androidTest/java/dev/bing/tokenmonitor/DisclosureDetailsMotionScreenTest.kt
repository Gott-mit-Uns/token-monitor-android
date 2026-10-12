package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * The project, device and tool-detail disclosures must use the original 250ms accordion
 * (styles.css:931-968 for settings groups, 6583-6609 for source rows) instead of appearing at
 * their final height. These are component-scope fraction checks on the virtual clock; the real
 * Android Window counterpart stays in the perfTest source set.
 */
class DisclosureDetailsMotionScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val animationClock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}

    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }

    private val updated get()=Instant.now().toString()
    private fun source()=HubParser.parse("""{"updatedAt":"$updated",
      "periods":{"today":{"totalTokens":3000,"costUsd":3,"unpricedTokens":50,
        "capabilities":{"tokenComponents":true},"cacheReadTokens":701,"outputTokens":100,"unclassifiedTokens":0,
        "clients":{"codex":1000,"claude":800,"opencode":200},
        "clientCosts":{"codex":1.5,"claude":0.8,"opencode":0.2},
        "clientUnpricedTokens":{"codex":50},
        "clientCacheReads":{"codex":1},"clientCacheWrites":{"codex":25},"clientOutputs":{"codex":0},
        "clientUnclassifiedTokens":{"codex":0},
        "clientModels":{"codex":{"public":600}},
        "clientModelCosts":{"codex":{"public":0.3}},
        "clientModelUnpricedTokens":{"codex":{"public":10}},
        "projects":{"small":{"label":"Small","tokens":300,"costUsd":1,"clients":{"codex":300}}},
        "models":{"model-small":300}}},
      "devices":[{"deviceId":"small","platform":"linux","updatedAt":"$updated",
        "periods":{"today":{"totalTokens":300,"clients":{"codex":300},"clientCosts":{"codex":1},"clientModels":{"codex":{"public":300}}}}}]}""")

    private fun setup(prefs:State<DisplayPreferences>,system:Float=1f) {
        val state=HubState(snapshot=source(),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,prefs.value.language,prefs.value.appearance) {
                CompositionLocalProvider(
                    LocalDesktopAnimationClock provides animationClock,
                    LocalDesktopSystemMotionScale provides system) {
                    HubScreen(state,actions,preferences=prefs.value)
                }
            }
        }}
        compose.waitForIdle()
    }

    private fun preferences(motion:String)=mutableStateOf(DisplayPreferences(language=LanguageChoice.ENGLISH,
        homeModules=emptyList(),appearance=AppearancePreferences(reduceMotion=motion)))

    private fun select(page:String) {
        compose.onNodeWithTag("hub-view-selector").performClick()
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithTag("view-$page").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(32)
    }
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun exists(tag:String)=compose.onAllNodesWithTag(tag,true).fetchSemanticsNodes().isNotEmpty()
    private fun fraction(tag:String)=compose.onNodeWithTag(tag,true).fetchSemanticsNode().config[DisclosureHeightFraction]
    private fun playing(tag:String)=compose.onNodeWithTag(tag,true).fetchSemanticsNode().config[DisclosureMotionPlaying]

    /** Expansion starts below its final 250ms height and settles at the complete natural height. */
    private fun opensInRealFractions(row:String,details:String,content:String) {
        show(row)
        compose.onNodeWithTag(row).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        assertTrue("$details is composed while opening",exists(details))
        val middle=fraction(details)
        assertTrue("$details animates through an intermediate height",middle>0.02f && middle<0.98f)
        assertTrue("$details reports its real playing state",playing(details))
        compose.mainClock.advanceTimeBy(400)
        assertEquals("$details settles at the complete natural height",1f,fraction(details),0.001f)
        assertFalse("$details stops playing",playing(details))
        assertTrue("$content is available once the disclosure is open",exists(content))
    }

    /** Collapsing keeps the original exit fractions and removes the content afterwards. */
    private fun closesInRealFractions(row:String,details:String) {
        show(row)
        compose.onNodeWithTag(row).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        assertTrue("$details is composed while closing",exists(details))
        val closing=fraction(details)
        assertTrue("$details animates through an intermediate closing height",closing>0.02f && closing<0.98f)
        compose.mainClock.advanceTimeBy(500)
        assertFalse("$details content is gone after the collapse",exists(details))
    }

    @Test fun projectSourceRowsUseTheOriginal250msAccordionFractions() {
        setup(preferences("off"))
        compose.mainClock.autoAdvance=false
        select("projects")
        compose.mainClock.advanceTimeBy(500)
        opensInRealFractions("project-row-small","project-details-motion-small","project-tool-small-0")
        closesInRealFractions("project-row-small","project-details-motion-small")
        compose.mainClock.autoAdvance=true
    }

    @Test fun deviceSourceRowsUseTheOriginal250msAccordionFractions() {
        setup(preferences("off"))
        compose.mainClock.autoAdvance=false
        select("devices")
        compose.mainClock.advanceTimeBy(500)
        opensInRealFractions("device-row-small","device-details-motion-small","device-expanded-small")
        closesInRealFractions("device-row-small","device-details-motion-small")
        compose.mainClock.autoAdvance=true
    }

    @Test fun toolDetailRowsUseTheOriginal250msAccordionFractions() {
        setup(preferences("off"))
        compose.mainClock.autoAdvance=false
        select("tools")
        compose.mainClock.advanceTimeBy(500)
        opensInRealFractions("breakdown-tool-codex","tool-details-motion-tool-codex","tool-detail-tool-codex-hit")
        closesInRealFractions("breakdown-tool-codex","tool-details-motion-tool-codex")
        compose.mainClock.autoAdvance=true
    }

    @Test fun reducedMotionOpensTheSourceAccordionAtItsFinalHeightWithoutFractions() {
        setup(preferences("on"))
        compose.mainClock.autoAdvance=false
        select("projects")
        compose.mainClock.advanceTimeBy(500)
        show("project-row-small")
        compose.onNodeWithTag("project-row-small").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("A reduced-motion disclosure is already composed",exists("project-details-motion-small"))
        assertEquals("Reduced motion has no intermediate height",1f,fraction("project-details-motion-small"),0.001f)
        assertFalse("Reduced motion does not report a playing fraction",playing("project-details-motion-small"))
        compose.mainClock.autoAdvance=true
    }
}
