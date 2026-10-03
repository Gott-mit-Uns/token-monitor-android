package io.github.theminionooo.tokenmonitor

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import org.junit.Assert.*
import org.junit.Test

class SecureConnectionStoreTest {
    @Test fun concurrentStoreInstancesRetainAReadableAuthenticatedRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val prefs = context.getSharedPreferences("secure_connection", Context.MODE_PRIVATE)
        val previous = prefs.all.mapValues { it.value as String }
        val records = (0 until 4).map { HubConnection("https://synthetic-$it.invalid", "synthetic-$it", false) }
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val gate = java.util.concurrent.CountDownLatch(1)
        try {
            SecureConnectionStore(context).clear()
            val threads = records.map { record ->
                Thread {
                    try {
                        gate.await()
                        val store = SecureConnectionStore(context)
                        repeat(8) {
                            store.save(record)
                            // Another writer may win; a mixed IV/ciphertext pair may never be returned.
                            check(store.read() in records)
                        }
                    } catch (error: Throwable) { errors.add(error) }
                }.apply { start() }
            }
            gate.countDown()
            threads.forEach { it.join(30_000) }
            assertTrue("Concurrent stores must finish", threads.none { it.isAlive })
            assertTrue("Concurrent stores must preserve authenticated records", errors.isEmpty())
            assertTrue(SecureConnectionStore(context).read() in records)
        } finally {
            val edit = prefs.edit().clear()
            previous.forEach { (key, value) -> edit.putString(key, value) }
            check(edit.commit())
        }
    }

    @Test fun encryptedRecordRoundTripsWithRandomizedCiphertextAndNoPlaintext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".preview"))
        val prefs = context.getSharedPreferences("secure_connection", Context.MODE_PRIVATE)
        val previous = prefs.all.mapValues { it.value as String }
        try {
            val store = SecureConnectionStore(context)
            val record = HubConnection("https://synthetic.invalid", "synthetic-test-secret", false)
            store.save(record)
            assertEquals(record, store.read())
            val first = prefs.getString("ciphertext", null)
            assertFalse(prefs.all.values.any { it.toString().contains(record.secret) })
            store.save(record)
            assertNotEquals(first, prefs.getString("ciphertext", null))
            val encoded = prefs.getString("ciphertext", null)!!
            val replacement = if (encoded.first() == 'A') 'B' else 'A'
            check(prefs.edit().putString("ciphertext", replacement + encoded.drop(1)).commit())
            assertNull("Modified authenticated ciphertext must be rejected", store.read())
            store.clear()
            assertTrue(prefs.all.isEmpty())
            assertNull(store.read())
        } finally {
            val edit = prefs.edit().clear()
            previous.forEach { (key, value) -> edit.putString(key, value) }
            check(edit.commit())
        }
    }
}
