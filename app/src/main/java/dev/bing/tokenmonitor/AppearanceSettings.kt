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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
        ThemeCodeControl(palette,::commit)
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

private fun fontChoices(display:Boolean)=buildList {if(display) add("follow" to R.string.appearance_font_follow);add("app" to R.string.appearance_font_app);add("bundled-ui" to R.string.appearance_font_bundled_ui);add("bundled-data" to R.string.appearance_font_bundled_data);add("system" to R.string.appearance_font_system);add("mono" to R.string.appearance_font_mono);add("custom" to R.string.appearance_custom)}

@Composable private fun FontControl(value:String,display:Boolean,titleWidth:androidx.compose.ui.unit.Dp,controlWidth:androidx.compose.ui.unit.Dp,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current;val role=if(display) "display" else "interface"
    val current=DesktopAppearance.fontPreset(value,display)
    var custom by rememberSaveable(current) {mutableStateOf(current=="custom")}
    var draft by rememberSaveable(value) {mutableStateOf(if(current=="custom") value else "")}
    DesktopSettingSelect(strings.getString(if(display) R.string.appearance_display_font else R.string.appearance_interface_font),fontChoices(display).map {(id,label)->id to strings.getString(label)},if(custom) "custom" else current,"appearance-$role",controlWidth=controlWidth,titleColumnWidth=titleWidth,fillControlWidth=true,trailing={
        DesktopEditAction("↺",strings.getString(if(display) R.string.compact_reset_display_font else R.string.compact_reset_interface_font),"appearance-$role-reset",visualSize=18,glyphSize=10) {custom=false;draft="";change(DisplayChange.Font(display,if(display) DesktopAppearance.defaultDisplayFont else ""))}
    }) {id->
        custom=id=="custom";if(!custom) change(DisplayChange.Font(display,DesktopAppearance.fontFor(id,display=display)))
    }
    if(custom) {
        val normalized=DesktopAppearance.font(draft)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            DesktopEditField(draft,{draft=it.take(160)},strings.getString(R.string.appearance_font_family),"appearance-$role-custom-field",Modifier.weight(1f),done={if(normalized.isNotEmpty()) change(DisplayChange.Font(display,normalized))})
            DesktopEditAction("✓",strings.getString(R.string.appearance_apply),"appearance-$role-apply",normalized.isNotEmpty()) {change(DisplayChange.Font(display,normalized))}
        }
        Row(Modifier.fillMaxWidth().border(1.dp,desktopEditLine(),RoundedCornerShape(6.dp)).background(LocalDesktopOverlayColor.current.copy(alpha=DesktopEditTokens.control),RoundedCornerShape(6.dp)).padding(horizontal=8.dp,vertical=4.dp).testTag("appearance-$role-preview"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(strings.getString(R.string.appearance_preview),style=quotaTextStyle(9),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(strings.getString(R.string.appearance_font_sample),style=quotaTextStyle(14).copy(fontFamily=nativeFont(normalized)))
        }
    }
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

@Composable private fun ThemeCodeControl(palette:Map<String,String>,change:(DisplayChange)->Unit) {
    val strings=LocalResources.current;val clipboard=LocalClipboard.current;val scope=rememberCoroutineScope()
    val code=DesktopAppearance.encode(palette)
    var draft by remember {mutableStateOf(code)}
    var focused by remember {mutableStateOf(false)}
    var generation by remember {mutableIntStateOf(0)}
    var status by remember {mutableIntStateOf(0)}
    var success by remember {mutableStateOf(false)}
    fun feedback(id:Int,ok:Boolean) {status=id;success=ok}
    fun applyEntered(text:String) {
        val parsed=DesktopAppearance.decode(text)
        if(parsed.colors==null) {feedback(if(parsed.reason=="unsupportedVersion") R.string.appearance_code_version else R.string.appearance_code_invalid,false);return}
        draft=DesktopAppearance.encode(parsed.colors)
        change(DisplayChange.ThemeCode(text));feedback(R.string.appearance_code_applied,true)
    }
    LaunchedEffect(code,focused) {if(!focused && draft!=code) {draft=code;generation++;status=0}}
    DesktopSettingNoteGroup(strings.getString(R.string.appearance_code_note),"appearance-theme-code") {
    Text(strings.getString(R.string.appearance_theme_code),style=quotaTextStyle(11))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        DesktopEditField(draft,{draft=it.uppercase(java.util.Locale.ROOT).take(64);generation++;status=0},strings.getString(R.string.appearance_theme_code),"appearance-theme-code-field",Modifier.weight(1f),textStyle=quotaTextStyle(10).copy(fontFamily=FontFamily.Monospace,letterSpacing=.1.sp),onFocus={focused=it},done={generation++;applyEntered(draft)})
        DesktopEditAction("⧉",strings.getString(R.string.appearance_copy_code),"appearance-code-copy") {
            generation++;val request=generation;draft=code;status=0
            scope.launch {val copied=runCatching {clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText(strings.getString(R.string.appearance_theme_code),code)))}.isSuccess
                if(generation==request && draft==code) feedback(if(copied) R.string.appearance_code_copied else R.string.appearance_code_clipboard_failed,copied)}
        }
        DesktopEditAction("⎘",strings.getString(R.string.appearance_code_paste_apply),"appearance-code-paste") {
            generation++;val request=generation;val before=draft;status=0
            scope.launch {
                val read=runCatching {clipboard.getClipEntry()?.clipData?.let {if(it.itemCount>0) it.getItemAt(0).text?.toString() else null}}
                if(generation!=request || draft!=before) return@launch
                if(read.isFailure) {feedback(R.string.appearance_code_clipboard_failed,false);return@launch}
                val pasted=read.getOrNull()?.trim().orEmpty()
                draft=pasted.take(64);applyEntered(pasted.take(64))
            }
        }
    }
    if(status!=0) Text(strings.getString(status),Modifier.testTag("appearance-code-status"),style=quotaTextStyle(10),color=if(success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
    }
}
