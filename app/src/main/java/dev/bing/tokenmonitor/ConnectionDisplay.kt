package dev.bing.tokenmonitor

import java.net.URI

/** Fixed desktop connectionEndpoint displays the host, omitting paths and user info. */
internal object ConnectionDisplay {
    /** Display the configured protocol and host while dropping every authentication-bearing part. */
    fun address(value:String?):String? = runCatching {
        val url=URI(value.orEmpty().trim())
        val scheme=url.scheme?.lowercase()
        if(scheme !in listOf("http","https") || url.host.isNullOrEmpty()) null
        else "$scheme://${url.host.lowercase()}" + if(url.port>=0) ":${url.port}" else ""
    }.getOrNull()
    fun endpoint(value:String?):String? = runCatching {
        val url=URI(value.orEmpty().trim())
        if(url.scheme?.lowercase() !in listOf("http","https") || url.host.isNullOrEmpty()) null
        else url.host.lowercase() + if(url.port>=0 && url.port!=if(url.scheme?.lowercase()=="https") 443 else 80) ":${url.port}" else ""
    }.getOrNull()
}
