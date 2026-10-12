package dev.bing.tokenmonitor

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Public actual controls, including composed chrome rather than text-layout colours alone. */
class ActionStateScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0;var submissions=0;var cancellations=0
        override fun connect(address:String,credential:String,alternate:String) {submissions++}
        override fun editConnection(address:String,alternate:String,replacementCredential:String?) {submissions++}
        override fun connectionEndpoints()=listOf("https://public.example.invalid","http://10.0.0.8:17321")
        override fun dismissConnectionFailure() {cancellations++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=true
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun loadPricing() {reads++}
        override fun loadDeviceHistory() {reads++}
        override fun forget() {reads++}
        override fun renameDevice(id:String,input:String) {reads++}
    }
    private fun visibleControl(tag:String):SemanticsNodeInteraction {
        val node=compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        val density=compose.activity.resources.displayMetrics.density
        val full=node.getUnclippedBoundsInRoot()
        val visible=node.fetchSemanticsNode().boundsInRoot
        val viewport=compose.onNodeWithTag(if(tag.startsWith("settings-")) "hub-content" else "action-state-host")
            .fetchSemanticsNode().boundsInRoot
        // Loading can move a previously visible control beyond a landscape viewport.
        // Assert the whole actual 48dp outer area before clicking or sampling its chrome.
        assertEquals("$tag outer left is not clipped",full.left.value*density,visible.left,1f)
        assertEquals("$tag outer top is not clipped",full.top.value*density,visible.top,1f)
        assertEquals("$tag outer right is not clipped",full.right.value*density,visible.right,1f)
        assertEquals("$tag outer bottom is not clipped",full.bottom.value*density,visible.bottom,1f)
        assertTrue("$tag outer area is completely inside the visible viewport",
            visible.left>=viewport.left-1f && visible.top>=viewport.top-1f &&
                visible.right<=viewport.right+1f && visible.bottom<=viewport.bottom+1f)
        return node
    }
    private fun capture(tag:String)=visibleControl(tag).captureToImage()
    private fun touchDisabled(tag:String) {
        visibleControl(tag).assertIsNotEnabled().performTouchInput {click(center)}
    }
    private fun geometry(tag:String,minVisual:Float) {
        val density=compose.activity.resources.displayMetrics.density
        val touch=visibleControl(tag).fetchSemanticsNode().boundsInRoot
        val visual=compose.onNodeWithTag("$tag-visual",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("Actual action keeps a 48dp touch height",touch.height/density>=47.5f)
        assertTrue("Actual action keeps a 48dp touch width",touch.width/density>=47.5f)
        assertTrue("Actual chrome keeps its desktop minimum height",visual.height/density>=minVisual-.5f)
        assertTrue("Chrome is separate from its touch area",visual.height<touch.height)
    }
    private fun assertWholeChromeFade(tag:String,enabled:ImageBitmap,disabled:ImageBitmap,opacity:Float,background:Boolean=true) {
        val before=enabled.toPixelMap();val after=disabled.toPixelMap()
        assertEquals(before.width,after.width);assertEquals(before.height,after.height)
        // The outer 48dp area includes the parent surface above the compact visible button.
        val base=before[0,0];val nextBase=after[0,0]
        fun channels(color:androidx.compose.ui.graphics.Color)=listOf(color.red,color.green,color.blue).map {(it*255).roundToInt()}
        val baseChannels=channels(base)
        assertTrue(channels(nextBase).zip(baseChannels).all {(a,b)->abs(a-b)<=1})
        val density=compose.activity.resources.displayMetrics.density
        val outer=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val visual=compose.onNodeWithTag("$tag-visual",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val left=(visual.left-outer.left).roundToInt();val right=(visual.right-outer.left).roundToInt()
        val top=(visual.top-outer.top).roundToInt();val bottom=(visual.bottom-outer.top).roundToInt()
        fun peak(pixels:androidx.compose.ui.graphics.PixelMap,xs:IntRange,ys:IntRange):List<Int> {
            val colors=ys.flatMap {y->xs.map {x->channels(pixels[x.coerceIn(0,pixels.width-1),y.coerceIn(0,pixels.height-1)])}}
            return colors.maxBy {values->values.zip(baseChannels).maxOf {(a,b)->abs(a-b)}}
        }
        fun checkPart(label:String,xs:IntRange,ys:IntRange,minContrast:Int) {
            val original=peak(before,xs,ys);val rendered=peak(after,xs,ys)
            assertTrue("$label rendered pixels were checked",original.zip(baseChannels).maxOf {(a,b)->abs(a-b)}>=minContrast)
            original.zip(baseChannels).forEachIndexed {index,(value,surface)->
                val expected=(surface+(value-surface)*opacity).roundToInt()
                assertTrue("$label must composite at $opacity",abs(rendered[index]-expected)<=3)
            }
        }
        // Different page loading layouts may move rasterized text by half a pixel.
        // Peak coverage within each actual chrome region avoids comparing shifted glyph edges.
        val inset=(3*density).roundToInt().coerceAtLeast(1)
        val middle=(top+bottom)/2
        if(background) checkPart("Control background",left+inset..left+inset+1,middle..middle+1,6)
        val corner=(9*density).roundToInt()
        checkPart("Control border",left+corner..right-corner,(bottom-density.toInt()-1)..bottom-1,10)
        checkPart("Control text",left+inset..right-inset,top+inset..bottom-inset,70)
    }
    private fun save(page:String)=saveVisualEvidence(compose.activity,compose.onRoot(),"action-state-$page-${if(dark) "dark" else "light"}.png")

    @Test fun readConnectionAndImageControlsKeepCompleteEnabledDisabledAndCancelStates() {
        val actions=Actions()
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1}}}""")
        val state=mutableStateOf(HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off")))
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,
            language=LanguageChoice.ENGLISH,homeModules=listOf("devices"),appearance=AppearancePreferences(colors=mapOf("accent" to "#ff0000"))))
        val phase=mutableStateOf("hub");val draft=HubConnectionDraft()
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN)}
        val imageState=mutableStateOf(BackgroundImageState(bitmap=bitmap,busy=false))
        val directory=File(compose.activity.cacheDir,"public-action-image-${UUID.randomUUID()}").apply {mkdirs()}
        val application=object:Application() {override fun getNoBackupFilesDir()=directory}
        val owner=object:ViewModelStoreOwner {override val viewModelStore=ViewModelStore()}
        val model=ViewModelProvider(owner,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:androidx.lifecycle.ViewModel> create(modelClass:Class<T>):T=BackgroundImageModel(application) as T
        })[BackgroundImageModel::class.java]
        var pickerLaunches=0
        val registry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {pickerLaunches++}
        }
        val registryOwner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        var dismissKeyboard:()->Unit={}
        try {
            compose.activity.runOnUiThread {compose.activity.setContent {
                TokenTheme(dark,prefs.value.language,prefs.value.appearance) {
                    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
                    SideEffect {dismissKeyboard={focus.clearFocus();keyboard?.hide()}}
                    CompositionLocalProvider(LocalBackgroundImageModel provides model,LocalActivityResultRegistryOwner provides registryOwner) {
                        Surface(Modifier.fillMaxSize().testTag("action-state-host"),color=MaterialTheme.colorScheme.background) {
                            when(phase.value) {
                                "hub"->HubScreen(state.value,actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})
                                "connection"->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    HubConnectionSettings(state.value,actions,true,null,null,{},draft)
                                }
                                else->Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    CompositionLocalProvider(LocalBackgroundImageState provides imageState.value) {BackgroundImageControl()}
                                }
                            }
                        }
                    }
                }
            }}
            compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"subscriptions")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-subscriptions-load"))
            compose.onNodeWithTag("settings-subscriptions-load").assertIsEnabled();geometry("settings-subscriptions-load",30f)
            val readEnabled=capture("settings-subscriptions-load");save("enabled")
            compose.runOnIdle {state.value=state.value.copy(loading=true)}
            touchDisabled("settings-subscriptions-load")
            assertWholeChromeFade("settings-subscriptions-load",readEnabled,capture("settings-subscriptions-load"),.38f);save("disabled")
            compose.runOnIdle {state.value=state.value.copy(loading=false,error=R.string.error_read)}
            compose.onNodeWithTag("settings-subscriptions-load").assertIsEnabled()
            assertEquals(snapshot,state.value.snapshot);assertEquals(0,actions.reads)

            compose.runOnIdle {state.value=state.value.copy(error=null);phase.value="connection"}
            compose.onNodeWithTag("connection-edit").performScrollTo().performClick()
            compose.onNodeWithTag("connection-save").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("connection-cancel").assertIsEnabled()
            touchDisabled("connection-save");assertEquals(0,actions.submissions)
            compose.onNodeWithTag("connection-alternate").performScrollTo().performTextReplacement("http://10.0.0.9:17321")
            compose.runOnIdle {dismissKeyboard()}
            compose.onNodeWithTag("connection-cancel").performScrollTo()
            compose.onNodeWithTag("connection-save").assertIsEnabled();geometry("connection-save",30f)
            val saveEnabled=capture("connection-save");val cancelEnabled=capture("connection-cancel")
            compose.runOnIdle {state.value=state.value.copy(loading=true)}
            touchDisabled("connection-save");touchDisabled("connection-cancel")
            assertWholeChromeFade("connection-save",saveEnabled,capture("connection-save"),.38f)
            assertWholeChromeFade("connection-cancel",cancelEnabled,capture("connection-cancel"),.38f,background=false)
            save("connection");assertEquals(0,actions.submissions);assertEquals(0,actions.cancellations)
            compose.runOnIdle {state.value=state.value.copy(loading=false)}
            compose.onNodeWithTag("connection-cancel").performClick()
            compose.onNodeWithTag("connection-save").assertDoesNotExist()
            assertEquals(0,actions.submissions);assertEquals(1,actions.cancellations)
            assertFalse(draft.editing);assertEquals("",draft.address);assertEquals("",draft.alternate)
            compose.onNodeWithTag("connection-edit").performScrollTo().performClick()
            compose.onNodeWithTag("connection-save").performScrollTo().assertIsNotEnabled()

            compose.runOnIdle {phase.value="background"}
            compose.onNodeWithTag("appearance-background-remove").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("appearance-background-choose").assertIsEnabled()
            geometry("appearance-background-choose",26f);geometry("appearance-background-remove",26f)
            val chooseEnabled=capture("appearance-background-choose");val removeEnabled=capture("appearance-background-remove")
            compose.runOnIdle {imageState.value=imageState.value.copy(busy=true)}
            touchDisabled("appearance-background-choose");touchDisabled("appearance-background-remove")
            assertWholeChromeFade("appearance-background-choose",chooseEnabled,capture("appearance-background-choose"),.5f)
            assertWholeChromeFade("appearance-background-remove",removeEnabled,capture("appearance-background-remove"),.5f)
            assertEquals(0,pickerLaunches);save("background")
            compose.runOnIdle {imageState.value=imageState.value.copy(busy=false)}
            compose.onNodeWithTag("appearance-background-choose").assertIsEnabled()
            compose.onNodeWithTag("appearance-background-remove").assertIsEnabled()
            assertEquals(0,actions.reads);assertEquals(0,actions.submissions)
        } finally {
            compose.activity.runOnUiThread {compose.activity.setContent {}}
            compose.runOnIdle {owner.viewModelStore.clear()}
            directory.deleteRecursively();bitmap.recycle()
        }
    }
}
