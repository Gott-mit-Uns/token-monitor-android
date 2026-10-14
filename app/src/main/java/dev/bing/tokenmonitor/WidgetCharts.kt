package dev.bing.tokenmonitor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.time.LocalDate
import kotlin.math.ceil

/** Raster charts inside real RemoteViews; missing observations are gaps, never made-up zeros. */
internal object WidgetCharts {
    fun percentage(value: Double?, color: Int, dark: Boolean): Bitmap {
        val image = Bitmap.createBitmap(200, 6, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (dark) Color.rgb(40, 48, 55) else Color.rgb(222, 228, 232) }
        canvas.drawRoundRect(0f, 0f, 200f, 6f, 3f, 3f, paint)
        if (value != null) { paint.color = color; canvas.drawRoundRect(0f, 0f, (value.coerceIn(0.0, 100.0) * 2).toFloat(), 6f, 3f, 3f, paint) }
        return image
    }
    fun trend(points: List<WidgetTrendPoint>, width: Int, dark: Boolean): Bitmap {
        val image = Bitmap.createBitmap(width.coerceIn(160, 720), 68, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(115, 189, 245); strokeWidth = 3f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        val peak = points.mapNotNull { it.tokens }.maxOrNull()?.coerceAtLeast(1) ?: 1
        val path = Path(); var continuing = false
        points.forEachIndexed { index, point ->
            if (point.tokens == null) { continuing = false; return@forEachIndexed }
            val x = 4 + (image.width - 8f) * index / (points.size - 1).coerceAtLeast(1)
            val y = 62 - point.tokens.toDouble().div(peak).times(54).toFloat()
            if (continuing) path.lineTo(x, y) else path.moveTo(x, y)
            continuing = true
            canvas.drawCircle(x, y, 2f, paint)
        }
        canvas.drawPath(path, paint)
        paint.color = if (dark) Color.argb(42, 163, 173, 187) else Color.argb(55, 72, 83, 75)
        paint.strokeWidth = 1f; canvas.drawLine(2f, 65f, image.width - 2f, 65f, paint)
        return image
    }

    fun activity(history: HubHistory, today: LocalDate, width: Int, dark: Boolean, weeks: Int): Bitmap {
        val image = Bitmap.createBitmap(width.coerceIn(160, 720), 140, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val rows = history.daily.sortedBy { it.date }.takeLast(182).associateBy { it.date }
        val peak = rows.values.mapNotNull { it.tokens }.maxOrNull()?.coerceAtLeast(1) ?: 1
        val count = weeks.coerceIn(4, 26)
        val sunday = today.minusDays(today.dayOfWeek.value.toLong() % 7)
        val start = sunday.minusWeeks(count - 1L)
        val cellWidth = (image.width - 4f) / count
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = if (dark) Color.rgb(35, 43, 48) else Color.rgb(218, 226, 226)
        val shades = intArrayOf(base, Color.rgb(34, 75, 58), Color.rgb(50, 119, 82), Color.rgb(74, 163, 105), Color.rgb(130, 205, 145))
        repeat(count * 7) { index ->
            val date = start.plusDays(index.toLong())
            val row = rows[date.toString()]
            val value = row?.tokens
            val level = if (value == null) 0 else ceil(value.toDouble() / peak * 4).toInt().coerceIn(0, 4)
            val x = index / 7 * cellWidth + 2
            val y = index % 7 * 20f + 1
            paint.color = if (date > today) Color.TRANSPARENT else shades[level]
            canvas.drawRoundRect(x, y, x + cellWidth - 3, y + 16, 2f, 2f, paint)
            if (date <= today && value == null) {
                paint.color = if (dark) Color.rgb(106, 115, 126) else Color.rgb(125, 135, 135)
                paint.strokeWidth = 1f
                canvas.drawLine(x + 2, y + 13, x + cellWidth - 5, y + 3, paint)
            }
        }
        return image
    }
}
