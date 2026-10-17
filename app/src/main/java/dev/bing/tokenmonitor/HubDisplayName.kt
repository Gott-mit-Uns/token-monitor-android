package dev.bing.tokenmonitor

/** Optional authenticated display metadata, never an endpoint identity or collector ID. */
internal object HubDisplayName {
    /** Alias survives only the same endpoint pair and authentication binding. */
    fun sameConfiguration(address:String?,alternate:String?,credential:String?,next:String,nextAlternate:String?,nextCredential:String):Boolean =
        address!=null && credential==nextCredential && setOfNotNull(address,alternate)==setOfNotNull(next,nextAlternate)
    fun normalize(value:String?):String? = value?.trim()?.takeIf { name ->
        name.isNotEmpty() && name.codePointCount(0,name.length)<=80 &&
            name.codePoints().noneMatch {point -> Character.isISOControl(point) ||
                Character.getType(point)==Character.FORMAT.toInt() || Character.getType(point)==Character.SURROGATE.toInt()}
    }
}
