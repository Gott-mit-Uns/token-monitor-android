package dev.bing.tokenmonitor

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Compose cannot capture dialogs below API28; system screenshots preserve the same asserted UI. */
internal fun saveVisualEvidence(activity: Activity, root: SemanticsNodeInteraction, filename: String) {
    root.assertIsDisplayed()
    val bitmap = if (Build.VERSION.SDK_INT >= 28) root.captureToImage().asAndroidBitmap()
        else {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            // Android 8's system screenshot does not wait for a newly composed dialog to draw.
            val drawn = CountDownLatch(1)
            activity.runOnUiThread { activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { drawn.countDown() }
            } }
            check(drawn.await(5, TimeUnit.SECONDS)) { "Synthetic visual frame did not draw" }
            instrumentation.uiAutomation.takeScreenshot()
        }
    checkNotNull(bitmap)
    File(activity.getExternalFilesDir(null), filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
