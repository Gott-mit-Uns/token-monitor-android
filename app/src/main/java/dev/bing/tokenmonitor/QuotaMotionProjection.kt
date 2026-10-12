package dev.bing.tokenmonitor

import java.time.Instant
import java.util.Locale

/** One visible Limits event projection shared by its owner and actual account rows. */
internal object QuotaMotionProjection {
    fun accountRowsKey(windows:List<Limit>):String {
        val first=windows.first()
        return first.presentation.recordIndex?.let {"record:$it"} ?: "legacy:${first.provider}:${first.account.orEmpty()}"
    }
    fun project(limits:List<Limit>,display:LimitDisplayPreferences,locale:Locale,now:Instant):Map<String,List<ProviderQuotaRow>> =
        LimitDisplay.groups(limits).associate {windows->
            val record=LimitProviderLayout.record(windows)
            accountRowsKey(windows) to LimitPreferences.filter(LimitProviderLayout.project(record,display.showUsed,locale,now),
                display.hiddenItems[windows.first().provider].orEmpty())
        }
}
