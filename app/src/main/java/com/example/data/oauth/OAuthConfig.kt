package com.example.data.oauth

import com.example.data.AccountType

data class OAuthProviderSpec(
    val providerType: AccountType,
    val title: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val userInfoEndpoint: String,
    val defaultClientId: String,
    val defaultScopes: String,
    val redirectUri: String = "com.gmscx.services://oauth",
    val logoDrawableName: String,
    val primaryColorHex: String
)

object OAuthConfig {
    val GOOGLE_SPEC = OAuthProviderSpec(
        providerType = AccountType.GOOGLE,
        title = "Google Account",
        authorizationEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo",
        defaultClientId = "532392590860-a9vuak7plrq3n29dpisigr866fun88vs.apps.googleusercontent.com",
        defaultScopes = "openid email profile https://www.googleapis.com/auth/youtube",
        redirectUri = "https://localhost",
        logoDrawableName = "ic_google",
        primaryColorHex = "#4285F4"
    )

    val YANDEX_SPEC = OAuthProviderSpec(
        providerType = AccountType.YANDEX,
        title = "Yandex Account",
        authorizationEndpoint = "https://oauth.yandex.ru/authorize",
        tokenEndpoint = "https://oauth.yandex.ru/token",
        userInfoEndpoint = "https://login.yandex.ru/info",
        defaultClientId = "gmscx_yandex_client_id_default",
        defaultScopes = "login:email login:avatar login:info",
        redirectUri = "com.gmscx.services://oauth",
        logoDrawableName = "ic_yandex",
        primaryColorHex = "#FC3F1D"
    )

    fun getSpecForType(type: AccountType): OAuthProviderSpec = when (type) {
        AccountType.GOOGLE -> GOOGLE_SPEC
        AccountType.YANDEX -> YANDEX_SPEC
    }
}

data class UserProfileResult(
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val rawResponseJson: String
)

data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
    val tokenType: String,
    val scope: String?
)
