package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SessionSettingsPreferenceTest {
    @Test fun v14BytesAndGlobalPrivacyRestoreWithoutWritingThenV15PersistsSessionOverride() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="session-v15-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(context,name);val file=File(context.noBackupFilesDir,name)
        try {
            for(global in listOf(false,true)) {
                val old=DisplayPreferences(hideTitles=global,homeModules=listOf("sessions","limits"),homeModuleOrder=defaultHomeModules.reversed(),main=MainDisplayPreferences(sessionContextMetric="remaining"),appearance=AppearancePreferences(reduceMotion="off"))
                store.save(old)
                val v14=file.readBytes().dropLast(2).toByteArray().also {it[0]=14};file.writeBytes(v14)
                assertEquals(old,store.load());assertTrue(v14.contentEquals(file.readBytes()))
                assertEquals(global,store.load().hideSessionTitles)
                val updated=DisplayChange.SessionTitles(false).apply(store.load());store.save(updated)
                assertEquals(16,file.readBytes()[0].toInt());assertEquals(updated,DisplayPreferenceStore(context,name).load())
                assertEquals(old,updated.copy(sessionTitlesEnabled=true))
            }
        } finally {store.clear()}
    }
    @Test fun invalidBooleanTruncatedAndTrailingDataAreRejectedWithoutRewriting() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="session-bounds-${UUID.randomUUID()}.bin";val store=DisplayPreferenceStore(context,name);val file=File(context.noBackupFilesDir,name)
        try {
            val selected=DisplayPreferences(sessionTitlesEnabled=false);store.save(selected);val good=file.readBytes()
            for(bad in listOf(good.copyOf().also {it[it.lastIndex]=2},good.copyOf().also {it[it.lastIndex-1]=2},good.dropLast(1).toByteArray(),good+0.toByte())) {
                file.writeBytes(bad);assertTrue(runCatching {store.load()}.isFailure);assertTrue(bad.contentEquals(file.readBytes()))
            }
            file.writeBytes(good);assertEquals(selected,store.load())
        } finally {store.clear()}
    }
}
