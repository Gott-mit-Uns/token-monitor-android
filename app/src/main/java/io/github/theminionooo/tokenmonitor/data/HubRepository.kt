package io.github.theminionooo.tokenmonitor.data

import android.content.Context
import android.os.SystemClock
import io.github.theminionooo.tokenmonitor.data.network.BackoffPolicy
import io.github.theminionooo.tokenmonitor.data.network.EndpointFailover
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidation
import io.github.theminionooo.tokenmonitor.data.network.HubAddressValidator
import io.github.theminionooo.tokenmonitor.data.network.HubDetailRevisions
import io.github.theminionooo.tokenmonitor.data.network.HubApiClient
import io.github.theminionooo.tokenmonitor.data.network.HubApiException
import io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess
import io.github.theminionooo.tokenmonitor.data.network.prepareHomeAddressRepair
import io.github.theminionooo.tokenmonitor.data.network.WireHubSnapshot
import io.github.theminionooo.tokenmonitor.data.protocol.HubProtocolParser
import io.github.theminionooo.tokenmonitor.data.protocol.HubStreamProtocol
import io.github.theminionooo.tokenmonitor.data.storage.SecureConnectionStore
import io.github.theminionooo.tokenmonitor.data.storage.SerialDiskQueue
import io.github.theminionooo.tokenmonitor.data.storage.SnapshotCache
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import io.github.theminionooo.tokenmonitor.domain.HubStats
import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import io.github.theminionooo.tokenmonitor.data.network.HubNetworkObserver
import io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator

internal data class HubRepositoryState(
    val hasConnection: Boolean = false,
    /** The saved Hub base address, for display only. The secret never enters UI state. */
    val connectionUrl: String? = null,
    /** The optional home Wi-Fi address tried when [connectionUrl] does not answer. */
    val fallbackUrl: String? = null,
    /** Whichever saved address answered most recently. */
    val activeUrl: String? = null,
    val allowLocalNetwork: Boolean = false,
    val localNetworkPermissionRequired: Boolean = false,
    val snapshot: HubSnapshot? = null,
    val refreshing: Boolean = false,
    val streamActive: Boolean = false,
    val widgetLiveActive: Boolean = false,
    val message: String? = null,
)

internal fun retainDeviceHistory(streamed: HubStats, previous: List<DeviceUsage>): HubStats = streamed.copy(
    devices = streamed.devices.map { incoming ->
        val existing = previous.firstOrNull { it.id == incoming.id }
        if (existing != null && incoming.history.daily.isEmpty() && incoming.history.monthly.isEmpty()) {
            incoming.copy(
                historyAvailable = incoming.historyAvailable ?: existing.historyAvailable,
                history = existing.history,
            )
        } else {
            incoming
        }
    },
)

/** Stream/poll updates reuse the latest device history rather than decoding the older /devices response. */
internal fun decodeStatsUpdate(wire: WireHubSnapshot, previousDevices: List<DeviceUsage>): HubSnapshot {
    val decoded = HubProtocolParser.decodeSnapshot(
        wire.health, wire.stats, null, wire.history, wire.subscriptions, wire.capturedAt,
    )
    if (!decoded.health.ok || decoded.health.role != "hub") throw HubApiException(200, "The address is not a Token Monitor Hub.")
    return decoded.copy(stats = retainDeviceHistory(decoded.stats, previousDevices))
}

internal enum class HubWorkMode { Idle, WidgetPolling, DashboardStreaming }

internal const val WIDGET_POLL_INTERVAL_MS = 60_000L

internal fun selectHubWorkMode(
    dashboardVisible: Boolean,
    widgetActive: Boolean,
    widgetDeadline: Long? = null,
    elapsedRealtime: Long = 0,
): HubWorkMode = when {
    dashboardVisible -> HubWorkMode.DashboardStreaming
    widgetActive && (widgetDeadline == null || elapsedRealtime < widgetDeadline) -> HubWorkMode.WidgetPolling
    else -> HubWorkMode.Idle
}

/** Owns state on the main dispatcher; socket and disk work run on IO dispatchers. */
internal class HubRepository(context: Context) {
    private val appContext = context.applicationContext
    private val connectionStore = SecureConnectionStore(appContext)
    private val cache = SnapshotCache(appContext)
    private val backoff = BackoffPolicy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val diskQueue = SerialDiskQueue(diskScope)
    private val network = HubNetworkObserver(appContext) { scope.launch { if (workAllowed) refreshNow(manual = false) } }
    private var connection: HubConnection? = connectionStore.read()
    private var activeUrl: String? = connection?.baseUrl
    private var lastWireSnapshot: WireHubSnapshot? = cache.read()
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<HubRepositoryState> = _state.asStateFlow()
    private var pendingCacheWrite: WireHubSnapshot? = null
    private var lastCacheWriteAt = 0L
    private var foregroundJob: Job? = null
    private var activeWorkMode = HubWorkMode.Idle
    private var subscriptionJob: Job? = null
    private var detailJob: Job? = null
    private var lastDetailAttemptAt = 0L
    private var forceDetails = false
    private var authenticationRejected = false
    private var activeApi: HubApiClient? = null
    @Volatile private var dashboardVisible = false
    @Volatile private var widgetActive = false
    @Volatile private var widgetDeadline: Long? = null
    private val desiredWorkMode get() = selectHubWorkMode(dashboardVisible, widgetActive, widgetDeadline, SystemClock.elapsedRealtime())
    private val workAllowed get() = desiredWorkMode != HubWorkMode.Idle
    @Volatile private var generation = 0L
    private var lastSubscriptionAttemptAt = 0L
    private var attemptedSubscriptionVersion = ""

    fun setDashboardVisible(visible: Boolean) {
        dashboardVisible = visible
        reconcileWork()
    }

    fun setWidgetActive(active: Boolean, deadlineElapsedRealtime: Long? = null) {
        widgetActive = active
        widgetDeadline = if (active) deadlineElapsedRealtime else null
        reconcileWork()
    }

    private fun reconcileWork() {
        _state.update { it.copy(localNetworkPermissionRequired = needsLocalPermission()) }
        val desired = desiredWorkMode
        if (desired == HubWorkMode.Idle) {
            network.stop()
            stopForegroundWork()
            return
        }
        network.start()
        if (!authenticationRejected && (foregroundJob?.isActive != true || activeWorkMode != desired)) {
            stopForegroundWork()
            startForegroundWork(desired)
        }
    }

    fun refreshNow(manual: Boolean = true) {
        val desired = desiredWorkMode
        if (connection == null || desired == HubWorkMode.Idle) return
        if (!manual && authenticationRejected) return
        if (manual) authenticationRejected = false
        forceDetails = manual
        stopForegroundWork()
        startForegroundWork(desired)
    }

    suspend fun validateAndSave(rawUrl: String, rawFallbackUrl: String, secret: String, allowLocalNetwork: Boolean): String? {
        val primary = when (val result = HubAddressValidator.validate(rawUrl, secret, allowLocalNetwork)) {
            is HubAddressValidation.Allowed -> result.connection
            is HubAddressValidation.Rejected -> return result.reason
        }
        val fallback = if (rawFallbackUrl.isBlank()) null else {
            when (val result = HubAddressValidator.validate(rawFallbackUrl, secret, true, allowPublicHttps = false)) {
                is HubAddressValidation.Allowed -> result.connection.baseUrl
                is HubAddressValidation.Rejected -> return "Home Wi-Fi address: ${result.reason}"
            }
        }
        val candidate = primary.copy(fallbackUrl = fallback?.takeIf { it != primary.baseUrl })
        return verifyAndSave(candidate)
    }

    suspend fun repairHomeAddress(rawAddress: String): String? {
        val saved = connection
        val candidate = when (val result = prepareHomeAddressRepair(saved, rawAddress)) {
            is HubAddressValidation.Allowed -> result.connection
            is HubAddressValidation.Rejected -> return result.reason
        }
        // Test the address the person selected; a reachable primary must not conceal a bad repair.
        return verifyAndSave(candidate, candidate.fallbackUrl ?: candidate.baseUrl)
    }

    private suspend fun verifyAndSave(candidate: HubConnection, testAddress: String? = null): String? {
        stopForegroundWork()
        val token = generation
        val api = HubApiClient { route ->
            if (generation != token) throw CancellationException("Connection check cancelled")
            requireLocalPermission(route)
        }.also { activeApi = it }
        return try {
            val (url, wire) = withContext(Dispatchers.IO) {
                val routes = testAddress?.let(::listOf) ?: permittedRoutes(candidate, null)
                EndpointFailover.run(routes) { api.loadSnapshot(candidate.copy(baseUrl = it)) }
            }
            coroutineContext.ensureActive()
            if (generation != token) return "Connection check cancelled. Try again."
            val snapshot = withContext(Dispatchers.IO) { parse(wire) }
            if (generation != token) return "Connection check cancelled. Try again."
            connectionStore.save(candidate)
            authenticationRejected = false
            connection = candidate
            activeUrl = url
            applySuccess(wire, snapshot)
            null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: HubApiException) { error.presentation }
        catch (_: Exception) { "The Hub could not be verified. Check the HTTPS or private Hub address and try again." }
        finally { api.close(); if (activeApi === api) activeApi = null; reconcileWork() }
    }

    fun disconnect() {
        connectionStore.clear()
        stopForegroundWork()
        connection = null
        activeUrl = null
        lastWireSnapshot = null
        pendingCacheWrite = null
        diskQueue.enqueue { cache.clear(); WidgetUpdateCoordinator.refresh(appContext) }
        _state.value = HubRepositoryState()
    }

    fun close() {
        network.stop()
        stopForegroundWork()
        scope.cancel()
        diskQueue.closeWhenIdle()
    }

    private fun initialState(): HubRepositoryState = HubRepositoryState(
        hasConnection = connection != null,
        connectionUrl = connection?.baseUrl,
        fallbackUrl = connection?.fallbackUrl,
        activeUrl = activeUrl,
        allowLocalNetwork = connection?.allowLocalNetwork ?: false,
        localNetworkPermissionRequired = needsLocalPermission(),
        snapshot = if (connection == null) null else lastWireSnapshot?.let { runCatching { parse(it, true) }.getOrNull() },
    )

    private fun startForegroundWork(mode: HubWorkMode = desiredWorkMode) {
        val saved = connection ?: return
        if (mode == HubWorkMode.Idle || mode != desiredWorkMode || foregroundJob?.isActive == true) return
        val token = generation
        val refreshDetails = forceDetails
        forceDetails = false
        val api = HubApiClient { route ->
            if (generation != token || desiredWorkMode != mode) throw CancellationException("Hub work ended")
            requireLocalPermission(route)
        }.also { activeApi = it }
        activeWorkMode = mode
        foregroundJob = scope.launch {
            var attempt = 0
            var initialized = false
            var lastStatsPollAt = 0L
            try {
                while (desiredWorkMode == mode && generation == token) {
                    try {
                        _state.update { it.copy(refreshing = true, message = null) }
                        val url = if (!initialized) {
                            val previous = if (refreshDetails) null else lastWireSnapshot
                            val (route, wire) = withContext(Dispatchers.IO) {
                                EndpointFailover.run(permittedRoutes(saved, activeUrl)) { api.loadSnapshot(saved.copy(baseUrl = it), previous) }
                            }
                            ensureActive()
                            if (generation != token) break
                            val snapshot = withContext(Dispatchers.IO) { parse(wire) }
                            if (generation != token) break
                            activeUrl = route
                            applySuccess(wire, snapshot)
                            initialized = true
                            lastStatsPollAt = android.os.SystemClock.elapsedRealtime()
                            route
                        } else {
                            val route = activeUrl ?: saved.baseUrl
                            if (android.os.SystemClock.elapsedRealtime() - lastStatsPollAt >= WIDGET_POLL_INTERVAL_MS) {
                                lastStatsPollAt = android.os.SystemClock.elapsedRealtime()
                                val raw = withContext(Dispatchers.IO) { api.getStats(saved.copy(baseUrl = route)) }
                                applyStatsUpdate(raw, token)
                                refreshSubscriptions(saved, api, token)
                                refreshHistory(saved, api, token)
                            }
                            route
                        }
                        when (mode) {
                            HubWorkMode.DashboardStreaming -> withContext(Dispatchers.IO) {
                                api.streamStats(saved.copy(baseUrl = url),
                                    onOpen = { withContext(Dispatchers.Main.immediate) {
                                        if (generation == token) _state.update { it.copy(streamActive = true, widgetLiveActive = false, refreshing = false) }
                                    } },
                                    onEvent = { event -> withContext(Dispatchers.Main.immediate) {
                                        if (generation == token) {
                                            if (applyStatsUpdate(event.data, token, streamEventType = event.type)) attempt = 0
                                            refreshSubscriptions(saved, api, token)
                                            refreshHistory(saved, api, token)
                                        }
                                    } },
                                )
                            }
                            HubWorkMode.WidgetPolling -> {
                                _state.update { it.copy(streamActive = false, widgetLiveActive = true, refreshing = false) }
                                while (desiredWorkMode == mode && generation == token) {
                                    delay(WIDGET_POLL_INTERVAL_MS)
                                    if (generation != token || desiredWorkMode != mode) break
                                    val raw = withContext(Dispatchers.IO) {
                                        api.getStats(saved.copy(baseUrl = url))
                                    }
                                    ensureActive()
                                    if (generation != token || desiredWorkMode != mode) break
                                    if (applyStatsUpdate(raw, token)) attempt = 0
                                    refreshSubscriptions(saved, api, token)
                                    refreshHistory(saved, api, token)
                                }
                            }
                            HubWorkMode.Idle -> Unit
                        }
                        if (generation == token) _state.update { it.copy(streamActive = false, widgetLiveActive = false, message = "Live updates paused. Retrying...") }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        if (generation != token) break
                        val rejected = error is HubApiException && error.statusCode in setOf(401, 403)
                        _state.update { it.copy(refreshing = false, streamActive = false, widgetLiveActive = false,
                            message = if (error is HubApiException) error.presentation else "Hub unavailable. Showing the last successful snapshot; retrying...") }
                        if (rejected) { authenticationRejected = true; break }
                        activeUrl = EndpointFailover.candidates(saved, activeUrl).getOrNull(1) ?: activeUrl
                    }
                    delay(backoff.delayForAttempt(attempt++))
                }
            } finally {
                api.close()
                if (generation == token) {
                    activeWorkMode = HubWorkMode.Idle
                    _state.update { it.copy(refreshing = false, streamActive = false, widgetLiveActive = false) }
                }
            }
        }
    }

    private fun stopForegroundWork() {
        generation++
        activeApi?.close()
        activeApi = null
        foregroundJob?.cancel()
        foregroundJob = null
        activeWorkMode = HubWorkMode.Idle
        detailJob?.cancel()
        detailJob = null
        subscriptionJob?.cancel()
        subscriptionJob = null
        _state.update { it.copy(refreshing = false, streamActive = false, widgetLiveActive = false) }
        pendingCacheWrite?.let { persist(it, force = true) }
    }

    private fun persist(wire: WireHubSnapshot, force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || now - lastCacheWriteAt >= 60_000L) {
            pendingCacheWrite = null
            lastCacheWriteAt = now
            diskQueue.enqueue { cache.save(wire); WidgetUpdateCoordinator.refresh(appContext) }
        } else pendingCacheWrite = wire
    }

    private fun parse(wire: WireHubSnapshot, cached: Boolean = false): HubSnapshot = HubProtocolParser.decodeSnapshot(
        wire.health, wire.stats, wire.devices, wire.history, wire.subscriptions, wire.capturedAt, cached,
    ).also { if (!it.health.ok || it.health.role != "hub") throw HubApiException(200, "The address is not a Token Monitor Hub.") }

    private fun needsLocalPermission(): Boolean = connection?.let {
        LocalNetworkAccess.needed(it) && !LocalNetworkAccess.granted(appContext)
    } ?: false

    private fun requireLocalPermission(route: HubConnection) {
        if (HubAddressValidator.isLocalAddress(route.baseUrl) && !LocalNetworkAccess.granted(appContext)) {
            throw HubApiException(0, LocalNetworkAccess.deniedMessage)
        }
    }

    private fun permittedRoutes(saved: HubConnection, preferred: String?): List<String> =
        LocalNetworkAccess.allowedRoutes(saved, preferred, LocalNetworkAccess.granted(appContext)).ifEmpty {
            throw HubApiException(0, LocalNetworkAccess.deniedMessage)
        }

    private fun applySuccess(wire: WireHubSnapshot, snapshot: HubSnapshot) {
        lastWireSnapshot = wire
        persist(wire, true)
        _state.value = HubRepositoryState(
            hasConnection = connection != null, connectionUrl = connection?.baseUrl,
            fallbackUrl = connection?.fallbackUrl, activeUrl = activeUrl,
            allowLocalNetwork = connection?.allowLocalNetwork ?: false, snapshot = snapshot,
            localNetworkPermissionRequired = needsLocalPermission(),
        )
    }

    private suspend fun applyStatsUpdate(raw: String, token: Long, streamEventType: String? = null): Boolean {
        val previous = _state.value.snapshot ?: return false
        val wire = lastWireSnapshot ?: return false
        val parsed = withContext(Dispatchers.IO) {
            if (streamEventType != null && streamEventType !in setOf("message", "snapshot", "stats", "freshness")) {
                return@withContext null
            }
            val normalized = runCatching {
                if (HubStreamProtocol.isFreshness(raw, streamEventType)) HubStreamProtocol.mergeFreshness(wire.stats, raw)
                else HubStreamProtocol.normalizeComplete(raw)
            }.getOrNull() ?: return@withContext null
            val updatedWire = wire.copy(stats = HubDetailRevisions.retainMarkers(wire.stats, normalized), capturedAt = System.currentTimeMillis())
            updatedWire to decodeStatsUpdate(updatedWire, previous.stats.devices)
        } ?: return false
        if (generation != token) return false
        if (lastWireSnapshot !== wire) return applyStatsUpdate(raw, token, streamEventType)
        // Preserve detail revisions and subscriptions refreshed before this event.
        val updatedWire = parsed.first.copy(subscriptions = lastWireSnapshot?.subscriptions)
        val next = parsed.second.copy(subscriptions = _state.value.snapshot?.subscriptions ?: parsed.second.subscriptions)
        lastWireSnapshot = updatedWire
        _state.update { it.copy(snapshot = next, refreshing = false, message = null) }
        persist(updatedWire, false)
        return true
    }

    private fun handleDetailFailure(error: Exception) {
        if (error is HubApiException && error.statusCode in setOf(401, 403)) {
            authenticationRejected = true
            stopForegroundWork()
            _state.update { it.copy(message = error.presentation) }
        }
        // Other failures retain the last good details and are throttled to a minute.
    }

    private fun refreshHistory(saved: HubConnection, api: HubApiClient, token: Long) {
        val wire = lastWireSnapshot ?: return
        if (!HubDetailRevisions.historyChanged(wire, wire.stats) || detailJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastDetailAttemptAt < 60_000L) return
        lastDetailAttemptAt = now
        val route = activeUrl ?: saved.baseUrl
        detailJob = scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { api.loadSnapshot(saved.copy(baseUrl = route), wire) }
                ensureActive()
                if (generation != token) return@launch
                val current = lastWireSnapshot ?: return@launch
                // Do not replace a newer streamed total with the HTTP snapshot captured earlier.
                val mergedStats = HubDetailRevisions.retainHistoryMarkers(loaded.stats, current.stats)
                val merged = current.copy(devices = loaded.devices, history = loaded.history, stats = mergedStats)
                val snapshot = withContext(Dispatchers.IO) { parse(merged) }
                if (generation != token) return@launch
                if (lastWireSnapshot !== current) return@launch
                lastWireSnapshot = merged
                _state.update { it.copy(snapshot = snapshot) }
                persist(merged, true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { handleDetailFailure(error) }
        }
    }

    private fun refreshSubscriptions(saved: HubConnection, api: HubApiClient, token: Long) {
        val snapshot = _state.value.snapshot ?: return
        val version = snapshot.stats.subscriptionsUpdatedAt
        if (version.isBlank() || version == snapshot.subscriptions.updatedAt || subscriptionJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (version == attemptedSubscriptionVersion && now - lastSubscriptionAttemptAt < 60_000L) return
        attemptedSubscriptionVersion = version
        lastSubscriptionAttemptAt = now
        val route = activeUrl ?: saved.baseUrl
        subscriptionJob = scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { api.getSubscriptions(saved.copy(baseUrl = route)) }
                ensureActive()
                if (generation != token) return@launch
                val current = _state.value.snapshot ?: return@launch
                val subscriptions = HubProtocolParser.decodeSubscriptions(raw)
                _state.update { it.copy(snapshot = current.copy(subscriptions = subscriptions)) }
                lastWireSnapshot?.let { it.copy(subscriptions = raw, stats = HubDetailRevisions.markVersion(it.stats, "subscriptionsUpdatedAt", subscriptions.updatedAt)) }?.let { lastWireSnapshot = it; persist(it, true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { handleDetailFailure(error) }
        }
    }

}
