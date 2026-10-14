package dev.bing.tokenmonitor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.ConnectionPool
import java.util.concurrent.CancellationException
import java.net.Proxy
import javax.net.SocketFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class HubState(val snapshot: Snapshot? = null, val loading: Boolean = false, val error: Int? = null, val savedAt: Long? = null, val history: HubHistory? = null, val subscriptions: List<Subscription>? = null, val live: Boolean = false, val deviceAliases: Map<String, String> = emptyMap(), val modelNames: ModelNames? = null, val pricing: HubPricing? = null, val deviceHistories: DeviceHistories? = null, val connectionFailure:Int? = null, val connectionRevision:Long = 0, val connectionAttempt:Long = 0,
    // A successful read is a Dashboard render event even if its history equals the previous payload.
    val historyRenderRevision:Long = 0,
    val historyReadReceipt:HistoryReadReceipt? = null, val lanPolicyRevision:Long = 0, val hubBuild:HubBuildInfo? = null,
    // Set only after an accepted authenticated read. Configuration order is never connection evidence.
    val lastSuccessfulEndpoint:String? = null, val connectedEndpoint:String? = null)
class HubViewModel @JvmOverloads constructor(application: Application, private val store: HubStore = HubStore(application),
    private val client: OkHttpClient = defaultClient(), private val displayStore: DisplayPreferenceStore = DisplayPreferenceStore(application)) : AndroidViewModel(application), HubUiActions {
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
    private val mutable = MutableStateFlow(HubState(loading = true))
    val state = mutable.asStateFlow()
    private val startupMutable = MutableStateFlow(HubStartup.LOADING)
    val startup = startupMutable.asStateFlow()
    // A fresh process must not restore an old Settings route. Activity recreation
    // retains this ViewModel/key and therefore still preserves the current page.
    val startupSessionId = java.util.UUID.randomUUID().toString()
    private var startupStoreLoaded = false
    private fun finishStartupIfIdle() {
        if (startupStoreLoaded && foreground && !mutable.value.loading &&
            !(networkRecovery != null && networkScope?.available == true && networkScope?.ready == false) &&
            startupMutable.value == HubStartup.LOADING)
            startupMutable.value = if (currentHub == null) HubStartup.SETTINGS else HubStartup.HOME
    }
    private val displayMutable = MutableStateFlow(runCatching { displayStore.load() }.getOrDefault(DisplayPreferences()))
    val display = displayMutable.asStateFlow()
    private val displayMutex = Mutex()
    fun changeDisplay(change: DisplayChange) = updateDisplay(change.apply(displayMutable.value))
    fun updateDisplay(preferences: DisplayPreferences) {
        displayMutable.value = preferences
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { displayMutex.withLock {
                if (displayMutable.value == preferences) runCatching {
                    displayStore.save(preferences)
                } else Result.success(Unit)
            } }
            if (result.isFailure) mutable.value = mutable.value.copy(error = R.string.error_preferences_save)
        }
    }
    private var currentHub: String? = null
    private var alternateHub: String? = null
    private var credential: String? = null
    private var lanEnabled = true
    private var endpointsAssociated = false
    private var lanRetryAfter = 0L
    private val http = HubHttp(client, ::transport).apply { pause() }
    @Volatile private var requestToken = 0L
    private var manualValidationTicket:Long? = null
    private var manualValidationBase:String? = null
    private var committingConnection=false
    @Volatile private var authBlocked = false
    private var tlsBlocked = false
    private var streamEligible = false
    @Volatile private var foreground = false
    @Volatile private var networkScope: HubNetworkScope? = null
    private fun permitsHttp(base: String, scope: HubNetworkScope? = networkScope) = lanEnabled && HubLocalPermission.granted(getApplication()) && scope?.let { it.permitsHttp(base) && it.socketFactory != null } == true
    // TLS idle socket close may send close_notify. Keep that I/O off Android's main
    // thread and independent of viewModelScope, which is cancelled before onCleared.
    // Capture retired pools now: a delayed close must never select a new local client.
    private fun retireIdlePools(vararg pools: ConnectionPool?) {
        val retired = pools.filterNotNull().distinct()
        if (retired.isNotEmpty()) Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            retired.forEach { it.evictAll() }
        })
    }
    private fun retireCurrentIdlePools() = retireIdlePools(client.connectionPool,
        synchronized(transportLock) { localClient?.connectionPool })
    private val transportLock = Any()
    private var localFactory: SocketFactory? = null
    private var localClient: OkHttpClient? = null
    private fun transport(base: String): OkHttpClient {
        if (networkScope?.available == false) throw CancellationException()
        if (base.startsWith("https://")) return client
        if (!permitsHttp(base)) throw CancellationException()
        val factory = networkScope?.socketFactory ?: throw CancellationException()
        return synchronized(transportLock) {
            if (localFactory !== factory) {
                retireIdlePools(localClient?.connectionPool)
                localFactory = factory
                localClient = client.newBuilder().socketFactory(factory).proxy(Proxy.NO_PROXY).connectionPool(ConnectionPool())
                    .connectTimeout(minOf(client.connectTimeoutMillis.takeIf { it > 0 } ?: 3000, 3000).toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(minOf(client.readTimeoutMillis.takeIf { it > 0 } ?: 5000, 5000).toLong(), TimeUnit.MILLISECONDS)
                    .callTimeout(minOf(client.callTimeoutMillis.takeIf { it > 0 } ?: 8000, 8000).toLong(), TimeUnit.MILLISECONDS).build()
            }
            localClient!!
        }
    }
    @Volatile private var epoch = 0L
    private var streamCall: Call? = null
    private var streamTransportActive = false
    private var streamRestartPending = false
    private var streamFrames: Channel<HubStreamSnapshot>? = null
    private var streamConsumer: Job? = null
    private data class StreamSave(val record: SavedHub, val binding: Long, val sequence: Long)
    private var streamSaves: Channel<StreamSave>? = null
    private var streamSaver: Job? = null
    private var reconnect: Job? = null
    private var retryDelay = 2000L
    private data class NetworkRecovery(val networkId: Long, var binding: Long,
        val forceHistory: Boolean = false, var retries: Int = 0)
    private var networkRecovery: NetworkRecovery? = null
    private var networkRetry: Job? = null
    private fun cancelNetworkRetry() { networkRetry?.cancel(); networkRetry = null }
    private fun cancelNetworkRecovery() { cancelNetworkRetry(); networkRecovery = null }
    private fun recoveryIsCurrent(recovery: NetworkRecovery): Boolean = networkRecovery === recovery &&
        foreground && !authBlocked && !tlsBlocked && !committingConnection && recovery.binding == bindingToken &&
        networkScope?.let { it.networkId == recovery.networkId && it.available && it.ready } == true
    private fun retryNetworkValidation(recovery: NetworkRecovery?, ticket: Long, error: Throwable) {
        if (recovery == null || networkRecovery !== recovery) return
        if (HubConnectionPolicy.classify(error) != HubConnectionPolicy.Failure.NETWORK || recovery.retries >= 3) {
            cancelNetworkRecovery(); return
        }
        if (!recoveryIsCurrent(recovery) || ticket != requestToken) return
        val wait = (2000L shl recovery.retries++) + kotlin.random.Random.nextLong(500)
        cancelNetworkRetry()
        networkRetry = viewModelScope.launch {
            delay(wait)
            if (!recoveryIsCurrent(recovery) || ticket != requestToken || mutable.value.loading) return@launch
            // Clear before refresh cancels outstanding jobs; retain this cycle's finite budget.
            networkRetry = null
            refreshSavedInScope(recovery.forceHistory, recovery)
        }
    }
    private val storageMutex = Mutex()
    @Volatile private var bindingToken = 0L
    @Volatile private var cacheSequence = 0L
    private var latestBody: String? = null
    private var historyBody: String? = null
    private var historySignature: String? = null
    private val historyReads = HistoryReadCoordinator()
    private var deviceHistoryBody: String? = null
    private var subscriptionsBody: String? = null
    private var modelNamesBody: String? = null
    private var pricingBody: String? = null
    private fun currentRecord(): SavedHub? {
        return SavedHub(currentHub ?: return null, credential ?: return null,
            latestBody ?: return null, mutable.value.savedAt ?: System.currentTimeMillis(), alternateHub, historyBody, subscriptionsBody, mutable.value.deviceAliases, modelNamesBody, pricingBody, deviceHistoryBody, historySignature,
            mutable.value.snapshot?.observedModelIds).also { it.lanEnabled = lanEnabled; it.endpointsAssociated = endpointsAssociated; it.hubBuild=mutable.value.hubBuild; it.lastSuccessfulEndpoint=mutable.value.lastSuccessfulEndpoint }
    }
    private suspend fun saveCurrent(stillValid: (() -> Boolean)? = null): Boolean {
        val record = currentRecord() ?: return false
        return persist(record, bindingToken, stillValid)
    }
    private fun historyKey(state: HubState = mutable.value) = HistoryReadKey(state.connectionRevision, homeHistorySignature(state.snapshot))
    private fun cancelHistory(phase: HistoryReadPhase = HistoryReadPhase.CANCELLED) {
        historyReads.cancelActive(phase)?.let { mutable.value = mutable.value.copy(historyReadReceipt = it) }
    }
    private fun refreshedSnapshot(old:HubState,parsed:Snapshot,live:Boolean,observedAt:Long=System.currentTimeMillis()):HubState {
        val deviceChanged=old.snapshot?.let {FixedPeriods.signature(parsed)!=FixedPeriods.signature(it)} ?: true
        val historyChanged=homeHistorySignature(parsed)!=homeHistorySignature(old.snapshot)
        val subscriptionsChanged=parsed.subscriptionsRevision!=null && parsed.subscriptionsRevision!=old.snapshot?.subscriptionsRevision
        val namesChanged=parsed.modelNamesRevision!=null && parsed.modelNamesRevision!=old.snapshot?.modelNamesRevision
        val pricingChanged=parsed.pricingRevision!=null && parsed.pricingRevision!=old.snapshot?.pricingRevision
        return old.copy(snapshot=parsed,live=live,error=if(old.connectionFailure!=null) R.string.error_connection_change else null,savedAt=observedAt,
            history=old.history.takeUnless {historyChanged},deviceHistories=old.deviceHistories.takeUnless {deviceChanged},
            subscriptions=old.subscriptions.takeUnless {subscriptionsChanged},modelNames=old.modelNames.takeUnless {namesChanged},pricing=old.pricing.takeUnless {pricingChanged})
    }
    private fun acceptSnapshot(parsed: Snapshot, body: String, live: Boolean, forceHistory: Boolean = false) =
        HubWorkTrace.trace(HubWorkStage.MAIN_ACCEPT, parsed.devices.size.toLong()) {
        val previousKey = historyKey()
        val invalidatedHistory = previousKey.signature != homeHistorySignature(parsed) && historyReads.receipt?.phase == HistoryReadPhase.STARTED
        if (invalidatedHistory) {
            cancelHistory(HistoryReadPhase.STALE)
            requestToken++
            http.cancel()
            mutable.value = mutable.value.copy(loading = false)
        }
        val updated=refreshedSnapshot(mutable.value,parsed,live)
        if(updated.pricing==null) pricingBody=null
        if(updated.modelNames==null) modelNamesBody=null
        if(updated.history==null) { historyBody=null; historySignature=null }
        if(updated.deviceHistories==null) deviceHistoryBody=null
        if(updated.subscriptions==null) subscriptionsBody=null
        latestBody=body
        val key = historyKey(updated)
        val receipt = if (forceHistory && previousKey == key) historyReads.queue(key) else historyReads.target(key)
        mutable.value=updated.copy(historyReadReceipt = receipt,
            error = if (invalidatedHistory) R.string.error_revision_changed else updated.error)
    }
    private fun interruptConnectionValidation() {
        if(manualValidationTicket==null) return
        manualValidationTicket=null
        manualValidationBase=null
        mutable.value=mutable.value.copy(connectionFailure=R.string.error_connection_interrupted,
            error=if(currentHub!=null) R.string.error_connection_change else R.string.error_connection_interrupted)
    }
    override fun dismissConnectionFailure() {
        mutable.value=mutable.value.copy(connectionFailure=null,error=mutable.value.error.takeUnless {it==R.string.error_connection_change})
    }
    /** The existing binding stays authoritative until the verified candidate is durably saved. */
    private fun replaceConnection(base:String,secret:String,alternate:String?) {
        cancelNetworkRecovery()
        cancelHistory()
        stopStream()
        bindingToken++ // Fence older cache writes, without changing the saved binding.
        val ticket=++requestToken
        manualValidationTicket=ticket
        manualValidationBase=base
        mutable.value=mutable.value.copy(loading=true,error=null,connectionFailure=null,connectionAttempt=mutable.value.connectionAttempt+1)
        viewModelScope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {
                val build=http.verify(base) {foreground && ticket==requestToken}
                val projected=http.readStats(base,secret) {foreground && ticket==requestToken}
                Triple(HubParser.parse(projected),projected.body,build)
            }}
            if(ticket!=requestToken) return@launch
            manualValidationTicket=null
            manualValidationBase=null
            if(result.isFailure) {
                val failure=result.exceptionOrNull()!!
                val affectsCurrent=base==currentHub && secret==credential && failure is HubReadException && failure.unauthorized
                if(affectsCurrent) {authBlocked=true;streamEligible=false}
                val reason=connectionMessage(failure)
                mutable.value=mutable.value.copy(loading=false,connectionFailure=reason,
                    error=if(currentHub!=null && !affectsCurrent) R.string.error_connection_change else reason)
                startStream()
                return@launch
            }
            val (parsed,body,build)=result.getOrThrow()
            val sameHub=secret==credential && (base==currentHub || (endpointsAssociated && base==alternateHub))
            val baseline=if(sameHub) mutable.value.copy(connectionFailure=null) else HubState()
            val next=refreshedSnapshot(baseline,parsed,false).copy(loading=false,connectionFailure=null,hubBuild=build,
                lastSuccessfulEndpoint=base,connectedEndpoint=base.takeIf {foreground && ticket==requestToken},
                connectionRevision=mutable.value.connectionRevision + if(sameHub) 0 else 1,
                connectionAttempt=mutable.value.connectionAttempt)
            val record=SavedHub(base,secret,body,next.savedAt!!,alternate,
                historyBody.takeIf {next.history!=null},subscriptionsBody.takeIf {next.subscriptions!=null},next.deviceAliases,
                modelNamesBody.takeIf {next.modelNames!=null},pricingBody.takeIf {next.pricing!=null},deviceHistoryBody.takeIf {next.deviceHistories!=null},
                historySignature.takeIf {next.history!=null}, parsed.observedModelIds).also {
                it.lanEnabled = lanEnabled
                it.hubBuild = build
                it.lastSuccessfulEndpoint = base
                it.endpointsAssociated = endpointsAssociated && secret == credential && setOf(base, alternate) == setOf(currentHub, alternateHub)
            }
            // Lifecycle/network changes stop all reads immediately. During the local atomic write,
            // defer reconnection until the committed binding or rollback is known.
            committingConnection=true
            val saved=try {persist(record,bindingToken)} finally {committingConnection=false}
            if(saved) {
                currentHub=base;alternateHub=alternate;credential=secret
                endpointsAssociated=record.endpointsAssociated;lanRetryAfter=0L
                latestBody=body;historyBody=record.history;subscriptionsBody=record.subscriptions
                historySignature=record.historySignature
                modelNamesBody=record.modelNames;pricingBody=record.pricing;deviceHistoryBody=record.deviceHistory
                authBlocked=false;tlsBlocked=false;streamEligible=ticket==requestToken
                historyReads.revalidated()
                mutable.value=next.copy(historyReadReceipt=historyReads.target(historyKey(next)),
                    connectedEndpoint=next.connectedEndpoint.takeIf {foreground && ticket==requestToken})
            } else mutable.value=mutable.value.copy(loading=false,connectionFailure=R.string.error_data_save,
                error=if(currentHub!=null) R.string.error_connection_change else R.string.error_data_save)
            if(foreground && ticket!=requestToken) {
                selectPreferredEndpoint()
                enforceSecureEndpoint()
                if(!streamEligible && !authBlocked && !mutable.value.loading) refreshSaved() else startStream()
            } else {if(foreground) enforceSecureEndpoint();startStream()}
        }
    }
    private suspend fun persist(record: SavedHub, token: Long, stillValid: (() -> Boolean)? = null,
        sequence: Long = ++cacheSequence): Boolean {
        return withContext(Dispatchers.IO) {
            val queued = HubWorkTrace.begin(HubWorkStage.CACHE_QUEUE_WAIT)
            var acquired = false
            try { storageMutex.withLock {
            acquired = true
            queued.end()
            if (token != bindingToken) false
            else if (sequence != cacheSequence) true
            else if (stillValid?.invoke() == false) true // Invalidated work is a no-op, not a storage failure.
            else runCatching {
                store.save(record)
            }.isSuccess
            } } finally { if (!acquired) queued.end(success = false) }
        }
    }
    fun setForeground(active: Boolean) {
        val wasForeground = foreground
        foreground = active
        if (active) {
            http.resume()
            if(committingConnection) return
            if (!wasForeground && !tlsBlocked) selectPreferredEndpoint()
            enforceSecureEndpoint()
            if (!streamEligible && !authBlocked && !tlsBlocked && !mutable.value.loading) refreshSaved() else startStream()
            finishStartupIfIdle()
        } else {
            cancelNetworkRecovery()
            cancelHistory()
            interruptConnectionValidation()
            stopStream()
            requestToken++
            http.pause()
            mutable.value = mutable.value.copy(loading = committingConnection)
        }
    }
    fun updateNetworkScope(scope: HubNetworkScope) {
        val previous = networkScope
        val changed = previous?.let { it.networkId != scope.networkId || it.available != scope.available } == true
        networkScope = scope
        if(committingConnection) {
            if(changed) {cancelHistory();requestToken++;streamEligible=false;http.cancel();retireCurrentIdlePools()}
            return
        }
        if(!changed && manualValidationBase?.let {it.startsWith("http://") && !permitsHttp(it)}==true) {
            cancelHistory();interruptConnectionValidation();requestToken++;http.cancel();mutable.value=mutable.value.copy(loading=false);startStream()
        }
        if (changed) {
            restartForNetwork()
            return
        }
        enforceSecureEndpoint()
        val base = currentHub
        val recovery = networkRecovery
        if (previous?.ready == false && scope.ready && recovery != null && recoveryIsCurrent(recovery) &&
            !mutable.value.loading && !streamEligible) {
            refreshSavedInScope(recovery.forceHistory, recovery)
            return
        }
        if (foreground && !authBlocked && !mutable.value.loading && !mutable.value.live && scope.available && base != null &&
            ((previous?.available == false) || (base.startsWith("http://") && !permitsHttp(base, previous) && permitsHttp(base)))) refreshSaved()
    }
    private fun restartForNetwork() {
        cancelNetworkRecovery()
        if(currentHub==null && manualValidationTicket==null) return
        cancelHistory()
        interruptConnectionValidation()
        stopStream()
        streamEligible = false
        requestToken++
        http.cancel()
        retireCurrentIdlePools()
        mutable.value = mutable.value.copy(loading = false)
        if (!foreground) return
        if (networkScope?.available == false) {
            if (!authBlocked) mutable.value = mutable.value.copy(error = R.string.error_no_network)
            finishStartupIfIdle()
            return
        }
        if (!authBlocked && !tlsBlocked) {
            val recovery = NetworkRecovery(networkScope?.networkId ?: return, bindingToken)
            networkRecovery = recovery
            refreshSavedInScope(false, recovery)
        }
    }
    private fun preferredEndpoint(): String? {
        val base = currentHub ?: return null
        val local = listOfNotNull(base, alternateHub).firstOrNull { it.startsWith("http://") }
        return HubConnectionPolicy.preferred(base, alternateHub, endpointsAssociated,
            local?.let(::permitsHttp) == true, android.os.SystemClock.elapsedRealtime() < lanRetryAfter)
    }
    private fun selectPreferredEndpoint() {
        val next = preferredEndpoint() ?: return
        if (!foreground || next == currentHub || authBlocked || tlsBlocked) return
        cancelHistory();stopStream();requestToken++;http.cancel()
        mutable.value = mutable.value.copy(loading = false)
        refresh(next, credential ?: return, currentHub)
    }
    private fun enforceSecureEndpoint() {
        val base = currentHub ?: return
        if (!foreground || !base.startsWith("http://") || permitsHttp(base)) return
        cancelHistory()
        stopStream()
        requestToken++
        http.cancel()
        mutable.value = mutable.value.copy(loading = false)
        val secure = alternateHub?.takeIf { endpointsAssociated && it.startsWith("https://") }
        val secret = credential
        if (secure != null && secret != null) {
            if (authBlocked) { currentHub = secure; alternateHub = base; bindingToken++; streamEligible = false }
            else if (!tlsBlocked) {
                // A pending default-network recovery owns this endpoint transition.
                // Preserve its finite budget when readiness arrives for the same scope.
                val recovery = networkRecovery
                if (recovery != null && recovery.networkId == networkScope?.networkId && recovery.binding == bindingToken)
                    refreshInScope(secure, secret, base, manual = false, forceHistory = recovery.forceHistory,
                        allowFallback = true, recovery = recovery)
                else refresh(secure, secret, base)
            }
        }
        else if (!authBlocked) mutable.value = mutable.value.copy(error = localConnectionMessage())
    }
    private fun localConnectionMessage() = if (!HubLocalPermission.granted(getApplication())) R.string.error_local_permission else R.string.error_local_network
    private fun connectionMessage(error: Throwable) = if (HubConnectionPolicy.classify(error) == HubConnectionPolicy.Failure.TLS) R.string.error_tls else (error as? HubReadException)?.display
        ?: if (error is java.io.IOException && !HubLocalPermission.granted(getApplication())) R.string.error_connection_local_check else R.string.error_connection
    private fun stopStream() {
        cancelNetworkRetry()
        epoch++
        streamRestartPending = false
        streamCall?.cancel(); streamCall = null
        streamFrames?.cancel(); streamFrames = null
        streamConsumer?.cancel(); streamConsumer = null
        streamSaves?.cancel(); streamSaves = null
        streamSaver?.cancel(); streamSaver = null
        reconnect?.cancel(); reconnect = null
        mutable.value = mutable.value.copy(live = false, connectedEndpoint = null)
    }
    private fun startStream() {
        if (!foreground || !streamEligible || tlsBlocked || streamCall != null || mutable.value.loading || authBlocked || networkScope?.available == false || streamConsumer?.isActive == true) return
        if (streamTransportActive) { streamRestartPending = true; return }
        val base = currentHub ?: return
        if (base.startsWith("http://") && !permitsHttp(base)) return
        val secret = credential ?: return
        val generation = ++epoch
        streamFrames?.cancel(); streamConsumer?.cancel()
        val frames = Channel<HubStreamSnapshot>(capacity = 1)
        streamFrames = frames
        // Retain the latest accepted cache write across ordinary reconnects, while keeping one pending slot.
        val saves = if (streamSaver?.isActive == true && streamSaves != null) streamSaves!! else {
            streamSaves?.cancel(); streamSaver?.cancel()
            Channel<StreamSave>(Channel.CONFLATED).also { queue ->
                streamSaves = queue
                streamSaver = viewModelScope.launch {
                    for (write in queue) {
                        val saved = persist(write.record, write.binding, stillValid = {
                            foreground && write.binding == bindingToken && !authBlocked
                        }, sequence = write.sequence)
                        if (!saved && foreground && write.binding == bindingToken && !authBlocked && latestBody == write.record.snapshot)
                            mutable.value = mutable.value.copy(error = R.string.error_snapshot_save)
                    }
                }
            }
        }
        val transportActive = AtomicBoolean(true)
        val consumer = viewModelScope.launch {
            // This basis belongs only to this SSE generation, never to an HTTP/cached/older stream state.
            var previous: Snapshot? = null
            for (frame in frames) {
                if (!foreground || epoch != generation || authBlocked) continue
                val basis = previous
                val parsed = withContext(Dispatchers.Default) { runCatching {
                    if (frame.isFreshness && basis != null) HubParser.applyFreshness(basis, frame.root)
                    else HubParser.parse(frame.root, frame.modelIndex)
                }.getOrNull() }
                if (parsed == null) {
                    // A failed new full frame must not let freshness reuse an older full frame's business data.
                    // The next frame retains the former full-parse fallback if no successful basis exists.
                    previous = null
                    continue
                }
                if (!foreground || epoch != generation || authBlocked) continue
                previous = parsed
                retryDelay = 2000L
                acceptSnapshot(parsed, frame.body, live = transportActive.get())
                mutable.value=mutable.value.copy(lastSuccessfulEndpoint=base,connectedEndpoint=base.takeIf {transportActive.get()})
                if (!transportActive.get()) mutable.value = mutable.value.copy(error = R.string.error_stream_retry)
                currentRecord()?.let { saves.trySend(StreamSave(it, bindingToken, ++cacheSequence)) }
            }
        }
        streamConsumer = consumer
        val stream = HubStream(transport(base).newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build())
        streamTransportActive = true
        streamRestartPending = false
        streamCall = stream.openProjected(base, secret, { foreground && epoch == generation && !authBlocked }, { frame ->
            // The producer blocks when this one-slot queue is full; no unbounded coroutine fan-out.
            HubWorkTrace.trace(HubWorkStage.SSE_FRAME_WAIT, frame.body.length.toLong()) {
                runBlocking { frames.send(frame) }
            }
        }, { ending ->
            transportActive.set(false)
            if (ending.unauthorized) frames.cancel() else frames.close()
            viewModelScope.launch {
            val unauthorized = ending.unauthorized
            if (!foreground || epoch != generation) return@launch
            streamCall = null
            authBlocked = unauthorized
            if (unauthorized) {
                streamConsumer?.cancel()
                streamSaves?.cancel(); streamSaver?.cancel()
                cancelHistory(HistoryReadPhase.AUTH_BLOCKED)
                requestToken++
                http.cancel()
                mutable.value = mutable.value.copy(loading = false)
            }
            mutable.value = mutable.value.copy(live = false, connectedEndpoint = null, error = if (unauthorized) R.string.error_stream_auth else if(ending.tlsFailure) R.string.error_tls else R.string.error_stream_retry)
            if (ending.tlsFailure) tlsBlocked = true
            if (ending.tlsFailure || !ending.recoverable) streamEligible = false
            if (!unauthorized && !ending.tlsFailure && ending.recoverable) reconnect = viewModelScope.launch {
                delay(maxOf(retryDelay, ending.retryAfterMs ?: 0L) + kotlin.random.Random.nextLong(500))
                if (!foreground || epoch != generation) return@launch
                retryDelay = (retryDelay * 2).coerceAtMost(60000L)
                // A valid final frame finishes parsing before an ordinary reconnect replaces its generation.
                consumer.join()
                if (foreground && epoch == generation) {
                    streamEligible = false
                    mutable.value = mutable.value.copy(loading = false)
                    refreshSaved()
                }
            }
        } }, released = {
            viewModelScope.launch {
                streamTransportActive = false
                if (streamRestartPending) { streamRestartPending = false; startStream() }
            }
        })
    }
    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching {
                store.load()?.let { saved ->
                    val snapshot = saved.parsedSnapshot ?: HubParser.parse(saved.snapshot, saved.observedModelIds)
                    val history = saved.history?.let { runCatching { HistoryParser.parse(it) }.getOrNull() }
                    HubState(snapshot = snapshot, savedAt = saved.savedAt, deviceAliases = saved.deviceAliases, hubBuild=saved.hubBuild,lastSuccessfulEndpoint=saved.lastSuccessfulEndpoint,
                        modelNames = saved.modelNames?.let { runCatching { ModelNamesParser.parse(it) }.getOrNull() },
                        pricing = saved.pricing?.let { runCatching { PricingParser.parse(it) }.getOrNull() },
                        deviceHistories = saved.deviceHistory?.let { runCatching { DeviceHistoryParser.parse(it).takeIf { value -> value.signature == FixedPeriods.signature(snapshot) } }.getOrNull() },
                        history = history, subscriptions = saved.subscriptions?.let { runCatching { SubscriptionParser.parse(it) }.getOrNull() }) to saved
                }
            } }
            loaded.onSuccess { restored ->
                val saved = restored?.second
                if (saved != null) {
                    currentHub = saved.address
                    alternateHub = saved.alternate
                    lanEnabled = saved.lanEnabled
                    endpointsAssociated = saved.endpointsAssociated
                    credential = saved.credential
                    latestBody = saved.snapshot
                    historyBody = saved.history
                    historySignature = saved.historySignature
                    deviceHistoryBody = saved.deviceHistory
                    subscriptionsBody = saved.subscriptions
                    modelNamesBody = saved.modelNames
                    pricingBody = saved.pricing
                    mutable.value = restored!!.first
                    startupStoreLoaded = true
                    val key = historyKey()
                    val cachedDays = mutable.value.history?.daily?.size?.takeIf { historySignature != null && historySignature == key.signature }
                    mutable.value = mutable.value.copy(historyReadReceipt = historyReads.target(key, cachedDays))
                    if (!foreground) return@onSuccess
                    refreshSaved()
                } else {
                    mutable.value = HubState()
                    startupStoreLoaded = true
                    startupMutable.value = HubStartup.SETTINGS
                }
            }.onFailure {
                mutable.value = HubState(error = R.string.error_decrypt)
                startupStoreLoaded = true
                startupMutable.value = HubStartup.SETTINGS
            }
        }
    }
    override fun connect(address: String, credential: String, alternate: String) = refresh(address, credential, alternate,manual=true)
    override fun refreshSaved() = refreshSaved(false)
    override fun refreshSaved(forceHistory: Boolean) {
        cancelNetworkRecovery()
        refreshSavedInScope(forceHistory, null)
    }
    private fun refreshSavedInScope(forceHistory: Boolean, recovery: NetworkRecovery?) {
        try {
            if (authBlocked || tlsBlocked) return
            val base = preferredEndpoint() ?: run {
                mutable.value = mutable.value.copy(error = localConnectionMessage()); return
            }
            val secret = credential ?: return
            refreshInScope(base, secret, if(base == currentHub) alternateHub else currentHub,
                manual = false, forceHistory = forceHistory, allowFallback = true, recovery = recovery)
        } finally { finishStartupIfIdle() }
    }
    override fun switchEndpoint() {
        val alternate = alternateHub ?: return
        val secret = credential ?: return
        refresh(alternate, secret, currentHub, manual = true)
    }
    override fun lanEnabled() = lanEnabled
    override fun endpointsAssociated() = endpointsAssociated
    override fun configureLan(enabled: Boolean, confirmAssociation: Boolean) {
        if (!foreground || mutable.value.loading || committingConnection) return
        cancelNetworkRecovery()
        val base = currentHub ?: return
        val secret = credential ?: return
        val local = listOfNotNull(base, alternateHub).firstOrNull { it.startsWith("http://") } ?: return
        if (confirmAssociation && (!enabled || alternateHub == null || authBlocked)) return
        // Disabling is immediate; enabling does not bypass system network/permission gates.
        if (!confirmAssociation) {
            lanEnabled = enabled
            mutable.value = mutable.value.copy(lanPolicyRevision = mutable.value.lanPolicyRevision + 1)
            cancelHistory();stopStream();requestToken++;http.cancel();streamEligible = false
            mutable.value = mutable.value.copy(loading = false)
            viewModelScope.launch {
                if (!saveCurrent()) mutable.value = mutable.value.copy(error = R.string.error_data_save)
                if (foreground && !authBlocked) refreshSaved()
            }
            return
        }
        lanEnabled = true
        if (!permitsHttp(local)) { mutable.value = mutable.value.copy(error = localConnectionMessage()); return }
        cancelHistory();stopStream();http.cancel();streamEligible = false
        val ticket = ++requestToken
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                for (candidate in listOfNotNull(base, alternateHub)) {
                    http.verify(candidate) { foreground && ticket == requestToken }
                    HubParser.parse(http.readStats(candidate, secret) { foreground && ticket == requestToken })
                }
            } }
            if (ticket != requestToken) return@launch
            if (result.isFailure) {
                val failure = result.exceptionOrNull()!!
                if (failure is HubReadException && failure.unauthorized) authBlocked = true
                if (HubConnectionPolicy.classify(failure) == HubConnectionPolicy.Failure.TLS) tlsBlocked = true
                mutable.value = mutable.value.copy(loading = false, error = connectionMessage(failure))
                return@launch
            }
            val record = currentRecord() ?: return@launch
            record.endpointsAssociated = true
            committingConnection = true
            val saved = try { persist(record, bindingToken) }
                finally { committingConnection = false }
            endpointsAssociated = saved
            mutable.value = mutable.value.copy(loading = false, error = if(saved) null else R.string.error_data_save)
            if (endpointsAssociated) lanRetryAfter = 0L
            if (foreground && !authBlocked && !tlsBlocked) refreshSaved()
        }
    }
    override fun connectionEndpoints() = listOfNotNull(currentHub,alternateHub)
    override fun editConnection(address: String, alternate: String, replacementCredential: String?) {
        refresh(address,replacementCredential ?: credential.orEmpty(),alternate,manual=true)
    }
    override fun hasAlternate() = alternateHub != null
    override fun endpointType() = if (currentHub?.startsWith("https://") == true) "https" else "http"
    override fun renameDevice(id: String, input: String) {
        if (mutable.value.loading || mutable.value.snapshot?.devices?.none { it.id == id } != false) return
        val name = input.trim()
        if (name.codePointCount(0, name.length) > 32 || name.any { it.isISOControl() }) {
            mutable.value = mutable.value.copy(error = R.string.error_device_name)
            return
        }
        val aliases = mutable.value.deviceAliases.toMutableMap()
        if (name.isEmpty()) aliases.remove(id) else aliases[id] = name
        mutable.value = mutable.value.copy(deviceAliases = aliases.toMap(), error = null)
        viewModelScope.launch { if (!saveCurrent()) mutable.value = mutable.value.copy(error = R.string.error_device_save) }
    }
    private fun resumeStreamAfterRead() {
        // A reconnect timer may have fired while the serial read/save queue was occupied.
        // Preserve an outstanding backoff; otherwise reopen once the queue is released.
        if (reconnect?.isActive != true) startStream()
    }
    private fun <T> readPart(path: String, parse: (String) -> T, onStale: (() -> Unit)? = null,
        historyManual: Boolean? = null, apply: (HubState, T) -> HubState): Boolean {
        if (!foreground || mutable.value.loading || authBlocked) return false
        if (!streamEligible) {
            mutable.value = mutable.value.copy(error = R.string.error_revalidate)
            return false
        }
        val base = currentHub ?: return false
        val secret = credential ?: return false
        val historyKey = historyKey()
        if (historyManual != null && !historyReads.canStart(historyKey, historyManual)) return false
        val ticket = ++requestToken
        val binding = bindingToken
        val historyStart = historyManual?.let {
            historyReads.begin(historyKey, ticket, it, mutable.value.snapshot?.historyPreview?.daily?.isNotEmpty() == true)
                ?: return false
        }
        fun revision() = when(path) { "devices" -> mutable.value.snapshot?.let(FixedPeriods::signature); "history" -> homeHistorySignature(mutable.value.snapshot); "subscriptions" -> mutable.value.snapshot?.subscriptionsRevision; "sync/settings/customPricing" -> mutable.value.snapshot?.pricingRevision; else -> mutable.value.snapshot?.modelNamesRevision }
        val revision = revision()
        mutable.value = mutable.value.copy(loading = true, error = null,
            historyReadReceipt = historyStart ?: mutable.value.historyReadReceipt)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val valid = { foreground && ticket == requestToken && binding == bindingToken && !authBlocked }
                val body = if (path == "devices") http.readDeviceHistory(base, secret, revision.orEmpty(), valid)
                    else http.readLarge(base, path, secret, valid)
                parse(body) to body
            } }
            if (ticket != requestToken || binding != bindingToken || !foreground || authBlocked) {
                historyStart?.let { start ->
                    historyReads.finish(start, if (authBlocked) HistoryReadPhase.AUTH_BLOCKED else HistoryReadPhase.CANCELLED)
                        ?.let { mutable.value = mutable.value.copy(historyReadReceipt = it) }
                }
                return@launch
            }
            val latestRevision = revision()
            val returnedPricing = result.getOrNull()?.first as? HubPricing
            val returnedDevices = result.getOrNull()?.first as? DeviceHistories
            val inventoryChanged = returnedDevices != null && returnedDevices.devices.map { it.id }.toSet() != mutable.value.snapshot?.devices?.map { it.id.trim() }?.filter { it.isNotEmpty() }?.toSet()
            if (revision != latestRevision || inventoryChanged || (returnedPricing != null && latestRevision != null && latestRevision.toLongOrNull() != returnedPricing.revision)) {
                val stale = historyStart?.let { historyReads.finish(it, HistoryReadPhase.STALE) }
                mutable.value = mutable.value.copy(loading = false, error = R.string.error_revision_changed,
                    historyReadReceipt = stale ?: mutable.value.historyReadReceipt)
                onStale?.invoke()
                resumeStreamAfterRead()
                return@launch
            }
            val error = result.exceptionOrNull()
            if (error != null) {
                val unauthorized = error is HubReadException && error.unauthorized
                if (unauthorized) { authBlocked = true; stopStream() }
                val phase = when {
                    unauthorized -> HistoryReadPhase.AUTH_BLOCKED
                    error is CancellationException -> HistoryReadPhase.CANCELLED
                    else -> HistoryReadPhase.FAILED
                }
                val finished = historyStart?.let { historyReads.finish(it, phase) }
                mutable.value = mutable.value.copy(loading = false,
                    connectedEndpoint = null,
                    error = (error as? HubReadException)?.display ?: R.string.error_read,
                    historyReadReceipt = finished ?: mutable.value.historyReadReceipt)
                resumeStreamAfterRead()
                return@launch
            }
            val (value, body) = result.getOrThrow()
            val finished = historyStart?.let { historyReads.finish(it, HistoryReadPhase.RESOLVED, (value as HubHistory).daily.size) }
            if (historyStart != null && finished == null) return@launch
            if (finished != null && !finished.accepted) {
                // A preview-backed empty response does not replace the visible or saved history.
                mutable.value = mutable.value.copy(loading = false, historyReadReceipt = finished)
                resumeStreamAfterRead()
                return@launch
            }
            when(path) {
                "devices" -> deviceHistoryBody = body
                "history" -> { historyBody = body; historySignature = historyKey.signature }
                "subscriptions" -> subscriptionsBody = body
                "sync/settings/customPricing" -> pricingBody = body
                else -> modelNamesBody = body
            }
            // Keep the serial queue occupied until the accepted record's save is settled.
            mutable.value = apply(mutable.value, value).copy(historyReadReceipt = finished ?: mutable.value.historyReadReceipt,
                lastSuccessfulEndpoint=base,connectedEndpoint=base)
            val saved = saveCurrent {
                !authBlocked && binding == bindingToken && (finished == null ||
                    (mutable.value.historyReadReceipt == finished && historyKey(mutable.value) == finished.key))
            }
            if (ticket == requestToken && binding == bindingToken && !authBlocked) {
                mutable.value = mutable.value.copy(loading = false,
                    error = if (saved) mutable.value.error else R.string.error_details_save)
                resumeStreamAfterRead()
            }
        }
        return true
    }
    override fun loadPricing() { readPart("sync/settings/customPricing", PricingParser::parse) { state, value -> state.copy(pricing = value) } }
    override fun loadModelNames() { readPart("sync/settings/modelAliases", ModelNamesParser::parse) { state, value -> state.copy(modelNames = value) } }
    override fun loadDeviceHistory() = readDeviceHistory(0)
    private fun readDeviceHistory(retries: Int) {
        readPart("devices", DeviceHistoryParser::parse,
            onStale = { if (retries < 3) readDeviceHistory(retries + 1) }) { state, value -> state.copy(deviceHistories = value) }
    }
    override fun loadHistory() { loadHistory(manual = false) }
    override fun loadHistory(manual: Boolean): Boolean = readPart("history", HistoryParser::parse, historyManual = manual) { state, value ->
        state.copy(history = value, historyRenderRevision = state.historyRenderRevision + 1)
    }
    override fun loadSubscriptions() { readPart("subscriptions", SubscriptionParser::parse) { state, value -> state.copy(subscriptions = value) } }
    override fun forget() {
        if (mutable.value.loading) return
        cancelNetworkRecovery()
        mutable.value = mutable.value.copy(loading = true)
        stopStream()
        bindingToken++
        cacheSequence++
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { storageMutex.withLock { runCatching {
                store.clear()
            } } }
            if (result.isSuccess) {
                currentHub = null
                alternateHub = null
                credential = null
                endpointsAssociated = false;lanEnabled = true;lanRetryAfter = 0L;tlsBlocked = false
                streamEligible = false
                latestBody = null; historyBody = null; historySignature = null; deviceHistoryBody = null; subscriptionsBody = null; modelNamesBody = null; pricingBody = null
                historyReads.reset()
                mutable.value = HubState(connectionRevision = mutable.value.connectionRevision + 1)
            } else mutable.value = mutable.value.copy(loading = false, error = R.string.error_clear)
        }
    }
    fun refresh(address: String, secret: String, alternateAddress: String? = alternateHub,manual:Boolean=false, forceHistory: Boolean = false, allowFallback: Boolean = true) {
        cancelNetworkRecovery()
        refreshInScope(address, secret, alternateAddress, manual, forceHistory, allowFallback, null)
    }
    private fun refreshInScope(address: String, secret: String, alternateAddress: String?, manual: Boolean,
        forceHistory: Boolean, allowFallback: Boolean, recovery: NetworkRecovery?) {
        if (!foreground || mutable.value.loading || committingConnection) return
        if (recovery != null && (networkRecovery !== recovery || recovery.binding != bindingToken ||
                networkScope?.networkId != recovery.networkId)) return
        if (networkScope?.available == false) {
            mutable.value = mutable.value.copy(error = R.string.error_no_network)
            return
        }
        val base = try { HubAddress.normalize(address) } catch (_: Exception) {
            mutable.value = mutable.value.copy(error = R.string.error_address)
            return
        }
        if (secret.isBlank() || secret.any { it.code < 32 || it.code > 126 }) {
            mutable.value = mutable.value.copy(error = R.string.error_credential)
            return
        }
        val alternate = try { alternateAddress?.takeIf { it.isNotBlank() }?.let { HubAddress.normalize(it) } } catch (_: Exception) {
            mutable.value = mutable.value.copy(error = R.string.error_alternate_address)
            return
        }
        if (base.startsWith("http://") && !permitsHttp(base)) {
            if ((manual || endpointsAssociated) && alternate?.startsWith("https://") == true)
                refreshInScope(alternate, secret, base, manual, forceHistory, true, recovery)
            else mutable.value = mutable.value.copy(error = localConnectionMessage())
            return
        }
        if (alternate != null && (alternate == base || java.net.URI(alternate).scheme == java.net.URI(base).scheme)) {
            mutable.value = mutable.value.copy(error = R.string.error_dual_scheme)
            return
        }
        if (!manual && base.startsWith("https://") && networkScope?.ready == false) {
            // onAvailable revokes the old scope first; wait for this network's details.
            if (recovery == null) networkRecovery = NetworkRecovery(networkScope!!.networkId, bindingToken, forceHistory)
            return
        }
        if(manual) {replaceConnection(base,secret,alternate);return}
        if (currentHub != base || credential != secret) {
            bindingToken++
            val sameHub = credential == secret && (base == currentHub || (endpointsAssociated && base == alternateHub))
            if (!sameHub) {
                mutable.value = HubState(connectionRevision = mutable.value.connectionRevision + 1)
                latestBody = null; historyBody = null; historySignature = null; deviceHistoryBody = null; subscriptionsBody = null; modelNamesBody = null; pricingBody = null
            }
            currentHub = base
        }
        credential = secret
        if (recovery != null) recovery.binding = bindingToken
        streamEligible = false
        authBlocked = false
        alternateHub = alternate
        stopStream()
        val ticket = ++requestToken
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val build=http.verify(base) { foreground && ticket == requestToken }
                    if (ticket != requestToken) throw java.util.concurrent.CancellationException()
                    val projected = http.readStats(base, secret) { foreground && ticket == requestToken }
                    Triple(HubParser.parse(projected),projected.body,build)
                }
            }
            if (ticket != requestToken) return@launch
            if (result.isFailure && allowFallback) {
                val failure = HubConnectionPolicy.classify(result.exceptionOrNull()!!)
                if (base.startsWith("http://") && failure == HubConnectionPolicy.Failure.NETWORK)
                    lanRetryAfter = android.os.SystemClock.elapsedRealtime() + 60000L
                val next = HubConnectionPolicy.fallback(base, alternate, endpointsAssociated,
                    alternate?.let(::permitsHttp) == true, failure)
                if (next != null) {
                    mutable.value = mutable.value.copy(loading = false)
                    refreshInScope(next, secret, base, manual = false, forceHistory = forceHistory,
                        allowFallback = false, recovery = recovery)
                    finishStartupIfIdle()
                    return@launch
                }
            }
            mutable.value = result.fold(
                { (parsed, text, build) ->
                    streamEligible = true
                    historyReads.revalidated()
                    mutable.value=mutable.value.copy(hubBuild=build,lastSuccessfulEndpoint=base,connectedEndpoint=base)
                    acceptSnapshot(parsed, text, live = false, forceHistory = forceHistory)
                    mutable.value.copy(loading = false)
                },
                { error ->
                    if (error is HubReadException && error.unauthorized) authBlocked = true
                    if (HubConnectionPolicy.classify(error) == HubConnectionPolicy.Failure.TLS) tlsBlocked = true
                    mutable.value.copy(loading = false, error = connectionMessage(error))
                }
            )
            finishStartupIfIdle()
            if (result.isSuccess) {
                if (networkRecovery === recovery) cancelNetworkRecovery()
                if (!saveCurrent()) mutable.value = mutable.value.copy(error = R.string.error_data_save)
                if (ticket == requestToken) startStream()
            } else retryNetworkValidation(recovery, ticket, result.exceptionOrNull()!!)
        }
    }
    override fun onCleared() {
        setForeground(false)
        credential = null
        client.dispatcher.cancelAll()
        retireCurrentIdlePools()
    }
}
