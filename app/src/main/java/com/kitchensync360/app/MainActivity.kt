package com.kitchensync360.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kitchensync360.app.data.KitchenSyncRepository
import com.kitchensync360.app.data.RepositorySnapshot
import com.kitchensync360.app.model.AppRole
import com.kitchensync360.app.model.ConnectionMode
import com.kitchensync360.app.model.ItemStatus
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.PresetAccounts
import com.kitchensync360.app.model.PresetDemoAccount
import com.kitchensync360.app.model.SyncStatus
import com.kitchensync360.app.model.UNACKNOWLEDGED_TIMEOUT_SECONDS
import com.kitchensync360.app.ui.components.ConfirmMetaCell
import com.kitchensync360.app.ui.components.ConfirmNoticeBlock
import com.kitchensync360.app.ui.components.ConfirmSummaryCard
import com.kitchensync360.app.ui.components.FloatingRefreshOverlay
import com.kitchensync360.app.ui.components.PlainBanner
import com.kitchensync360.app.ui.components.PlainConfirmModal
import com.kitchensync360.app.ui.components.PlainOutlineButton
import com.kitchensync360.app.ui.components.StatusCountPill
import com.kitchensync360.app.ui.components.StatusDot
import com.kitchensync360.app.ui.screens.DashboardScreen
import com.kitchensync360.app.ui.screens.SignInScreen
import com.kitchensync360.app.ui.screens.StationScreen
import com.kitchensync360.app.ui.screens.WaiterScreen
import com.kitchensync360.app.ui.screens.WatchScreen
import com.kitchensync360.app.ui.screens.isWatchLayout
import com.kitchensync360.app.ui.screens.WatchPreviewDialog
import com.kitchensync360.app.ui.theme.KitchenSync360Theme
import com.kitchensync360.app.ui.theme.KsAmberSurface
import com.kitchensync360.app.ui.theme.KsAmberText
import com.kitchensync360.app.ui.theme.KsBackground
import com.kitchensync360.app.ui.theme.KsBlueSurface
import com.kitchensync360.app.ui.theme.KsBlueText
import com.kitchensync360.app.ui.theme.KsBorder
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsGreenSurface
import com.kitchensync360.app.ui.theme.KsGreenText
import com.kitchensync360.app.ui.theme.KsRedText
import com.kitchensync360.app.ui.theme.KsSurface
import com.kitchensync360.app.ui.theme.KsSurfaceElevated
import com.kitchensync360.app.ui.theme.KsTextMuted
import com.kitchensync360.app.ui.theme.KsTextPrimary
import com.kitchensync360.app.ui.theme.KsTextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep phone awake during live presentation and watch bridging
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val repository = KitchenSyncRepository.getInstance(applicationContext)

        setContent {
            KitchenSync360Theme {
                KitchenSyncAppRoot(repository = repository)
            }
        }
    }
}

@Composable
private fun KitchenSyncAppRoot(
    repository: KitchenSyncRepository
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snapshot by repository.state.collectAsState()
    var showSignOutModal by remember { mutableStateOf(false) }
    var showWatchPreview by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        repository.refreshNotificationPermissionState()
    }

    // Request notification permission on Android 13+ on first launch
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !repository.notificationManager.isNotificationPermissionGranted()
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Refresh permission and snapshot on foreground resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                repository.refreshNotificationPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val openNotificationPermissionOrSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val settingsIntent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching {
            context.startActivity(settingsIntent)
        }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    val configuration = LocalConfiguration.current
    if (isWatchLayout(context, configuration)) {
        val waiterPreset = PresetAccounts.accounts.first { it.role == AppRole.WAITER }
        WatchScreen(
            snapshot = snapshot,
            isRound = configuration.isScreenRound,
            // Debug builds: one-tap demo sign-in on the watch. Release: staff sign in on the phone.
            onSignInAsWaiter = if (BuildConfig.DEMO_TOOLS) {
                { repository.signInWithPreset(waiterPreset) }
            } else {
                null
            },
            onAcknowledgeOrder = { orderId -> repository.acknowledgeOrder(orderId) },
            onMarkPickedUp = { orderId -> repository.markOrderPickedUp(orderId) },
            onRefresh = { repository.manualReconnectAndRefresh() },
            onSignOut = { repository.signOut() },
            onDismissBanner = { repository.clearBanner() }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KsBackground)
            .statusBarsPadding()
    ) {
        val currentUser = snapshot.currentUser
        if (currentUser == null) {
            SignInScreen(
                snapshot = snapshot,
                onSelectPresetAccount = { account ->
                    repository.signInWithPreset(account)
                },
                onSignInCredentials = { email, password ->
                    repository.signInWithCredentials(email, password)
                },
                onDismissBanner = { repository.clearBanner() }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                AuthenticatedTopHeader(
                    snapshot = snapshot,
                    onSwitchAccount = { preset -> repository.signInWithPreset(preset) },
                    onRequestSignOut = { showSignOutModal = true }
                )

                if (!snapshot.bannerMessage.isNullOrBlank()) {
                    PlainBanner(
                        message = snapshot.bannerMessage!!,
                        isError = snapshot.bannerIsError,
                        onDismiss = { repository.clearBanner() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                when (currentUser.appRole) {
                    AppRole.CONTROLLER -> {
                        DashboardScreen(
                            snapshot = snapshot,
                            onCreateOrder = { tableNumber, items ->
                                repository.createOrder(tableNumber, items)
                            },
                            onCancelOrder = { orderId ->
                                repository.cancelOrder(orderId)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AppRole.KITCHEN -> {
                        StationScreen(
                            snapshot = snapshot,
                            onSelectStation = { station ->
                                repository.selectStation(station)
                            },
                            onAcceptStationOrder = { orderId, station ->
                                repository.acceptStationOrder(orderId, station)
                            },
                            onMarkItemReady = { itemId ->
                                repository.markItemReady(itemId)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AppRole.WAITER -> {
                        WaiterScreen(
                            snapshot = snapshot,
                            onAcknowledgeOrder = { orderId ->
                                repository.acknowledgeOrder(orderId)
                            },
                            onMarkPickedUp = { orderId ->
                                repository.markOrderPickedUp(orderId)
                            },
                            onRequestNotificationPermission = openNotificationPermissionOrSettings,
                            modifier = Modifier.weight(1f),
                            onOpenWatchPreview = if (BuildConfig.DEMO_TOOLS) {
                                { showWatchPreview = true }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            FloatingRefreshOverlay(
                isRefreshing = snapshot.isManualRefreshing,
                onRefresh = { repository.manualReconnectAndRefresh() }
            )
        }
    }

    if (showWatchPreview && BuildConfig.DEMO_TOOLS && snapshot.currentUser != null) {
        WatchPreviewDialog(
            snapshot = snapshot,
            onAcknowledgeOrder = { orderId -> repository.acknowledgeOrder(orderId) },
            onMarkPickedUp = { orderId -> repository.markOrderPickedUp(orderId) },
            onRefresh = { repository.manualReconnectAndRefresh() },
            onDismissBanner = { repository.clearBanner() },
            onClose = { showWatchPreview = false }
        )
    }

    if (showSignOutModal && snapshot.currentUser != null) {
        SignOutConfirmDialog(
            snapshot = snapshot,
            onDismiss = { showSignOutModal = false },
            onConfirmSignOut = {
                showSignOutModal = false
                repository.signOut()
            }
        )
    }
}

@Composable
private fun SignOutConfirmDialog(
    snapshot: RepositorySnapshot,
    onDismiss: () -> Unit,
    onConfirmSignOut: () -> Unit
) {
    val user = snapshot.currentUser ?: return
    val isCloud = snapshot.connectionMode == ConnectionMode.SUPABASE_CLOUD
    val syncLabel = when {
        !isCloud -> "Local Demo"
        snapshot.syncStatus == SyncStatus.CONNECTED -> "Live"
        snapshot.syncStatus == SyncStatus.DISCONNECTED -> "Offline"
        else -> "Reconnecting"
    }
    val preparingCount = snapshot.orders.count { it.order.orderStatus == OrderStatus.PREPARING }
    val readyCount = snapshot.orders.count { it.order.orderStatus == OrderStatus.READY }
    val ackCount = snapshot.orders.count { it.order.orderStatus == OrderStatus.ACKNOWLEDGED }
    val now = System.currentTimeMillis()
    val overdueCount = snapshot.orders.count {
        it.isUnacknowledgedOverdue(now, UNACKNOWLEDGED_TIMEOUT_SECONDS)
    }

    PlainConfirmModal(
        title = "Sign Out of KitchenSync 360?",
        subtitle = "Live updates and controls on this device will stop until you sign back in.",
        confirmLabel = "Sign Out",
        cancelLabel = "Stay Signed In",
        destructive = false,
        onDismiss = onDismiss,
        onConfirm = onConfirmSignOut
    ) {
        ConfirmSummaryCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(KsSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConfirmMetaCell(
                    label = "Operator",
                    value = user.displayName,
                    subValue = user.email,
                    modifier = Modifier.weight(1f)
                )
                ConfirmMetaCell(
                    label = "Role & Sync",
                    value = user.appRole.label,
                    subValue = syncLabel,
                    subColor = if (isCloud && snapshot.syncStatus == SyncStatus.CONNECTED) KsGreenText else KsAmberText,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SignOutStatTile(
                    label = "Preparing",
                    count = preparingCount,
                    color = KsAmberText,
                    modifier = Modifier.weight(1f)
                )
                SignOutStatTile(
                    label = "Ready",
                    count = readyCount,
                    color = KsGreenText,
                    modifier = Modifier.weight(1f)
                )
                SignOutStatTile(
                    label = "Acked",
                    count = ackCount,
                    color = KsBlueText,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (overdueCount > 0) {
            ConfirmNoticeBlock(
                title = "$overdueCount ready ${if (overdueCount == 1) "order" else "orders"} waiting for waiter",
                body = "Food is plated at the pass and still needs waiter confirmation.",
                isDanger = true
            )
        } else {
            ConfirmNoticeBlock(
                body = "Other kitchen and waiter devices remain connected independently.",
                isDanger = false
            )
        }
    }
}

@Composable
private fun SignOutStatTile(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(KsSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun AuthenticatedTopHeader(
    snapshot: RepositorySnapshot,
    onSwitchAccount: (PresetDemoAccount) -> Unit,
    onRequestSignOut: () -> Unit
) {
    val currentUser = snapshot.currentUser ?: return
    val isCloud = snapshot.connectionMode == ConnectionMode.SUPABASE_CLOUD

    val statusColor = when {
        !isCloud -> KsAmberText
        snapshot.syncStatus == SyncStatus.CONNECTED -> KsGreenText
        snapshot.syncStatus == SyncStatus.DISCONNECTED -> KsRedText
        else -> KsAmberText
    }
    val statusText = when {
        !isCloud -> "Local"
        snapshot.syncStatus == SyncStatus.CONNECTED -> "Live"
        snapshot.syncStatus == SyncStatus.DISCONNECTED -> "Offline"
        else -> "Syncing"
    }

    // Live counts for role switcher tabs
    val activeOrderCount = remember(snapshot.orders) {
        snapshot.orders.count {
            it.order.orderStatus in setOf(
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.ACKNOWLEDGED
            )
        }
    }
    val openKitchenItemsCount = remember(snapshot.orders) {
        snapshot.orders.sumOf { orderWithItems ->
            if (orderWithItems.order.orderStatus in setOf(OrderStatus.CANCELED, OrderStatus.PICKED_UP)) {
                0
            } else {
                orderWithItems.items.count { it.itemStatus != ItemStatus.READY }
            }
        }
    }
    val waiterActionCount = remember(snapshot.orders) {
        snapshot.orders.count {
            it.order.orderStatus == OrderStatus.READY ||
                it.order.orderStatus == OrderStatus.ACKNOWLEDGED
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = KsSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Bar: Brand + Live Connection Indicator on Left, Uniform-Height Controls on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "KitchenSync 360",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = KsTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        StatusDot(color = statusColor, size = 6.dp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlainOutlineButton(
                        text = "Sign Out",
                        onClick = onRequestSignOut,
                        textColor = KsTextSecondary,
                        minHeight = 34.dp
                    )
                }
            }

            // Segmented Workspace Mode Switcher (Expeditor / Kitchen / Waiter) with Highlighted Counts.
            // Debug only: it switches accounts without a password, so release builds never show it.
            if (BuildConfig.DEMO_TOOLS) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KsBackground)
                    .border(1.dp, KsBorder, RoundedCornerShape(6.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PresetAccounts.accounts.forEach { preset ->
                    val isCurrent = currentUser.email.equals(preset.email, ignoreCase = true)
                    val (tabTitle, badgeCount, countTextColor, countBgColor) = when (preset.role) {
                        AppRole.CONTROLLER -> listOf("Expeditor", activeOrderCount, KsBlueText, KsBlueSurface)
                        AppRole.KITCHEN -> listOf("Kitchen", openKitchenItemsCount, KsAmberText, KsAmberSurface)
                        AppRole.WAITER -> listOf("Waiter", waiterActionCount, KsGreenText, KsGreenSurface)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isCurrent) KsSurfaceElevated else Color.Transparent)
                            .then(
                                if (isCurrent) {
                                    Modifier.border(1.dp, KsGreenPrimary, RoundedCornerShape(4.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                if (!isCurrent) {
                                    onSwitchAccount(preset)
                                }
                            }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = tabTitle as String,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isCurrent) KsTextPrimary else KsTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusCountPill(
                                count = badgeCount as Int,
                                textColor = if ((badgeCount) > 0) countTextColor as Color else KsTextMuted,
                                backgroundColor = if ((badgeCount) > 0) countBgColor as Color else KsSurfaceElevated
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = KsBorder)
        }
    }
}
