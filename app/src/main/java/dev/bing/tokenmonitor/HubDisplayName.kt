package dev.bing.tokenmonitor

/** Optional authenticated display metadata, never an endpoint identity or collector ID. */
internal object HubDisplayName {
    fun normalize(value:String?):String? = value?.trim()?.takeIf { name ->
        name.isNotEmpty() && name.codePointCount(0,name.length)<=80 &&
            name.codePoints().noneMatch {point -> Character.isISOControl(point) ||
                Character.getType(point)==Character.FORMAT.toInt() || Character.getType(point)==Character.SURROGATE.toInt()}
    }
}
