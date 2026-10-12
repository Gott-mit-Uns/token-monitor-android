package dev.bing.tokenmonitor

/** Explicit evidence status, independent of app/Hub package versions or build fingerprints. */
internal object DesktopParityMetadata {
    const val reference="0.69.0"
    const val fullyAligned=false
    // health.version is the protocol version; core/collection fingerprints are not app versions.
    val hubVersion:String?=null
}
