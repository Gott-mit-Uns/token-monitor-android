package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BundledFontMenuScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun bothMenusExposeBuiltInFontsAndKeepIndependentSelections() {
        val dark=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
        val prefs=mutableStateOf(DisplayPreferences(appearance=AppearancePreferences(reduceMotion="on")))
        compose.activity.runOnUiThread {compose.activity.setContent {TokenTheme(dark,LanguageChoice.ENGLISH,prefs.value.appearance) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                AppearanceSettings(prefs.value,{prefs.value=it.apply(prefs.value)},embedded=true)
            }
        }}}
        compose.onNodeWithTag("appearance-colors-toggle").performScrollTo().performClick()
        for(role in listOf("interface","display")) {
            compose.onNodeWithTag("appearance-$role-select").performScrollTo().performClick()
            compose.onNodeWithTag("appearance-$role-bundled-ui-label",useUnmergedTree=true).assertTextEquals("Inter + Noto Sans SC")
            compose.onNodeWithTag("appearance-$role-bundled-data-label",useUnmergedTree=true).assertTextEquals("JetBrains Mono")
            compose.onNodeWithTag("appearance-$role-bundled-data").performScrollTo().performClick()
            compose.onNodeWithTag("appearance-$role-value",useUnmergedTree=true).assertTextEquals("JetBrains Mono")
            compose.onNodeWithTag("appearance-$role-select").performScrollTo().performClick()
            saveVisualEvidence(compose.activity,compose.onNodeWithTag("appearance-$role-menu",useUnmergedTree=true),"bundled-font-menu-$role-${if(dark) "dark" else "light"}.png")
            compose.onNodeWithTag("appearance-$role-bundled-ui").performClick()
            compose.onNodeWithTag("appearance-$role-value",useUnmergedTree=true).assertTextEquals("Inter + Noto Sans SC")
        }
        compose.runOnIdle {
            assertEquals(DesktopAppearance.bundledUiFont,prefs.value.appearance.interfaceFont)
            assertEquals(DesktopAppearance.bundledUiFont,prefs.value.appearance.displayFont)
            assertEquals(BundledFonts.ui,nativeFont(prefs.value.appearance.interfaceFont))
            assertEquals(BundledFonts.data,nativeFont(DesktopAppearance.bundledDataFont))
        }
    }
}
