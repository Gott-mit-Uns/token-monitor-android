package dev.bing.tokenmonitor

import org.junit.Assert.*
import org.junit.Test

class HubBuildInfoTest {
    private fun info(core:Long=85,runtime:Long=5)=HubBuildInfo("node-hub",1,core,HubBuildRegistry.entries.getValue("core")[core] ?: "sha256:"+"a".repeat(64),runtime,HubBuildRegistry.entries.getValue("node-hub")[runtime] ?: "sha256:"+"b".repeat(64))
    @Test fun currentBuildMatchesDesktopRegistry() {assertEquals("current",info().status)}
    @Test fun oldBuildIsReportedWithItsRealRevisions() {assertEquals("updateAvailable",info(74).status)}
    @Test fun futureBuildIsNewer() {assertEquals("remoteNewer",info(86,6).status)}
    @Test fun mixedOlderAndNewerIsUnknown() {assertEquals("unknown",info(74,6).status)}
    @Test fun mismatchedKnownFingerprintIsUnknown() {assertEquals("unknown",info().copy(coreBuildId="sha256:"+"a".repeat(64)).status)}
    @Test fun legacyHealthNeverTreatsProtocolVersionAsSoftwareVersion() {val value=HubBuildInfo.parse("""{"version":1,"runtime":"node"}""")!!;assertNull(value.coreRevision);assertEquals("legacy",value.status)}
    @Test fun nullBuildIsUnknownRatherThanLegacy() {assertEquals("unknown",HubBuildInfo.parse("""{"hubBuild":null}""")!!.status)}
    @Test fun malformedMetadataCannotBreakHealthParsing() {val value=HubBuildInfo.parse("""{"hubBuild":{"runtime":"node-hub","schemaVersion":1,"coreRevision":-1,"runtimeRevision":1.5,"coreBuildId":"arbitrary data"}}""")!!;assertEquals("unknown",value.status);assertNull(value.coreRevision);assertNull(value.runtimeRevision);assertNull(value.coreBuildId)}
    @Test fun cacheRoundTripPreservesBuildAndStatus() {assertEquals(info(74),HubBuildInfo.parse(info(74).cache()))}
    @Test fun legacyCacheRemainsLegacy() {val value=HubBuildInfo.parse("""{"runtime":"worker"}""")!!;assertEquals(value,HubBuildInfo.parse(value.cache()))}
    @Test fun unsupportedRuntimeIsUnknown() {assertEquals("unknown",info().copy(runtime="unrecognized").status)}
    @Test fun nonObjectAndInvalidJsonDoNotBecomeVersions() {assertNull(HubBuildInfo.parse("[]"));assertNull(HubBuildInfo.parse("broken"))}
}
