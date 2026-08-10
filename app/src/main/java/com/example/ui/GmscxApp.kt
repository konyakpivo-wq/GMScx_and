package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AccountType
import com.example.ui.screens.AccountDetailDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.OAuthAuthDialog
import com.example.ui.screens.SelfCheckScreen
import com.example.ui.screens.ServicesScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmscxApp(viewModel: GmscxViewModel) {
    val context = LocalContext.current
    val accounts by viewModel.accountsState.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val oauthDialogState by viewModel.oauthDialogState.collectAsStateWithLifecycle()
    val selectedAccount by viewModel.selectedAccount.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Accounts, 1: Services, 2: Self-Check

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.AccountAdded -> {
                    Toast.makeText(
                        context,
                        "Registered ${event.type.displayName}: ${event.email}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                text = "GMSCX Services",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "microG Clone",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "com.gmscx.services • ${accounts.size} account(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Accounts") },
                    label = { Text("Accounts") },
                    modifier = Modifier.testTag("tab_accounts")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Services") },
                    label = { Text("Services") },
                    modifier = Modifier.testTag("tab_services")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.FactCheck, contentDescription = "Self-Check") },
                    label = { Text("Self-Check") },
                    modifier = Modifier.testTag("tab_selfcheck")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> AccountsScreen(
                    accounts = accounts,
                    onAddGoogleAccount = { viewModel.performGoogleSignIn(context) },
                    onAddYandexAccount = { viewModel.openOAuthFlow(AccountType.YANDEX) },
                    onSelectAccount = { viewModel.selectAccount(it) },
                    onToggleSync = { account, enabled -> viewModel.toggleAccountSync(account, enabled) }
                )

                1 -> ServicesScreen(
                    serviceState = serviceState,
                    onToggleGcm = { viewModel.gmsServiceManager.toggleGcm(it) },
                    onToggleSafetyNet = { viewModel.gmsServiceManager.toggleSafetyNet(it) },
                    onToggleUnifiedNlp = { viewModel.gmsServiceManager.toggleUnifiedNlp(it) },
                    onToggleGoogleSync = { viewModel.gmsServiceManager.toggleGoogleSync(it) },
                    onToggleYandexSync = { viewModel.gmsServiceManager.toggleYandexSync(it) },
                    onRegisterAppGcm = { pkg, name -> viewModel.registerAppPush(pkg, name) },
                    onUnregisterAppGcm = { pkg -> viewModel.unregisterAppPush(pkg) }
                )

                2 -> SelfCheckScreen(
                    checkList = viewModel.gmsServiceManager.getSelfCheckList(accounts.size),
                    onOpenAccounts = { selectedTab = 0 }
                )
            }
        }
    }

    // OAuth Authorization Dialog
    if (oauthDialogState.isOpen) {
        OAuthAuthDialog(
            state = oauthDialogState,
            onClose = { viewModel.closeOAuthDialog() },
            onCodeReceived = { viewModel.onOAuthCodeReceived(it) },
            onDirectAccessTokenReceived = { viewModel.onDirectAccessTokenReceived(it) },
            onCookiesReceived = { viewModel.onGoogleCookiesReceived(it) },
            onUpdateClientId = { viewModel.updateCustomClientId(it) },
            onUpdateClientSecret = { viewModel.updateCustomClientSecret(it) },
            onQuickDemoAuth = { type -> viewModel.addQuickDemoAccount(type) }
        )
    }

    // Account Details Dialog
    selectedAccount?.let { account ->
        AccountDetailDialog(
            account = account,
            onDismiss = { viewModel.selectAccount(null) },
            onDeleteAccount = { viewModel.deleteAccount(it) }
        )
    }
}
