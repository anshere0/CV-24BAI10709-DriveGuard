package com.driveguard.mobile.settings.data

data class BackendSettings(
    val backendUrl: String = BackendSettingsRepository.DEFAULT_BACKEND_URL,
    val edgeDeviceId: String = "",
)
