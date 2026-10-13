package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class DigitRoll070ScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val clock=DesktopAnimationClock {compose.mainClock.currentTime*1_000_000L}
    @Test fun finiteRollKeepsWidthAndDoesNotRecomposeLongListEachFrame() {
        compose.mainClock.autoAdvance=false
        val value=mutableStateOf("1,099");val reduce=mutableStateOf("off");val scale=mutableFloatStateOf(1f)
        val durationScale=mutableFloatStateOf(1f)
        val rollingDraws=AtomicInteger();val owners=AtomicInteger();val draws=AtomicInteger();val active=AtomicInteger();val layers=AtomicInteger()
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(true,LanguageChoice.ENGLISH,AppearancePreferences(reduceMotion=reduce.value)) {
                CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalDesktopSystemMotionScale provides scale.floatValue,LocalHeadlineMotionScale provides if(reduce.value=="on") 0f else durationScale.floatValue) {
                    SideEffect {owners.incrementAndGet()}
                    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("roll-list")) {
                        items((0 until 2000).toList(),key={it}) {i->
                            CompositionLocalProvider(LocalTokenRollFrameObserver provides if(i==0) {running,count->draws.incrementAndGet();if(running) rollingDraws.incrementAndGet();active.set(if(running) 1 else 0);layers.set(count)} else null) {
                                TokenNumberText(if(i==0) value.value else "9,999",Modifier.testTag("roll-$i"),style=quotaTextStyle(24),softWrap=false)
                            }
                        }
                    }
                }
            }
        }}
        compose.mainClock.advanceTimeBy(32)
        val before=compose.onNodeWithTag("roll-0").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle {value.value="1,100"};compose.mainClock.advanceTimeBy(64)
        val parentCount=owners.get();val drawsBefore=draws.get()
        compose.mainClock.advanceTimeBy(224)
        assertEquals(parentCount,owners.get());assertTrue(draws.get()>drawsBefore);assertTrue(rollingDraws.get()>0)
        assertTrue(layers.get()<=10)
        assertEquals(before,compose.onNodeWithTag("roll-0").fetchSemanticsNode().boundsInRoot.width,.5f)
        compose.onNodeWithTag("roll-0").assertTextEquals("1,100")
        saveVisualEvidence(compose.activity,compose.onRoot(),"digit-roll-middle.png")
        compose.runOnIdle {value.value="1,090"};compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {reduce.value="on"};compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle();assertEquals(0,active.get());compose.onNodeWithTag("roll-0").assertTextEquals("1,090")
        compose.runOnIdle {reduce.value="off";scale.floatValue=0f;value.value="1,091"};compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle();assertEquals(0,active.get())
        compose.runOnIdle {scale.floatValue=1f;value.value="1,092"};compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle();assertEquals(1,active.get())
        compose.runOnIdle {durationScale.floatValue=2f};compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle();assertEquals(0,active.get());compose.onNodeWithTag("roll-0").assertTextEquals("1,092")
        compose.runOnIdle {value.value="1,093"};compose.mainClock.advanceTimeBy(64)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle();assertEquals(0,active.get());compose.onNodeWithTag("roll-0").assertTextEquals("1,093")
        compose.mainClock.advanceTimeBy(1400)
        compose.onNodeWithTag("roll-list").performScrollToIndex(1999)
        compose.onNodeWithTag("roll-1999").assertTextEquals("9,999")
        compose.mainClock.autoAdvance=true
    }
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadDeviceHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun forget() {}
        override fun renameDevice(id:String,input:String) {}
    }
    @Test fun realHeadlineRollCountSwitchUnavailableAndPeriodAreExactAndFinite() {
        compose.mainClock.autoAdvance=false
        val mode=mutableStateOf("roll");val state=mutableStateOf(HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":11111},"month":{"totalTokens":999}}}"""),history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val actions=Actions();val active=AtomicInteger()
        compose.activity.runOnUiThread {compose.activity.setContent {
            val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,homeModules=emptyList(),appearance=AppearancePreferences(tokenNumberAnimation=mode.value,reduceMotion="off"))
            TokenTheme(true,LanguageChoice.ENGLISH,prefs.appearance) {CompositionLocalProvider(LocalDesktopAnimationClock provides clock,LocalTokenRollFrameObserver provides {running,_->active.set(if(running) 1 else 0)}) {
                HubScreen(state.value,actions,preferences=prefs)
            }}
        }}
        compose.mainClock.advanceTimeBy(32);compose.onNodeWithTag("hub-total-tokens").assertTextEquals("11,111");compose.waitForIdle();assertEquals(0,active.get())
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":22222},"month":{"totalTokens":999}}}"""))}
        compose.mainClock.advanceTimeBy(96);compose.waitForIdle();assertEquals(1,active.get())
        compose.runOnIdle {mode.value="count"};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("22,222");compose.waitForIdle();assertEquals(0,active.get())
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":33333},"month":{"totalTokens":999}}}"""))}
        compose.mainClock.advanceTimeBy(160)
        val interim=compose.onNodeWithTag("hub-total-tokens").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text.replace(",","").toLong()
        assertTrue(interim in 22223L..33332L)
        compose.runOnIdle {mode.value="roll"};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("33,333");compose.waitForIdle();assertEquals(0,active.get())
        compose.onNodeWithTag("hub-period-month").performClick();compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("999")
        saveVisualEvidence(compose.activity,compose.onRoot(),"digit-roll-headline-period-middle.png")
        compose.mainClock.advanceTimeBy(800);compose.waitForIdle();assertEquals(0,active.get())
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1}}}"""))}
        compose.mainClock.advanceTimeBy(32);compose.onNodeWithTag("hub-total-tokens").assertTextEquals("—");compose.waitForIdle();assertEquals(0,active.get())
        compose.runOnIdle {state.value=state.value.copy(snapshot=HubParser.parse("""{"periods":{"month":{"totalTokens":444}}}"""))}
        compose.mainClock.advanceTimeBy(32);compose.onNodeWithTag("hub-total-tokens").assertTextEquals("444");compose.waitForIdle();assertEquals(0,active.get())
        assertEquals(0,actions.reads);compose.mainClock.autoAdvance=true
    }
    @Test fun appearanceAdvancedAndAnimationChoiceKeepExistingPreferences() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(appearance=AppearancePreferences(reduceMotion="on",showLiveDot=false,showToolIcons=false,interfaceFont=DesktopAppearance.bundledUiFont),main=MainDisplayPreferences(titleIconOnly=true)))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH,prefs.value.appearance) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                DesktopSettingSelect("Token number animation",listOf("roll" to "Digit roll","count" to "Count up"),prefs.value.appearance.tokenNumberAnimation,"token-number-animation") {prefs.value=DisplayChange.TokenNumberAnimation(it).apply(prefs.value)}
                AppearanceSettings(prefs.value,{prefs.value=it.apply(prefs.value)},embedded=true)
            }
        }}}
        compose.onNodeWithTag("appearance-live-dot").assertDoesNotExist()
        compose.onNodeWithTag("appearance-colors-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("appearance-live-dot").assertIsOff()
        compose.onNodeWithTag("appearance-tool-icons").assertIsOff()
        compose.onNodeWithTag("main-title-icon").assertIsOn()
        compose.onNodeWithTag("token-number-animation-select").performScrollTo().performClick()
        compose.onNodeWithTag("token-number-animation-count").performClick()
        compose.runOnIdle {assertEquals("count",prefs.value.appearance.tokenNumberAnimation);assertFalse(prefs.value.appearance.showLiveDot);assertTrue(prefs.value.main.titleIconOnly)}
        saveVisualEvidence(compose.activity,compose.onRoot(),"digit-roll-appearance-${if(dark) "dark" else "light"}.png")
    }
    @Test fun v16PreferencesStayByteCompatibleAndOptionalModeHasSafeFallback() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="number070-${UUID.randomUUID()}.bin"
        val file=File(context.noBackupFilesDir,name);val mode=File(context.noBackupFilesDir,name+".number-animation");val store=DisplayPreferenceStore(context,name)
        try {
            val prefs=DisplayPreferences(language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(reduceMotion="on",showToolIcons=false,showLiveDot=false,displayFont=DesktopAppearance.bundledDataFont),main=MainDisplayPreferences(titleIconOnly=true),limits=LimitDisplayPreferences(homeBars=true))
            store.save(prefs);val original=file.readBytes();assertEquals(16,original[0].toInt());assertEquals(prefs,store.load());assertFalse(mode.exists())
            val next=DisplayChange.TokenNumberAnimation("count").apply(prefs);store.save(next)
            assertTrue(original.contentEquals(file.readBytes()));assertEquals(next,DisplayPreferenceStore(context,name).load())
            mode.writeBytes(byteArrayOf(1,77));assertEquals(prefs,store.load());assertTrue(original.contentEquals(file.readBytes()))
            mode.writeBytes(byteArrayOf(1));assertEquals(prefs,store.load())
            mode.delete();assertEquals(prefs,store.load());assertTrue(original.contentEquals(file.readBytes()))
        } finally {store.clear()}
    }
}
