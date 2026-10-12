package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class Device069Test {
    @Test fun sharedNameResolverKeepsDesktopOrderTrimsBlankAliasesAndHidesParserSentinel() {
        val device=Device("id","hostname",false,null,null,null,hubDisplayName="Hub name")
        assertEquals("Alias",DeviceDisplay.name(device,"  Alias  "))
        assertEquals("Hub name",DeviceDisplay.name(device,"   "))
        assertEquals("id",DeviceDisplay.name(device.copy(hubDisplayName=" ")))
        assertEquals("hostname",DeviceDisplay.name(device.copy(id="",hubDisplayName=null)))
        assertEquals("device",DeviceDisplay.name(device.copy(id="",name="__unnamed_device__",hubDisplayName=null)))
        assertEquals("Unknown",DeviceDisplay.name(device.copy(id="",name="",hubDisplayName=null),fallback="Unknown"))
    }
}
