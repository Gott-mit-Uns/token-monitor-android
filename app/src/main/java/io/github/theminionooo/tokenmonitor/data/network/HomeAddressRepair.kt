package io.github.theminionooo.tokenmonitor.data.network

import io.github.theminionooo.tokenmonitor.domain.HubConnection

/** Preparing a repair never changes the saved pairing or discloses its credential to UI state. */
internal fun prepareHomeAddressRepair(saved: HubConnection?, rawAddress: String): HubAddressValidation {
    if (saved == null) return HubAddressValidation.Rejected("Connect your Hub before repairing its home address.")
    val validated = HubAddressValidator.validate(rawAddress, saved.secret, true)
    if (validated !is HubAddressValidation.Allowed) return validated
    val address = validated.connection.baseUrl
    if (!HubAddressValidator.isLocalAddress(address)) {
        return HubAddressValidation.Rejected("Use the desktop's private home Wi-Fi address here.")
    }
    return HubAddressValidation.Allowed(saved.copy(fallbackUrl = address.takeIf { it != saved.baseUrl }))
}
