package io.github.theminionooo.tokenmonitor.fork

import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import org.junit.Assert.*
import org.junit.Test

class HubDeviceNamesTest {
    @Test fun hubIdentityWinsWithoutChangingDeviceStatistics() {
        val stats=HubProtocolParser.decodeStats("""{"devices":[{"deviceId":"DXP4800","hostname":"container-hash","platform":"linux","periods":{"today":{"totalTokens":0}}},{"deviceId":"MAC Mini","hostname":"Mac-mini.local","platform":"darwin","periods":{"today":{"totalTokens":12}}}]}""")
        val original=io.github.theminionooo.tokenmonitor.domain.HubSnapshot(stats=stats)
        val displayed=hubNamedSnapshot(original)
        assertEquals(listOf("DXP4800","MAC Mini"),displayed.stats.devices.map { it.hostname })
        assertEquals(original.stats.devices.map { it.periods },displayed.stats.devices.map { it.periods })
        assertEquals(listOf("linux","darwin"),displayed.stats.devices.map { it.platform })
        assertEquals("container-hash",original.stats.devices.first().hostname)
    }
}
