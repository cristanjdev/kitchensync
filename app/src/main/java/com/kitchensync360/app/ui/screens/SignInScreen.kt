package com.kitchensync360.app.ui.screens

import com.kitchensync360.app.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kitchensync360.app.data.RepositorySnapshot
import com.kitchensync360.app.model.AppRole
import com.kitchensync360.app.model.ConnectionMode
import com.kitchensync360.app.model.PresetAccounts
import com.kitchensync360.app.model.PresetDemoAccount
import com.kitchensync360.app.model.SyncStatus
import com.kitchensync360.app.ui.components.PlainBanner
import com.kitchensync360.app.ui.components.PlainCard
import com.kitchensync360.app.ui.components.PlainOutlineButton
import com.kitchensync360.app.ui.components.PlainPrimaryButton
import com.kitchensync360.app.ui.components.StatusDot
import com.kitchensync360.app.ui.theme.KsAmberText
import com.kitchensync360.app.ui.theme.KsAmberWarning
import com.kitchensync360.app.ui.theme.KsBlueInfo
import com.kitchensync360.app.ui.theme.KsBorder
import com.kitchensync360.app.ui.theme.KsBorderStrong
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsGreenText
import com.kitchensync360.app.ui.theme.KsRedText
import com.kitchensync360.app.ui.theme.KsSurfaceElevated
import com.kitchensync360.app.ui.theme.KsSurfaceSubtle
import com.kitchensync360.app.ui.theme.KsTextMuted
import com.kitchensync360.app.ui.theme.KsTextPrimary
import com.kitchensync360.app.ui.theme.KsTextSecondary

@Composable
fun SignInScreen(
    snapshot: RepositorySnapshot,
    onSelectPresetAccount: (PresetDemoAccount) -> Unit,
    onSignInCredentials: (email: String, password: String) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Release builds: plain email + password form only. Debug builds add the demo shortcuts.
    val demoTools = BuildConfig.DEMO_TOOLS
    var showManualForm by remember { mutableStateOf(!demoTools) }
    var email by remember { mutableStateOf(if (demoTools) PresetAccounts.accounts.first().email else "") }
    var password by remember { mutableStateOf(PresetAccounts.DEFAULT_DEMO_PASSWORD) }

    val isCloud = snapshot.connectionMode == ConnectionMode.SUPABASE_CLOUD
    val isSigningIn = snapshot.syncStatus == SyncStatus.SYNCING

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Brand & Connection Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KitchenSync 360",
                        style = MaterialTheme.typography.headlineLarge,
                        color = KsTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Kitchen-to-Waiter Order Synchronization",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KsTextSecondary
                    )
                }
            }

            if (!snapshot.bannerMessage.isNullOrBlank()) {
                PlainBanner(
                    message = snapshot.bannerMessage,
                    isError = snapshot.bannerIsError,
                    onDismiss = onDismissBanner
                )
            }

            // Role Selection Section (debug only: one-tap sign-in with the demo password)
            if (demoTools) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select Device Role",
                    style = MaterialTheme.typography.titleLarge,
                    color = KsTextPrimary
                )
                Text(
                    text = "Tap a provisioned account to open its workspace immediately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KsTextSecondary
                )

                PresetAccounts.accounts.forEach { account ->
                    val stripeColor = when (account.role) {
                        AppRole.CONTROLLER -> KsGreenPrimary
                        AppRole.KITCHEN -> KsAmberWarning
                        AppRole.WAITER -> KsBlueInfo
                    }
                    val roleTitle = when (account.role) {
                        AppRole.CONTROLLER -> "Expeditor Dashboard"
                        AppRole.KITCHEN -> "Kitchen Station Display"
                        AppRole.WAITER -> "Waiter & Watch Bridge"
                    }

                    PlainCard(
                        modifier = Modifier.clickable(enabled = !isSigningIn) {
                            email = account.email
                            password = account.password
                            onSelectPresetAccount(account)
                        },
                        borderColor = KsBorderStrong,
                        backgroundColor = KsSurfaceElevated,
                        accentStripeColor = stripeColor,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = roleTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = KsTextPrimary
                            )
                            Text(
                                text = if (isSigningIn && email == account.email) "Signing In…" else "Open →",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = KsGreenText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = account.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = KsTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.labelMedium,
                            color = KsTextMuted
                        )
                    }
                }
            }

            // Collapsible Manual Credentials Form
            PlainCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (demoTools) Modifier.clickable { showManualForm = !showManualForm } else Modifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (demoTools) "Sign In with Email & Password" else "Sign In",
                        style = MaterialTheme.typography.titleMedium,
                        color = KsTextPrimary
                    )
                    if (demoTools) {
                        Text(
                            text = if (showManualForm) "Hide" else "Expand",
                            style = MaterialTheme.typography.labelMedium,
                            color = KsGreenText
                        )
                    }
                }

                if (showManualForm) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KsGreenPrimary,
                            unfocusedBorderColor = KsBorderStrong
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KsGreenPrimary,
                            unfocusedBorderColor = KsBorderStrong
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PlainPrimaryButton(
                        text = if (isSigningIn) "Signing In…" else "Sign In",
                        onClick = { onSignInCredentials(email, password) },
                        enabled = !isSigningIn && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Accounts and roles are provisioned by the backend administrator.",
                        style = MaterialTheme.typography.labelMedium,
                        color = KsTextMuted
                    )
                }
            }
        }
    }
}
