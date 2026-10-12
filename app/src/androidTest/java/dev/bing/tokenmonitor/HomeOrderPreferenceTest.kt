package dev.bing.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class HomeOrderPreferenceTest {
    @Test fun v14PersistsHiddenSlotsAndChangesOnlyOrderOrMembership() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="home-v14-${UUID.randomUUID()}.bin"
        val store=DisplayPreferenceStore(context,name);val file=File(context.noBackupFilesDir,name)
        val p=DisplayPreferences(homeModules=listOf("models","activity","limits"),homeModuleOrder=listOf("devices","models","tools","activity","sessions","limits"),currency="CNY",currencyRates=mapOf("CNY" to "7"),appearance=AppearancePreferences(reduceMotion="off"),modelRanking="cost")
        try {
            store.save(p);assertEquals(16,file.readBytes()[0].toInt());assertEquals(p,DisplayPreferenceStore(context,name).load())
            val shown=DisplayChange.HomeModuleVisibility("tools").apply(store.load());store.save(shown)
            assertEquals(listOf("models","tools","activity","limits"),store.load().homeModules);assertEquals(p.homeModuleOrder,store.load().homeModuleOrder)
            val hidden=DisplayChange.HomeModuleVisibility("tools").apply(store.load());store.save(hidden);assertEquals(p,store.load())
            val moved=DisplayChange.HomeModuleMove("tools",1).apply(store.load());store.save(moved)
            assertEquals(p.homeModules,store.load().homeModules);assertEquals(listOf("devices","models","activity","tools","sessions","limits"),store.load().homeModuleOrder)
            val all=DisplayChange.ShowAllHomeModules.apply(store.load());store.save(all);assertEquals(all.homeModuleOrder,store.load().homeModules)
            assertEquals(p,store.load().copy(homeModules=p.homeModules,homeModuleOrder=p.homeModuleOrder))
        } finally {store.clear()}
    }
    @Test fun malformedFullOrderAndPartialAtomicWritesCannotReplaceValidPreferences() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="home-bounds-${UUID.randomUUID()}.bin"
        val store=DisplayPreferenceStore(context,name);val file=File(context.noBackupFilesDir,name)
        val p=DisplayPreferences(homeModuleOrder=defaultHomeModules)
        try {
            store.save(p);val good=file.readBytes()
            listOf(listOf("tools"),defaultHomeModules+"unknown",defaultHomeModules+"tools").forEach {order->
                assertTrue(runCatching {store.save(p.copy(homeModuleOrder=order))}.isFailure);assertTrue(good.contentEquals(file.readBytes()))
            }
            val duplicate=good.copyOf().also {it[it.lastIndex-1]=it[it.lastIndex-2]};file.writeBytes(duplicate)
            assertTrue(runCatching {store.load()}.isFailure);assertTrue(duplicate.contentEquals(file.readBytes()))
            file.writeBytes(good+0.toByte());assertTrue(runCatching {store.load()}.isFailure)
            file.writeBytes(good);assertEquals(p,store.load())
        } finally {store.clear()}
    }
}
