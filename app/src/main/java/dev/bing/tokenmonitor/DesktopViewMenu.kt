package dev.bing.tokenmonitor

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable internal fun DesktopViewMenu(expanded:Boolean,current:HubPage,pages:List<HubPage>,width:Dp=126.dp,dismiss:()->Unit,choose:(HubPage)->Unit) {
    val strings=LocalResources.current
    DesktopOptionMenu(expanded,pages.map {DesktopMenuOption(it.name.lowercase(),strings.getString(it.title),"view-${it.name.lowercase()}",it.icon)},current.name.lowercase(),"hub-view-menu",width,above=true,wrapNavigation=true,horizontalNavigation=true,animate=true,maxHeight=null,dismiss=dismiss) {id->choose(pages.first {it.name.lowercase()==id})}
}
