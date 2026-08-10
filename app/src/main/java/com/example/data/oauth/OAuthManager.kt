package com.example.data.oauth

import android.net.Uri
import android.util.Base64
import com.example.data.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class OAuthManager {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var currentCodeVerifier: String = ""
    private var currentState: String = ""

    fun generateRandomString(length: Int = 32): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun buildAuthUrl(
        spec: OAuthProviderSpec,
        customClientId: String? = null,
        customScope: String? = null,
        forcePrompt: Boolean = true
    ): String {
        val clientId = customClientId?.takeIf { it.isNotBlank() } ?: spec.defaultClientId
        val scope = customScope?.takeIf { it.isNotBlank() } ?: spec.defaultScopes

        currentCodeVerifier = generateRandomString(48)
        val codeChallenge = generateCodeChallenge(currentCodeVerifier)
        currentState = generateRandomString(16)

        val uriBuilder = Uri.parse(spec.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", spec.redirectUri)
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("state", currentState)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")

        if (spec.providerType == AccountType.GOOGLE && forcePrompt) {
            uriBuilder.appendQueryParameter("prompt", "select_account consent")
            uriBuilder.appendQueryParameter("access_type", "offline")
        } else if (spec.providerType == AccountType.YANDEX && forcePrompt) {
            uriBuilder.appendQueryParameter("force_confirm", "yes")
        }

        return uriBuilder.build().toString()
    }

    suspend fun exchangeCodeForToken(
        spec: OAuthProviderSpec,
        code: String,
        customClientId: String? = null,
        customClientSecret: String? = null
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        try {
            val clientId = customClientId?.takeIf { it.isNotBlank() } ?: spec.defaultClientId
            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", spec.redirectUri)
                .add("client_id", clientId)
                .add("code_verifier", currentCodeVerifier)

            if (!customClientSecret.isNullOrBlank()) {
                formBuilder.add("client_secret", customClientSecret)
            }

            val request = Request.Builder()
                .url(spec.tokenEndpoint)
                .post(formBuilder.build())
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: $bodyStr")
                )
            }

            val json = JSONObject(bodyStr)
            val accessToken = json.optString("access_token")
            if (accessToken.isBlank()) {
                return@withContext Result.failure(Exception("Missing access_token in response: $bodyStr"))
            }

            val refreshToken = json.optString("refresh_token", null)
            val expiresIn = json.optLong("expires_in", 3600)
            val tokenType = json.optString("token_type", "Bearer")
            val scope = json.optString("scope", null)

            Result.success(
                OAuthTokenResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresIn,
                    tokenType = tokenType,
                    scope = scope
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(
        spec: OAuthProviderSpec,
        accessToken: String
    ): Result<UserProfileResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(spec.userInfoEndpoint)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
            }

            val json = JSONObject(bodyStr)
            var email = ""
            var name = ""
            var avatarUrl: String? = null

            when (spec.providerType) {
                AccountType.GOOGLE -> {
                    email = json.optString("email")
                    name = json.optString("name", json.optString("given_name", email.substringBefore("@")))
                    avatarUrl = json.optString("picture", null)
                }
                AccountType.YANDEX -> {
                    email = json.optString("default_email", json.optString("email"))
                    if (email.isBlank()) {
                        val login = json.optString("login")
                        email = if (login.isNotBlank()) "$login@yandex.ru" else "user@yandex.ru"
                    }
                    val firstName = json.optString("first_name")
                    val lastName = json.optString("last_name")
                    name = if (firstName.isNotBlank()) "$firstName $lastName".trim() else json.optString("real_name", json.optString("display_name", email))
                    val avatarId = json.optString("default_avatar_id", null)
                    if (!avatarId.isNullOrBlank()) {
                        avatarUrl = "https://avatars.yandex.net/get-yapic/$avatarId/islands-200"
                    }
                }
            }

            if (email.isBlank()) {
                email = "user_${System.currentTimeMillis()}@${spec.providerType.name.lowercase()}.com"
            }
            if (name.isBlank()) {
                name = email.substringBefore("@")
            }

            Result.success(
                UserProfileResult(
                    email = email,
                    displayName = name,
                    avatarUrl = avatarUrl,
                    rawResponseJson = bodyStr
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
