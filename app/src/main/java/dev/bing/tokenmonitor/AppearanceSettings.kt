package dev.bing.tokenmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun AppearanceSettings(preferences:DisplayPreferences,change:(DisplayChange)->Unit,embedded:Boolean=false) {
    val strings=LocalResources.current
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    fun commit(action:DisplayChange) {focus.clearFocus();keyboard?.hide();change(action)}
    var expanded by rememberSaveable {mutableStateOf(false)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    val palette=renderedDesktopPalette(preferences.appearance.colors)
    val preset=DesktopAppearance.presets.entries.firstOrNull {it.value==palette}?.key
    val labels=mapOf("default" to R.string.appearance_graphite,"obsidian" to R.string.appearance_carbon,"porcelain" to R.string.appearance_paper)
    if(!embedded) DesktopTextButton({expanded=!expanded},Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("appearance-toggle")) {
        Text(strings.getString(R.string.appearance_title),Modifier.weight(1f),style=quotaTextStyle(11))
        Text(strings.getString(labels[preset] ?: R.string.appearance_custom),style=quotaTextStyle(10),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(!expanded && !embedded) return
    Column(Modifier.fillMaxWidth().testTag("appearance-controls"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(strings.getString(R.string.appearance_theme_title).uppercase(strings.configuration.locales[0]),Modifier.weight(1f),style=quotaTextStyle(9).copy(letterSpacing=1.35.sp),color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.9f))
            DesktopEditAction("↺",strings.getString(R.string.appearance_reset_colors),"appearance-reset-colors",visualSize=24,glyphSize=16,chrome=false) {commit(DisplayChange.Theme(preferences.theme))}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            labels.forEach {(id,label)->DesktopThemePreset(strings.getString(label),DesktopAppearance.presets.getValue(id).getValue("accent"),preset==id,"appearance-preset-$id") {commit(DisplayChange.ThemePreset(id))}}
        }
        HorizontalDivider(color=desktopEditLine(),thickness=1.dp)
        DesktopTextButton({advanced=!advanced},Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current).testTag("appearance-colors-toggle"),contentPadding=PaddingValues(0.dp)) {
            Text(strings.getString(R.string.appearance_colors),Modifier.weight(1f),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(advanced) "⌃" else "⌄",style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(advanced) {
            AppearanceVisibilityControls(preferences,::commit)
            DesktopSettingSwitch(strings.getString(R.string.main_title_icon),preferences.main.titleIconOnly,"main-title-icon") {commit(DisplayChange.TitleIconOnly(it))}
            DesktopAppearance.colorKeys.forEach {key->ColorControl(key,palette.getValue(key),preferences.appearance.colors.containsKey(key),::commit)}
            HorizontalDivider(color=desktopEditLine(),thickness=1.dp)
            DesktopSettingSelect(strings.getString(R.string.appearance_text_size),listOf("standard" to strings.getString(R.string.appearance_size_standard),"larger" to strings.getString(R.string.appearance_size_larger),"largest" to strings.getString(R.string.appearance_size_largest)),preferences.appearance.textSize,"appearance-size",description=strings.getString(R.string.appearance_size_note)) {commit(DisplayChange.TextSize(it))}
            DesktopSettingNoteGroup(strings.getString(R.string.appearance_font_note),"appearance-fonts") {FontSettingsControls(preferences,::commit)}
        }
        VendorColorSettings(preferences,::commit)
    }
}

/** Shared label/minimum-control columns choose the same breakpoint; controls fill to the reset target. */
@Composable internal fun FontSettingsControls(preferences:DisplayPreferences,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current;val density=LocalDensity.current;val style=quotaTextStyle(11)
    val titles=listOf(R.string.appearance_interface_font,R.string.appearance_display_font).map(strings::getString)
    val titleWidth=with(density) {rememberDesktopLabelWidths(titles,style).max().toDp()}
    val controlWidth=104.dp // Minimum useful dropdown width; long names wrap instead of moving the reset.
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        FontControl(preferences.appearance.interfaceFont,false,titleWidth,controlWidth,change)
        FontControl(preferences.appearance.displayFont,true,titleWidth,controlWidth,change)
    }
}

private fun fontChoices(display:Boolean)=buildList {if(display) add("follow" to R.string.appearance_font_follow);if(display) add("app" to R.string.appearance_font_app);add("bundled-ui" to R.string.appearance_font_bundled_ui);add("bundled-data" to R.string.appearance_font_bundled_data);add("system" to R.string.appearance_font_system);add("mono" to R.string.appearance_font_mono)}

@Composable private fun FontControl(value:String,display:Boolean,titleWidth:androidx.compose.ui.unit.Dp,controlWidth:androidx.compose.ui.unit.Dp,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current;val role=if(display) "display" else "interface"
    val current=DesktopAppearance.fontPreset(value,display)
    // Legacy custom values still render; the dropdown only offers supported presets.
    val selected=if(current=="custom") strings.getString(R.string.appearance_font_saved) else current
    DesktopSettingSelect(strings.getString(if(display) R.string.appearance_display_font else R.string.appearance_interface_font),fontChoices(display).map {(id,label)->id to strings.getString(label)},selected,"appearance-$role",controlWidth=controlWidth,titleColumnWidth=titleWidth,fillControlWidth=true,trailing={
        DesktopEditAction("↺",strings.getString(if(display) R.string.compact_reset_display_font else R.string.compact_reset_interface_font),"appearance-$role-reset",visualSize=18,glyphSize=10) {change(DisplayChange.Font(display,if(display) DesktopAppearance.defaultDisplayFont else ""))}
    }) {id->change(DisplayChange.Font(display,DesktopAppearance.fontFor(id,display=display)))}
}

@Composable private fun ColorControl(key:String,value:String,overridden:Boolean,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current
    val label=strings.getString(mapOf("accent" to R.string.appearance_color_accent,"chart" to R.string.appearance_color_chart,"bg" to R.string.appearance_color_bg,"text" to R.string.appearance_color_text,"muted" to R.string.appearance_color_muted).getValue(key))
    DesktopColorRow(label,value,"appearance-color-$key",overridden,{change(DisplayChange.ThemeColor(key,null))}) {change(DisplayChange.ThemeColor(key,it))}
}

@Composable internal fun DesktopColorRow(label:String,value:String,tag:String,overridden:Boolean,reset:()->Unit,change:(String)->Unit) {
    val strings=LocalResources.current
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    var open by rememberSaveable {mutableStateOf(false)}
    val preview=LocalAppearancePreview.current
    val session=remember {Any()}
    val displayed=preview?.swatch(tag,value) ?: value
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,preview) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) {open=false;preview?.clear(session)}}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);preview?.clear(session)}
    }
    Row(Modifier.fillMaxWidth().heightIn(min=LocalDesktopSettingsRowHeight.current),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.weight(1f),style=quotaTextStyle(11),color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        Box(Modifier.size(48.dp).clickable(role=Role.Button,onClick={focus.clearFocus();keyboard?.hide();open=true}).testTag("$tag-open").semantics {contentDescription=label+" "+value},contentAlignment=Alignment.Center) {
            Box(Modifier.size(30.dp,22.dp).border(1.dp,desktopEditLine(),RoundedCornerShape(6.dp)).padding(2.dp).background(hexColor(displayed),RoundedCornerShape(4.dp)).testTag("$tag-swatch"))
        }
        DesktopEditAction("↺",strings.getString(R.string.appearance_reset)+" "+label,"$tag-reset",overridden,visualSize=18,glyphSize=10,click=reset)
    }
    if(open) DesktopColorPicker(label,value,tag,{preview?.clear(session);open=false},preview={preview?.show(tag,it,session)}) {preview?.clear(session);open=false;change(it)}
}
