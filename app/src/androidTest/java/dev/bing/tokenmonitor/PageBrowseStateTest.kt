package dev.bing.tokenmonitor

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class PageBrowseStateTest {
    @Test fun eachPageRetainsItsQueryAndScrollAcrossSavedStateRestore() {
        val holder=PageBrowseState.Saver.restore(HubPage.entries.flatMap {page->listOf(page.name,"",if(page==HubPage.PRICING) 12 else if(page==HubPage.TOOLS) 3 else 0,if(page==HubPage.PRICING) 34 else if(page==HubPage.TOOLS) 9 else 0)})!!
        holder.query(HubPage.PRICING).value="model-a"
        holder.query(HubPage.TOOLS).value="tool-b"
        assertEquals("model-a",holder.query(HubPage.PRICING).value)
        assertEquals(12,holder.scroll(HubPage.PRICING).firstVisibleItemIndex)
        val scope=object:SaverScope {override fun canBeSaved(value:Any)=true}
        val value=with(PageBrowseState.Saver) {scope.save(holder)}!!
        val restored=PageBrowseState.Saver.restore(value)!!
        assertEquals("model-a",restored.query(HubPage.PRICING).value)
        assertEquals("tool-b",restored.query(HubPage.TOOLS).value)
        assertEquals(12,restored.scroll(HubPage.PRICING).firstVisibleItemIndex)
        assertEquals(34,restored.scroll(HubPage.PRICING).firstVisibleItemScrollOffset)
        assertEquals(3,restored.scroll(HubPage.TOOLS).firstVisibleItemIndex)
        assertEquals("",restored.query(HubPage.HOME).value)
    }
}
