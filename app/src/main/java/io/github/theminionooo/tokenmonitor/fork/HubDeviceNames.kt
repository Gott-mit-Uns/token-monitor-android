package io.github.theminionooo.tokenmonitor.fork

import io.github.theminionooo.tokenmonitor.domain.HubSnapshot

/** Hub-uploaded device identity is authoritative; never apply historical Android aliases. */
internal fun hubNamedSnapshot(snapshot: HubSnapshot): HubSnapshot = snapshot.copy(
    stats = snapshot.stats.copy(devices = snapshot.stats.devices.map { device ->
        device.copy(hostname = device.id.ifBlank { device.hostname })
    }),
)
