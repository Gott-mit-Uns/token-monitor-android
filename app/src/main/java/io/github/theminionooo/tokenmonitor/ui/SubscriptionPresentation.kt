package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.Subscription
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun subscriptionRenewal(subscription: Subscription, today: LocalDate = LocalDate.now()): String {
    if (subscription.kind == "topup" || !subscription.autoRenew) return ""
    fun parse(text: String) = runCatching { LocalDate.parse(text) }.getOrNull()
    val override = parse(subscription.nextRenewalOverride)
    if (override != null && !override.isBefore(today)) return override.toString()
    val anchor = parse(subscription.startDate) ?: return ""
    val step = subscription.intervalCount.coerceAtLeast(1).toLong() * if (subscription.interval == "year") 12 else 1
    val elapsed = (today.year - anchor.year) * 12L + today.monthValue - anchor.monthValue
    var periods = (elapsed / step).coerceAtLeast(0)
    var next = anchor.plusMonths(periods * step)
    while (next.isBefore(today)) { periods++; next = anchor.plusMonths(periods * step) }
    return next.toString()
}

internal fun subscriptionDays(subscription: Subscription, today: LocalDate = LocalDate.now()): Long? {
    val start = runCatching { LocalDate.parse(if (subscription.kind == "topup") subscription.topUps.minOfOrNull { it.date }.orEmpty() else subscription.startDate) }.getOrNull() ?: return null
    if (start.isAfter(today)) return 0
    val explicitEnd = runCatching { LocalDate.parse(subscription.endDate) }.getOrNull()
    val stop = explicitEnd ?: if (subscription.kind != "topup" && !subscription.autoRenew) start.plusMonths(subscription.intervalCount.coerceAtLeast(1).toLong() * if (subscription.interval == "year") 12 else 1) else null
    val end = stop?.takeIf { it.isBefore(today) } ?: today
    return ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
}

/** ROI belongs to a provider rollup: Hub tool usage cannot identify the signed-in account. */
internal fun providerValueMultiple(snapshot: io.github.theminionooo.tokenmonitor.domain.HubSnapshot, provider: String, options: io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions, today: LocalDate = LocalDate.now()): Double? {
    val tool = when (provider) { "codex" -> "codex"; "claude" -> "claude"; "minimax" -> "mcode"; else -> return null }
    val subscriptions = snapshot.subscriptions.entries.filter { it.provider == provider && it.kind != "topup" }
    var monthlyUsd = 0.0
    for (subscription in subscriptions) {
        val start = runCatching { LocalDate.parse(subscription.startDate) }.getOrNull() ?: return null
        val step = subscription.intervalCount.coerceAtLeast(1).toLong() * if (subscription.interval == "year") 12 else 1
        val stop = runCatching { LocalDate.parse(subscription.endDate) }.getOrNull()
            ?: if (!subscription.autoRenew) start.plusMonths(step) else null
        if (start.isAfter(today) || (stop != null && !stop.isAfter(today))) continue
        val rate = if (subscription.currency == "USD") 1.0 else options.currencyRates[subscription.currency]
            ?: options.usdRate.takeIf { options.currency == subscription.currency } ?: return null
        if (!rate.isFinite() || rate <= 0) return null
        monthlyUsd += subscription.amountMinor / 100.0 / rate / step
    }
    val cost = snapshot.month.clientCosts[tool] ?: return null
    return if (monthlyUsd.isFinite() && cost.isFinite() && monthlyUsd > 0 && cost > 0) cost / monthlyUsd else null
}
