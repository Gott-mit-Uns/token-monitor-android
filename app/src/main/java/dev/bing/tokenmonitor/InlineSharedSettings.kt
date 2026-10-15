package dev.bing.tokenmonitor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Settings disclosures share the page's typography, scrolling, IME insets and saved state. */
@Composable internal fun InlineSettingsDisclosure(title:String,status:String,tag:String,expanded:Boolean,enabled:Boolean=true,toggle:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    fun close() {focus.clearFocus();keyboard?.hide();toggle()}
    BackHandler(expanded) {close()}
    Column(Modifier.fillMaxWidth()) {
        DesktopSharedSettingHeader(title,status,tag,expanded,enabled) {if(expanded) close() else toggle()}
        if(expanded) Column(Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("$tag-details"),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
    }
}

/** Hub prices remain authoritative read-only data; this is the same list without a second page. */
@Composable internal fun InlinePricingSettings(prices:HubPricing?,loading:Boolean,load:()->Unit) {
    val strings=LocalResources.current
    var expanded by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var page by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(prices?.models) {if(prices?.models?.none {it.model==selected}!=false) selected=null}
    val status=if(prices?.initialized!=true) "—" else if(prices.models.isEmpty()) strings.getString(R.string.settings_custom_pricing_none) else strings.getString(R.string.settings_custom_pricing_count,prices.models.size)
    InlineSettingsDisclosure(strings.getString(R.string.settings_custom_pricing_title),status,"settings-open-pricing",expanded,toggle={expanded=!expanded}) {
        Text(strings.getString(R.string.pricing_intro),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(strings.getString(R.string.shared_readonly_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        DesktopActionButton(onClick=load,enabled=!loading,modifier=Modifier.testTag("shared-pricing-refresh")) {Text(strings.getString(if(prices==null) R.string.load_pricing else R.string.refresh),style=quotaTextStyle(11))}
        if(prices==null) Text(strings.getString(R.string.pricing_not_loaded),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            Text(strings.getQuantityString(R.plurals.shared_pricing_summary,prices.models.size,prices.models.size),Modifier.testTag("shared-pricing-summary"),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!prices.initialized) Text(strings.getString(R.string.pricing_uninitialized),style=quotaTextStyle(11))
            else if(prices.models.isEmpty()) Text(strings.getString(R.string.pricing_empty),style=quotaTextStyle(11))
            else {
                DesktopListSearch(query,{query=it;page=0},strings.getString(R.string.search_dimension,strings.getString(R.string.dimension_models)),"shared-pricing-search")
                val rows=prices.models.filter {it.model.contains(query,true)}
                if(rows.isEmpty()) Text(strings.getString(R.string.pricing_no_match),style=quotaTextStyle(11))
                val pages=((rows.size+39)/40).coerceAtLeast(1);val current=page.coerceAtMost(pages-1)
                if(pages>1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    DesktopTextButton({page=current-1},enabled=current>0,modifier=Modifier.testTag("shared-pricing-prev")) {Text(strings.getString(R.string.shared_previous),style=quotaTextStyle(11))}
                    Text("${current+1}/$pages",Modifier.testTag("shared-pricing-page"),style=quotaTextStyle(11))
                    DesktopTextButton({page=current+1},enabled=current<pages-1,modifier=Modifier.testTag("shared-pricing-next")) {Text(strings.getString(R.string.shared_next),style=quotaTextStyle(11))}
                }
                rows.drop(current*40).take(40).forEach {price->
                    key(price.model) {
                        PricingRow(price) {selected=if(selected==price.model) null else price.model}
                        if(selected==price.model) Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp).testTag("shared-price-details"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            PriceDetailsContent(price)
                        }
                    }
                }
            }
            Text(strings.getString(R.string.shared_revision,prices.revision.toString(),prices.updated ?: strings.getString(R.string.update_time_unknown)),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
