package io.github.theminionooo.tokenmonitor.data.storage

import java.net.URI
import java.security.MessageDigest

internal object DeviceAliasRules {
    fun normalize(input: String): String {
        require(input.codePoints().noneMatch { Character.isISOControl(it) || it == 0x2028 || it == 0x2029 }) { "Device names cannot contain line breaks or control characters." }
        val value = input.trim()
        require(value.codePointCount(0, value.length) <= 32) { "Device names can contain at most 32 characters." }
        return value
    }

    fun key(hubUrl: String, deviceId: String): String {
        val uri = URI(hubUrl.trim())
        val scheme = uri.scheme.lowercase()
        val host = uri.host.lowercase()
        val port = uri.port.takeIf { it >= 0 } ?: if (scheme == "https") 443 else 80
        val normalized = "$scheme://$host:$port${uri.path.orEmpty().trimEnd('/')}"
        fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "${digest(normalized)}.${digest(deviceId)}"
    }
}
