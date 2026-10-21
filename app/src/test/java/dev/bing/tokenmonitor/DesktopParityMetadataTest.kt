package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DesktopParityMetadataTest {
    @Test fun referenceDoesNotClaimFullParityOrInferHubAppVersionFromProtocol() {
        assertEquals("0.77.0",DesktopParityMetadata.reference)
        assertFalse(DesktopParityMetadata.fullyAligned)
        assertNull(HubBuildInfo.parse("""{"version":1}""")!!.coreRevision)
    }
}
