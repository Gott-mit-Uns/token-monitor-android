package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class DesktopParityMetadataTest {
    @Test fun referenceDoesNotClaimFullParityOrInferHubAppVersionFromProtocol() {
        assertEquals("0.69.0",DesktopParityMetadata.reference)
        assertFalse(DesktopParityMetadata.fullyAligned)
        assertNull(DesktopParityMetadata.hubVersion)
    }
}
