package com.kitchensync360.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitchensync360.app.data.RepositorySnapshot
import com.kitchensync360.app.model.CreateOrderItemPayload
import com.kitchensync360.app.model.ItemStatus
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.PresetMenu
import com.kitchensync360.app.model.StationSummaryState
import com.kitchensync360.app.model.StationType
import com.kitchensync360.app.model.UNACKNOWLEDGED_TIMEOUT_SECONDS
import com.kitchensync360.app.ui.components.ConfirmMetaCell
import com.kitchensync360.app.ui.components.ConfirmNoticeBlock
import com.kitchensync360.app.ui.components.ConfirmSummaryCard
import com.kitchensync360.app.ui.components.OverdueAlertBanner
import com.kitchensync360.app.ui.components.PlainCard
import com.kitchensync360.app.ui.components.PlainConfirmModal
import com.kitchensync360.app.ui.components.PlainModalDialog
import com.kitchensync360.app.ui.components.PlainOutlineButton
import com.kitchensync360.app.ui.components.PlainPrimaryButton
import com.kitchensync360.app.ui.components.StatusCountPill
import com.kitchensync360.app.ui.components.formatClockShort
import com.kitchensync360.app.ui.components.formatElapsedShort
import com.kitchensync360.app.ui.components.responsiveCardRows
import com.kitchensync360.app.ui.components.responsiveColumnCount
import com.kitchensync360.app.ui.theme.KsAmberSurface
import com.kitchensync360.app.ui.theme.KsAmberText
import com.kitchensync360.app.ui.theme.KsAmberWarning
import com.kitchensync360.app.ui.theme.KsBackground
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

private enum class DashboardFilter(val label: String, val matchingStatus: OrderStatus?) {
    ALL("All Orders", null),
    PREPARING("Preparing", OrderStatus.PREPARING),
    READY("Ready", OrderStatus.READY),
    ACKNOWLEDGED("Acknowledged", OrderStatus.ACKNOWLEDGED),
    PICKED_UP("Picked Up", OrderStatus.PICKED_UP)
}

private data class DashboardSection(
    val status: OrderStatus,
    val title: String,
    val subtitle: String,
    val orders: List<OrderWithItems>
)

@Composable
fun DashboardScreen(
    snapshot: RepositorySnapshot,
    onCreateOrder: (tableNumber: Int, items: List<CreateOrderItemPayload>) -> Unit,
    onCancelOrder: (orderId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(DashboardFilter.ALL) }
    var showCreateOrderModal by remember { mutableStateOf(false) }
    var cancelTargetOrderId by remember { mutableStateOf<String?>(null) }
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 1-second ticker to evaluate the 30-second unacknowledged warning from backend ready_at
    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val nonCanceledOrders = remember(snapshot.orders) {
        snapshot.orders.filter { it.order.orderStatus != OrderStatus.CANCELED }
    }

    val preparingOrders = nonCanceledOrders.filter { it.order.orderStatus == OrderStatus.PREPARING }
    val readyOrders = nonCanceledOrders.filter { it.order.orderStatus == OrderStatus.READY }
    val acknowledgedOrders = nonCanceledOrders.filter { it.order.orderStatus == OrderStatus.ACKNOWLEDGED }
    val pickedUpOrders = nonCanceledOrders.filter { it.order.orderStatus == OrderStatus.PICKED_UP }

    val overdueOrders = readyOrders.filter {
        it.isUnacknowledgedOverdue(nowEpochMillis, UNACKNOWLEDGED_TIMEOUT_SECONDS)
    }

    val cancelTarget = remember(cancelTargetOrderId, snapshot.orders) {
        cancelTargetOrderId?.let { id ->
            snapshot.orders.firstOrNull { it.order.id == id && it.canCancel }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        val columnCount = responsiveColumnCount(maxWidth)

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Bar: Expeditor Overview & Primary "+ New Order" CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Expeditor Pass",
                        style = MaterialTheme.typography.headlineMedium,
                        color = KsTextPrimary
                    )
                    val activeCount = preparingOrders.size + readyOrders.size + acknowledgedOrders.size
                    Text(
                        text = if (activeCount == 0) {
                            "No Active Orders on the Pass"
                        } else {
                            "${preparingOrders.size} Preparing · ${readyOrders.size} Ready · ${acknowledgedOrders.size} Acknowledged"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = KsTextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                PlainPrimaryButton(
                    text = "+ New Order",
                    onClick = { showCreateOrderModal = true },
                    enabled = !snapshot.isCreatingOrder,
                    minHeight = 40.dp
                )
            }

            // Single-row overdue alert strip matching web-dashboard
            if (overdueOrders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OverdueAlertBanner(
                    overdueOrders = overdueOrders,
                    nowEpochMillis = nowEpochMillis,
                    compactLabel = "Waiting",
                    expandedLabel = "Waiting for Waiter",
                    actionLabel = if (selectedFilter != DashboardFilter.READY) {
                        "Ready (${readyOrders.size})"
                    } else {
                        null
                    },
                    onActionClick = if (selectedFilter != DashboardFilter.READY) {
                        { selectedFilter = DashboardFilter.READY }
                    } else {
                        null
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Filter Pills with Status-Highlighted Counts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        DashboardFilter.ALL -> nonCanceledOrders.size
                        DashboardFilter.PREPARING -> preparingOrders.size
                        DashboardFilter.READY -> readyOrders.size
                        DashboardFilter.ACKNOWLEDGED -> acknowledgedOrders.size
                        DashboardFilter.PICKED_UP -> pickedUpOrders.size
                    }
                    val isSelected = selectedFilter == filter
                    val hasOverdueInTab = (filter == DashboardFilter.READY || filter == DashboardFilter.ALL) &&
                        overdueOrders.isNotEmpty()
                    val (pillTextColor, pillBgColor) = statusHighlightColors(
                        status = filter.matchingStatus,
                        isOverdue = hasOverdueInTab
                    )

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) KsSurfaceElevated else KsSurfaceSubtle)
                            .border(
                                width = 1.dp,
                                color = when {
                                    isSelected -> KsGreenPrimary
                                    hasOverdueInTab -> KsRedDanger
                                    else -> KsBorderStrong
                                },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) KsTextPrimary else KsTextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusCountPill(
                                count = count,
                                textColor = pillTextColor,
                                backgroundColor = pillBgColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val sections = remember(
                selectedFilter,
                preparingOrders,
                readyOrders,
                acknowledgedOrders,
                pickedUpOrders
            ) {
                when (selectedFilter) {
                    DashboardFilter.ALL -> listOf(
                        DashboardSection(OrderStatus.READY, "Ready", "Waiting for Waiter", readyOrders),
                        DashboardSection(OrderStatus.PREPARING, "Preparing", "In Kitchen", preparingOrders),
                        DashboardSection(OrderStatus.ACKNOWLEDGED, "Acknowledged", "Heading to Pass", acknowledgedOrders),
                        DashboardSection(OrderStatus.PICKED_UP, "Picked Up", "Completed", pickedUpOrders)
                    )
                    DashboardFilter.PREPARING -> listOf(
                        DashboardSection(OrderStatus.PREPARING, "Preparing", "In Kitchen", preparingOrders)
                    )
                    DashboardFilter.READY -> listOf(
                        DashboardSection(OrderStatus.READY, "Ready", "Waiting for Waiter", readyOrders)
                    )
                    DashboardFilter.ACKNOWLEDGED -> listOf(
                        DashboardSection(OrderStatus.ACKNOWLEDGED, "Acknowledged", "Heading to Pass", acknowledgedOrders)
                    )
                    DashboardFilter.PICKED_UP -> listOf(
                        DashboardSection(OrderStatus.PICKED_UP, "Picked Up", "Completed", pickedUpOrders)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                sections.forEach { section ->
                    val (titleColor, countBg) = statusHighlightColors(
                        status = section.status,
                        isOverdue = section.status == OrderStatus.READY && overdueOrders.isNotEmpty()
                    )

                    item(key = "section_header_${section.status.id}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor
                                )
                                StatusCountPill(
                                    count = section.orders.size,
                                    textColor = titleColor,
                                    backgroundColor = countBg,
                                    borderColor = titleColor.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }

                    if (section.orders.isEmpty()) {
                        item(key = "empty_${section.status.id}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KsSurfaceSubtle)
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No Orders in ${section.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KsTextMuted
                                )
                            }
                        }
                    } else {
                        responsiveCardRows(
                            itemsList = section.orders,
                            columnCount = columnCount,
                            keyPrefix = "dash_${section.status.id}",
                            itemKey = { it.order.id }
                        ) { orderWithItems ->
                            DashboardOrderCard(
                                orderWithItems = orderWithItems,
                                nowEpochMillis = nowEpochMillis,
                                isPending = snapshot.pendingOrderIds.contains(orderWithItems.order.id),
                                onRequestCancel = { cancelTargetOrderId = orderWithItems.order.id }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateOrderModal) {
        CreateOrderModal(
            onDismiss = { showCreateOrderModal = false },
            onSubmit = { tableNumber, items ->
                onCreateOrder(tableNumber, items)
                showCreateOrderModal = false
            }
        )
    }

    if (cancelTarget != null) {
        CancelOrderConfirmModal(
            orderWithItems = cancelTarget,
            nowEpochMillis = nowEpochMillis,
            isPending = snapshot.pendingOrderIds.contains(cancelTarget.order.id),
            onDismiss = { cancelTargetOrderId = null },
            onConfirmCancel = {
                val id = cancelTarget.order.id
                cancelTargetOrderId = null
                onCancelOrder(id)
            }
        )
    }
}

private fun statusHighlightColors(
    status: OrderStatus?,
    isOverdue: Boolean = false
): Pair<Color, Color> = when {
    isOverdue -> KsRedText to KsRedSurface
    status == OrderStatus.PREPARING -> KsAmberText to KsAmberSurface
    status == OrderStatus.READY -> KsGreenText to KsGreenSurface
    status == OrderStatus.ACKNOWLEDGED -> KsBlueText to KsBlueSurface
    status == OrderStatus.PICKED_UP -> KsTextSecondary to KsSurfaceElevated
    else -> KsGreenText to KsGreenSurface
}

@Composable
private fun DashboardOrderCard(
    orderWithItems: OrderWithItems,
    nowEpochMillis: Long,
    isPending: Boolean,
    onRequestCancel: () -> Unit
) {
    val order = orderWithItems.order
    val isOverdue = orderWithItems.isUnacknowledgedOverdue(
        nowEpochMillis = nowEpochMillis,
        timeoutSeconds = UNACKNOWLEDGED_TIMEOUT_SECONDS
    )
    val readyElapsedSec = orderWithItems.readyElapsedSeconds(nowEpochMillis)
    val orderAgeSec = ((nowEpochMillis - order.createdAtMillis) / 1000L).coerceAtLeast(0L)

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
        else -> KsBorder
    }

    PlainCard(
        modifier = Modifier.fillMaxHeight(),
        borderColor = borderColor,
        accentStripeColor = stripeColor,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Row 1: Table Number, Order #, Elapsed / Timing Badge
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
                    text = "Table ${order.tableNumber}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = KsTextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = orderWithItems.displayOrderNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = KsTextSecondary
                )
            }

            val timingLabel = when (order.orderStatus) {
                OrderStatus.PREPARING -> formatElapsedShort(orderAgeSec)
                OrderStatus.READY -> if (isOverdue) {
                    "Overdue · ${formatElapsedShort(readyElapsedSec)}"
                } else {
                    "Ready ${readyElapsedSec}s"
                }
                OrderStatus.ACKNOWLEDGED -> {
                    val readyMs = order.readyAtMillis
                    val ackMs = order.acknowledgedAtMillis
                    if (readyMs != null && ackMs != null) {
                        "Ack in ${formatElapsedShort(((ackMs - readyMs) / 1000L).coerceAtLeast(0L))}"
                    } else {
                        "Acknowledged"
                    }
                }
                OrderStatus.PICKED_UP -> formatClockShort(order.pickedUpAt)
                OrderStatus.CANCELED -> "Canceled"
            }

            OrderTimingPill(
                text = timingLabel,
                status = order.orderStatus,
                isOverdue = isOverdue
            )
        }

        // Row 2: Flat 3-Station Readiness Strip (hidden for already Picked Up history cards)
        if (order.orderStatus != OrderStatus.PICKED_UP) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StationType.entries.forEach { station ->
                    val summary = orderWithItems.stationSummary(station)
                    val labelColor = when (summary) {
                        StationSummaryState.READY -> KsGreenText
                        StationSummaryState.PREPARING -> KsAmberText
                        StationSummaryState.PENDING -> KsTextPrimary
                        StationSummaryState.NOT_REQUIRED -> KsTextMuted
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .background(KsBackground)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = station.displayName.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = KsTextMuted,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = summary.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 3: Line Items (show live status while Preparing; station name once Ready/Picked Up)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            orderWithItems.items.forEach { item ->
                val showLiveItemStatus = order.orderStatus == OrderStatus.PREPARING
                val metaColor = if (showLiveItemStatus) {
                    when (item.itemStatus) {
                        ItemStatus.READY -> KsGreenText
                        ItemStatus.PREPARING -> KsAmberText
                        ItemStatus.PENDING -> KsTextSecondary
                    }
                } else {
                    KsTextMuted
                }
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
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = KsTextPrimary
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
                        text = if (showLiveItemStatus) {
                            item.itemStatus.displayName
                        } else {
                            item.stationType.displayName
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = metaColor
                    )
                }
            }
        }

        // Push footer to card bottom so multi-column cards align evenly
        Spacer(modifier = Modifier.weight(1f, fill = true))

        // Row 4: Bordered Cancel Order Button (opens detailed confirmation modal)
        if (orderWithItems.canCancel) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                PlainOutlineButton(
                    text = if (isPending) "Canceling…" else "Cancel Order",
                    onClick = onRequestCancel,
                    enabled = !isPending,
                    borderColor = KsRedDanger.copy(alpha = 0.55f),
                    textColor = KsRedText,
                    minHeight = 36.dp
                )
            }
        }
    }
}

@Composable
private fun OrderTimingPill(
    text: String,
    status: OrderStatus,
    isOverdue: Boolean
) {
    val (bg, border, textColor) = when {
        isOverdue -> Triple(KsRedDanger, KsRedDanger, Color.White)
        status == OrderStatus.PREPARING -> Triple(KsAmberSurface, KsAmberWarning.copy(alpha = 0.4f), KsAmberText)
        status == OrderStatus.READY -> Triple(KsGreenSurface, KsGreenPrimary.copy(alpha = 0.45f), KsGreenText)
        status == OrderStatus.ACKNOWLEDGED -> Triple(KsBlueSurface, KsBlueInfo.copy(alpha = 0.45f), KsBlueText)
        else -> Triple(KsSurfaceElevated, KsBorder, KsTextSecondary)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun CancelOrderConfirmModal(
    orderWithItems: OrderWithItems,
    nowEpochMillis: Long,
    isPending: Boolean,
    onDismiss: () -> Unit,
    onConfirmCancel: () -> Unit
) {
    val order = orderWithItems.order
    val totalQty = orderWithItems.items.sumOf { it.quantity }
    val statusColor = when (order.orderStatus) {
        OrderStatus.PREPARING -> KsAmberText
        OrderStatus.READY -> KsGreenText
        OrderStatus.ACKNOWLEDGED -> KsBlueText
        else -> KsTextPrimary
    }

    val (timingLabel, timingValue) = when (order.orderStatus) {
        OrderStatus.READY -> "Waiting" to formatElapsedShort(orderWithItems.readyElapsedSeconds(nowEpochMillis))
        OrderStatus.ACKNOWLEDGED -> {
            val readyMs = order.readyAtMillis
            val ackMs = order.acknowledgedAtMillis
            val ackText = if (readyMs != null && ackMs != null) {
                formatElapsedShort(((ackMs - readyMs) / 1000L).coerceAtLeast(0L))
            } else {
                "Confirmed"
            }
            "Waiter Ack" to ackText
        }
        else -> {
            val ageSec = ((nowEpochMillis - order.createdAtMillis) / 1000L).coerceAtLeast(0L)
            "Elapsed" to formatElapsedShort(ageSec)
        }
    }

    val statusImpact = when (order.orderStatus) {
        OrderStatus.PREPARING -> "Removes this ticket from active Grill, Fryer, and Pastry displays immediately."
        OrderStatus.READY -> "Clears the active ready alert from the waiter phone and paired Wear OS watch."
        OrderStatus.ACKNOWLEDGED -> "The waiter already acknowledged this alert. Notify them directly if items were already plated."
        else -> "This ticket will be closed and removed from active displays."
    }

    PlainConfirmModal(
        title = "Cancel Table ${order.tableNumber}",
        subtitle = "Order ${orderWithItems.displayOrderNumber} · Placed ${formatClockShort(order.createdAt)}",
        confirmLabel = if (isPending) "Canceling…" else "Yes, Cancel Order",
        cancelLabel = "Keep Order",
        destructive = true,
        busy = isPending,
        onDismiss = onDismiss,
        onConfirm = onConfirmCancel
    ) {
        ConfirmSummaryCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .background(KsSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConfirmMetaCell(
                    label = "Status",
                    value = order.orderStatus.displayName,
                    valueColor = statusColor,
                    modifier = Modifier.weight(1f)
                )
                ConfirmMetaCell(
                    label = "Items",
                    value = "$totalQty (${orderWithItems.items.size} ${if (orderWithItems.items.size == 1) "line" else "lines"})",
                    modifier = Modifier.weight(1f)
                )
                ConfirmMetaCell(
                    label = timingLabel,
                    value = timingValue,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orderWithItems.items.forEach { item ->
                    val itemColor = when (item.itemStatus) {
                        ItemStatus.READY -> KsGreenText
                        ItemStatus.PREPARING -> KsAmberText
                        ItemStatus.PENDING -> KsTextSecondary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.quantity}×",
                                style = MaterialTheme.typography.bodyLarge,
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
                            text = "${item.stationType.displayName} · ${item.itemStatus.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = itemColor
                        )
                    }
                }
            }
        }

        ConfirmNoticeBlock(
            title = "Action Cannot Be Undone",
            body = statusImpact,
            isDanger = true
        )
    }
}

@Composable
private fun CreateOrderModal(
    onDismiss: () -> Unit,
    onSubmit: (tableNumber: Int, items: List<CreateOrderItemPayload>) -> Unit
) {
    var tableNumberText by remember { mutableStateOf("12") }
    var stationFilter by remember { mutableStateOf<StationType?>(null) }
    val quantities = remember { mutableStateMapOf<String, Int>() }
    var validationError by remember { mutableStateOf<String?>(null) }

    val quickTables = (1..20).toList()
    val selectedMenuEntries = PresetMenu.items.filter { (quantities[it.id] ?: 0) > 0 }
    val totalSelectedItems = selectedMenuEntries.sumOf { quantities[it.id] ?: 0 }
    val activeStationCount = selectedMenuEntries.map { it.station }.distinct().size
    val parsedTable = tableNumberText.toIntOrNull()
    val hasValidTable = parsedTable != null && parsedTable in 1..999

    PlainModalDialog(
        title = "New Table Order",
        subtitle = "Tap a table number and tap any dish below to build the ticket.",
        headerIcon = null,
        onDismiss = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Unified Touch Table Selection Bar (Stepper + Tables 1..20)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = KsBackground,
                border = BorderStroke(1.dp, KsBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(KsSurfaceElevated)
                            .border(1.dp, KsBorderStrong, RoundedCornerShape(5.dp))
                            .clickable {
                                val next = ((parsedTable ?: 1) - 1).coerceIn(1, 999)
                                tableNumberText = next.toString()
                                validationError = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = KsTextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .width(60.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(KsSurfaceElevated)
                            .border(
                                1.dp,
                                if (hasValidTable) KsBorderStrong else KsRedDanger,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = KsTextMuted
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        BasicTextField(
                            value = tableNumberText,
                            onValueChange = { input ->
                                tableNumberText = input.filter { it.isDigit() }.take(3)
                                validationError = null
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(KsGreenPrimary),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = KsTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(KsSurfaceElevated)
                            .border(1.dp, KsBorderStrong, RoundedCornerShape(5.dp))
                            .clickable {
                                val next = ((parsedTable ?: 1) + 1).coerceIn(1, 999)
                                tableNumberText = next.toString()
                                validationError = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = KsTextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(KsBorder)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickTables.forEach { num ->
                            val isSelected = tableNumberText == num.toString()
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isSelected) KsGreenPrimary else KsSurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) KsGreenPrimary else KsBorder,
                                        RoundedCornerShape(5.dp)
                                    )
                                    .clickable {
                                        tableNumberText = num.toString()
                                        validationError = null
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = num.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else KsTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Station Filter Tabs with Selected Count Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filterOptions: List<Pair<String, StationType?>> = listOf(
                    "All" to null,
                    "Grill" to StationType.GRILL,
                    "Fryer" to StationType.FRYER,
                    "Pastry" to StationType.PASTRY
                )
                filterOptions.forEach { (label, st) ->
                    val selected = stationFilter == st
                    val stationCount = if (st == null) {
                        totalSelectedItems
                    } else {
                        PresetMenu.byStation(st).sumOf { quantities[it.id] ?: 0 }
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selected) KsSurfaceElevated else KsSurfaceSubtle)
                            .border(
                                1.dp,
                                if (selected) KsGreenPrimary else KsBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { stationFilter = st }
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) KsTextPrimary else KsTextSecondary,
                            maxLines = 1
                        )
                        if (stationCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$stationCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KsGreenText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Scrollable Menu List Grouped by Station
            val displayedStations = if (stationFilter != null) {
                listOf(stationFilter!!)
            } else {
                StationType.entries
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayedStations.forEach { station ->
                    val stationItems = PresetMenu.byStation(station)
                    val stationSelectedQty = stationItems.sumOf { quantities[it.id] ?: 0 }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = KsBackground,
                        border = BorderStroke(1.dp, KsBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = station.displayName.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = KsTextSecondary
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (stationSelectedQty > 0) KsGreenSurface else KsSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (stationSelectedQty > 0) KsGreenPrimary else KsBorder,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$stationSelectedQty",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stationSelectedQty > 0) KsGreenText else KsTextMuted
                                    )
                                }
                            }

                            stationItems.forEach { menuItem ->
                                val qty = quantities[menuItem.id] ?: 0
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            quantities[menuItem.id] = (qty + 1).coerceAtMost(20)
                                            validationError = null
                                        },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (qty > 0) KsGreenSurface else KsSurfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (qty > 0) KsGreenPrimary else KsBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = menuItem.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (qty > 0) FontWeight.SemiBold else FontWeight.Medium,
                                            color = KsTextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (qty == 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(KsSurfaceSubtle)
                                                    .border(1.dp, KsBorderStrong, RoundedCornerShape(5.dp))
                                                    .clickable {
                                                        quantities[menuItem.id] = 1
                                                        validationError = null
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "+",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = KsTextSecondary
                                                )
                                            }
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(KsSurfaceSubtle)
                                                        .border(1.dp, KsBorderStrong, RoundedCornerShape(5.dp))
                                                        .clickable {
                                                            val next = (qty - 1).coerceAtLeast(0)
                                                            if (next == 0) {
                                                                quantities.remove(menuItem.id)
                                                            } else {
                                                                quantities[menuItem.id] = next
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "−",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = KsTextPrimary
                                                    )
                                                }
                                                Text(
                                                    text = "${qty}×",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KsGreenText
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(KsSurfaceSubtle)
                                                        .border(1.dp, KsBorderStrong, RoundedCornerShape(5.dp))
                                                        .clickable {
                                                            quantities[menuItem.id] = (qty + 1).coerceAtMost(20)
                                                            validationError = null
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "+",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = KsTextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Live Order Summary Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = KsBackground,
                border = BorderStroke(1.dp, KsBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ORDER SUMMARY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KsTextSecondary
                            )
                            if (totalSelectedItems > 0) {
                                Text(
                                    text = "· $totalSelectedItems ${if (totalSelectedItems == 1) "item" else "items"} ($activeStationCount ${if (activeStationCount == 1) "station" else "stations"})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KsGreenText
                                )
                            }
                        }
                        if (totalSelectedItems > 0) {
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = KsTextSecondary,
                                modifier = Modifier.clickable {
                                    quantities.clear()
                                    validationError = null
                                }
                            )
                        }
                    }

                    if (selectedMenuEntries.isEmpty()) {
                        Text(
                            text = "No items selected yet — tap any dish above to add it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KsTextMuted
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            selectedMenuEntries.forEach { item ->
                                val qty = quantities[item.id] ?: 0
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(KsSurfaceElevated)
                                        .border(1.dp, KsGreenPrimary.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                                        .clickable {
                                            quantities.remove(item.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${qty}×",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KsGreenText
                                    )
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = KsTextPrimary
                                    )
                                    Text(
                                        text = "✕",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = KsTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KsRedText
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Footer Buttons (equal height, clean spacing)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlainOutlineButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    minHeight = 42.dp,
                    modifier = Modifier.weight(0.4f)
                )
                PlainPrimaryButton(
                    text = if (totalSelectedItems > 0) {
                        "Send Order ($totalSelectedItems)"
                    } else {
                        "Send Order"
                    },
                    enabled = totalSelectedItems > 0 && hasValidTable,
                    onClick = {
                        val tableNum = tableNumberText.toIntOrNull() ?: 0
                        val selectedPayloads = selectedMenuEntries.map { item ->
                            CreateOrderItemPayload(
                                menuItemId = item.id,
                                menuLabel = item.label,
                                quantity = quantities[item.id] ?: 1,
                                station = item.station.id
                            )
                        }
                        if (tableNum !in 1..999) {
                            validationError = "Enter a table number between 1 and 999."
                        } else if (selectedPayloads.isEmpty()) {
                            validationError = "Tap at least one menu item to add it to the order."
                        } else {
                            onSubmit(tableNum, selectedPayloads)
                        }
                    },
                    minHeight = 42.dp,
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    }
}
