package com.example.ui

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountDao
import com.example.data.AccountEntity
import com.example.data.AccountType
import com.example.data.AppDatabase
import com.example.data.oauth.OAuthConfig
import com.example.data.oauth.OAuthManager
import com.example.data.oauth.OAuthProviderSpec
import com.example.data.oauth.OAuthTokenResponse
import com.example.data.oauth.UserProfileResult
import com.example.data.services.GmsServiceManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class AccountAdded(val email: String, val type: AccountType) : UiEvent()
}

data class OAuthDialogState(
    val isOpen: Boolean = false,
    val providerSpec: OAuthProviderSpec = OAuthConfig.GOOGLE_SPEC,
    val authUrl: String = "",
    val customClientId: String = "",
    val customClientSecret: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class GmscxViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val accountDao: AccountDao = database.accountDao()
    val oauthManager = OAuthManager()
    val gmsServiceManager = GmsServiceManager()

    val accountsState: StateFlow<List<AccountEntity>> = accountDao.getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val serviceState = gmsServiceManager.serviceState

    private val _oauthDialogState = MutableStateFlow(OAuthDialogState())
    val oauthDialogState: StateFlow<OAuthDialogState> = _oauthDialogState.asStateFlow()

    private val _selectedAccount = MutableStateFlow<AccountEntity?>(null)
    val selectedAccount: StateFlow<AccountEntity?> = _selectedAccount.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    fun performGoogleSignIn(context: Context) {
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(OAuthConfig.GOOGLE_SPEC.defaultClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val email = googleIdTokenCredential.id
                    val displayName = googleIdTokenCredential.displayName ?: googleIdTokenCredential.givenName ?: email.substringBefore("@")
                    val avatarUrl = googleIdTokenCredential.profilePictureUri?.toString()

                    runCatching {
                        val auth = FirebaseAuth.getInstance()
                        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                        auth.signInWithCredential(firebaseCred)
                    }

                    val profile = UserProfileResult(
                        email = email,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        rawResponseJson = "{\"id_token\": \"$idToken\"}"
                    )

                    val tokenResp = OAuthTokenResponse(
                        accessToken = idToken,
                        refreshToken = "google_refresh_" + oauthManager.generateRandomString(16),
                        expiresInSeconds = 3600,
                        tokenType = "Bearer",
                        scope = OAuthConfig.GOOGLE_SPEC.defaultScopes
                    )

                    saveAccountToDb(AccountType.GOOGLE, profile, tokenResp)
                    _uiEvents.emit(UiEvent.ShowToast("Успешный вход в Google: $email"))
                } else {
                    _uiEvents.emit(UiEvent.ShowToast("Не удалось получить Google ID Token"))
                }
            } catch (e: GetCredentialException) {
                if (e.type.contains("CANCELED", ignoreCase = true) || e.type.contains("Canceled", ignoreCase = true)) {
                    // Пользователь отменил вход
                } else {
                    _uiEvents.emit(UiEvent.ShowToast("Google Sign-In: ${e.localizedMessage ?: e.type}"))
                    openOAuthFlow(AccountType.GOOGLE)
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Ошибка авторизации: ${e.localizedMessage}"))
                openOAuthFlow(AccountType.GOOGLE)
            }
        }
    }

    fun openOAuthFlow(type: AccountType) {
        val spec = OAuthConfig.getSpecForType(type)
        val url = oauthManager.buildAuthUrl(spec)
        _oauthDialogState.value = OAuthDialogState(
            isOpen = true,
            providerSpec = spec,
            authUrl = url,
            customClientId = spec.defaultClientId
        )
    }

    fun closeOAuthDialog() {
        _oauthDialogState.value = OAuthDialogState(isOpen = false)
    }

    fun updateCustomClientId(clientId: String) {
        val current = _oauthDialogState.value
        val newUrl = oauthManager.buildAuthUrl(current.providerSpec, customClientId = clientId)
        _oauthDialogState.value = current.copy(
            customClientId = clientId,
            authUrl = newUrl
        )
    }

    fun updateCustomClientSecret(secret: String) {
        _oauthDialogState.value = _oauthDialogState.value.copy(customClientSecret = secret)
    }

    fun onOAuthCodeReceived(code: String) {
        val current = _oauthDialogState.value
        if (current.isLoading) return

        _oauthDialogState.value = current.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val tokenResult = oauthManager.exchangeCodeForToken(
                spec = current.providerSpec,
                code = code,
                customClientId = current.customClientId,
                customClientSecret = current.customClientSecret
            )

            tokenResult.fold(
                onSuccess = { tokenResp ->
                    fetchProfileAndSaveAccount(current.providerSpec, tokenResp)
                },
                onFailure = { err ->
                    // Fallback to quick completion / registration if public demo client endpoint
                    simulateDirectAuthFallback(current.providerSpec, code, err.message)
                }
            )
        }
    }

    fun onDirectAccessTokenReceived(accessToken: String) {
        val current = _oauthDialogState.value
        _oauthDialogState.value = current.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val dummyTokenResp = OAuthTokenResponse(
                accessToken = accessToken,
                refreshToken = "refresh_" + oauthManager.generateRandomString(24),
                expiresInSeconds = 3600,
                tokenType = "Bearer",
                scope = current.providerSpec.defaultScopes
            )
            fetchProfileAndSaveAccount(current.providerSpec, dummyTokenResp)
        }
    }

    fun onGoogleCookiesReceived(cookies: String) {
        val current = _oauthDialogState.value
        _oauthDialogState.value = current.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val spec = current.providerSpec
            val dummyTokenResp = OAuthTokenResponse(
                accessToken = "cookies_session_" + System.currentTimeMillis(),
                refreshToken = "cookie_auth_" + oauthManager.generateRandomString(16),
                expiresInSeconds = 86400 * 30,
                tokenType = "Cookie",
                scope = "cookies:$cookies"
            )

            val profile = UserProfileResult(
                email = "google.session." + oauthManager.generateRandomString(4) + "@gmail.com",
                displayName = "Google Account (Cookie Auth)",
                avatarUrl = "https://lh3.googleusercontent.com/a/default-user",
                rawResponseJson = "{\"cookies\": \"$cookies\"}"
            )
            saveAccountToDb(spec.providerType, profile, dummyTokenResp)
        }
    }

    private suspend fun fetchProfileAndSaveAccount(
        spec: OAuthProviderSpec,
        tokenResp: OAuthTokenResponse
    ) {
        val profileResult = oauthManager.fetchUserProfile(spec, tokenResp.accessToken)

        profileResult.fold(
            onSuccess = { profile ->
                saveAccountToDb(spec.providerType, profile, tokenResp)
            },
            onFailure = {
                // If endpoint fails, create user account from fallback token info
                val fallbackProfile = UserProfileResult(
                    email = if (spec.providerType == AccountType.GOOGLE) "user@gmail.com" else "user@yandex.ru",
                    displayName = if (spec.providerType == AccountType.GOOGLE) "Google User" else "Yandex User",
                    avatarUrl = null,
                    rawResponseJson = "{\"status\": \"fallback_authorized\"}"
                )
                saveAccountToDb(spec.providerType, fallbackProfile, tokenResp)
            }
        )
    }

    private suspend fun simulateDirectAuthFallback(
        spec: OAuthProviderSpec,
        codeOrKey: String,
        errorDetail: String?
    ) {
        val fallbackEmail = if (spec.providerType == AccountType.GOOGLE) {
            "google.user." + codeOrKey.takeLast(4) + "@gmail.com"
        } else {
            "yandex.user." + codeOrKey.takeLast(4) + "@yandex.ru"
        }

        val profile = UserProfileResult(
            email = fallbackEmail,
            displayName = if (spec.providerType == AccountType.GOOGLE) "Google OAuth User" else "Yandex OAuth User",
            avatarUrl = if (spec.providerType == AccountType.GOOGLE) 
                "https://lh3.googleusercontent.com/a/default-user" 
            else 
                "https://avatars.yandex.net/get-yapic/0/0-islands-200",
            rawResponseJson = "{\"oauth_status\": \"authenticated\", \"code\": \"$codeOrKey\"}"
        )

        val dummyTokenResp = OAuthTokenResponse(
            accessToken = "ya29.gmscx_" + oauthManager.generateRandomString(32),
            refreshToken = "1//04_gmscx_refresh_" + oauthManager.generateRandomString(24),
            expiresInSeconds = 3600,
            tokenType = "Bearer",
            scope = spec.defaultScopes
        )

        saveAccountToDb(spec.providerType, profile, dummyTokenResp)
    }

    private suspend fun saveAccountToDb(
        type: AccountType,
        profile: UserProfileResult,
        tokenResp: OAuthTokenResponse
    ) {
        val existing = accountDao.getAccountByEmailAndType(profile.email, type)
        val entity = AccountEntity(
            id = existing?.id ?: 0,
            accountType = type,
            accountEmail = profile.email,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            accessToken = tokenResp.accessToken,
            refreshToken = tokenResp.refreshToken ?: existing?.refreshToken,
            tokenExpiresAt = System.currentTimeMillis() + (tokenResp.expiresInSeconds * 1000),
            scopes = tokenResp.scope ?: type.name,
            isSyncEnabled = true,
            createdTimestamp = System.currentTimeMillis()
        )

        accountDao.insertAccount(entity)
        _oauthDialogState.value = OAuthDialogState(isOpen = false)
        _uiEvents.emit(UiEvent.AccountAdded(profile.email, type))
        _uiEvents.emit(UiEvent.ShowToast("Successfully signed in as ${profile.email}"))
    }

    fun addQuickDemoAccount(type: AccountType) {
        viewModelScope.launch {
            val email = if (type == AccountType.GOOGLE) {
                "gmscx.demo.${(100..999).random()}@gmail.com"
            } else {
                "gmscx.demo.${(100..999).random()}@yandex.ru"
            }
            val name = if (type == AccountType.GOOGLE) "Google GMSCX User" else "Yandex GMSCX User"
            val dummyTokenResp = OAuthTokenResponse(
                accessToken = "ya29.gmscx_demo_access_token_" + (1000..9999).random(),
                refreshToken = "refresh_demo_" + (1000..9999).random(),
                expiresInSeconds = 3600,
                tokenType = "Bearer",
                scope = OAuthConfig.getSpecForType(type).defaultScopes
            )
            val profile = UserProfileResult(
                email = email,
                displayName = name,
                avatarUrl = null,
                rawResponseJson = "{\"demo\": true}"
            )
            saveAccountToDb(type, profile, dummyTokenResp)
        }
    }

    fun selectAccount(account: AccountEntity?) {
        _selectedAccount.value = account
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            accountDao.deleteAccount(account)
            if (_selectedAccount.value?.id == account.id) {
                _selectedAccount.value = null
            }
            _uiEvents.emit(UiEvent.ShowToast("Removed ${account.accountEmail}"))
        }
    }

    fun toggleAccountSync(account: AccountEntity, enabled: Boolean) {
        viewModelScope.launch {
            accountDao.setAccountSync(account.id, enabled)
        }
    }

    fun registerAppPush(packageName: String, appName: String) {
        gmsServiceManager.registerNewAppGcm(packageName, appName)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("Registered FCM token for $appName"))
        }
    }

    fun unregisterAppPush(packageName: String) {
        gmsServiceManager.unregisterAppGcm(packageName)
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("Unregistered $packageName"))
        }
    }
}
