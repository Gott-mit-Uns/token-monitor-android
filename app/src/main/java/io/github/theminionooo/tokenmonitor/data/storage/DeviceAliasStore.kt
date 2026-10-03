package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import android.content.SharedPreferences
import io.github.theminionooo.tokenmonitor.domain.DeviceUsage
import io.github.theminionooo.tokenmonitor.domain.HubSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceAliasStore(context: Context) {
    private val preferences = context.getSharedPreferences("device_aliases", Context.MODE_PRIVATE)
    private fun read() = preferences.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    private val state = MutableStateFlow(read())
    val aliases = state.asStateFlow()
    // Preferences are shared across store instances in one process.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.value = read() }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }
    fun close() = preferences.unregisterOnSharedPreferenceChangeListener(listener)
    fun alias(hubUrl: String, deviceId: String): String? = preferences.getString(DeviceAliasRules.key(hubUrl, deviceId), null)
    fun save(hubUrl: String, deviceId: String, input: String): String? {
        val value = try { DeviceAliasRules.normalize(input) } catch (error: IllegalArgumentException) { return error.message }
        val key = DeviceAliasRules.key(hubUrl, deviceId)
        val editor = preferences.edit()
        if (value.isEmpty()) editor.remove(key) else editor.putString(key, value)
        if (!editor.commit()) return "Could not save the device name. Try again."
        state.value = read()
        return null
    }
    fun displaySnapshot(hubUrl: String, snapshot: HubSnapshot): HubSnapshot =
        snapshot.copy(stats = snapshot.stats.copy(devices = displayDevices(hubUrl, snapshot.stats.devices)))
    fun displayDevices(hubUrl: String, devices: List<DeviceUsage>) = devices.map { device ->
        alias(hubUrl, device.id)?.let { device.copy(hostname = it) } ?: device
    }
}
