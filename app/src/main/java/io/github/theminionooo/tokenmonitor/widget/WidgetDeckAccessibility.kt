package io.github.theminionooo.tokenmonitor.widget

import io.github.theminionooo.tokenmonitor.ui.displayName
import io.github.theminionooo.tokenmonitor.ui.formatUsageCost
import io.github.theminionooo.tokenmonitor.ui.formatTokens
import io.github.theminionooo.tokenmonitor.ui.providerLabel
import kotlin.math.roundToInt

/** Describe the visible page, without speaking account identities or session titles. */
internal fun widgetDeckPageSummary(page: WidgetDeckPage, data: WidgetDeckData): String {
    val snapshot = data.snapshot ?: return data.emptyMessage
    fun usage(row: WidgetDeckUsageRow, tool: Boolean): String =
        "${if (tool) row.name.displayName() else row.name}: ${formatTokens(row.tokens)} tokens, ${(row.share * 100).roundToInt()}% share, ${formatUsageCost(row.costUsd, row.unpricedTokens)} estimated cost."
    return when (page) {
        WidgetDeckPage.Overview -> buildString {
            append("${formatTokens(snapshot.today.totalTokens)} tokens, ${formatUsageCost(snapshot.today.costUsd, snapshot.today.unpricedTokens)} estimated cost for ${data.date}. ")
            data.stats.take(3).forEach { (value, label) -> append("$label: $value. ") }
            data.tools.take(3).forEach { append("${it.name.displayName()}: ${(it.share * 100).roundToInt()}% share. ") }
            append("Last seven days: ${formatTokens(data.week.sumOf { it.tokens })} recorded tokens, ${formatUsageCost(data.week.sumOf { it.costUsd }, data.week.sumOf { it.unpricedTokens })} estimated cost.")
        }
        WidgetDeckPage.Limits -> data.limitGroups.take(WidgetDeckGrid.Limits.ROW_TOPS.size).flatMap { group ->
            group.windows.take(2).map { window ->
                "${group.provider.providerLabel()} ${window.title}: ${window.remainingPercent.roundToInt()}% left. ${window.reset.ifBlank { "Reset not reported" }}."
            }
        }.joinToString(" ").ifBlank { "No quota windows reported." }
        WidgetDeckPage.Breakdown -> buildString {
            if (data.tools.isEmpty() && data.models.isEmpty()) append("No tool or model breakdown reported.")
            else {
                append("Tools. ")
                data.tools.take(WidgetDeckGrid.Breakdown.ROW_TOPS.size).forEach { append(usage(it, true)).append(' ') }
                if (data.tools.isEmpty()) append("No tool breakdown reported. ")
                append("Models. ")
                data.models.take(WidgetDeckGrid.Breakdown.ROW_TOPS.size).forEach { append(usage(it, false)).append(' ') }
                if (data.models.isEmpty()) append("No model breakdown reported.")
            }
        }
        WidgetDeckPage.Activity -> if (data.history.isEmpty()) "No activity history reported." else buildString {
            append("Seven days ending ${data.date}: ${formatTokens(data.week.sumOf { it.tokens })} tokens, ${formatUsageCost(data.week.sumOf { it.costUsd }, data.week.sumOf { it.unpricedTokens })} estimated cost. ")
            append("${data.week.size} recorded days; ${(7 - data.week.size).coerceAtLeast(0)} days have no observation. ")
            append("Peak day: ${formatTokens(data.week.maxOfOrNull { it.tokens } ?: 0)} tokens. ${data.activeDays} active days in the activity grid. ")
            if (data.messagesToday > 0) append("${data.messagesToday} messages for ${data.date}.")
            else append("${formatUsageCost(snapshot.today.costUsd, snapshot.today.unpricedTokens)} estimated cost for ${data.date}.")
        }
    }.trim()
}
