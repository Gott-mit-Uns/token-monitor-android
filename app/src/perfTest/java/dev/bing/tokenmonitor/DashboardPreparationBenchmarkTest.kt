package dev.bing.tokenmonitor

import android.os.Build
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

/** Fixed geometry isolates data/target preparation from V5 layout changes. Public fixtures only. */
class DashboardPreparationBenchmarkTest {
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun fixture(days:Int,keys:Int):HubHistory {
        val daily=List(days) {day->
            val values=(0 until keys).associate {key->
                "public-$key" to HistoryAttribution((day%13+1L)*(key+1L)*1000L,"0",0)
            }
            HistoryRow(LocalDate.of(2026,10,11).minusDays((days-day-1).toLong()).toString(),
                values.values.sumOf {it.tokens!!},"0",0,values,values)
        }
        return HubHistory(daily.reversed(),emptyList())
    }
    @Test fun fixedGeometryProjectionAndPreparation() = runBlocking {
        assertTrue("Only the task-owned emulator is permitted",Build.HARDWARE in listOf("goldfish","ranchu","cutf_cvm") || Build.PRODUCT.startsWith("sdk"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val reports=JSONArray()
        for ((days,keys,mode) in listOf(Triple(30,8,DashboardTrendMode.BARS),
            Triple(366,80,DashboardTrendMode.BARS),Triple(366,80,DashboardTrendMode.KLINE))) {
            val history=fixture(days,keys)
            val times=mutableListOf<Double>();val preparation=mutableListOf<Double>()
            var fingerprint=""
            repeat(20) {index->
                val begin=System.nanoTime()
                val model=DashboardTrendProjection.project(history,"all",false,mode,800.0,360.0,"en")
                val projected=System.nanoTime()
                val prepared=prepareDashboardChart(model,mapOf("default" to 0xFF888888L),IntSize(1600,720))
                val complete=System.nanoTime()
                assertSame(model,prepared.model)
                assertEquals(days,model.daily.size)
                if(mode==DashboardTrendMode.BARS) {
                    assertEquals(days*keys,model.bars.sumOf {it.segments.size})
                    assertEquals(history.daily.sumOf {it.tokens!!}.toString(),model.legend.sumOf {it.value}.toString())
                }
                val current=hash(model.toString().toByteArray())
                if(fingerprint.isEmpty()) fingerprint=current else assertEquals(fingerprint,current)
                if(index>=5) {times+=(projected-begin)/1e6;preparation+=(complete-projected)/1e6}
            }
            fun samples(values:List<Double>):JSONObject {
                val sorted=values.sorted()
                return JSONObject().put("valuesMs",JSONArray(values)).put("p50Ms",sorted[7]).put("p95Ms",sorted[14])
            }
            reports.put(JSONObject().put("days",days).put("keysPerDimension",keys).put("mode",mode.name)
                .put("modelSha256",fingerprint).put("projection",samples(times)).put("preparation",samples(preparation)))
        }
        File(context.getExternalFilesDir(null),"dashboard-preparation-public.json").writeText(JSONObject()
            .put("schemaVersion",1).put("apkSha256",hash(File(context.applicationInfo.sourceDir).readBytes()))
            .put("androidApi",Build.VERSION.SDK_INT).put("warmups",5).put("recordedSamples",15)
            .put("geometry","800x360 source / 1600x720 pixels; all range; en; perClient; fixed default palette #888888")
            .put("scope","Public deterministic Android projection/preparation; not live Hub, Window FPS or physical device")
            .put("scenarios",reports).toString(2))
    }
}
