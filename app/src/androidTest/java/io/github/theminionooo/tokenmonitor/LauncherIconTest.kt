package io.github.theminionooo.tokenmonitor

import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

class LauncherIconTest {
    @Test fun adaptiveLayersHaveSafeLogoAndNoDarkOuterRing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val icon = context.packageManager.getApplicationIcon(context.packageName) as AdaptiveIconDrawable
        val background = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        icon.background.setBounds(0, 0, 108, 108); icon.background.draw(Canvas(background))
        for ((x, y) in listOf(0 to 0, 107 to 0, 0 to 107, 107 to 107)) {
            val pixel = background.getPixel(x, y)
            assertEquals(255, Color.alpha(pixel)); assertTrue(Color.blue(pixel) > 150)
        }
        val foreground = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        icon.foreground.setBounds(0, 0, 108, 108); icon.foreground.draw(Canvas(foreground))
        val occupied = (0 until 108).flatMap { y -> (0 until 108).filter { x -> Color.alpha(foreground.getPixel(x, y)) > 0 }.map { x -> x to y } }
        assertTrue(occupied.isNotEmpty())
        assertTrue(occupied.all { (x, y) -> x in 21..86 && y in 21..86 })
        assertTrue(occupied.maxOf { it.first } - occupied.minOf { it.first } + 1 >= 48)
        val masked = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 144, 144); icon.draw(Canvas(masked))
        for ((x, y) in listOf(72 to 7, 72 to 136, 7 to 72, 136 to 72)) {
            val pixel = masked.getPixel(x, y)
            assertTrue(Color.alpha(pixel) > 200); assertTrue("Unexpected dark icon ring", Color.blue(pixel) > 150)
        }
        val density = context.resources.displayMetrics.density
        val preview = Bitmap.createBitmap((360 * density).toInt(), (220 * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f * density }
        for (row in 0..1) {
            val top = row * 110f
            paint.color = if (row == 0) Color.rgb(238, 241, 248) else Color.rgb(11, 15, 26)
            canvas.drawRect(0f, top * density, 360f * density, (top + 110) * density, paint)
            paint.color = if (row == 0) Color.BLACK else Color.WHITE
            canvas.drawText(if (row == 0) "浅色背景 · 原生自适应图标" else "深色背景 · 原生自适应图标", 12f * density, (top + 20) * density, paint)
            for ((column, size) in listOf(48, 64).withIndex()) {
                val x = 48 + column * 150; val y = (top + 28 + (64 - size) / 2).toInt()
                // AdaptiveIconDrawable caches at its bounds size, so use physical pixels.
                icon.setBounds((x * density).toInt(), (y * density).toInt(), ((x + size) * density).toInt(), ((y + size) * density).toInt()); icon.draw(canvas)
                canvas.drawText("${size}dp", x * density, (top + 105) * density, paint)
            }
        }
        context.openFileOutput("launcher-icon.png", 0).use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        background.recycle(); foreground.recycle(); masked.recycle(); preview.recycle()
    }
}
