package dev.bing.tokenmonitor

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

enum class ThemeChoice { SYSTEM, LIGHT, DARK }
val homeModuleIds = listOf("limits", "tools", "models", "devices", "activity", "trends", "sessions")
// The original Home has six modules; Activity contains both the heatmap and trend.
// Keep the ordinal catalog unchanged so saved V3–V11 selections retain their meaning.
val defaultHomeModules = listOf("limits", "tools", "models", "sessions", "devices", "activity")
data class DisplayPreferences(val theme: ThemeChoice = ThemeChoice.SYSTEM, val hideTitles: Boolean = false, val language: LanguageChoice = LanguageChoice.CHINESE,
    val homeModules: List<String> = HomeModulePreferences.freshVisible, val hiddenTools: Set<String> = emptySet(),
    val pinnedTools: List<String> = emptyList(), val toolOrder: List<String> = emptyList(), val monthMode: String = "month", val lastPeriod: String = "today",
    val heatmapMetric: String = "cost", val homeActiveDaysWindow: String = "all", val currency: String = "USD", val currencyRates: Map<String, String> = emptyMap(),
    val appearance: AppearancePreferences = AppearancePreferences(), val limits: LimitDisplayPreferences = LimitDisplayPreferences(),val main:MainDisplayPreferences = MainDisplayPreferences(),val modelRanking:String="tokens",val homeModuleOrder:List<String> = emptyList(), val sessionTitlesEnabled:Boolean=true) {
    val hideSessionTitles:Boolean get() = hideTitles || !sessionTitlesEnabled
}

sealed interface DisplayChange {
    data class ReduceMotion(val mode:String):DisplayChange
    data class TokenNumberAnimation(val mode:String):DisplayChange
    data class ModelRanking(val metric:String):DisplayChange
    data class MainViewVisible(val id:String,val visible:Boolean):DisplayChange
    data class MainViewMove(val id:String,val direction:Int):DisplayChange
    data object ResetMainViewOrder:DisplayChange
    data object ShowAllMainViews:DisplayChange
    data class SessionTitles(val enabled:Boolean):DisplayChange
    data class SessionContextMetric(val metric:String):DisplayChange
    data class TitleIconOnly(val enabled:Boolean):DisplayChange
    data class CompactTotal(val enabled:Boolean):DisplayChange
    data class CompactUnits(val units:String):DisplayChange
    data class ShowLiveRate(val enabled:Boolean):DisplayChange
    data class LiveRateMode(val mode:String):DisplayChange
    data class LiveRateDevice(val device:String):DisplayChange
    data class SwapMainActions(val enabled:Boolean):DisplayChange
    data class Theme(val choice: ThemeChoice) : DisplayChange
    data class ThemePreset(val id: String) : DisplayChange
    data class ThemeColor(val key: String, val value: String?) : DisplayChange
    data class ThemeCode(val value: String) : DisplayChange
    data class VendorColor(val id: String, val value: String?) : DisplayChange
    data object ResetVendorColors : DisplayChange
    data class ToolIcons(val visible: Boolean) : DisplayChange
    data class LiveDot(val visible: Boolean) : DisplayChange
    data class LimitUsed(val used: Boolean) : DisplayChange
    data class MaskLimitEmails(val masked: Boolean) : DisplayChange
    data class LimitSource(val visible: Boolean) : DisplayChange
    data class LimitProviderMove(val id:String,val direction:Int,val home:Boolean=false):DisplayChange
    data class LimitItemVisibility(val provider:String,val item:String,val visible:Boolean):DisplayChange
    data class RestoreLimitItems(val provider:String):DisplayChange
    data class RestoreLimitOrder(val home:Boolean=false):DisplayChange
    data class HomeLimitProviderVisibility(val id:String):DisplayChange
    data object ShowAllHomeLimitProviders:DisplayChange
    data class HomeLimitCount(val count:Int):DisplayChange
    data class HomeLimitBars(val bars:Boolean):DisplayChange
    data class HighlightLowHomeLimits(val enabled:Boolean):DisplayChange
    data class HomeLimitNames(val visible:Boolean):DisplayChange
    data class TextSize(val size: String) : DisplayChange
    data class Font(val display: Boolean, val value: String) : DisplayChange
    data class Language(val choice: LanguageChoice) : DisplayChange
    data class HideTitles(val hidden: Boolean) : DisplayChange
    data class HomeModules(val modules: List<String>) : DisplayChange
    data class HomeModuleVisibility(val id:String):DisplayChange
    data class HomeModuleMove(val id:String,val direction:Int):DisplayChange
    data object ResetHomeModuleOrder:DisplayChange
    data object ShowAllHomeModules:DisplayChange
    data class MonthMode(val mode: String) : DisplayChange
    data class PeriodSelection(val selection: String) : DisplayChange
    data class HeatmapMetric(val metric: String) : DisplayChange
    data class HomeActiveDaysWindow(val window: String) : DisplayChange
    data class Currency(val code: String) : DisplayChange
    data class CurrencyRate(val code: String, val value: String?) : DisplayChange
    data class ToolVisibility(val id: String) : DisplayChange
    data class ToolPin(val id: String) : DisplayChange
    data class ToolMove(val id: String, val direction: Int) : DisplayChange
    data object ToolUsageOrder : DisplayChange
    data object ShowAllTools : DisplayChange
    fun apply(current: DisplayPreferences): DisplayPreferences = when (this) {
        is MainViewVisible -> {val hidden=MainDisplay.hidden(current.main.hiddenViews);if(id !in MainDisplay.ids || (!visible && id !in hidden && MainDisplay.visible(current.main).size<=1)) current else current.copy(main=current.main.copy(hiddenViews=MainDisplay.hidden(if(visible) hidden-id else hidden+id)))}
        is MainViewMove -> current.copy(main=MainDisplay.move(current.main,id,direction))
        ResetMainViewOrder -> current.copy(main=current.main.copy(viewOrder=emptyList()))
        ShowAllMainViews -> current.copy(main=current.main.copy(hiddenViews=emptySet()))
        is SessionTitles -> current.copy(sessionTitlesEnabled=enabled)
        is SessionContextMetric -> if(metric !in listOf("used","remaining")) current else current.copy(main=current.main.copy(sessionContextMetric=metric))
        is TitleIconOnly -> current.copy(main=current.main.copy(titleIconOnly=enabled))
        is CompactTotal -> current.copy(main=current.main.copy(compactTotal=enabled))
        is CompactUnits -> if(units !in listOf("western","localized")) current else current.copy(main=current.main.copy(compactUnits=units))
        is ShowLiveRate -> current.copy(main=current.main.copy(showLiveRate=enabled))
        is LiveRateMode -> if(mode !in listOf("speed","burn")) current else current.copy(main=current.main.copy(rateMode=mode))
        is LiveRateDevice -> if(device.length>256 || device.any(Char::isISOControl)) current else current.copy(main=current.main.copy(rateDevice=device))
        is SwapMainActions -> current.copy(main=current.main.copy(swapActions=enabled))
        is TokenNumberAnimation -> current.copy(appearance=current.appearance.copy(tokenNumberAnimation=TokenNumberAnimationMode.normalize(mode)))
        is ReduceMotion -> current.copy(appearance=current.appearance.copy(reduceMotion=DesktopMotionPreference.normalize(mode)))
        is ModelRanking -> current.copy(modelRanking=if(metric=="cost") "cost" else "tokens")
        is Theme -> current.copy(theme = choice, appearance = current.appearance.copy(colors = emptyMap()))
        is ThemePreset -> DesktopAppearance.presetOverrides(id)?.let { colors -> current.copy(theme = if(DesktopAppearance.light(DesktopAppearance.presets.getValue(id).getValue("bg"))) ThemeChoice.LIGHT else ThemeChoice.DARK, appearance = current.appearance.copy(colors=colors)) } ?: current
        is ThemeColor -> if(key !in DesktopAppearance.colorKeys || (value!=null && DesktopAppearance.hex(value)==null)) current
            else current.copy(appearance=current.appearance.copy(colors=if(value==null) current.appearance.colors-key else current.appearance.colors+(key to DesktopAppearance.hex(value)!!)))
        is ThemeCode -> DesktopAppearance.decode(value).colors?.let { colors -> current.copy(theme=if(DesktopAppearance.light(colors.getValue("bg"))) ThemeChoice.LIGHT else ThemeChoice.DARK,appearance=current.appearance.copy(colors=colors)) } ?: current
        is VendorColor -> if(id !in desktopVendorColors || (value!=null && DesktopAppearance.hex(value)==null)) current
            else current.copy(appearance=current.appearance.copy(vendorColors=if(value==null) current.appearance.vendorColors-id else current.appearance.vendorColors+(id to DesktopAppearance.hex(value)!!)))
        ResetVendorColors -> current.copy(appearance=current.appearance.copy(vendorColors=emptyMap()))
        is ToolIcons -> current.copy(appearance=current.appearance.copy(showToolIcons=visible))
        is LiveDot -> current.copy(appearance=current.appearance.copy(showLiveDot=visible))
        is LimitUsed -> current.copy(limits=current.limits.copy(showUsed=used))
        is MaskLimitEmails -> current.copy(limits=current.limits.copy(maskEmails=masked))
        is LimitSource -> current.copy(limits=current.limits.copy(showSource=visible))
        is LimitProviderMove -> current.copy(limits=if(home) current.limits.copy(homeProviderOrder=LimitPreferences.move(current.limits.homeProviderOrder.ifEmpty {current.limits.providerOrder},id,direction)) else current.limits.copy(providerOrder=LimitPreferences.move(current.limits.providerOrder,id,direction)))
        is LimitItemVisibility -> current.copy(limits=current.limits.copy(hiddenItems=LimitPreferences.setHidden(current.limits.hiddenItems,provider,item,!visible)))
        is RestoreLimitItems -> current.copy(limits=current.limits.copy(hiddenItems=current.limits.hiddenItems-provider))
        is RestoreLimitOrder -> current.copy(limits=if(home) current.limits.copy(homeProviderOrder=emptyList()) else current.limits.copy(providerOrder=emptyList()))
        is HomeLimitProviderVisibility -> if(id !in LimitPreferences.providers) current else current.copy(limits=current.limits.copy(hiddenHomeProviders=if(id in current.limits.hiddenHomeProviders) current.limits.hiddenHomeProviders-id else current.limits.hiddenHomeProviders+id))
        ShowAllHomeLimitProviders -> current.copy(limits=current.limits.copy(hiddenHomeProviders=emptySet()))
        is HomeLimitCount -> if(count !in 1..12) current else current.copy(limits=current.limits.copy(homeAccountCount=count))
        is HomeLimitBars -> current.copy(limits=current.limits.copy(homeBars=bars))
        is HighlightLowHomeLimits -> current.copy(limits=current.limits.copy(highlightLowHomeLimits=enabled))
        is HomeLimitNames -> current.copy(limits=current.limits.copy(showHomeProviderNames=visible))
        is TextSize -> if(size in DesktopAppearance.sizes) current.copy(appearance=current.appearance.copy(textSize=size)) else current
        is Font -> if(value.isNotBlank() && DesktopAppearance.font(value).isEmpty()) current else current.copy(appearance=if(display) current.appearance.copy(displayFont=DesktopAppearance.font(value)) else current.appearance.copy(interfaceFont=DesktopAppearance.font(value)))
        is Language -> current.copy(language = choice)
        is HideTitles -> current.copy(hideTitles = hidden)
        is HomeModules -> HomeModulePreferences.replace(current,modules)
        is HomeModuleVisibility -> HomeModulePreferences.toggle(current,id)
        is HomeModuleMove -> HomeModulePreferences.move(current,id,direction)
        ResetHomeModuleOrder -> HomeModulePreferences.reset(current)
        ShowAllHomeModules -> HomeModulePreferences.showAll(current)
        is MonthMode -> current.copy(monthMode = mode.takeIf { it in FixedPeriods.modes } ?: "month")
        is PeriodSelection -> current.copy(lastPeriod = selection.takeIf { it in FixedPeriods.modes || it in listOf("today", "allTime") } ?: "today", monthMode = if (selection in FixedPeriods.modes) selection else current.monthMode)
        is HeatmapMetric -> current.copy(heatmapMetric = metric.takeIf { it in listOf("cost", "tokens") } ?: "cost")
        is HomeActiveDaysWindow -> current.copy(homeActiveDaysWindow = window.takeIf { it in listOf("all", "year") } ?: "all")
        is Currency -> current.copy(currency = UsageCurrency.normalize(code))
        is CurrencyRate -> if (code !in UsageCurrency.codes || code == "USD" || (value != null && !UsageCurrency.validRate(value.trim()))) current
            else current.copy(currencyRates = if (value == null) current.currencyRates - code else current.currencyRates + (code to value.trim()))
        is ToolVisibility -> ToolDisplay.toggleHidden(current, id)
        is ToolPin -> ToolDisplay.togglePin(current, id)
        is ToolMove -> ToolDisplay.move(current, id, direction)
        ToolUsageOrder -> current.copy(toolOrder = emptyList(), pinnedTools = emptyList())
        ShowAllTools -> current.copy(hiddenTools = emptySet())
    }
}

/** Only presentation choices, never connection data or credentials. Excluded from backups. */
class DisplayPreferenceStore(context: Context, name: String = "display-preferences.bin") {
    // AtomicFile has no cross-instance lock; widget reads and app writes share this process.
    companion object { private val diskLock = Any(); internal const val maxBytes=3*1024*1024 }
    private val file = AtomicFile(File(context.noBackupFilesDir, name))
    // Independent optional choice: keep the V16 record readable by previous releases.
    private val numberAnimationFile = AtomicFile(File(context.noBackupFilesDir, name+".number-animation"))
    private fun numberAnimation():String = runCatching {
        require(numberAnimationFile.baseFile.length()==2L)
        val bytes=numberAnimationFile.readFully()
        if(bytes.contentEquals(byteArrayOf(1,1))) "count" else "roll"
    }.getOrDefault("roll")
    private fun withNumberAnimation(value:DisplayPreferences)=value.copy(appearance=value.appearance.copy(tokenNumberAnimation=numberAnimation()))
    fun load(): DisplayPreferences = synchronized(diskLock) {
        if (!file.baseFile.exists()) return withNumberAnimation(DisplayPreferences())
        require(file.baseFile.length() <= maxBytes)
        val bytes = file.readFully()
        if(bytes.firstOrNull() !in setOf(11.toByte(),12.toByte(),13.toByte(),14.toByte(),15.toByte(),16.toByte())) require(bytes.size<=4096)
        if (bytes.firstOrNull() in setOf(4.toByte(), 5.toByte(), 6.toByte(), 7.toByte(), 8.toByte(), 9.toByte(), 10.toByte(),11.toByte(),12.toByte(),13.toByte(),14.toByte(),15.toByte(),16.toByte())) return withNumberAnimation(decodeModern(bytes))
        require((bytes.size == 3 && bytes[0] == 1.toByte()) || (bytes.size == 4 && bytes[0] == 2.toByte()) || (bytes.size in 6..11 && bytes[0] == 3.toByte() && bytes.size == bytes[4].toInt() + 5))
        require(bytes[2] in 0..1)
        val language = if (bytes[0] == 1.toByte()) LanguageChoice.CHINESE else LanguageChoice.entries.getOrNull(bytes[3].toInt()) ?: error("Invalid language")
        val modules = if (bytes[0] == 3.toByte()) bytes.drop(5).map { homeModuleIds.getOrNull(it.toInt()) ?: error("Invalid home module") }.also { require(it.distinct().size == it.size) } else defaultHomeModules
        return withNumberAnimation(DisplayPreferences(ThemeChoice.entries.getOrNull(bytes[1].toInt()) ?: error("Invalid preference"), bytes[2] == 1.toByte(), language, modules))
    }
    private fun decodeModern(bytes: ByteArray): DisplayPreferences = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val version = input.readUnsignedByte().also { require(it in 4..16) }
        val theme = ThemeChoice.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid theme")
        val hide = input.readUnsignedByte().also { require(it in 0..1) } == 1
        val language = LanguageChoice.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid language")
        val count = input.readUnsignedByte().also { require(it in 1..homeModuleIds.size) }
        val modules = List(count) { homeModuleIds.getOrNull(input.readUnsignedByte()) ?: error("Invalid home module") }
        require(modules.distinct().size == modules.size)
        fun tools(): List<String> {
            val size = input.readUnsignedByte().also { require(it <= desktopManagedToolIds.size) }
            val values = List(size) { input.readUTF() }
            require(values.all { it in desktopManagedToolIds } && values.distinct().size == values.size)
            return values
        }
        val hidden = tools().toSet(); val pins = tools(); val order = tools()
        val monthMode = if (version >= 5) input.readUTF().also { require(it in FixedPeriods.modes) } else "month"
        val lastPeriod = if (version >= 5) input.readUTF().also { require(it in FixedPeriods.modes || it in listOf("today", "allTime")) } else "today"
        val heatmapMetric = if (version >= 6) input.readUTF().also { require(it in listOf("cost", "tokens")) } else "cost"
        val activeDaysWindow = if (version >= 6) input.readUTF().also { require(it in listOf("all", "year")) } else "all"
        val currency = if (version >= 7) input.readUTF().also { require(it in UsageCurrency.codes) } else "USD"
        val rates = if (version >= 7) buildMap {
            val size = input.readUnsignedByte().also { require(it <= 3) }
            repeat(size) {
                val code = input.readUTF(); val value = input.readUTF()
                require(code in UsageCurrency.codes && code != "USD" && !containsKey(code) && UsageCurrency.validRate(value))
                put(code, value)
            }
        } else emptyMap()
        val appearance = if(version>=8) {
            val size=input.readUTF();val interfaceFont=input.readUTF();val displayFont=input.readUTF()
            val colors=buildMap {
                val count=input.readUnsignedByte().also { require(it<=DesktopAppearance.colorKeys.size) }
                repeat(count) { val key=input.readUTF();val value=input.readUTF();require(key in DesktopAppearance.colorKeys && !containsKey(key));put(key,value) }
            }
            val vendors=if(version>=9) buildMap<String,String> {
                val count=input.readUnsignedByte().also {require(it<=desktopVendorColors.size)}
                repeat(count) {val key=input.readUTF();val value=input.readUTF();require(key in desktopVendorColors && !containsKey(key));put(key,value)}
            } else emptyMap()
            fun flag()=input.readUnsignedByte().also {require(it in 0..1)}==1
            AppearancePreferences(colors,size,interfaceFont,displayFont,vendors,if(version>=9) flag() else true,if(version>=9) flag() else true).also { require(DesktopAppearance.valid(it)) }
        } else AppearancePreferences()
        fun limitFlag()=input.readUnsignedByte().also {require(it in 0..1)}==1
        val oldLimits=if(version>=10) LimitDisplayPreferences(limitFlag(),limitFlag(),limitFlag()) else LimitDisplayPreferences()
        fun providers():List<String> {
            val size=input.readUnsignedByte().also {require(it<=LimitPreferences.providers.size)}
            return List(size) {input.readUTF()}.also {require(LimitPreferences.selection(it)==it)}
        }
        val limits=if(version>=11) {
            val order=providers();val hiddenHome=providers().toSet();val homeOrder=providers()
            val hidden=buildMap<String,List<String>> {
                val size=input.readUnsignedByte().also {require(it<=LimitPreferences.providers.size)}
                repeat(size) {
                    val id=input.readUTF();require(id in LimitPreferences.providers && !containsKey(id))
                    val count=input.readUnsignedByte().also {require(it in 1..64)}
                    put(id,List(count) {input.readUTF()})
                }
            }.also {require(LimitPreferences.hidden(it)==it)}
            oldLimits.copy(providerOrder=order,hiddenHomeProviders=hiddenHome,homeProviderOrder=homeOrder,hiddenItems=hidden,
                homeAccountCount=input.readUnsignedByte().also {require(it in 1..12)},homeBars=limitFlag(),showHomeProviderNames=limitFlag())
        } else oldLimits
        val main=if(version>=12) {
            fun views():List<String> {val size=input.readUnsignedByte().also {require(it<=MainDisplay.storedIds.size)};return List(size) {input.readUTF()}.also {require(it.distinct().size==it.size && it.all {id->id in MainDisplay.storedIds})}}
            MainDisplayPreferences(views(),views().toSet(),limitFlag(),limitFlag(),input.readUTF(),limitFlag(),input.readUTF(),input.readUTF(),limitFlag(),input.readUTF()).also {require(MainDisplay.valid(it))}
        } else MainDisplayPreferences()
        val motion=if(version>=13) input.readUTF().also {require(it in listOf("system","on","off"))} else "system"
        val ranking=if(version>=13) input.readUTF().also {require(it in listOf("tokens","cost"))} else "tokens"
        val homeOrder=if(version>=14) {
            val size=input.readUnsignedByte().also {require(it<=homeModuleIds.size)}
            List(size) {homeModuleIds.getOrNull(input.readUnsignedByte()) ?: error("Invalid home order")}.also {require(it.distinct().size==it.size)}
        } else emptyList()
        val sessionTitles=if(version>=15) limitFlag() else true
        val highlight=if(version>=16) limitFlag() else false
        require(input.available() == 0)
        DisplayPreferences(theme, hide, language, modules, hidden, pins, order, monthMode, lastPeriod, heatmapMetric, activeDaysWindow, currency, rates, appearance.copy(reduceMotion=motion), limits.copy(highlightLowHomeLimits=highlight),main,ranking,homeOrder,sessionTitles).also {require(HomeModulePreferences.valid(it))}
    }
    fun save(preferences: DisplayPreferences) = synchronized(diskLock) {
        require(preferences.homeModules.isNotEmpty() && preferences.homeModules.size <= homeModuleIds.size && preferences.homeModules.distinct().size == preferences.homeModules.size && preferences.homeModules.all { it in homeModuleIds })
        require(HomeModulePreferences.valid(preferences))
        require(preferences.monthMode in FixedPeriods.modes)
        require(preferences.lastPeriod in FixedPeriods.modes || preferences.lastPeriod in listOf("today", "allTime"))
        require(preferences.heatmapMetric in listOf("cost", "tokens"))
        require(preferences.homeActiveDaysWindow in listOf("all", "year"))
        require(preferences.currency in UsageCurrency.codes)
        require(preferences.currencyRates.size <= 3 && preferences.currencyRates.all { (code, value) -> code in UsageCurrency.codes && code != "USD" && UsageCurrency.validRate(value) })
        require(DesktopAppearance.valid(preferences.appearance))
        require(preferences.modelRanking in listOf("tokens","cost"))
        require(MainDisplay.valid(preferences.main))
        val limits=preferences.limits
        listOf(limits.providerOrder,limits.homeProviderOrder,limits.hiddenHomeProviders.toList()).forEach {require(LimitPreferences.selection(it)==it)}
        require(LimitPreferences.hidden(limits.hiddenItems)==limits.hiddenItems && limits.homeAccountCount in 1..12)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { data ->
            data.writeByte(16); data.writeByte(preferences.theme.ordinal); data.writeBoolean(preferences.hideTitles); data.writeByte(preferences.language.ordinal)
            data.writeByte(preferences.homeModules.size)
            preferences.homeModules.forEach { data.writeByte(homeModuleIds.indexOf(it)) }
            listOf(preferences.hiddenTools.toList(), preferences.pinnedTools, preferences.toolOrder).forEach { values ->
                require(values.size <= desktopManagedToolIds.size && values.distinct().size == values.size && values.all { it in desktopManagedToolIds })
                data.writeByte(values.size); values.forEach(data::writeUTF)
            }
            data.writeUTF(preferences.monthMode)
            data.writeUTF(preferences.lastPeriod)
            data.writeUTF(preferences.heatmapMetric)
            data.writeUTF(preferences.homeActiveDaysWindow)
            data.writeUTF(preferences.currency)
            data.writeByte(preferences.currencyRates.size)
            preferences.currencyRates.toSortedMap().forEach { (code, rate) -> data.writeUTF(code); data.writeUTF(rate) }
            data.writeUTF(preferences.appearance.textSize);data.writeUTF(preferences.appearance.interfaceFont);data.writeUTF(preferences.appearance.displayFont)
            data.writeByte(preferences.appearance.colors.size)
            preferences.appearance.colors.toSortedMap().forEach { (key,value) -> data.writeUTF(key);data.writeUTF(value) }
            data.writeByte(preferences.appearance.vendorColors.size)
            preferences.appearance.vendorColors.toSortedMap().forEach {(key,value) -> data.writeUTF(key);data.writeUTF(value)}
            data.writeBoolean(preferences.appearance.showToolIcons);data.writeBoolean(preferences.appearance.showLiveDot)
            data.writeBoolean(preferences.limits.showUsed);data.writeBoolean(preferences.limits.maskEmails);data.writeBoolean(preferences.limits.showSource)
            listOf(limits.providerOrder,limits.hiddenHomeProviders.sorted(),limits.homeProviderOrder).forEach {values->data.writeByte(values.size);values.forEach(data::writeUTF)}
            data.writeByte(limits.hiddenItems.size)
            limits.hiddenItems.toSortedMap().forEach {(provider,items)->data.writeUTF(provider);data.writeByte(items.size);items.forEach(data::writeUTF)}
            data.writeByte(limits.homeAccountCount);data.writeBoolean(limits.homeBars);data.writeBoolean(limits.showHomeProviderNames)
            val main=preferences.main
            listOf(main.viewOrder,main.hiddenViews.sorted()).forEach {values->data.writeByte(values.size);values.forEach(data::writeUTF)}
            data.writeBoolean(main.titleIconOnly);data.writeBoolean(main.compactTotal);data.writeUTF(main.compactUnits);data.writeBoolean(main.showLiveRate)
            data.writeUTF(main.rateMode);data.writeUTF(main.rateDevice);data.writeBoolean(main.swapActions);data.writeUTF(main.sessionContextMetric)
            data.writeUTF(preferences.appearance.reduceMotion);data.writeUTF(preferences.modelRanking)
            data.writeByte(preferences.homeModuleOrder.size);preferences.homeModuleOrder.forEach {data.writeByte(homeModuleIds.indexOf(it))}
            data.writeBoolean(preferences.sessionTitlesEnabled)
            data.writeBoolean(preferences.limits.highlightLowHomeLimits)
        }
        val bytes = buffer.toByteArray().also { require(it.size <= maxBytes) }
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) { file.failWrite(output); throw error }
        if(TokenNumberAnimationMode.normalize(preferences.appearance.tokenNumberAnimation)=="roll") numberAnimationFile.delete()
        else {
            val modeOutput=numberAnimationFile.startWrite()
            try {modeOutput.write(byteArrayOf(1,1));numberAnimationFile.finishWrite(modeOutput)}
            catch(error:Exception) {numberAnimationFile.failWrite(modeOutput);throw error}
        }
    }
    fun clear() = synchronized(diskLock) { file.delete();numberAnimationFile.delete() }
}
