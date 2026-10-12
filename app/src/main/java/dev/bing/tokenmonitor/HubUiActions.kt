package dev.bing.tokenmonitor

/** Screen actions are separate from credentials/storage so the UI can be verified with synthetic data. */
interface HubUiActions {
    fun connect(address: String, credential: String, alternate: String)
    /** URL-only draft. Saved credentials stay inside the model/store. */
    fun connectionEndpoints(): List<String> = emptyList()
    /** Null keeps the saved credential; a non-null value explicitly replaces it. */
    fun editConnection(address: String, alternate: String, replacementCredential: String?) {
        connect(address, replacementCredential.orEmpty(), alternate)
    }
    fun dismissConnectionFailure() {}
    fun lanEnabled(): Boolean = true
    fun endpointsAssociated(): Boolean = false
    fun configureLan(enabled: Boolean, confirmAssociation: Boolean = false) {}
    fun refreshSaved()
    /** Home refresh queues a new history cycle after the stats read succeeds. */
    fun refreshSaved(forceHistory: Boolean) { refreshSaved() }
    fun switchEndpoint()
    fun hasAlternate(): Boolean
    fun endpointType(): String
    fun loadPricing() {}
    fun loadModelNames()
    fun loadDeviceHistory() {}
    fun loadHistory()
    /** True only when the model accepts a real history network start. */
    fun loadHistory(manual: Boolean): Boolean { loadHistory(); return true }
    fun loadSubscriptions()
    fun forget()
    fun renameDevice(id: String, input: String)
}
