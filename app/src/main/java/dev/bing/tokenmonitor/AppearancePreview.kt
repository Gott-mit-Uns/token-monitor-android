package dev.bing.tokenmonitor

import androidx.compose.runtime.*

internal data class AppearanceColorPreview(val vendor:Boolean,val key:String,val color:String)

/** A display-only overlay on saved appearance. No store, ViewModel or network references. */
@Stable internal class AppearancePreviewState {
    var value by mutableStateOf<AppearanceColorPreview?>(null)
        private set
    private var owner:Any?=null
    fun show(tag:String,color:String,session:Any) {
        val vendor=tag.startsWith("vendor-color-")
        val key=when {vendor->tag.removePrefix("vendor-color-");tag.startsWith("appearance-color-")->tag.removePrefix("appearance-color-");else->return}
        if(key !in if(vendor) DesktopVendorPalette.ids else DesktopAppearance.colorKeys) return
        val normalized=DesktopAppearance.hex(color) ?: return
        owner=session;value=AppearanceColorPreview(vendor,key,normalized)
    }
    fun clear(session:Any?=null) {if(session==null || owner===session) {owner=null;value=null}}
    fun rendered(saved:AppearancePreferences):AppearancePreferences {
        val next=value ?: return saved
        return if(next.vendor) saved.copy(vendorColors=saved.vendorColors+(next.key to next.color))
            else saved.copy(colors=saved.colors+(next.key to next.color))
    }
    fun swatch(tag:String,saved:String):String {
        val next=value ?: return saved
        return if(tag==(if(next.vendor) "vendor-color-" else "appearance-color-")+next.key) next.color else saved
    }
}

internal data class AppearancePreviewBase(val dark:Boolean,val language:LanguageChoice,val appearance:AppearancePreferences)
internal val LocalAppearancePreview=staticCompositionLocalOf<AppearancePreviewState?> {null}
internal val LocalAppearancePreviewBase=staticCompositionLocalOf<AppearancePreviewBase?> {null}
internal val LocalDesktopBaseLanguage=staticCompositionLocalOf<LanguageChoice?> {null}
internal val LocalDesktopBaseDark=staticCompositionLocalOf<Boolean?> {null}

/** Native colour chooser controls retain their baseline contrast during page previews. */
@Composable internal fun AppearancePickerTheme(content:@Composable ()->Unit) {
    val base=LocalAppearancePreviewBase.current
    if(base==null) content() else TokenTheme(base.dark,base.language,base.appearance,content)
}
