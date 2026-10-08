package io.github.theminionooo.tokenmonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.theminionooo.tokenmonitor.data.HubRepositoryPool
import io.github.theminionooo.tokenmonitor.widget.WidgetLiveService
import io.github.theminionooo.tokenmonitor.widget.WidgetUpdateCoordinator
import io.github.theminionooo.tokenmonitor.data.HubRepositoryState
import io.github.theminionooo.tokenmonitor.data.network.HubDiscovery
import io.github.theminionooo.tokenmonitor.data.network.LocalNetworkAccess
import io.github.theminionooo.tokenmonitor.data.network.ServiceStatusClient
import io.github.theminionooo.tokenmonitor.data.storage.DisplayPreferences
import io.github.theminionooo.tokenmonitor.data.storage.LimitBarMetric
import io.github.theminionooo.tokenmonitor.data.storage.RankingMetric
import io.github.theminionooo.tokenmonitor.data.storage.ReduceMotionMode
import io.github.theminionooo.tokenmonitor.data.storage.IconScale
import io.github.theminionooo.tokenmonitor.data.storage.TextScale
import io.github.theminionooo.tokenmonitor.domain.ServiceStatusSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

internal enum class DashboardDestination(val title: String) {
    Home("Home"),
    Tools("Tools"),
    Status("Status"),
    Devices("Devices"),
    Models("Models"),
    Projects("Projects"),
    Sessions("Sessions"),
    Limits("Limits"),
    Trends("Trends"),
    Settings("Settings"),
}

internal data class ConnectionFormState(
    val saving: Boolean = false,
    val result: String? = null,
)

internal data class ServiceStatusState(
    val loading: Boolean = false,
    val snapshot: ServiceStatusSnapshot? = null,
)

internal data class HubDiscoveryState(
    val searching: Boolean = false,
    val found: String? = null,
    val message: String? = null,
)

internal class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HubRepositoryPool.acquire(application)
    val rawExportSnapshot get() = repository.state.value.snapshot
    val originalDeviceNames: Map<String, String>
        get() = repository.state.value.snapshot?.stats?.devices?.associate { it.id to it.id.ifBlank { it.hostname } }.orEmpty()

    private val displayPreferences = DisplayPreferences(application)
    private val serviceStatusClient = ServiceStatusClient()

    private val desktopPreferences = io.github.theminionooo.tokenmonitor.data.storage.DesktopPreferences(application)
    val desktopOptions = desktopPreferences.options
    fun saveDesktopOptions(value: io.github.theminionooo.tokenmonitor.data.storage.DesktopOptions): Boolean {
        val saved = desktopPreferences.save(value)
        if (saved) viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) { WidgetUpdateCoordinator.refresh(getApplication()) }
        return saved
    }

    val hubState: StateFlow<HubRepositoryState> = combine(repository.state, desktopOptions) { state, options ->
        val url = state.connectionUrl
        val snapshot = state.snapshot
        if (url != null && snapshot != null) state.copy(snapshot = presentSnapshot(hubNamedSnapshot(snapshot), options)) else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.state.value.let { state ->
        val url = state.connectionUrl
        val snapshot = state.snapshot
        if (url != null && snapshot != null) state.copy(snapshot = hubNamedSnapshot(snapshot)) else state
    })
    private val _destination = MutableStateFlow(
        if (repository.state.value.hasConnection) DashboardDestination.Home else DashboardDestination.Settings,
    )
    val destination: StateFlow<DashboardDestination> = _destination.asStateFlow()
    private val _connectionForm = MutableStateFlow(ConnectionFormState())
    val connectionForm: StateFlow<ConnectionFormState> = _connectionForm.asStateFlow()
    val displayOptions = displayPreferences.options
    private val _serviceStatus = MutableStateFlow(ServiceStatusState())
    val serviceStatus: StateFlow<ServiceStatusState> = _serviceStatus.asStateFlow()
    private val hubDiscovery = HubDiscovery()
    private val _discovery = MutableStateFlow(HubDiscoveryState())
    val discovery: StateFlow<HubDiscoveryState> = _discovery.asStateFlow()
    private var discoveryJob: Job? = null

    private var resumed = false
    private var serviceStatusJob: Job? = null
    private var settingsReturnTo = DashboardDestination.Home

    fun onResume() {
        resumed = true
        if (LocalNetworkAccess.granted(getApplication())) {
            if (_connectionForm.value.result == LocalNetworkAccess.deniedMessage) {
                _connectionForm.value = _connectionForm.value.copy(result = null)
            }
            if (_discovery.value.message == LocalNetworkAccess.deniedMessage) {
                _discovery.value = _discovery.value.copy(message = null)
            }
        }
        updateForegroundWork()
    }

    fun onPause() {
        resumed = false
        updateForegroundWork()
    }

    fun choose(destination: DashboardDestination) {
        val current = _destination.value
        if (destination == DashboardDestination.Settings && current != DashboardDestination.Settings) settingsReturnTo = current
        _destination.value = destination
        _connectionForm.value = _connectionForm.value.copy(result = null)
        updateForegroundWork()
    }

    /**
     * System Back mirrors the desktop window: Settings returns to the view that
     * opened it, any other view returns Home, and Home lets Android leave the app.
     * Returns false when the app should not intercept the gesture.
     */
    fun navigateBack(): Boolean {
        val current = _destination.value
        return when {
            !hubState.value.hasConnection -> false
            current == DashboardDestination.Settings -> {
                choose(settingsReturnTo)
                true
            }
            current != DashboardDestination.Home -> {
                choose(DashboardDestination.Home)
                true
            }
            else -> false
        }
    }

    fun refresh() {
        repository.refreshNow()
        if (_destination.value == DashboardDestination.Status) refreshServiceStatus(force = true)
    }

    fun setColorfulToolMarks(enabled: Boolean) = displayPreferences.setColorfulToolMarks(enabled)
    fun setCompactTokenTotal(enabled: Boolean) = displayPreferences.setCompactTokenTotal(enabled)
    fun setReduceMotion(mode: ReduceMotionMode) = displayPreferences.setReduceMotion(mode)
    fun setIconScale(scale: IconScale) = displayPreferences.setIconScale(scale)
    fun setHomeChineseUnits(enabled: Boolean) = displayPreferences.setHomeChineseUnits(enabled)
    fun setTextScale(scale: TextScale) = displayPreferences.setTextScale(scale)
    fun setFollowSystemTheme(enabled: Boolean) {
        displayPreferences.setFollowSystemTheme(enabled)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            WidgetUpdateCoordinator.refresh(getApplication())
        }
    }
    fun setThemeCode(code: String?) {
        displayPreferences.setThemeCode(code)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            WidgetUpdateCoordinator.refresh(getApplication())
        }
    }
    fun setShowLiveIndicator(enabled: Boolean) = displayPreferences.setShowLiveIndicator(enabled)
    fun setShowToolIcons(enabled: Boolean) = displayPreferences.setShowToolIcons(enabled)
    fun setRankingMetric(metric: RankingMetric) = displayPreferences.setRankingMetric(metric)
    fun setShowLimitSource(enabled: Boolean) = displayPreferences.setShowLimitSource(enabled)
    fun setShowAccountEmails(enabled: Boolean) = displayPreferences.setShowAccountEmails(enabled)
    fun setShowSessionTitles(enabled: Boolean) = displayPreferences.setShowSessionTitles(enabled)
    fun setLimitBarMetric(metric: LimitBarMetric) = displayPreferences.setLimitBarMetric(metric)
    fun setDefaultPeriod(period: String) = displayPreferences.setDefaultPeriod(period)
    fun setViewVisible(view: String, visible: Boolean) = displayPreferences.setViewVisible(view, visible)
    fun setHomeModuleVisible(module: String, visible: Boolean) = displayPreferences.setHomeModuleVisible(module, visible)
    fun moveView(view: String, offset: Int) = displayPreferences.moveView(view, offset)
    fun moveHomeModule(module: String, offset: Int) = displayPreferences.moveHomeModule(module, offset)

    fun saveConnection(url: String, fallbackUrl: String, secret: String, allowLocalNetwork: Boolean) {
        checkConnection(returnHome = true) { repository.validateAndSave(url, fallbackUrl, secret, allowLocalNetwork) }
    }

    fun repairHomeAddress(address: String) {
        checkConnection(returnHome = false) { repository.repairHomeAddress(address) }
    }

    fun localNetworkPermissionDenied() {
        _connectionForm.value = ConnectionFormState(result = LocalNetworkAccess.deniedMessage)
        _discovery.value = HubDiscoveryState(message = LocalNetworkAccess.deniedMessage)
    }

    private fun checkConnection(returnHome: Boolean, operation: suspend () -> String?) {
        if (_connectionForm.value.saving) return
        viewModelScope.launch {
            _connectionForm.value = ConnectionFormState(saving = true)
            try {
                WidgetLiveService.stop(getApplication())
                repository.setWidgetActive(false)
                val error = operation()
                _connectionForm.value = ConnectionFormState(result = error ?: "Connected. The home address is verified and saved.")
                if (error == null) {
                    _discovery.value = HubDiscoveryState()
                    if (returnHome) choose(DashboardDestination.Home)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _connectionForm.value = ConnectionFormState(result = "Connection check paused. Try again.")
                throw cancelled
            } finally {
                _connectionForm.value = _connectionForm.value.copy(saving = false)
            }
        }
    }

    /** Looks for a Hub on the current Wi-Fi and reports the address so the form can fill it in. */
    fun findHomeHub() {
        if (!LocalNetworkAccess.granted(getApplication())) {
            localNetworkPermissionDenied()
            return
        }
        if (discoveryJob?.isActive == true) return
        _discovery.value = HubDiscoveryState(searching = true)
        discoveryJob = viewModelScope.launch {
            val found = runCatching { hubDiscovery.findOnLocalNetwork() }.getOrNull()
            _discovery.value = HubDiscoveryState(
                found = found,
                message = if (found != null) "Found a Hub. Test and save this address to use it."
                else "No Hub answered on this Wi-Fi. Check that the desktop app is running with Host Hub on, then try again.",
            )
        }
    }

    fun disconnect() {
        WidgetLiveService.stop(getApplication())
        repository.setWidgetActive(false)
        repository.disconnect()
        choose(DashboardDestination.Settings)
        _connectionForm.value = ConnectionFormState(result = "The saved Hub connection was removed from this phone.")
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        serviceStatusJob?.cancel()
        repository.setDashboardVisible(false)
        HubRepositoryPool.release(repository)
    }

    private fun updateForegroundWork() {
        repository.setDashboardVisible(resumed && _destination.value != DashboardDestination.Settings)
        if (resumed && _destination.value == DashboardDestination.Status) {
            refreshServiceStatus()
        } else {
            serviceStatusJob?.cancel()
            serviceStatusJob = null
            _serviceStatus.value = _serviceStatus.value.copy(loading = false)
        }
    }

    private fun refreshServiceStatus(force: Boolean = false) {
        if (!resumed || _destination.value != DashboardDestination.Status || serviceStatusJob?.isActive == true) return
        val current = _serviceStatus.value.snapshot
        if (!force && current != null && System.currentTimeMillis() - current.checkedAt < serviceStatusCacheMs) return
        _serviceStatus.value = _serviceStatus.value.copy(loading = true)
        serviceStatusJob = viewModelScope.launch {
            val snapshot = runCatching { serviceStatusClient.load() }.getOrNull()
            if (resumed && _destination.value == DashboardDestination.Status) {
                _serviceStatus.value = ServiceStatusState(loading = false, snapshot = snapshot ?: current)
            }
        }
    }

    private companion object {
        const val serviceStatusCacheMs = 60_000L
    }
}
