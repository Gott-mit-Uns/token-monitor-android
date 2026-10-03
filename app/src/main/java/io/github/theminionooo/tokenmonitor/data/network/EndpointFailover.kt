package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection
import kotlinx.coroutines.CancellationException

/**
 * Chooses between the primary Hub address and the optional home Wi-Fi address.
 * The address that answered last is tried first, so a phone that arrives home
 * with Tailscale off, or leaves home on mobile data, moves over on its own.
 */
internal object EndpointFailover {
    /** Candidate base URLs in trial order, starting with [preferred] when it is still configured. */
    fun candidates(connection: HubConnection, preferred: String?): List<String> {
        val fallback = connection.fallbackUrl?.takeIf { it.isNotBlank() && it != connection.baseUrl }
        val all = listOfNotNull(connection.baseUrl, fallback)
        if (preferred == null || preferred !in all) return all
        return listOf(preferred) + all.filter { it != preferred }
    }

    /**
     * Runs [block] against each candidate until one succeeds and returns that address with the
     * result. A rejected secret is reported immediately because trying another address cannot
     * fix it; any other failure moves on to the next address. The first failure is rethrown
     * when every address fails.
     */
    inline fun <T> run(candidates: List<String>, block: (String) -> T): Pair<String, T> {
        var firstFailure: Exception? = null
        for (url in candidates) {
            try {
                return url to block(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: HubApiException) {
                if (error.statusCode in setOf(401, 403)) throw error
                if (firstFailure == null) firstFailure = error
            } catch (error: Exception) {
                if (firstFailure == null) firstFailure = error
            }
        }
        throw firstFailure ?: IllegalStateException("No Hub address is configured.")
    }
}
