package dev.bing.tokenmonitor

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hub times are instants; display in the device zone, without wire fractions or Z. */
internal object LimitDetailTime {
    private val display = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    fun format(value: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
        value?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { display.format(Instant.parse(raw).atZone(zone)) }.getOrNull()
        }
}
