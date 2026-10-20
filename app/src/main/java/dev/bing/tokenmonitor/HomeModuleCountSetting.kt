package dev.bing.tokenmonitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable internal fun HomeModuleCountSetting(id:String,prefs:DisplayPreferences,update:(DisplayChange)->Unit) {
    val strings=LocalResources.current
    val selected=HomeModuleCounts.count(prefs,id)
    var draft by remember(id,selected) {mutableStateOf(selected.toString())}
    fun commit() {
        val value=HomeModuleCounts.normalize(id,draft)
        draft=value.toString()
        if(value!=selected) update(DisplayChange.HomeModuleCount(id,draft))
    }
    Column(Modifier.fillMaxWidth().padding(start=14.dp).testTag("home-count-details-$id")) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(strings.getString(R.string.home_item_count),Modifier.weight(1f),style=quotaTextStyle(11))
            DesktopEditField(draft,{draft=it.take(32)},strings.getString(R.string.home_item_count),"home-count-$id",Modifier.width(64.dp),keyboardType=KeyboardType.Number,onFocus={if(!it) commit()},done=::commit)
        }
        if(id=="sessions") Text(strings.getString(R.string.home_session_count_note),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
