package dev.bing.tokenmonitor

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Compose layout assertions; synthetic density cases are not physical-device evidence. */
class SettingSelectLayoutScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val ids=LanguageChoice.entries.map {it.name}
    private fun node(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true)
    private fun layout(tag:String):TextLayoutResult {
        val result=mutableListOf<TextLayoutResult>()
        node(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(result)}
        return result.single()
    }
    private fun assertComplete(tag:String,expected:String) {
        node(tag).assertTextEquals(expected).assertIsDisplayed()
        val text=layout(tag)
        assertFalse("$tag must not hide text outside the measured box: size=${text.size}, constraints=${text.layoutInput.constraints}, lines=${text.lineCount}, widthOverflow=${text.didOverflowWidth}, heightOverflow=${text.didOverflowHeight}, style=${text.layoutInput.style}",text.hasVisualOverflow)
        for(line in 0 until text.lineCount) assertFalse("$tag line $line must be complete",text.isLineEllipsized(line))
        assertEquals(expected,text.layoutInput.text.text)
    }
    private fun assertInside(childTag:String,parentTag:String) {
        // Unclipped bounds catch rows whose visible part alone would look contained.
        val child=node(childTag).getUnclippedBoundsInRoot()
        val parent=node(parentTag).getUnclippedBoundsInRoot()
        val tolerance=.5.dp
        assertTrue("$childTag left must be inside $parentTag",child.left>=parent.left-tolerance)
        assertTrue("$childTag right must be inside $parentTag",child.right<=parent.right+tolerance)
        assertTrue("$childTag top must be inside $parentTag",child.top>=parent.top-tolerance)
        assertTrue("$childTag bottom must be inside $parentTag",child.bottom<=parent.bottom+tolerance)
    }
    private fun labels(language:LanguageChoice)=listOf("中文","English",if(language==LanguageChoice.CHINESE) "跟随系统" else "System default")
    private fun assertAllOptions(language:LanguageChoice) {
        val menu=node("layout-language-menu")
        val scroll=menu.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("The three language choices must be visible without scrolling",0f,scroll.maxValue(),.01f)
        ids.zip(labels(language)).forEach {(id,label)->
            assertComplete("layout-language-$id-label",label)
            assertInside("layout-language-$id","layout-language-menu")
            assertInside("layout-language-$id-label","layout-language-$id")
            assertTrue("Each option keeps its touch target",node("layout-language-$id").getUnclippedBoundsInRoot().height>=47.5.dp)
        }
        ids.zipWithNext().forEach {(first,next)->
            assertTrue("Adjacent language touch targets must not overlap",node("layout-language-$first").getUnclippedBoundsInRoot().bottom<=node("layout-language-$next").getUnclippedBoundsInRoot().top)
        }
    }

    @Test fun completeOptionsRemainVisibleForEverySelectionLanguageAndFontScale() {
        val language=mutableStateOf(LanguageChoice.CHINESE)
        val selected=mutableStateOf(LanguageChoice.CHINESE.name)
        val fontScale=mutableFloatStateOf(1f)
        val textSize=mutableStateOf("standard")
        compose.activityRule.scenario.onActivity {activity->activity.setContent {
            val baseDensity=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density,fontScale.floatValue)) {
                TokenTheme(true,language.value,AppearancePreferences(textSize=textSize.value,reduceMotion="on")) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.TopStart) {
                        Column(Modifier.widthIn(max=360.dp).fillMaxWidth().padding(8.dp)) {
                            val strings=LocalResources.current
                            DesktopSettingSelect(strings.getString(R.string.language_label),
                                listOf(LanguageChoice.CHINESE.name to strings.getString(R.string.language_chinese),LanguageChoice.ENGLISH.name to "English",LanguageChoice.SYSTEM.name to strings.getString(R.string.follow_system)),
                                selected.value,"layout-language") {selected.value=it}
                        }
                    }
                }
            }
        }}
        for(displayLanguage in listOf(LanguageChoice.CHINESE,LanguageChoice.ENGLISH)) {
            for(scale in listOf(1f,1.3f,2f)) for(size in listOf("standard","largest")) for(choice in LanguageChoice.entries) {
                compose.runOnIdle {language.value=displayLanguage;fontScale.floatValue=scale;textSize.value=size;selected.value=choice.name}
                assertComplete("layout-language-value",labels(displayLanguage)[choice.ordinal])
                assertInside("layout-language-value","layout-language-select")
                node("layout-language-select").performClick()
                assertAllOptions(displayLanguage)
                node("layout-language-${choice.name}").assertIsSelected().performClick()
                compose.runOnIdle {assertEquals(choice.name,selected.value)}
                node("layout-language-menu").assertDoesNotExist()
            }
        }
    }

    @Test fun narrowLargeTextStacksTheControlAndKeepsTheFullSelectedValue() {
        val selected=mutableStateOf(LanguageChoice.SYSTEM.name)
        compose.activityRule.scenario.onActivity {activity->activity.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                TokenTheme(true,LanguageChoice.ENGLISH,AppearancePreferences(textSize="largest",reduceMotion="on")) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Column(Modifier.width(180.dp).padding(8.dp)) {
                            DesktopSettingSelect("Language",ids.zip(labels(LanguageChoice.ENGLISH)),selected.value,"layout-language") {selected.value=it}
                        }
                    }
                }
            }
        }}
        assertComplete("layout-language-title","Language")
        assertComplete("layout-language-value","System default")
        assertInside("layout-language-title","layout-language-row")
        assertInside("layout-language-select","layout-language-row")
        assertInside("layout-language-value","layout-language-select")
        assertTrue("The complete selector moves below the label when the row is too narrow",node("layout-language-select").getUnclippedBoundsInRoot().top>=node("layout-language-title").getUnclippedBoundsInRoot().bottom)
        assertTrue("A genuinely constrained closed value wraps naturally",layout("layout-language-value").lineCount>1)
        node("layout-language-select").performClick()
        assertAllOptions(LanguageChoice.ENGLISH)
        node("layout-language-ENGLISH").performClick()
        assertComplete("layout-language-value","English")
    }

    @Test fun systemLanguageOptionIsCompleteAndPersistsAcrossActualActivityRecreation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=DisplayPreferenceStore(context)
        val previous=store.load()
        try {
            compose.activityRule.scenario.onActivity {activity->
                ViewModelProvider(activity)[HubViewModel::class.java].updateDisplay(previous.copy(language=LanguageChoice.CHINESE,appearance=previous.appearance.copy(reduceMotion="on")))
            }
            compose.onNodeWithTag("hub-settings-toggle").performClick()
            openSettingsSection(compose,"general")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-language-select"))
            node("settings-language-select").performClick()
            listOf(LanguageChoice.CHINESE.name to "中文",LanguageChoice.ENGLISH.name to "English",LanguageChoice.SYSTEM.name to "跟随系统").forEach {(id,label)->
                assertComplete("settings-language-$id-label",label)
                assertInside("settings-language-$id","settings-language-menu")
                assertInside("settings-language-$id-label","settings-language-$id")
            }
            node("settings-language-SYSTEM").performClick()
            compose.waitUntil(5000) {store.load().language==LanguageChoice.SYSTEM}
            assertEquals(LanguageChoice.SYSTEM,store.load().language)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("hub-settings-toggle").assertIsDisplayed()
            openSettingsSection(compose,"general")
            compose.onNodeWithTag("hub-content").performScrollToNode(hasTestTag("settings-language-select"))
            val followsChinese=context.resources.configuration.locales[0].language=="zh"
            assertComplete("settings-language-value",if(followsChinese) "跟随系统" else "System default")
            node("settings-language-select").performClick()
            node("settings-language-SYSTEM").assertIsSelected()
            assertComplete("settings-language-SYSTEM-label",if(followsChinese) "跟随系统" else "System default")
            node("settings-language-SYSTEM").performClick()
            assertEquals(LanguageChoice.SYSTEM,DisplayPreferenceStore(context).load().language)
        } finally {store.save(previous)}
    }
}
