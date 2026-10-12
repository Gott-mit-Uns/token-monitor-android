package dev.bing.tokenmonitor

/** Association is a user assertion plus successful reads, never a cryptographic Hub identity. */
internal object HubConnectionPolicy {
    enum class Failure { NETWORK, AUTH, TLS, PROTOCOL, CANCELLED }
    fun preferred(current: String, alternate: String?, associated: Boolean, lanAllowed: Boolean,
        lanCoolingDown: Boolean = false): String? {
        val endpoints = listOfNotNull(current, alternate)
        val local = endpoints.firstOrNull { it.startsWith("http://") }
        val secure = endpoints.firstOrNull { it.startsWith("https://") }
        if (!associated) return current.takeIf { it.startsWith("https://") || lanAllowed }
        return local?.takeIf { lanAllowed && !lanCoolingDown } ?: secure ?: local?.takeIf { lanAllowed }
    }
    fun fallback(current: String, alternate: String?, associated: Boolean, lanAllowed: Boolean,
        failure: Failure): String? = alternate?.takeIf {
        associated && failure == Failure.NETWORK && (it.startsWith("https://") || lanAllowed) && it != current
    }
    fun classify(error: Throwable): Failure {
        val causes = generateSequence(error) { it.cause }.take(16).toList()
        return when {
            causes.any { it is java.util.concurrent.CancellationException } -> Failure.CANCELLED
            causes.any { it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException } -> Failure.TLS
            error is HubReadException && error.unauthorized -> Failure.AUTH
            error is HubReadException && error.display == R.string.error_unavailable -> Failure.NETWORK
            error is java.io.IOException -> Failure.NETWORK
            else -> Failure.PROTOCOL
        }
    }
}
