package dev.bing.tokenmonitor

import android.content.res.Resources

fun Device.displayName(strings: Resources): String = DeviceDisplay.name(this,fallback=strings.getString(R.string.unnamed_device))
fun Limit.displayProvider(strings: Resources): String = if (provider == "__unknown_provider__") strings.getString(R.string.unknown_provider) else providerLabel(provider)

fun Subscription.displayProvider(strings: Resources): String = if (provider == "__unknown_provider__") strings.getString(R.string.unknown_provider) else providerLabel(provider)
