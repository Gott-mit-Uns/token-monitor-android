package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun PricingRow(price: ModelPrice,onClick:()->Unit) {
    val strings = LocalResources.current
    Column(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=onClick).padding(vertical = 8.dp).testTag("shared-price-${price.model}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(price.model, style = quotaTextStyle(11,bold=true))
        val labels=linkedMapOf("cacheReadPerM" to R.string.shared_price_hit,"inputPerM" to R.string.shared_price_miss,"outputPerM" to R.string.token_output,"cacheWritePerM" to R.string.token_cache_write,"cacheWrite1hPerM" to R.string.shared_price_hour).mapValues {strings.getString(it.value)}
        val meta=sharedPricingSummary(price,labels)
        Text(meta,style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.6f))
    }
}
