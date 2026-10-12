package dev.bing.tokenmonitor

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

class BackgroundImageScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val uri = Uri.parse("content://dev.bing.tokenmonitor.nativeclient.test.public-image/sample")
    private fun await(condition: () -> Boolean) { compose.waitUntil(10000, condition) }
    private fun model(): BackgroundImageModel { lateinit var result: BackgroundImageModel; compose.activityRule.scenario.onActivity { result=ViewModelProvider(it)[BackgroundImageModel::class.java] }; return result }
    private fun stage(value:String)=instrumentation.sendStatus(2,android.os.Bundle().apply {putString("nativeBackgroundStage",value)})
    private fun fixture() {
        target.contentResolver.openInputStream(uri)!!.use {assertTrue(it.read()!=-1)}
    }
    private fun snapshot()=HubState(snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":123456789,"costUsd":12.5}}}"""),savedAt=0)
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {}
        override fun refreshSaved(){reads++}
        override fun switchEndpoint(){}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory(){reads++}
        override fun loadModelNames(){reads++}
        override fun loadSubscriptions(){reads++}
        override fun forget(){}
        override fun renameDevice(id:String,input:String){}
    }
    @After fun cleanup() {
        File(target.noBackupFilesDir,"background-image.bin").delete()
        File(target.noBackupFilesDir,"background-image.bin.bak").delete()
        File(target.noBackupFilesDir,"background-image.bin.new").delete()
    }
    @Test fun actualPickerContractPersistsCancelsFailsAndRemovesWithoutHubReads() {
        stage("entered")
        fixture();val model=model();await {!model.state.value.busy}
        stage("ready")
        var picked:Uri?=uri;var launches=0
        val registry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
                val intent=contract.createIntent(target,input)
                assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.action);assertArrayEquals(arrayOf("image/*"),intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
                launches++
                @Suppress("UNCHECKED_CAST") dispatchResult(requestCode,picked as O)
            }
        }
        val owner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        val actions=Actions();val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT))
        compose.activity.runOnUiThread {compose.activity.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {TokenTheme(dark) {HubScreen(snapshot(),actions,preferences=prefs.value,updateDisplay={prefs.value=it.apply(prefs.value)})}}}}
        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"appearance")
        stage("settings")
        fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
        fun click(tag:String){show(tag);compose.onNodeWithTag(tag).performClick()}
        val before=actions.reads
        click("appearance-background-choose");await {!model.state.value.busy && model.state.value.bitmap!=null}
        stage("selected")
        assertEquals(1,launches);assertEquals(28,BackgroundImageStore(target).load().opacity)
        val picture=compose.onNodeWithTag("appearance-background-controls").fetchSemanticsNode().positionInRoot.y
        val motion=compose.onNodeWithTag("settings-motion-on").fetchSemanticsNode().positionInRoot.y
        val theme=compose.onNodeWithTag("appearance-theme-code-field").fetchSemanticsNode().positionInRoot.y
        assertTrue("Desktop picture controls must precede motion and interface theme",picture<motion && motion<theme)
        compose.onNodeWithTag("appearance-background-choose").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("appearance-background-remove").assertHeightIsAtLeast(48.dp)
        if(target.resources.configuration.fontScale<=1f && target.resources.configuration.screenWidthDp>=320) {
            val label=compose.onNodeWithTag("appearance-background-description").fetchSemanticsNode()
            val button=compose.onNodeWithTag("appearance-background-choose").fetchSemanticsNode()
            assertTrue("Ordinary width uses the original label/action line",label.positionInRoot.y<button.positionInRoot.y+button.size.height && button.positionInRoot.y<label.positionInRoot.y+label.size.height)
        }
        val original=requireNotNull(BackgroundImageStore(target).load().png)
        show("appearance-background-opacity")
        compose.onNodeWithTag("appearance-background-opacity").assertHeightIsAtLeast(48.dp)
        val rail=compose.onNodeWithTag("appearance-background-opacity").captureToImage().toPixelMap()
        val samples=listOf(.1f,.6f,.9f).map {rail[(rail.width*it).toInt(),rail.height/2].toArgb()}
        fun channel(value:Int,shift:Int)=(value shr shift) and 255
        val shift=listOf(0,8,16).maxBy {kotlin.math.abs(channel(samples[2],it)-channel(samples[0],it))}
        val first=channel(samples[0],shift);val middle=channel(samples[1],shift);val last=channel(samples[2],shift)
        assertTrue("Desktop rail is a continuous faint-to-full gradient",kotlin.math.abs(middle-first)>5 && kotlin.math.abs(last-middle)>5 && (middle-first)*(last-middle)>0)
        saveVisualEvidence(compose.activity,compose.onRoot(),"background-settings-${if(dark) "dark" else "light"}.png")
        picked=null;click("appearance-background-choose");assertArrayEquals(original,BackgroundImageStore(target).load().png)
        stage("cancelled")
        picked=uri.buildUpon().path("/invalid").build();click("appearance-background-choose");await {!model.state.value.busy && model.state.value.error}
        assertArrayEquals(original,BackgroundImageStore(target).load().png)
        stage("invalid_retained")
        show("appearance-background-opacity");compose.onNodeWithTag("appearance-background-opacity").performSemanticsAction(SemanticsActions.SetProgress) {it(70f)}
        await {!model.state.value.busy && BackgroundImageStore(target).load().opacity==70}
        stage("opacity_saved")
        compose.onNodeWithTag("appearance-background-opacity").performTouchInput {click(center)}
        await {!model.state.value.busy && BackgroundImageStore(target).load().opacity in 40..60}
        click("appearance-background-reset");await {!model.state.value.busy && BackgroundImageStore(target).load().opacity==28}
        click("appearance-background-remove");await {!model.state.value.busy && model.state.value.bitmap==null}
        stage("removed")
        assertNull(BackgroundImageStore(target).load().png);assertEquals(before,actions.reads)
        saveVisualEvidence(compose.activity,compose.onRoot(),"background-removed-${if(dark) "dark" else "light"}.png")
        // Restore through a fresh actual production Activity/VM from the private record.
        picked=uri;click("appearance-background-choose");await {!model.state.value.busy && model.state.value.bitmap!=null}
        stage("reselected")
        compose.activityRule.scenario.recreate();await {model().state.value.bitmap!=null && !model().state.value.busy}
        stage("recreated")
        assertArrayEquals(original,BackgroundImageStore(target).load().png)
        lateinit var fresh:BackgroundImageModel
        instrumentation.runOnMainSync {fresh=BackgroundImageModel(target.applicationContext as Application)}
        await {!fresh.state.value.busy};assertNotNull(fresh.state.value.bitmap)
        assertEquals(28,fresh.state.value.opacity);assertEquals(400,requireNotNull(fresh.state.value.bitmap).width)
        stage("restored")
    }
    @Test fun nativePixelsUseCenteredCoverAndIndependentOpacityWhileHeadlineRemainsInteractive() {
        fixture();val model=model();await {!model.state.value.busy}
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val actions=Actions()
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark) {CompositionLocalProvider(LocalBackgroundImageState provides model.state.collectAsState().value) {DesktopBackgroundSurface {}}}}}
        instrumentation.runOnMainSync {model.select(uri)};await {!model.state.value.busy && model.state.value.bitmap!=null}
        fun pixels(opacity:Int):Int {
            instrumentation.runOnMainSync {model.previewOpacity(opacity);model.saveOpacity()};await {!model.state.value.busy && BackgroundImageStore(target).load().opacity==opacity}
            compose.waitForIdle();val image=compose.onRoot().captureToImage().toPixelMap()
            return image[image.width/2,image.height*3/4].toArgb()
        }
        val base=android.graphics.Color.parseColor(if(dark) "#303438" else "#f6f7f9")
        assertEquals(base,pixels(0))
        val full=pixels(100)
        fun channel(value:Int,shift:Int)=(value ushr shift) and 255
        for(shift in listOf(0,8,16)) {
            val green=channel(android.graphics.Color.GREEN,shift)
            assertEquals(green*.82+channel(base,shift)*.18,channel(full,shift).toDouble(),2.0)
        }
        saveVisualEvidence(compose.activity,compose.onRoot(),"background-full-${if(dark) "dark" else "light"}.png")
        val normal=pixels(28)
        for(shift in listOf(0,8,16)) assertEquals(channel(full,shift)*.28+channel(base,shift)*.72,channel(normal,shift).toDouble(),2.0)
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark) {HubScreen(snapshot(),actions,preferences=DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT))}}}
        compose.waitForIdle();val before=actions.reads
        saveVisualEvidence(compose.activity,compose.onRoot(),"background-home-${if(dark) "dark" else "light"}.png")
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("123,456,789").performClick();compose.onNodeWithTag("hub-token-components").assertExists()
        assertEquals(before,actions.reads)
    }
    @Test fun boundedDecoderAndAtomicRecordKeepPreviouslySavedPictureOnInvalidReplacement() {
        fixture()
        val directory=File(target.cacheDir,"background-store-public-test").apply {mkdirs()}
        val context=object:ContextWrapper(target) {override fun getNoBackupFilesDir()=directory}
        val store=BackgroundImageStore(context)
        val bytes=target.contentResolver.openInputStream(uri)!!.use {it.readBytes()}
        try {
            store.save(BackgroundImageRecord(70,bytes));assertArrayEquals(bytes,store.load().png)
            assertThrows(IllegalArgumentException::class.java) {store.save(BackgroundImageRecord(101,bytes))}
            assertEquals(70,store.load().opacity);assertArrayEquals(bytes,store.load().png)
            assertThrows(IllegalArgumentException::class.java) {BackgroundImageDecoder.read(ByteArrayInputStream(ByteArray(BackgroundImageRules.sourceBytes+1)))}
            assertThrows(IllegalArgumentException::class.java) {BackgroundImageDecoder.decode(byteArrayOf(1,2,3))}
            val large=Bitmap.createBitmap(2400,600,Bitmap.Config.ARGB_8888);large.eraseColor(android.graphics.Color.GREEN)
            val decoded=BackgroundImageDecoder.decode(BackgroundImageDecoder.png(large));large.recycle()
            assertEquals(2000,decoded.width);assertEquals(500,decoded.height);decoded.recycle()
            val jpeg=File(directory,"public-orientation.jpg")
            val portrait=Bitmap.createBitmap(40,80,Bitmap.Config.ARGB_8888);portrait.eraseColor(android.graphics.Color.BLUE)
            jpeg.outputStream().use {assertTrue(portrait.compress(Bitmap.CompressFormat.JPEG,100,it))};portrait.recycle()
            android.media.ExifInterface(jpeg.absolutePath).apply {setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()}
            val oriented=BackgroundImageDecoder.decode(jpeg.readBytes());assertEquals(80,oriented.width);assertEquals(40,oriented.height);oriented.recycle()
            val writer=java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val a=writer.submit {repeat(20) {BackgroundImageStore(context).save(BackgroundImageRecord(it%101,bytes))}}
                val b=writer.submit {repeat(20) {assertArrayEquals(bytes,BackgroundImageStore(context).load().png)}}
                a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS)
            } finally {writer.shutdownNow()}
            store.save(BackgroundImageRecord(70));assertNull(store.load().png);assertEquals(70,store.load().opacity)
        } finally {directory.deleteRecursively()}
    }
}
