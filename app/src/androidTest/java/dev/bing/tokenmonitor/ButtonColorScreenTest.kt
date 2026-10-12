package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual desktop action roles remain distinct from a user-selected accent. */
class ButtonColorScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val dark get()=InstrumentationRegistry.getArguments().getString("visualDark")!="false"
    private class Actions:HubUiActions {
        var reads=0
        override fun connect(address:String,credential:String,alternate:String) {reads++}
        override fun refreshSaved() {reads++}
        override fun switchEndpoint() {reads++}
        override fun hasAlternate()=false
        override fun endpointType()="https"
        override fun loadHistory() {reads++}
        override fun loadModelNames() {reads++}
        override fun loadSubscriptions() {reads++}
        override fun loadPricing() {reads++}
        override fun loadDeviceHistory() {reads++}
        override fun forget() {reads++}
        override fun renameDevice(id:String,input:String) {reads++}
    }
    private fun show(tag:String)=compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag(tag))
    private fun click(tag:String) {show(tag);compose.onNodeWithTag(tag).performClick()}
    private fun textColor(node:SemanticsNodeInteraction):Color {
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        return layouts.single().layoutInput.style.color
    }
    private fun buttonTextColor(tag:String)=textColor(compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree=true))
    private fun pageTextColor()=textColor(compose.onNode(
        hasText("Theme Code") and hasAnyAncestor(hasTestTag("appearance-controls")),useUnmergedTree=true))
    private fun save(page:String,dialog:String?=null)=saveVisualEvidence(compose.activity,
        if(dialog==null) compose.onRoot() else compose.onNodeWithTag(dialog),
        "button-color-$page-${if(dark) "dark" else "light"}.png")

    @Test fun ordinaryActionsUseDesktopTextWhileSelectedDayAndPickerTransactionsKeepTheirRoles() {
        val initial=DisplayPreferences(theme=if(dark) ThemeChoice.DARK else ThemeChoice.LIGHT,
            language=LanguageChoice.ENGLISH,appearance=AppearancePreferences(colors=mapOf("accent" to "#ff0000")))
        val prefs=mutableStateOf(initial)
        val snapshot=HubParser.parse("""{"periods":{"today":{"totalTokens":1000,"costUsd":1}}}""")
        val state=HubState(snapshot=snapshot,history=HubHistory(emptyList(),emptyList()),modelNames=ModelNames(emptyMap(),"off"))
        val actions=Actions();var commits=0
        val text=if(dark) Color(0xFFEEF5FB) else Color(0xFF1C1F26)
        val code=DesktopAppearance.encode(DesktopAppearance.palette(initial.appearance.colors,dark))
        compose.activity.runOnUiThread {compose.activity.setContent {
            TokenTheme(dark,prefs.value.language,prefs.value.appearance) {
                HubScreen(state,actions,preferences=prefs.value,updateDisplay={commits++;prefs.value=it.apply(prefs.value)})
            }
        }}
        compose.onNodeWithTag("hub-total-tokens").assertTextEquals("1,000")
        compose.onNodeWithTag("hub-period-today").assertIsSelected()
        assertEquals(Color.Red,buttonTextColor("hub-period-today"))

        compose.onNodeWithTag("hub-settings-toggle").performClick();openSettingsSection(compose,"subscriptions")
        show("settings-subscriptions-load")
        compose.onNodeWithTag("settings-subscriptions-load").assertIsEnabled()
        assertEquals(text,buttonTextColor("settings-open-subscriptions"))
        assertEquals(text,buttonTextColor("settings-subscriptions-load"))
        save("settings")

        openSettingsSection(compose,"appearance");click("appearance-colors-toggle")
        show("appearance-theme-code-field");compose.onNodeWithTag("appearance-theme-code-field").assertTextEquals(code)
        click("appearance-color-text-open")
        compose.onNodeWithTag("appearance-color-text").performTextReplacement("#ff0000")
        compose.onNodeWithTag("appearance-color-text-apply").performScrollTo().assertIsEnabled()
        // The real page previews red text while its native picker retains the saved baseline.
        assertEquals(Color.Red,pageTextColor())
        assertEquals(text,buttonTextColor("appearance-color-text-apply"))
        assertEquals(text,buttonTextColor("appearance-color-text-cancel"))
        assertEquals(initial,prefs.value);assertEquals(0,commits)
        save("picker","appearance-color-text-dialog")

        compose.onNodeWithTag("appearance-color-text").performTextReplacement("invalid")
        compose.onNodeWithTag("appearance-color-text-apply").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("appearance-color-text-cancel").assertIsEnabled()
        assertEquals(text.copy(alpha=.5f),buttonTextColor("appearance-color-text-apply"))
        assertEquals(text,buttonTextColor("appearance-color-text-cancel"))
        assertEquals(Color.Red,pageTextColor())
        save("disabled","appearance-color-text-dialog")
        compose.onNodeWithTag("appearance-color-text-cancel").performClick()
        compose.onNodeWithTag("appearance-color-text-dialog").assertDoesNotExist()
        assertEquals(text,pageTextColor())
        show("appearance-theme-code-field");compose.onNodeWithTag("appearance-theme-code-field").assertTextEquals(code)
        assertEquals(initial,prefs.value);assertEquals(0,commits)

        click("appearance-color-accent-open")
        compose.onNodeWithTag("appearance-color-accent").performTextReplacement("#00aa00")
        compose.onNodeWithTag("appearance-color-accent-apply").performScrollTo().assertIsEnabled()
        assertEquals(text,buttonTextColor("appearance-color-accent-apply"))
        compose.onNodeWithTag("appearance-color-accent-apply").performClick()
        compose.onNodeWithTag("appearance-color-accent-dialog").assertDoesNotExist()
        assertEquals("#00aa00",prefs.value.appearance.colors["accent"]);assertEquals(1,commits)
        show("appearance-theme-code-field");compose.onNodeWithTag("appearance-theme-code-field")
            .assertTextEquals(DesktopAppearance.encode(DesktopAppearance.palette(prefs.value.appearance.colors,dark)))
        compose.onNodeWithTag("hub-settings-toggle").performClick()
        compose.onNodeWithTag("hub-period-today").assertIsSelected()
        assertEquals(Color(0xFF00AA00),buttonTextColor("hub-period-today"))
        assertEquals(snapshot,state.snapshot);assertEquals(0,actions.reads)
    }
}
