package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSystemDisplayTest {
    @Test fun debianCodenameIsHiddenOnlyInSummary() {
        assertEquals("Debian GNU/Linux 12", compactDeviceSystem("Debian GNU/Linux 12 (bookworm)"))
        assertEquals("Debian GNU/Linux 13", compactDeviceSystem("Debian GNU/Linux 13"))
    }
    @Test fun otherReportedSystemsArePreserved() {
        listOf("macOS 27.0.1", "Windows 11 26H2", "Ubuntu 24.04 (Noble)", "").forEach {
            assertEquals(it, compactDeviceSystem(it))
        }
    }
}
