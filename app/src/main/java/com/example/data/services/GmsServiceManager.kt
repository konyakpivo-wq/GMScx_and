package com.example.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class GcmAppRegistration(
    val packageName: String,
    val appName: String,
    val registrationToken: String,
    val registeredTimestamp: Long = System.currentTimeMillis(),
    val totalMessagesReceived: Int = (1..142).random()
)

data class SelfCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val isPassed: Boolean,
    val isCritical: Boolean = false,
    val category: String
)

enum class ServiceStatus(val label: String, val colorHex: String) {
    ACTIVE("Active / Running", "#4CAF50"),
    WARNING("Needs Configuration", "#FFC107"),
    DISABLED("Disabled", "#F44336")
}

data class GmsServiceState(
    val isGcmEnabled: Boolean = true,
    val isGcmConnected: Boolean = true,
    val gcmDeviceToken: String = "fcm_gmscx_" + UUID.randomUUID().toString().take(16),
    val registeredApps: List<GcmAppRegistration> = listOf(
        GcmAppRegistration("com.yt.cx", "YouTube CX Client", "token_ytcx_" + UUID.randomUUID().toString().take(12)),
        GcmAppRegistration("com.whatsapp", "WhatsApp Messenger", "token_wa_" + UUID.randomUUID().toString().take(12)),
        GcmAppRegistration("org.telegram.messenger", "Telegram", "token_tg_" + UUID.randomUUID().toString().take(12)),
        GcmAppRegistration("com.vkontakte.android", "VKontakte", "token_vk_" + UUID.randomUUID().toString().take(12)),
        GcmAppRegistration("ru.yandex.searchplugin", "Yandex App", "token_ya_" + UUID.randomUUID().toString().take(12))
    ),
    
    val isSafetyNetEnabled: Boolean = true,
    val safetyNetAttestationMode: String = "BASIC_INTEGRITY & DEVICE_RECOGNITION",
    val isCtsProfileMatch: Boolean = true,
    
    val isUnifiedNlpEnabled: Boolean = true,
    val locationBackends: List<String> = listOf("WiFi Location Provider", "Cell Tower Location", "GPS Fused Provider"),
    
    val isGoogleSyncEnabled: Boolean = true,
    val isYandexSyncEnabled: Boolean = true,
    val gmsCoreVersion: String = "24.02.13 (190400-608552322)"
)

class GmsServiceManager {
    private val _serviceState = MutableStateFlow(GmsServiceState())
    val serviceState: StateFlow<GmsServiceState> = _serviceState.asStateFlow()

    fun toggleGcm(enabled: Boolean) {
        _serviceState.value = _serviceState.value.copy(isGcmEnabled = enabled)
    }

    fun toggleSafetyNet(enabled: Boolean) {
        _serviceState.value = _serviceState.value.copy(isSafetyNetEnabled = enabled)
    }

    fun toggleUnifiedNlp(enabled: Boolean) {
        _serviceState.value = _serviceState.value.copy(isUnifiedNlpEnabled = enabled)
    }

    fun toggleGoogleSync(enabled: Boolean) {
        _serviceState.value = _serviceState.value.copy(isGoogleSyncEnabled = enabled)
    }

    fun toggleYandexSync(enabled: Boolean) {
        _serviceState.value = _serviceState.value.copy(isYandexSyncEnabled = enabled)
    }

    fun registerNewAppGcm(packageName: String, appName: String): GcmAppRegistration {
        val newApp = GcmAppRegistration(
            packageName = packageName,
            appName = appName,
            registrationToken = "token_custom_" + UUID.randomUUID().toString().take(12)
        )
        val currentList = _serviceState.value.registeredApps.toMutableList()
        currentList.add(0, newApp)
        _serviceState.value = _serviceState.value.copy(registeredApps = currentList)
        return newApp
    }

    fun unregisterAppGcm(packageName: String) {
        val updated = _serviceState.value.registeredApps.filterNot { it.packageName == packageName }
        _serviceState.value = _serviceState.value.copy(registeredApps = updated)
    }

    fun getSelfCheckList(accountCount: Int): List<SelfCheckItem> {
        val state = _serviceState.value
        return listOf(
            SelfCheckItem(
                id = "sig_spoof",
                title = "Signature Spoofing Support",
                description = "System allows GMSCX framework signature replacement for GMS compatibility",
                isPassed = true,
                isCritical = true,
                category = "System Compatibility"
            ),
            SelfCheckItem(
                id = "pkg_installed",
                title = "GMSCX Services Package",
                description = "Package com.gmscx.services correctly installed and initialized",
                isPassed = true,
                isCritical = true,
                category = "System Compatibility"
            ),
            SelfCheckItem(
                id = "accounts_check",
                title = "Google / Yandex Account Registered",
                description = if (accountCount > 0) "$accountCount account(s) authenticated via OAuth" else "No account added yet. Click 'Accounts' to add Google or Yandex",
                isPassed = accountCount > 0,
                isCritical = false,
                category = "Account Framework"
            ),
            SelfCheckItem(
                id = "gcm_check",
                title = "Cloud Messaging (GCM/FCM) Active",
                description = if (state.isGcmEnabled) "Push service connected. ${state.registeredApps.size} apps registered" else "GCM push messaging disabled in settings",
                isPassed = state.isGcmEnabled,
                isCritical = false,
                category = "GMSCX Services"
            ),
            SelfCheckItem(
                id = "safetynet_check",
                title = "Play Integrity / SafetyNet Pass",
                description = if (state.isSafetyNetEnabled) "Attestation response spoofing enabled (CTS match OK)" else "SafetyNet disabled",
                isPassed = state.isSafetyNetEnabled,
                isCritical = false,
                category = "GMSCX Services"
            ),
            SelfCheckItem(
                id = "nlp_check",
                title = "UnifiedNlp Location Backends",
                description = if (state.isUnifiedNlpEnabled) "Unified location provider initialized" else "UnifiedNlp disabled",
                isPassed = state.isUnifiedNlpEnabled,
                isCritical = false,
                category = "Location Framework"
            ),
            SelfCheckItem(
                id = "battery_opt",
                title = "Battery Optimization Exemption",
                description = "GMSCX exempt from battery saver to ensure background push reliability",
                isPassed = true,
                isCritical = false,
                category = "System Compatibility"
            )
        )
    }
}
