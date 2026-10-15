package dev.bing.tokenmonitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable internal fun SharedDataHeader(title:String,summary:String,tag:String,onInfo:()->Unit) {
    val strings=LocalResources.current
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("$tag-summary"))
        }
        DesktopTextButton(onClick=onInfo,modifier=Modifier.heightIn(min=48.dp).testTag("$tag-info")) {Text(strings.getString(R.string.shared_details))}
    }
}

@Composable internal fun PriceDetails(price:ModelPrice,onClose:()->Unit) {
    CompactUsageDialog(price.model,"shared-price-details",onClose) {PriceDetailsContent(price)}
}

@Composable internal fun PriceDetailsContent(price:ModelPrice) {
    val strings=LocalResources.current
    val labels=linkedMapOf("inputPerM" to R.string.shared_price_miss,"outputPerM" to R.string.token_output,"cacheReadPerM" to R.string.shared_price_hit,"cacheWritePerM" to R.string.token_cache_write,"cacheWrite1hPerM" to R.string.shared_price_hour)
    Text(strings.getString(R.string.shared_rate_units),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    labels.forEach { (key,label) ->
        val rate=price.rates[key]
        val value=when {rate==null -> strings.getString(R.string.price_unset);rate.toDouble()==0.0 -> strings.getString(R.string.price_zero);else -> sharedRateText(rate)}
        Text(strings.getString(R.string.price_rate,strings.getString(label),value),style=quotaTextStyle(11),modifier=Modifier.testTag("shared-price-$key"))
    }
}

@Composable internal fun SubscriptionRow(record:Subscription,today:LocalDate,identity:String,accounts:List<JsonObject> = emptyList(),onClick:()->Unit) {
    val strings=LocalResources.current;val colors=MaterialTheme.colorScheme
    val view=SubscriptionPresentation.row(record,accounts,today,strings.configuration.locales[0],LocalLimitDisplay.current.maskEmails,LocalDesktopToolIcons.current)
    val shape=RoundedCornerShape(6.dp)
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(shape).background(LocalDesktopSunkenColor.current.copy(alpha=0.36f))
        .border(1.dp,colors.outlineVariant,shape).clickable(onClick=onClick).semantics {role=Role.Button}
        .padding(horizontal=8.dp,vertical=7.dp).testTag("shared-subscription-$identity"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(view.icon) BrandMark(record.provider,Modifier.padding(start=1.dp).graphicsLayer(alpha=0.85f),providerMark=true,size=13.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(view.title,Modifier.testTag("shared-subscription-$identity-title"),style=quotaTextStyle(11))
            Text(view.meta,style=quotaTextStyle(9),color=colors.onSurfaceVariant,modifier=Modifier.testTag("shared-subscription-$identity-meta"))
            if(view.warning.isNotEmpty()) Text(view.warning,style=quotaTextStyle(9),color=LocalDesktopWarningColor.current,modifier=Modifier.testTag("shared-subscription-$identity-warning"))
        }
    }
}

@Composable internal fun SubscriptionDetails(record:Subscription,today:LocalDate,accounts:List<JsonObject> = emptyList(),onClose:()->Unit) {
    val strings=LocalResources.current
    val title=SubscriptionPresentation.row(record,accounts,today,strings.configuration.locales[0],LocalLimitDisplay.current.maskEmails,false).title
    CompactUsageDialog(title,"shared-subscription-details",onClose) {
        record.plan?.let {Text(LimitDisplay.text(it,LocalLimitDisplay.current.maskEmails),style=quotaTextStyle(11))}
        val context=LocalSubscriptionReadContext.current ?: SubscriptionReadContext(listOf(record),accounts,emptyMap(),today,strings.configuration.locales[0],LocalUsageCurrency.current)
        SubscriptionTooltipTable(context.forSubscription(record,SubscriptionAccounts.match(record,accounts)),"shared-subscription")
        record.topUps.forEach {top -> Text(strings.getString(R.string.top_up,top.date,top.amount?.toPlainString() ?: "—",record.currency.orEmpty()),style=MaterialTheme.typography.bodySmall)}
        record.note?.let {Text(LimitDisplay.text(it,LocalLimitDisplay.current.maskEmails),style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable internal fun ModelRuleControl(names:ModelNames?,loading:Boolean,load:()->Unit) {
    val strings=LocalResources.current
    var expanded by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    var page by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(names) {if(names==null || !names.initialized) {expanded=false;query=""}}
    if(names==null) {
        DesktopActionButton(onClick=load,enabled=!loading,modifier=Modifier.heightIn(min=48.dp).testTag("shared-model-rules-load"),visualTag="shared-model-rules-load-visual") {Text(strings.getString(R.string.load_model_names),style=quotaTextStyle(11))}
        Text(strings.getString(R.string.original_model_names),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val status=if(!names.initialized) strings.getString(R.string.shared_model_rules_uninitialized) else if(names.aliases.isEmpty()) strings.getString(when(names.grouping) {"prefix"->R.string.shared_group_prefix;"duplicates"->R.string.shared_group_duplicates;else->R.string.shared_group_off}) else strings.getString(R.string.settings_model_aliases_count,names.aliases.size)
        InlineSettingsDisclosure(strings.getString(R.string.settings_model_aliases_title),status,"shared-model-rules-open",expanded,enabled=names.initialized,toggle={expanded=!expanded}) {
            Column(Modifier.fillMaxWidth().testTag("shared-model-rules"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(strings.getString(R.string.settings_model_aliases_title),Modifier.testTag("shared-model-rules-title"),style=quotaTextStyle(11))
        Text(strings.getString(R.string.shared_model_names),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(strings.getString(R.string.shared_rule_grouping,strings.getString(when(names.grouping) {"prefix" -> R.string.shared_group_prefix;"duplicates" -> R.string.shared_group_duplicates;else -> R.string.shared_group_off})),style=quotaTextStyle(11))
        DesktopActionButton(onClick=load,enabled=!loading,modifier=Modifier.heightIn(min=48.dp).testTag("shared-rules-refresh"),visualTag="shared-rules-refresh-visual") {Text(strings.getString(R.string.refresh))}
        DesktopListSearch(query,{query=it;page=0},strings.getString(R.string.shared_rule_search),"shared-rule-search")
        val rows=names.aliases.entries.filter {query.isEmpty() || it.key.contains(query,true) || it.value.contains(query,true)}.sortedBy {it.key}
        if(rows.isEmpty()) Text(strings.getString(if(names.aliases.isEmpty()) R.string.shared_rules_empty else R.string.pricing_no_match),style=quotaTextStyle(11))
        val pages=((rows.size+99)/100).coerceAtLeast(1);val current=page.coerceAtMost(pages-1)
        if(pages>1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            DesktopTextButton(onClick={page=current-1},enabled=current>0,modifier=Modifier.heightIn(min=48.dp).testTag("shared-rules-prev")) {Text(strings.getString(R.string.shared_previous))}
            Text("${current+1}/$pages",style=quotaTextStyle(11),modifier=Modifier.testTag("shared-rules-page"))
            DesktopTextButton(onClick={page=current+1},enabled=current<pages-1,modifier=Modifier.heightIn(min=48.dp).testTag("shared-rules-next")) {Text(strings.getString(R.string.shared_next))}
        }
        rows.drop(current*100).take(100).forEach {row ->
            Text(row.key,style=quotaTextStyle(11))
            Text("→ "+row.value,style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(strings.getString(R.string.shared_readonly_note),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
