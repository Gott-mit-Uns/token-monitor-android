package dev.bing.tokenmonitor

import java.net.URI

/** Fixed desktop connectionEndpoint displays the host, omitting paths and user info. */
internal object ConnectionDisplay {
    fun endpoint(value:String?):String? = runCatching {
        val url=URI(value.orEmpty().trim())
        if(url.scheme?.lowercase() !in listOf("http","https") || url.host.isNullOrEmpty()) null
        else url.host.lowercase() + if(url.port>=0 && url.port!=if(url.scheme?.lowercase()=="https") 443 else 80) ":${url.port}" else ""
    }.getOrNull()
}
