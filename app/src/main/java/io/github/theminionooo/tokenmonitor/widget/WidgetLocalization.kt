package io.github.theminionooo.tokenmonitor.widget

import android.content.Context
import io.github.theminionooo.tokenmonitor.R
import io.github.theminionooo.tokenmonitor.localization.localizedContext
import io.github.theminionooo.tokenmonitor.localization.uiText

/** Only presentation strings enter this helper; Hub and persistence keys stay unchanged. */
internal fun widgetText(context: Context, text: String): String {
    val localized = localizedContext(context)
    if (localized.resources.configuration.locales[0].language != "zh") return text
    val exact = widgetLabels
    exact[text]?.let { return localized.getString(it) }
    return text.split("\n").joinToString("\n") { line ->
        var result = line
        for ((source, id) in widgetLabels.entries.sortedByDescending { it.key.length }) {
            if (!result.contains(source)) continue
            val boundary = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(source) + "(?![\\p{L}\\p{N}])")
            result = boundary.replace(result) { localized.getString(id) }
        }
        uiText(localized, result)
    }
}

private val widgetLabels = mapOf(
        "LIVE" to R.string.widget_local_6990f01ad9,
        "OFFLINE" to R.string.widget_local_c689932b7d,
        "CONNECTING" to R.string.widget_local_48b998e89b,
        "UPDATING" to R.string.widget_local_45ae63fc05,
        "STALE" to R.string.widget_local_600a54dee1,
        "NO DATA" to R.string.widget_local_e3503343fe,
        "SAVED" to R.string.widget_local_24f3f74350,
        "WAITING" to R.string.widget_local_c271b656de,
        "TODAY" to R.string.widget_local_7a46866bc7,
        "USAGE" to R.string.widget_local_cf3bfa9a15,
        "OPEN" to R.string.widget_local_33906a7dfd,
        "OPEN APP" to R.string.widget_local_46274aa1f6,
        "LIMITS" to R.string.widget_local_83fa9b97f0,
        "7 DAYS" to R.string.widget_local_ba6e98ecd5,
        "MESSAGES" to R.string.widget_local_85cda815c6,
        "ACTIVE TODAY" to R.string.widget_local_2c30aebfc6,
        "STREAK" to R.string.widget_local_fc661abe8a,
        "Total tokens" to R.string.widget_local_e6dad16eef,
        "This week" to R.string.widget_local_7b72883e07,
        "No history yet" to R.string.widget_local_a21ab23cd2,
        "left" to R.string.widget_local_12c0f1fbad,
        "Reset not reported" to R.string.widget_local_13aea97320,
        "No tool or model breakdown reported" to R.string.widget_local_5b8abacf44,
        "Tools" to R.string.widget_local_4fa8cc860c,
        "Models" to R.string.widget_local_f3798f81c7,
        "Tokens" to R.string.widget_local_c38c6c1f3a,
        "Share" to R.string.widget_local_09ca55ca52,
        "No activity history reported" to R.string.widget_local_9662870fc0,
        "Activity" to R.string.widget_local_81c0d915fa,
        "Active days" to R.string.widget_local_340f3c0ba3,
        "Days" to R.string.widget_local_f6bb0f468a,
        "Messages today" to R.string.widget_local_4401b74874,
        "Messages" to R.string.widget_local_f1702b4686,
        "Cost today" to R.string.widget_local_ef1eaeca08,
        "Cost" to R.string.widget_local_64ae43e8fe,
        "No quota windows reported" to R.string.widget_local_e804180a12,
        "Refresh widget now" to R.string.widget_local_0a2d5088ed,
        "Refreshing widget" to R.string.widget_local_02b07454ec,
        "Turn widget Live off" to R.string.widget_local_771bcc67fa,
        "Turn widget Live on for one hour" to R.string.widget_local_9c50a2616b,
        "Connecting to Hub" to R.string.widget_local_2ac8603836,
        "No saved data" to R.string.widget_local_c2434cd55b,
        "Connecting" to R.string.widget_local_c1f3b71fdf,
        "Waiting for usage" to R.string.widget_local_f46e7632c3,
        "No data yet" to R.string.widget_local_8c48f89bdb,
        "No saved usage" to R.string.widget_local_e6df3fbce7,
        "Live is connecting to your Hub." to R.string.widget_local_b313a120fc,
        "Check your Hub in the app." to R.string.widget_local_f4f269cd7c,
        "Open the app to connect or check your Hub." to R.string.widget_local_f631945193,
        "Open app" to R.string.widget_local_b329e00d89,
        "Previous Token Monitor page" to R.string.widget_local_1a48cf329c,
        "Next Token Monitor page" to R.string.widget_local_7a813d2c87,
        "Seven calendar days of tokens; a dash means no recorded data" to R.string.widget_local_6b87791146,
        "Connecting to your Hub…" to R.string.widget_local_588c644c08,
        "Refreshing your saved usage…" to R.string.widget_local_cfa3c11a38,
        "Hub offline. Open Token Monitor to check the connection." to R.string.widget_local_1565ddc4f8,
        "Open Token Monitor to connect or refresh your Hub." to R.string.widget_local_59e2428574,
        "Waiting for the Hub" to R.string.widget_local_43049ce522,
        "Fetching a fresh Hub snapshot" to R.string.widget_local_cda7e389c7,
        "Widget updates for one hour" to R.string.widget_local_f38cd14b9f,
        "Token Monitor · Live" to R.string.widget_local_ef5146c254,
        "Refreshing Token Monitor" to R.string.widget_local_82285b1b0f,
        "Widget live updates" to R.string.widget_local_e64fb5b63a,
        "Stop" to R.string.widget_local_9e253470c8,
        "Reset now" to R.string.widget_local_a7ea0da313,
        "Weekly" to R.string.widget_local_158f3da592,
        "Session" to R.string.widget_local_f7f1997c6c,
        "Monthly" to R.string.widget_local_d31edb7b8a,
        "Daily" to R.string.widget_local_728298d3db,
        "Until" to R.string.widget_local_96bdb66ba4,
        "Updated" to R.string.widget_local_f2f8570ddd,
        "Live until" to R.string.widget_local_cb037c0948,
        "estimated cost" to R.string.widget_local_615cfa2d17,
        "est. cost" to R.string.widget_local_b4ec780d60,
        "msgs" to R.string.widget_local_b3d150e2b3,
        "days" to R.string.widget_local_5548ae4f34,
        "day" to R.string.widget_local_a2620cbc10,
        "tokens" to R.string.widget_local_3391436a4e,
        "today" to R.string.widget_local_2dd2bed725,
        "Snapshot saved at" to R.string.widget_local_4db03cf946,
        "Open dashboard" to R.string.widget_local_7ad1caed59,
        "Today by tool" to R.string.widget_local_cfb4a070f4,
        "percent remaining" to R.string.widget_local_dedcb4c64a,
        "percent" to R.string.widget_local_56b2495454,
        "Peak" to R.string.widget_local_c83dbbd3e6,
    )
