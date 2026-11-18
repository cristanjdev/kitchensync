package com.kitchensync360.app.ui.screens

import com.kitchensync360.app.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitchensync360.app.data.RepositorySnapshot
import com.kitchensync360.app.model.ItemStatus
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.UNACKNOWLEDGED_TIMEOUT_SECONDS
import com.kitchensync360.app.ui.components.OverdueAlertBanner
import com.kitchensync360.app.ui.components.PlainCard
import com.kitchensync360.app.ui.components.PlainOutlineButton
import com.kitchensync360.app.ui.components.PlainPrimaryButton
import com.kitchensync360.app.ui.components.StatusCountPill
import com.kitchensync360.app.ui.components.formatElapsedShort
import com.kitchensync360.app.ui.components.responsiveCardRows
import com.kitchensync360.app.ui.components.responsiveColumnCount
import com.kitchensync360.app.ui.theme.KsAmberSurface
import com.kitchensync360.app.ui.theme.KsAmberText
import com.kitchensync360.app.ui.theme.KsAmberWarning
import com.kitchensync360.app.ui.theme.KsBlueInfo
import com.kitchensync360.app.ui.theme.KsBlueSurface
import com.kitchensync360.app.ui.theme.KsBlueText
import com.kitchensync360.app.ui.theme.KsBorder
import com.kitchensync360.app.ui.theme.KsBorderStrong
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsGreenSurface
import com.kitchensync360.app.ui.theme.KsGreenText
import com.kitchensync360.app.ui.theme.KsRedDanger
import com.kitchensync360.app.ui.theme.KsRedSurface
import com.kitchensync360.app.ui.theme.KsRedText
import com.kitchensync360.app.ui.theme.KsSurfaceElevated
import com.kitchensync360.app.ui.theme.KsSurfaceSubtle
import com.kitchensync360.app.ui.theme.KsTextMuted
import com.kitchensync360.app.ui.theme.KsTextPrimary
import com.kitchensync360.app.ui.theme.KsTextSecondary
import kotlinx.coroutines.delay

private enum class WaiterQueueFilter(val label: String) {
    PICKUP_QUEUE("Pickup"),
    IN_KITCHEN("In Kitchen"),
    SERVED("Picked Up")
}

@Composable
fun WaiterScreen(
    snapshot: RepositorySnapshot,
    onAcknowledgeOrder: (orderId: String) -> Unit,
    onMarkPickedUp: (orderId: String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    /** Demo builds only: opens the on-phone watch preview. Null hides the button. */
    onOpenWatchPreview: (() -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf(WaiterQueueFilter.PICKUP_QUEUE) }
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val assignedOrders = remember(snapshot.orders, snapshot.currentUser) {
        val waiterId = snapshot.currentUser?.id
        snapshot.orders.filter { orderWithItems ->
            val order = orderWithItems.order
            order.orderStatus != OrderStatus.CANCELED &&
                (order.assignedWaiterId == null || waiterId == null || order.assignedWaiterId == waiterId)
        }
    }

    val pickupQueueOrders = remember(assignedOrders) {
        assignedOrders.filter {
            it.order.orderStatus == OrderStatus.READY ||
                it.order.orderStatus == OrderStatus.ACKNOWLEDGED
        }.sortedWith(
            compareBy<OrderWithItems> {
                when (it.order.orderStatus) {
                    OrderStatus.READY -> 0
                    OrderStatus.ACKNOWLEDGED -> 1
                    else -> 2
                }
            }.thenBy { it.order.readyAtMillis ?: Long.MAX_VALUE }
        )
    }

    val inKitchenOrders = remember(assignedOrders) {
        assignedOrders
            .filter { it.order.orderStatus == OrderStatus.PREPARING }
            .sortedBy { it.order.orderNumber }
    }

    val servedOrders = remember(assignedOrders) {
        assignedOrders
            .filter { it.order.orderStatus == OrderStatus.PICKED_UP }
            .sortedByDescending { it.order.orderNumber }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val columnCount = responsiveColumnCount(maxWidth)

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Row: Waiter Queue Title + Equal-Height & Equal-Width Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Waiter Service Queue",
                        style = MaterialTheme.typography.headlineMedium,
                        color = KsTextPrimary
                    )
                    val readyCount = pickupQueueOrders.count { it.order.orderStatus == OrderStatus.READY }
                    val ackCount = pickupQueueOrders.count { it.order.orderStatus == OrderStatus.ACKNOWLEDGED }
                    Text(
                        text = when {
                            readyCount > 0 -> "$readyCount Ready to Confirm · $ackCount to Collect"
                            ackCount > 0 -> "$ackCount Acknowledged — Collect at Pass"
                            else -> "All Ready Orders Collected · Watch Active"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (readyCount > 0) KsGreenText else KsTextSecondary
                    )
                }
                // No staff header buttons: the floating refresh button already syncs.
                // Demo builds get a Watch Preview for clients without a watch.
                if (onOpenWatchPreview != null) {
                    PlainOutlineButton(
                        text = "Watch Preview",
                        onClick = onOpenWatchPreview,
                        minHeight = 36.dp
                    )
                }
            }

            // Honest Notification Permission Warning (Acceptance Scenario #12)
            if (!snapshot.notificationPermissionGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(KsRedSurface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Notification Permission Required for Watch & Phone Alerts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = KsRedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android notifications are currently blocked. Ready alerts cannot reach this phone or your paired Wear OS watch until notifications are enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KsTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PlainOutlineButton(
                        text = "Enable Notifications",
                        onClick = onRequestNotificationPermission,
                        borderColor = KsRedDanger,
                        textColor = KsRedText,
                        minHeight = 36.dp
                    )
                }
            } else if (!snapshot.phase1WatchStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(KsGreenSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = snapshot.phase1WatchStatus,
                        style = MaterialTheme.typography.labelLarge,
                        color = KsGreenText
                    )
                }
            }

            val overduePickupOrders = pickupQueueOrders.filter {
                it.isUnacknowledgedOverdue(nowEpochMillis, UNACKNOWLEDGED_TIMEOUT_SECONDS)
            }
            if (overduePickupOrders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OverdueAlertBanner(
                    overdueOrders = overduePickupOrders,
                    nowEpochMillis = nowEpochMillis,
                    compactLabel = "Confirm",
                    expandedLabel = "Ready for Confirmation",
                    actionLabel = if (selectedFilter != WaiterQueueFilter.PICKUP_QUEUE) {
                        "Pickup"
                    } else {
                        null
                    },
                    onActionClick = if (selectedFilter != WaiterQueueFilter.PICKUP_QUEUE) {
                        { selectedFilter = WaiterQueueFilter.PICKUP_QUEUE }
                    } else {
                        null
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Equal-Height Filter Tabs with Status-Highlighted Counts (no text truncation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WaiterQueueFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val count = when (filter) {
                        WaiterQueueFilter.PICKUP_QUEUE -> pickupQueueOrders.size
                        WaiterQueueFilter.IN_KITCHEN -> inKitchenOrders.size
                        WaiterQueueFilter.SERVED -> servedOrders.size
                    }
                    val (pillText, pillBg) = when (filter) {
                        WaiterQueueFilter.PICKUP_QUEUE -> KsGreenText to KsGreenSurface
                        WaiterQueueFilter.IN_KITCHEN -> KsAmberText to KsAmberSurface
                        WaiterQueueFilter.SERVED -> KsTextSecondary to KsSurfaceSubtle
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) KsSurfaceElevated else KsSurfaceSubtle)
                            .border(
                                1.dp,
                                if (isSelected) KsGreenPrimary else KsBorderStrong,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) KsTextPrimary else KsTextSecondary,
                                maxLines = 1
                            )
                            StatusCountPill(
                                count = count,
                                textColor = pillText,
                                backgroundColor = pillBg
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val displayedOrders = when (selectedFilter) {
                WaiterQueueFilter.PICKUP_QUEUE -> pickupQueueOrders
                WaiterQueueFilter.IN_KITCHEN -> inKitchenOrders
                WaiterQueueFilter.SERVED -> servedOrders
            }

            if (displayedOrders.isEmpty()) {
                PlainCard {
                    Text(
                        text = when (selectedFilter) {
                            WaiterQueueFilter.PICKUP_QUEUE -> "No Orders Waiting for Pickup"
                            WaiterQueueFilter.IN_KITCHEN -> "No Orders Currently Cooking in the Kitchen"
                            WaiterQueueFilter.SERVED -> "No Orders Picked Up Yet"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = KsTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (selectedFilter) {
                            WaiterQueueFilter.PICKUP_QUEUE -> "When all stations finish a table's items, the order appears here as Ready and alerts your phone and paired Wear OS watch."
                            WaiterQueueFilter.IN_KITCHEN -> "Orders still being prepared by Grill, Fryer, or Pastry appear here so you can check progress for guests."
                            WaiterQueueFilter.SERVED -> "Orders you mark as Picked Up move here."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = KsTextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    responsiveCardRows(
                        itemsList = displayedOrders,
                        columnCount = columnCount,
                        keyPrefix = "waiter_${selectedFilter.name}",
                        itemKey = { it.order.id }
                    ) { orderWithItems ->
                        WaiterOrderCard(
                            orderWithItems = orderWithItems,
                            nowEpochMillis = nowEpochMillis,
                            isPendingWrite = snapshot.pendingOrderIds.contains(orderWithItems.order.id),
                            failedWriteMessage = snapshot.failedWriteByOrderId[orderWithItems.order.id],
                            onAcknowledge = { onAcknowledgeOrder(orderWithItems.order.id) },
                            onMarkPickedUp = { onMarkPickedUp(orderWithItems.order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaiterOrderCard(
    orderWithItems: OrderWithItems,
    nowEpochMillis: Long,
    isPendingWrite: Boolean,
    failedWriteMessage: String?,
    onAcknowledge: () -> Unit,
    onMarkPickedUp: () -> Unit
) {
    val order = orderWithItems.order
    val isOverdue = orderWithItems.isUnacknowledgedOverdue(
        nowEpochMillis = nowEpochMillis,
        timeoutSeconds = UNACKNOWLEDGED_TIMEOUT_SECONDS
    )
    val readyElapsedSec = orderWithItems.readyElapsedSeconds(nowEpochMillis)

    val stripeColor = when {
        isOverdue -> KsRedDanger
        order.orderStatus == OrderStatus.READY -> KsGreenPrimary
        order.orderStatus == OrderStatus.ACKNOWLEDGED -> KsBlueInfo
        order.orderStatus == OrderStatus.PREPARING -> KsAmberWarning
        else -> KsBorderStrong
    }

    val borderColor = when {
        isOverdue -> KsRedDanger
        order.orderStatus == OrderStatus.READY -> KsGreenPrimary
        order.orderStatus == OrderStatus.ACKNOWLEDGED -> KsBlueInfo
        else -> KsBorder
    }

    PlainCard(
        modifier = Modifier.fillMaxHeight(),
        borderColor = borderColor,
        accentStripeColor = stripeColor,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Card Header: Table Number, Order #, Status Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Table ${order.tableNumber}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = KsTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = orderWithItems.displayOrderNumber,
                        style = MaterialTheme.typography.titleLarge,
                        color = KsTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        isOverdue -> "Ready ${formatElapsedShort(readyElapsedSec)} Ago · Acknowledge Now"
                        order.orderStatus == OrderStatus.READY -> "Ready ${formatElapsedShort(readyElapsedSec)} Ago · Awaiting Ack"
                        order.orderStatus == OrderStatus.ACKNOWLEDGED -> "Acknowledged · Collect Tray at Pass"
                        order.orderStatus == OrderStatus.PREPARING -> {
                            val readyLines = orderWithItems.items.count { it.itemStatus == ItemStatus.READY }
                            "In Kitchen ($readyLines/${orderWithItems.items.size} Items Ready)"
                        }
                        else -> "Collected and Served"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isOverdue -> KsRedText
                        order.orderStatus == OrderStatus.READY -> KsGreenText
                        order.orderStatus == OrderStatus.ACKNOWLEDGED -> KsBlueText
                        else -> KsTextSecondary
                    }
                )
            }

            val (badgeBg, badgeBorder, badgeText, badgeLabel) = when {
                isOverdue -> listOf(KsRedSurface, KsRedDanger, KsRedText, "OVERDUE")
                order.orderStatus == OrderStatus.READY -> listOf(KsGreenSurface, KsGreenPrimary, KsGreenText, "ORDER READY")
                order.orderStatus == OrderStatus.ACKNOWLEDGED -> listOf(KsBlueSurface, KsBlueInfo, KsBlueText, "GO COLLECT")
                order.orderStatus == OrderStatus.PREPARING -> listOf(KsAmberSurface, KsAmberWarning, KsAmberText, "COOKING")
                else -> listOf(KsSurfaceSubtle, KsBorderStrong, KsTextSecondary, "PICKED UP")
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBg as Color)
                    .border(1.dp, badgeBorder as Color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeLabel as String,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeText as Color
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tray Checklist (clean spacing, no divider line)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            orderWithItems.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${item.quantity}×",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KsGreenText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.menuLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = KsTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (order.orderStatus == OrderStatus.PREPARING) {
                            "${item.stationType.displayName} · ${item.itemStatus.displayName}"
                        } else {
                            item.stationType.displayName
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.itemStatus == ItemStatus.READY) KsGreenText else KsTextMuted
                    )
                }
            }
        }

        // Honest Pending or Failed Write Feedback
        if (isPendingWrite) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Saving to Backend…",
                style = MaterialTheme.typography.labelLarge,
                color = KsAmberText
            )
        }

        if (!failedWriteMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(KsRedSurface)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Not Saved: $failedWriteMessage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KsRedText
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = true))

        // Primary Action Button pinned to bottom of equal-height cards
        if (order.orderStatus == OrderStatus.READY || order.orderStatus == OrderStatus.ACKNOWLEDGED) {
            Spacer(modifier = Modifier.height(12.dp))
            when (order.orderStatus) {
                OrderStatus.READY -> {
                    PlainPrimaryButton(
                        text = if (isPendingWrite) {
                            "Confirming Acknowledgment…"
                        } else {
                            "Acknowledge Order (Table ${order.tableNumber})"
                        },
                        onClick = onAcknowledge,
                        enabled = !isPendingWrite,
                        containerColor = KsGreenPrimary,
                        minHeight = 44.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OrderStatus.ACKNOWLEDGED -> {
                    PlainPrimaryButton(
                        text = if (isPendingWrite) {
                            "Confirming Pickup…"
                        } else {
                            "Confirm Picked Up (Table ${order.tableNumber})"
                        },
                        onClick = onMarkPickedUp,
                        enabled = !isPendingWrite,
                        containerColor = KsBlueInfo,
                        minHeight = 44.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> Unit
            }
        }
    }
}
