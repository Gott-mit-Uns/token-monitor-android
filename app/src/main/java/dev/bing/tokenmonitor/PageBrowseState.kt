package dev.bing.tokenmonitor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver

/** Only display state is retained. This holder owns no reads, subscriptions or Hub data. */
internal class PageBrowseState {
    private val queries=mutableMapOf<String,MutableState<String>>()
    private val scrolls=mutableMapOf<String,LazyListState>()
    fun query(page:HubPage)=queries.getOrPut(page.name) {mutableStateOf("")}
    fun scroll(page:HubPage)=scrolls.getOrPut(page.name) {LazyListState()}
    companion object {
        val Saver=listSaver<PageBrowseState,Any>(save={holder->
            HubPage.entries.flatMap {page->listOf(page.name,holder.query(page).value,holder.scroll(page).firstVisibleItemIndex,holder.scroll(page).firstVisibleItemScrollOffset)}
        },restore={values->PageBrowseState().apply {
            values.chunked(4).forEach {entry->queries[entry[0] as String]=mutableStateOf(entry[1] as String);scrolls[entry[0] as String]=LazyListState(entry[2] as Int,entry[3] as Int)}
        }})
    }
}
