package io.github.theminionooo.tokenmonitor

import androidx.test.platform.app.InstrumentationRegistry
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SnapshotCacheSafetyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun prepare() { check(context.packageName.endsWith(".preview")); SnapshotCache(context).clear() }
    @After fun clean() { SnapshotCache(context).clear() }

    @Test fun unicodeSnapshotPreservesCapturedTimeAndAllDetails() {
        val wire = WireHubSnapshot("健康", "中文统计😀", "设备一", "全年历史", "订阅", 123456L)
        val cache = SnapshotCache(context)
        cache.save(wire)
        assertEquals(wire, cache.read())
    }

    @Test fun oversizedDeclaredPartIsRejectedBeforeAllocation() {
        DataOutputStream(GZIPOutputStream(File(context.filesDir, "last-snapshot.bin").outputStream())).use {
            it.writeInt(2); it.writeLong(123456L); it.writeInt(32 * 1024 * 1024 + 1)
        }
        assertNull(SnapshotCache(context).read())
    }

    @Test fun invalidNegativeLengthCannotMasqueradeAsMissingField() {
        DataOutputStream(GZIPOutputStream(File(context.filesDir, "last-snapshot.bin").outputStream())).use {
            it.writeInt(2); it.writeLong(123456L); it.writeInt(-2)
        }
        assertNull(SnapshotCache(context).read())
    }
}
