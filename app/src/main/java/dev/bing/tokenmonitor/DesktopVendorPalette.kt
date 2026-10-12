package dev.bing.tokenmonitor

/** Pure fixed desktop vendor palette. Unknown models keep their own stable color family. */
internal object DesktopVendorPalette {
    val ids=desktopVendorColors.keys.filter {it!="default"}+"default"
    fun clean(values:Map<String,String>)=values.mapNotNull {(id,value) -> if(id in desktopVendorColors) DesktopAppearance.hex(value)?.let {id to it} else null}.toMap()
    fun resolve(overrides:Map<String,String>)=desktopVendorColors+clean(overrides).mapValues {0xFF000000L or it.value.drop(1).toLong(16)}
}
