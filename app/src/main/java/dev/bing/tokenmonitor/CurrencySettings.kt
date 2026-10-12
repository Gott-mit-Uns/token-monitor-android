package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable internal fun CurrencySettings(preferences:DisplayPreferences,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val labels=mapOf("USD" to R.string.usage_currency_usd,"TWD" to R.string.usage_currency_twd,"HKD" to R.string.usage_currency_hkd,"CNY" to R.string.usage_currency_cny)
    DesktopSettingSelect(strings.getString(R.string.usage_currency),UsageCurrency.codes.map {it to strings.getString(labels.getValue(it))},preferences.currency,"usage-currency") {focus.clearFocus();keyboard?.hide();change(DisplayChange.Currency(it))}
    if(preferences.currency!="USD") {
        val code=preferences.currency;val saved=preferences.currencyRates[code]
        DesktopSettingChoices(strings.getString(R.string.usage_exchange_rate),listOf("builtin" to strings.getString(R.string.usage_rate_builtin),"manual" to strings.getString(R.string.usage_rate_manual)),if(saved==null) "builtin" else "manual","usage-rate-mode") {mode->
            focus.clearFocus();keyboard?.hide()
            change(DisplayChange.CurrencyRate(code,if(mode=="manual") UsageCurrency(code,preferences.currencyRates).rate else null))
        }
        if(saved!=null) {
            var rate by rememberSaveable(code,saved) {mutableStateOf(saved)}
            var hadFocus by remember {mutableStateOf(false)}
            fun submit(clear:Boolean) {
                val next=rate.trim().takeIf(UsageCurrency::validRate)
                hadFocus=false
                if(clear) {focus.clearFocus();keyboard?.hide()}
                if(next!=saved) change(DisplayChange.CurrencyRate(code,next))
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("1 USD =",style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopEditField(rate,{rate=it.take(128)},strings.getString(R.string.usage_rate_label,code),"usage-currency-rate",Modifier.weight(1f),keyboardType=KeyboardType.Decimal,
                    onFocus={active->val before=hadFocus;hadFocus=active;if(before && !active) submit(false)},done={submit(true)})
                Text(code,style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopEditAction("✓",strings.getString(R.string.usage_rate_apply),"usage-rate-save") {submit(true)}
            }
        } else Text(strings.getString(R.string.usage_builtin_rate),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(strings.getString(R.string.usage_currency_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
}
