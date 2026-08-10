package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AccountType
import com.example.ui.OAuthDialogState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthAuthDialog(
    state: OAuthDialogState,
    onClose: () -> Unit,
    onCodeReceived: (String) -> Unit,
    onDirectAccessTokenReceived: (String) -> Unit,
    onUpdateClientId: (String) -> Unit,
    onUpdateClientSecret: (String) -> Unit,
    onQuickDemoAuth: (AccountType) -> Unit
) {
    if (!state.isOpen) return

    var selectedTab by remember { mutableIntStateOf(0) } // 0: OAuth Web, 1: Developer Settings, 2: Manual / Quick Auth
    var manualTokenText by remember { mutableStateOf("") }
    var webViewLoading by remember { mutableStateOf(true) }
    var currentWebUrl by remember { mutableStateOf(state.authUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val brandColor = if (state.providerSpec.providerType == AccountType.GOOGLE) {
        Color(0xFF4285F4)
    } else {
        Color(0xFFFC3F1D)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog Header
                Surface(
                    color = brandColor,
                    contentColor = Color.White
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "OAuth 2.0 Authorization",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.8f))
                                )
                                Text(
                                    text = "Sign in to ${state.providerSpec.title}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.testTag("oauth_dialog_close")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tab selector
                        SecondaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("OAuth Web", color = Color.White) },
                                icon = { Icon(Icons.Default.Language, contentDescription = null, tint = Color.White) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Config & Keys", color = Color.White) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Quick / Token", color = Color.White) },
                                icon = { Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.White) }
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = brandColor)
                }

                if (state.errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Tab Contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> {
                            // OAuth WebView Tab
                            Column(modifier = Modifier.fillMaxSize()) {
                                // URL Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentWebUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { webViewRef?.reload() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reload",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (webViewLoading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("oauth_webview"),
                                    factory = { context ->
                                        WebView(context).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.userAgentString =
                                                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                                            webViewClient = object : WebViewClient() {
                                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                    super.onPageStarted(view, url, favicon)
                                                    webViewLoading = true
                                                    url?.let { currentWebUrl = it }
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    webViewLoading = false
                                                }

                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val url = request?.url?.toString() ?: return false
                                                    currentWebUrl = url

                                                    // Check for OAuth redirect URI match
                                                    if (url.startsWith(state.providerSpec.redirectUri) ||
                                                        url.contains("code=") ||
                                                        url.contains("access_token=")
                                                    ) {
                                                        val uri = Uri.parse(url)
                                                        val code = uri.getQueryParameter("code")
                                                        val token = uri.getQueryParameter("access_token")

                                                        if (!code.isNullOrBlank()) {
                                                            onCodeReceived(code)
                                                            return true
                                                        } else if (!token.isNullOrBlank()) {
                                                            onDirectAccessTokenReceived(token)
                                                            return true
                                                        }
                                                    }
                                                    return false
                                                }
                                            }
                                            webViewRef = this
                                            loadUrl(state.authUrl)
                                        }
                                    },
                                    update = { view ->
                                        if (view.url != state.authUrl && currentWebUrl.isEmpty()) {
                                            view.loadUrl(state.authUrl)
                                        }
                                    }
                                )
                            }
                        }

                        1 -> {
                            // Developer Settings Tab
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "OAuth 2.0 Credentials Config",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Configure Client ID and Secret for ${state.providerSpec.title} API.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = state.customClientId,
                                    onValueChange = { onUpdateClientId(it) },
                                    label = { Text("Client ID") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("client_id_input"),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = state.customClientSecret,
                                    onValueChange = { onUpdateClientSecret(it) },
                                    label = { Text("Client Secret (Optional for Web PKCE)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("client_secret_input"),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = state.providerSpec.redirectUri,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Redirect URI (Deep Link)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { selectedTab = 0 },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                                ) {
                                    Text("Apply & Reload OAuth Flow")
                                }
                            }
                        }

                        2 -> {
                            // Quick Demo & Token Injection
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Quick Sign-In & Token Sandbox",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Easily add an account or paste an OAuth token manually.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        onQuickDemoAuth(state.providerSpec.providerType)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("quick_sandbox_login_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = brandColor)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Instant ${state.providerSpec.title} OAuth Registration")
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                HorizontalDivider()

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Manual Token / Auth Code",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = manualTokenText,
                                    onValueChange = { manualTokenText = it },
                                    label = { Text("OAuth Code or Access Token") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_token_input"),
                                    minLines = 2
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (manualTokenText.isNotBlank()) {
                                            if (manualTokenText.startsWith("ya29.") || manualTokenText.length > 50) {
                                                onDirectAccessTokenReceived(manualTokenText.trim())
                                            } else {
                                                onCodeReceived(manualTokenText.trim())
                                            }
                                        }
                                    },
                                    enabled = manualTokenText.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("submit_manual_token_button")
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Submit OAuth Token")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
