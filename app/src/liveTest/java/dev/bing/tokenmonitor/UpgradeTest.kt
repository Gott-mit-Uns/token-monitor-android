package dev.bing.tokenmonitor

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Two separate processes around an actual APK replacement; synthetic profile only. */
class UpgradeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun version() = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    @Test fun seedProfileUsingInstalled017Storage() = seedProfile("0.1.7-dev")
    @Test fun seedProfileUsingInstalled020Storage() = seedProfile("0.2.0-dev")
    @Test fun seedProfileUsingInstalled021Storage() = seedProfile("0.2.1-dev")
    @Test fun seedProfileUsingInstalled022Storage() = seedProfile("0.2.2-dev")
    @Test fun seedProfileUsingInstalled023Storage() = seedProfile("0.2.3-dev")
    @Test fun seedProfileUsingInstalled024Storage() = seedProfile("0.2.4-dev")
    @Test fun seedProfileUsingInstalled025Storage() = seedProfile("0.2.5-dev")
    @Test fun seedProfileUsingInstalled026Storage() = seedProfile("0.2.6-dev")
    @Test fun seedProfileUsingInstalled027Storage() = seedProfile("0.2.7-dev")
    @Test fun seedProfileUsingInstalled028Storage() = seedProfile("0.2.8-dev")
    @Test fun seedProfileUsingInstalled029Storage() = seedProfile("0.2.9-dev")
    @Test fun seedProfileUsingInstalled0210Storage() = seedProfile("0.2.10-dev")
    @Test fun seedProfileUsingInstalled0211Storage() = seedProfile("0.2.11-dev")
    @Test fun seedProfileUsingInstalled0213Storage() = seedProfile("0.2.13-dev")
    @Test fun seedProfileUsingInstalled0215Storage() = seedProfile("0.2.15-dev")
    @Test fun seedProfileUsingInstalled0214Storage() = seedProfile("0.2.14-dev")
    @Test fun seedProfileUsingInstalled0212Storage() = seedProfile("0.2.12-dev")
    @Test fun seedProfileUsingInstalled0216Storage() = seedProfile("0.2.16-dev")
    @Test fun seedProfileUsingInstalled0227Storage() = seedProfile("0.2.27-dev")
    @Test fun seedProfileUsingInstalled0248Storage() = seedProfile("0.2.48-dev")

    /** Diagnostic only: the installed old APK's storage ABI, printed for the upgrade driver. */
    @Test fun installedStorageAbiReport() {
        val display = DisplayPreferences::class.java.constructors.map { it.parameterCount }.sorted().joinToString(",")
        val hubStore = HubStore::class.java.constructors.map { it.parameterCount }.sorted().joinToString(",")
        val savedHub = SavedHub::class.java.constructors.map { it.parameterCount }.sorted().joinToString(",")
        val line = "UPGRADE_ABI version=${version()} DisplayPreferences=$display HubStore=$hubStore SavedHub=$savedHub"
        android.util.Log.i("UpgradeAbi", line)
        println(line)
    }

    /**
     * Minimal 0.2.48 -> current round trip. 0.2.48 and this round share one storage
     * implementation, so the old encrypted record and display preferences must load unchanged
     * without the older, per-version expectation tables.
     */
    @Test fun upgradedNativeRestoresFrom0248Storage() {
        assertEquals("0.2.51-dev", version())
        val saved = HubStore(context).load()
        assertNotNull("The upgraded build decrypts the 0.2.48 record", saved)
        assertEquals(5000000123L, HubParser.parse(saved!!.snapshot).periods.getValue("today").tokens)
        assertTrue("Synthetic credential remains valid", runCatching { UUID.fromString(saved.credential) }.isSuccess)
        val restored = DisplayPreferenceStore(context).load()
        assertEquals(ThemeChoice.DARK, restored.theme)
        assertEquals(LanguageChoice.ENGLISH, restored.language)
        assertEquals("last30", restored.monthMode)
        assertEquals("CNY", restored.currency)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var model: HubViewModel
            activity.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
            try {
                val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
                while ((model.state.value.snapshot == null || model.state.value.loading) && System.nanoTime() < until) Thread.sleep(20)
                assertEquals(5000000123L, model.state.value.snapshot!!.periods.getValue("today").tokens)
                assertEquals(restored, model.display.value)
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { model.setForeground(false) }
            }
        }
    }
    @Test fun seedProfileUsingInstalled0224Storage() = seedProfile("0.2.24-dev")
    @Test fun seedProfileUsingInstalled0223Storage() = seedProfile("0.2.23-dev")
    @Test fun seedProfileUsingInstalled0222Storage() = seedProfile("0.2.22-dev")
    @Test fun seedProfileUsingInstalled0221Storage() = seedProfile("0.2.21-dev")
    @Test fun seedProfileUsingInstalled0220Storage() = seedProfile("0.2.20-dev")
    @Test fun seedProfileUsingInstalled0219Storage() = seedProfile("0.2.19-dev")
    @Test fun seedProfileUsingInstalled0218Storage() = seedProfile("0.2.18-dev")
    @Test fun seedProfileUsingInstalled0217Storage() = seedProfile("0.2.17-dev")
    private fun upgradeMain():Any=MainDisplayPreferences(viewOrder=listOf("sessions","home","limits","tools","models","projects","devices","trends","subscriptions","pricing"),hiddenViews=setOf("pricing"),titleIconOnly=false,compactTotal=true,compactUnits="localized",showLiveRate=true,rateMode="burn",swapActions=true,sessionContextMetric="remaining")
    private fun seedProfile(oldVersion: String) {
        assertEquals(oldVersion, version())
        // Use the installed old constructor and encryption implementation, not today's default ABI.
        // 0.2.48 carries the independent expanded-record limit as a fourth parameter; older builds
        // only expose (Context, String, Int), so prefer the newer shape and fall back.
        val store = runCatching {
            HubStore::class.java.getConstructor(Context::class.java, String::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .newInstance(context, "hub-state.bin", 10 * 1024 * 1024, 32 * 1024 * 1024) as HubStore
        }.getOrElse {
            HubStore::class.java.getConstructor(Context::class.java, String::class.java, Int::class.javaPrimitiveType)
                .newInstance(context, "hub-state.bin", 10 * 1024 * 1024) as HubStore
        }
        assertNull("Upgrade fixture requires empty application data", store.load())
        val credential = UUID.randomUUID().toString()
        val snapshot="""{"periods":{"today":{"totalTokens":5000000123}}}"""
        val fields=arrayOf<Any?>("https://upgrade-fixture.invalid", credential,
            snapshot, 1234L, "http://10.0.2.2:9",
            """{"daily":[{"date":"2026-10-09","tokens":123}],"monthly":[]}""", """{"subscriptions":[]}""",
            mapOf("fixture-desktop" to "Upgrade fixture"),
            """{"ok":true,"value":{"modelAliases":{"vendor/model":"model"},"modelAliasGrouping":"off"}}""",
            """{"ok":true,"version":1,"revision":1,"value":[{"modelId":"fixture-model","inputPerM":0}]}""")
        val allFields=when {
            // 0.2.48 added the retained history signature and the observed model list after the
            // 0.2.27 record shape (13-parameter primary constructor, reported by the ABI check).
            oldVersion=="0.2.48-dev" -> (fields.toList()+listOf<Any?>(DeviceHistoryParser.project("""{"devices":[]}""",FixedPeriods.signature(HubParser.parse(snapshot))),null,null)).toTypedArray()
            oldVersion in listOf("0.2.2-dev","0.2.3-dev","0.2.4-dev","0.2.5-dev","0.2.6-dev","0.2.7-dev","0.2.8-dev","0.2.9-dev","0.2.10-dev","0.2.11-dev","0.2.12-dev","0.2.13-dev","0.2.14-dev","0.2.15-dev","0.2.16-dev","0.2.17-dev","0.2.18-dev","0.2.19-dev","0.2.20-dev","0.2.21-dev","0.2.22-dev","0.2.23-dev","0.2.24-dev","0.2.27-dev") -> fields+DeviceHistoryParser.project("""{"devices":[]}""",FixedPeriods.signature(HubParser.parse(snapshot)))
            else -> fields
        }
        val saved=SavedHub::class.java.constructors.single { it.parameterCount==allFields.size }.newInstance(*allFields) as SavedHub
        store.save(saved)
        assertTrue("Old installed implementation restores the complete synthetic record", store.load() == saved)
        assertFalse(File(context.noBackupFilesDir, "hub-state.bin").readBytes().toString(Charsets.ISO_8859_1).contains(credential))
        val oldDisplay = if(oldVersion in listOf("0.2.27-dev","0.2.48-dev")) DisplayPreferences(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false,"off"),LimitDisplayPreferences(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true,true),upgradeMain() as MainDisplayPreferences,"cost",listOf("trends","limits","tools","models","sessions","devices","activity"),false)
        else if(oldVersion in listOf("0.2.23-dev","0.2.24-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==19}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==8}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false,"off") as AppearancePreferences,LimitDisplayPreferences::class.java.constructors.single {it.parameterCount==10}.newInstance(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true) as LimitDisplayPreferences,upgradeMain(),"cost",listOf("trends","limits","tools","models","sessions","devices","activity"),false) as DisplayPreferences
        else if(oldVersion in listOf("0.2.20-dev","0.2.21-dev","0.2.22-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==18}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==8}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false,"off") as AppearancePreferences,LimitDisplayPreferences::class.java.constructors.single {it.parameterCount==10}.newInstance(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true) as LimitDisplayPreferences,upgradeMain(),"cost",listOf("trends","limits","tools","models","sessions","devices","activity")) as DisplayPreferences
        else if(oldVersion in listOf("0.2.17-dev","0.2.18-dev","0.2.19-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==17}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==8}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false,"off") as AppearancePreferences,LimitDisplayPreferences::class.java.constructors.single {it.parameterCount==10}.newInstance(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true) as LimitDisplayPreferences,upgradeMain(),"cost") as DisplayPreferences
        else if(oldVersion in listOf("0.2.14-dev","0.2.15-dev","0.2.16-dev","0.2.17-dev","0.2.18-dev","0.2.19-dev","0.2.20-dev","0.2.21-dev","0.2.22-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==16}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==7}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false) as AppearancePreferences,LimitDisplayPreferences::class.java.constructors.single {it.parameterCount==10}.newInstance(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true) as LimitDisplayPreferences,upgradeMain()) as DisplayPreferences
        else if(oldVersion in listOf("0.2.12-dev","0.2.13-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==15}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==7}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false) as AppearancePreferences,LimitDisplayPreferences::class.java.constructors.single {it.parameterCount==10}.newInstance(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true) as LimitDisplayPreferences) as DisplayPreferences
        else if(oldVersion in listOf("0.2.10-dev","0.2.11-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==15}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==7}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false) as AppearancePreferences,LimitDisplayPreferences::class.java.constructors.single {it.parameterCount==3}.newInstance(true,true,true) as LimitDisplayPreferences) as DisplayPreferences
        else if(oldVersion=="0.2.9-dev") DisplayPreferences::class.java.constructors.single {it.parameterCount==14}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==7}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false) as AppearancePreferences) as DisplayPreferences
        else if(oldVersion in listOf("0.2.7-dev","0.2.8-dev","0.2.9-dev","0.2.10-dev")) DisplayPreferences::class.java.constructors.single {it.parameterCount==14}
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7"),AppearancePreferences::class.java.constructors.single {it.parameterCount==4}.newInstance(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","") as AppearancePreferences) as DisplayPreferences
        else if (oldVersion in listOf("0.2.4-dev","0.2.5-dev","0.2.6-dev","0.2.7-dev","0.2.8-dev","0.2.9-dev","0.2.10-dev")) DisplayPreferences::class.java.constructors.single { it.parameterCount==13 }
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year","CNY",mapOf("CNY" to "7")) as DisplayPreferences
        else if (oldVersion == "0.2.3-dev") DisplayPreferences::class.java.getConstructor(ThemeChoice::class.java, Boolean::class.javaPrimitiveType, LanguageChoice::class.java, List::class.java, Set::class.java, List::class.java, List::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30","tokens","year") as DisplayPreferences
        else if (oldVersion == "0.2.2-dev") DisplayPreferences::class.java.getConstructor(ThemeChoice::class.java, Boolean::class.javaPrimitiveType, LanguageChoice::class.java, List::class.java, Set::class.java, List::class.java, List::class.java, String::class.java, String::class.java)
            .newInstance(ThemeChoice.DARK,true,LanguageChoice.ENGLISH,listOf("trends","tools","devices"),setOf("claude"),listOf("hermes"),emptyList<String>(),"last30","last30") as DisplayPreferences
        else if (oldVersion == "0.2.1-dev") DisplayPreferences::class.java.getConstructor(ThemeChoice::class.java, Boolean::class.javaPrimitiveType, LanguageChoice::class.java, List::class.java, Set::class.java, List::class.java, List::class.java)
            .newInstance(ThemeChoice.DARK, true, LanguageChoice.ENGLISH, listOf("trends", "tools", "devices"), setOf("claude"), listOf("hermes"), emptyList<String>()) as DisplayPreferences
        else if (oldVersion == "0.2.0-dev") DisplayPreferences::class.java.getConstructor(ThemeChoice::class.java, Boolean::class.javaPrimitiveType, LanguageChoice::class.java, List::class.java)
            .newInstance(ThemeChoice.DARK, true, LanguageChoice.ENGLISH, listOf("trends", "tools", "devices")) as DisplayPreferences
        else DisplayPreferences::class.java.getConstructor(ThemeChoice::class.java, Boolean::class.javaPrimitiveType, LanguageChoice::class.java)
            .newInstance(ThemeChoice.DARK, true, LanguageChoice.ENGLISH) as DisplayPreferences
        DisplayPreferenceStore(context).save(oldDisplay)
        // Deliberately retain this profile across package installation; host clears it in finally.
    }

    @Test fun upgradedNativeRestoresOldKeystoreAndActualActivityCache() {
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals(arguments.getString("upgradedVersion") ?: "0.2.38-dev", version())
        val saved = HubStore(context).load()
        assertNotNull("Updated APK decrypts the old application's record", saved)
        assertEquals(5000000123L, HubParser.parse(saved!!.snapshot).periods.getValue("today").tokens)
        assertTrue("Synthetic credential remains valid", runCatching { UUID.fromString(saved.credential) }.isSuccess)
        val preferences = DisplayPreferences(ThemeChoice.DARK, true, LanguageChoice.ENGLISH,
            if (arguments.getString("upgradeFrom") in listOf("0.2.0", "0.2.1", "0.2.2","0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) listOf("trends", "tools", "devices") else defaultHomeModules,
            if (arguments.getString("upgradeFrom") in listOf("0.2.1", "0.2.2","0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) setOf("claude") else emptySet(),
            if (arguments.getString("upgradeFrom") in listOf("0.2.1", "0.2.2","0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) listOf("hermes") else emptyList(),
            monthMode=if (arguments.getString("upgradeFrom") in listOf("0.2.2","0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) "last30" else "month",
            lastPeriod=if (arguments.getString("upgradeFrom") in listOf("0.2.2","0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) "last30" else "today",
            heatmapMetric=if (arguments.getString("upgradeFrom") in listOf("0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) "tokens" else "cost",
            homeActiveDaysWindow=if (arguments.getString("upgradeFrom") in listOf("0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) "year" else "all",
            currency=if(arguments.getString("upgradeFrom") in listOf("0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) "CNY" else "USD",
            currencyRates=if(arguments.getString("upgradeFrom") in listOf("0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) mapOf("CNY" to "7") else emptyMap(),appearance=if(arguments.getString("upgradeFrom") in listOf("0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) AppearancePreferences(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","",mapOf("codex" to "#ff0000"),false,false) else if(arguments.getString("upgradeFrom") in listOf("0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) AppearancePreferences(mapOf("bg" to "#0b0c0e","chart" to "#ff0000"),"largest","serif","") else AppearancePreferences(),limits=if(arguments.getString("upgradeFrom") in listOf("0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) LimitDisplayPreferences(true,true,true,listOf("mimo","codex"),mapOf("factory" to listOf("credits")),setOf("kimi"),listOf("factory"),5,true,true) else if(arguments.getString("upgradeFrom") in listOf("0.2.10","0.2.11")) LimitDisplayPreferences(true,true,true) else LimitDisplayPreferences(),main=if(arguments.getString("upgradeFrom") in listOf("0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) upgradeMain() as MainDisplayPreferences else MainDisplayPreferences()).let {profile->if(arguments.getString("upgradeFrom") in listOf("0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) profile.copy(appearance=profile.appearance.copy(reduceMotion="off"),modelRanking="cost") else profile}
        val orderedPreferences=if(arguments.getString("upgradeFrom") in listOf("0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) preferences.copy(homeModuleOrder=listOf("trends","limits","tools","models","sessions","devices","activity")) else preferences
        val expectedPreferences=if(arguments.getString("upgradeFrom") in listOf("0.2.23","0.2.24","0.2.27")) orderedPreferences.copy(sessionTitlesEnabled=false) else orderedPreferences
        val latestExpected=if(arguments.getString("upgradeFrom")=="0.2.27") expectedPreferences.copy(limits=expectedPreferences.limits.copy(highlightLowHomeLimits=true)) else expectedPreferences
        assertTrue(DisplayPreferenceStore(context).load() == latestExpected)
        fun await(condition: () -> Boolean) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!condition() && System.nanoTime() < until) Thread.sleep(20)
            assertTrue("Upgrade restoration condition unavailable; private diagnostics omitted", condition())
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var model: HubViewModel
            activity.onActivity { model = ViewModelProvider(it)[HubViewModel::class.java] }
            try {
                await { model.state.value.snapshot != null && !model.state.value.loading }
                val state = model.state.value
                assertEquals(5000000123L, state.snapshot!!.periods.getValue("today").tokens)
                assertEquals(123L, state.history!!.daily.single().tokens)
                assertTrue(state.subscriptions?.isEmpty() == true)
                assertTrue(state.deviceAliases["fixture-desktop"] == "Upgrade fixture")
                assertTrue(state.modelNames!!.aliases["vendor/model"] == "model")
                assertTrue(state.pricing!!.models.single().rates.getValue("inputPerM").signum() == 0)
                assertTrue(model.display.value == latestExpected)
                if (arguments.getString("upgradeFrom") in listOf("0.2.2","0.2.3","0.2.4","0.2.5","0.2.6","0.2.7","0.2.8","0.2.9","0.2.10","0.2.11","0.2.12","0.2.13","0.2.14","0.2.15","0.2.16","0.2.17","0.2.18","0.2.19","0.2.20","0.2.21","0.2.22","0.2.23","0.2.24","0.2.27")) assertNotNull(state.deviceHistories) else assertNull(state.deviceHistories)
                assertEquals(preferences.monthMode, model.display.value.monthMode)
                assertEquals(preferences.lastPeriod, model.display.value.lastPeriod)
                assertEquals("https", model.endpointType())
                assertFalse(state.live)
                assertNotNull("Unavailable HTTPS retains the old cache", state.error)
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { model.setForeground(false) }
            }
        }
    }
}
