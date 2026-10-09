package io.github.theminionooo.tokenmonitor.ui

import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import io.github.theminionooo.tokenmonitor.domain.UsagePeriod

/** Presentation only: preserve the complete Hub snapshot and rank the selected period. */
internal fun activeHomeDevices(devices: List<DeviceUsage>, period: DashboardPeriod): List<Pair<DeviceUsage, UsagePeriod>> =
    devices.map { it to period.usage(it) }
        .filter { (_, usage) -> usage.totalTokens > 0 }
        .sortedByDescending { (_, usage) -> usage.totalTokens }
