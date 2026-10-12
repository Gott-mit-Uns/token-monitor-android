package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HubLanSettingsScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun approvalRequiresDialogActionAndIndependentToggleWorksAcrossThemesAndLargeFonts() {
        var confirmations=0
        val actions=object:HubUiActions {
            var enabled by mutableStateOf(true)
            override fun connectionEndpoints()=listOf("https://public.example.invalid","http://192.168.1.2:3000")
            override fun lanEnabled()=enabled
            override fun configureLan(enabled:Boolean,confirmAssociation:Boolean) {this.enabled=enabled;if(confirmAssociation) confirmations++}
            override fun connect(address:String,credential:String,alternate:String) {}
            override fun refreshSaved() {}
            override fun switchEndpoint() {}
            override fun hasAlternate()=true
            override fun endpointType()="https"
            override fun loadModelNames() {}
            override fun loadHistory() {}
            override fun loadSubscriptions() {}
            override fun forget() {}
            override fun renameDevice(id:String,input:String) {}
        }
        for(dark in listOf(false,true)) for(scale in listOf(1f,2f)) {
            compose.runOnUiThread {
                actions.enabled=true
                compose.activity.setContent {
                    val original=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(original.density,scale)) {
                        TokenTheme(dark,LanguageChoice.ENGLISH) {
                            Surface {Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                HubConnectionSettings(HubState(live=true),actions,true,null,null,{},remember {HubConnectionDraft()})
                            }}
                        }
                    }
                }
            }
            val before=confirmations
            compose.onNodeWithTag("connection-lan-associate").performScrollTo().performClick()
            compose.onNodeWithTag("connection-lan-warning").assertExists()
            assertEquals(before,confirmations)
            compose.onNodeWithTag("connection-lan-confirm").assertIsDisplayed().performClick()
            assertEquals(before+1,confirmations)
            compose.onNodeWithTag("connection-lan-warning").assertDoesNotExist()
            compose.onNodeWithTag("connection-lan-toggle").performScrollTo().performClick()
            compose.runOnIdle {assertFalse(actions.enabled)}
            compose.onNodeWithTag("connection-lan-associate").assertIsNotEnabled()
        }
    }
}
