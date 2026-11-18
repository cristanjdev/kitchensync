package com.kitchensync360.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.kitchensync360.app.model.OrderItemDto
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.StationSummaryState
import com.kitchensync360.app.model.StationType
import com.kitchensync360.app.ui.components.PlainCard
import com.kitchensync360.app.ui.components.PlainPrimaryButton
import com.kitchensync360.app.ui.components.StatusCountPill
import com.kitchensync360.app.ui.components.formatElapsedShort
import com.kitchensync360.app.ui.components.responsiveCardRows
import com.kitchensync360.app.ui.components.responsiveColumnCount
import com.kitchensync360.app.ui.theme.KsAmberSurface
import com.kitchensync360.app.ui.theme.KsAmberText
import com.kitchensync360.app.ui.theme.KsAmberWarning
import com.kitchensync360.app.ui.theme.KsBackground
import com.kitchensync360.app.ui.theme.KsBorder
import com.kitchensync360.app.ui.theme.KsBorderStrong
import com.kitchensync360.app.ui.theme.KsGreenPrimary
import com.kitchensync360.app.ui.theme.KsGreenSurface
import com.kitchensync360.app.ui.theme.KsGreenText
import com.kitchensync360.app.ui.theme.KsSurfaceElevated
import com.kitchensync360.app.ui.theme.KsSurfaceSubtle
import com.kitchensync360.app.ui.theme.KsTextMuted
import com.kitchensync360.app.ui.theme.KsTextPrimary
import com.kitchensync360.app.ui.theme.KsTextSecondary
import com.kitchensync360.app.ui.theme.StationFryerAccent
import com.kitchensync360.app.ui.theme.StationGrillAccent
import com.kitchensync360.app.ui.theme.StationPastryAccent
import kotlinx.coroutines.delay

@Composable
fun StationScreen(
    snapshot: RepositorySnapshot,
    onSelectStation: (StationType) -> Unit,
    onAcceptStationOrder: (orderId: String, station: StationType) -> Unit,
    onMarkItemReady: (itemId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStation = snapshot.selectedStation
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val stationOrders = remember(snapshot.orders, activeStation) {
        snapshot.orders.filter { orderWithItems ->
            val status = orderWithItems.order.orderStatus
            val isActiveOrder = status != OrderStatus.CANCELED && status != OrderStatus.PICKED_UP
            val hasStationItems = orderWithItems.itemsForStation(activeStation).isNotEmpty()
            isActiveOrder && hasStationItems
        }
    }

    val openStationOrders = remember(stationOrders, activeStation) {
        stationOrders
            .filter { it.stationSummary(activeStation) != StationSummaryState.READY }
            .sortedBy { it.order.orderNumber }
    }
    val completedStationOrders = remember(stationOrders, activeStation) {
        stationOrders
            .filter { it.stationSummary(activeStation) == StationSummaryState.READY }
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

            // Station Selector Bar: Grill · Fryer · Pastry (no dots, highlighted count badges)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StationType.entries.forEach { station ->
                    val isSelected = station == activeStation
                    val accentColor = when (station) {
                        StationType.GRILL -> StationGrillAccent
                        StationType.FRYER -> StationFryerAccent
                        StationType.PASTRY -> StationPastryAccent
                    }
                    val openLinesCount = snapshot.orders.sumOf { orderWithItems ->
                        if (orderWithItems.order.orderStatus in setOf(OrderStatus.CANCELED, OrderStatus.PICKED_UP)) {
                            0
                        } else {
                            orderWithItems.itemsForStation(station)
                                .count { it.itemStatus != ItemStatus.READY }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectStation(station) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) KsSurfaceElevated else KsSurfaceSubtle,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else KsBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = station.displayName.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) accentColor else KsTextSecondary,
                                    maxLines = 1
                                )
                                StatusCountPill(
                                    count = openLinesCount,
                                    textColor = if (openLinesCount > 0) KsAmberText else KsTextMuted,
                                    backgroundColor = if (openLinesCount > 0) KsAmberSurface else KsBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (openLinesCount == 0) {
                                    "All Caught Up"
                                } else {
                                    "$openLinesCount To Cook"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) KsTextSecondary else KsTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (stationOrders.isEmpty()) {
                PlainCard {
                    Text(
                        text = "${activeStation.displayName} Station Is Clear",
                        style = MaterialTheme.typography.titleLarge,
                        color = KsTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "New tickets with ${activeStation.displayName} items appear here automatically. Canceled and collected orders are hidden.",
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
                    if (openStationOrders.isNotEmpty()) {
                        item(key = "header_open_${activeStation.id}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Active ${activeStation.displayName} Queue",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KsAmberText
                                    )
                                    StatusCountPill(
                                        count = openStationOrders.size,
                                        textColor = KsAmberText,
                                        backgroundColor = KsAmberSurface,
                                        borderColor = KsAmberWarning.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        responsiveCardRows(
                            itemsList = openStationOrders,
                            columnCount = columnCount,
                            keyPrefix = "open_${activeStation.id}",
                            itemKey = { it.order.id }
                        ) { orderWithItems ->
                            StationOrderTicketCard(
                                orderWithItems = orderWithItems,
                                station = activeStation,
                                stationItems = orderWithItems.itemsForStation(activeStation),
                                nowEpochMillis = nowEpochMillis,
                                isOrderPending = snapshot.pendingOrderIds.contains(orderWithItems.order.id),
                                pendingItemIds = snapshot.pendingItemIds,
                                onAcceptStationOrder = {
                                    onAcceptStationOrder(orderWithItems.order.id, activeStation)
                                },
                                onMarkItemReady = onMarkItemReady
                            )
                        }
                    }

                    if (completedStationOrders.isNotEmpty()) {
                        item(key = "header_done_${activeStation.id}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${activeStation.displayName} Ready",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KsGreenText
                                    )
                                    StatusCountPill(
                                        count = completedStationOrders.size,
                                        textColor = KsGreenText,
                                        backgroundColor = KsGreenSurface,
                                        borderColor = KsGreenPrimary.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        responsiveCardRows(
                            itemsList = completedStationOrders,
                            columnCount = columnCount,
                            keyPrefix = "done_${activeStation.id}",
                            itemKey = { it.order.id }
                        ) { orderWithItems ->
                            StationOrderTicketCard(
                                orderWithItems = orderWithItems,
                                station = activeStation,
                                stationItems = orderWithItems.itemsForStation(activeStation),
                                nowEpochMillis = nowEpochMillis,
                                isOrderPending = snapshot.pendingOrderIds.contains(orderWithItems.order.id),
                                pendingItemIds = snapshot.pendingItemIds,
                                onAcceptStationOrder = {
                                    onAcceptStationOrder(orderWithItems.order.id, activeStation)
                                },
                                onMarkItemReady = onMarkItemReady
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StationOrderTicketCard(
    orderWithItems: OrderWithItems,
    station: StationType,
    stationItems: List<OrderItemDto>,
    nowEpochMillis: Long,
    isOrderPending: Boolean,
    pendingItemIds: Set<String>,
    onAcceptStationOrder: () -> Unit,
    onMarkItemReady: (itemId: String) -> Unit
) {
    val hasPendingItems = stationItems.any { it.itemStatus == ItemStatus.PENDING }
    val allStationReady = stationItems.all { it.itemStatus == ItemStatus.READY }
    val ageSeconds = ((nowEpochMillis - orderWithItems.order.createdAtMillis) / 1000L).coerceAtLeast(0L)

    val otherRequiredStations = StationType.entries.filter {
        it != station && orderWithItems.stationSummary(it) != StationSummaryState.NOT_REQUIRED
    }
    val otherReadyCount = otherRequiredStations.count {
        orderWithItems.stationSummary(it) == StationSummaryState.READY
    }

    val stripeColor = when {
        allStationReady -> KsGreenPrimary
        hasPendingItems -> KsTextSecondary
        else -> KsAmberWarning
    }

    val borderColor = when {
        allStationReady -> KsGreenPrimary
        !hasPendingItems -> KsAmberWarning
        else -> KsBorderStrong
    }

    PlainCard(
        modifier = Modifier.fillMaxHeight(),
        borderColor = borderColor,
        accentStripeColor = stripeColor,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Ticket Header: Table Number, Order #, Elapsed Time, Station Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Table ${orderWithItems.order.tableNumber}",
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "· ${formatElapsedShort(ageSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = KsTextMuted
                    )
                }

                if (otherRequiredStations.isNotEmpty() && !allStationReady) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (otherReadyCount == otherRequiredStations.size) {
                            "Other Stations Ready — Finishing This Completes the Order"
                        } else {
                            "Other Stations: $otherReadyCount/${otherRequiredStations.size} Ready"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (otherReadyCount == otherRequiredStations.size) KsAmberText else KsTextMuted
                    )
                }
            }

            val (badgeBg, badgeBorder, badgeText, badgeLabel) = when {
                allStationReady -> listOf(KsGreenSurface, KsGreenPrimary, KsGreenText, "STATION READY")
                hasPendingItems -> listOf(KsSurfaceSubtle, KsBorderStrong, KsTextPrimary, "NEW TICKET")
                else -> listOf(KsAmberSurface, KsAmberWarning, KsAmberText, "COOKING")
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

        // Accept Order CTA
        if (hasPendingItems) {
            Spacer(modifier = Modifier.height(10.dp))
            PlainPrimaryButton(
                text = if (isOrderPending) {
                    "Accepting Order…"
                } else {
                    "Accept Order & Start ${station.displayName}"
                },
                onClick = onAcceptStationOrder,
                enabled = !isOrderPending,
                containerColor = KsAmberWarning,
                minHeight = 40.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Station Line Items (clean tonal rows, no unnecessary divider line)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stationItems.forEach { item ->
                val isItemPendingWrite = pendingItemIds.contains(item.id)
                val isReady = item.itemStatus == ItemStatus.READY
                val isPreparing = item.itemStatus == ItemStatus.PREPARING

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = if (isReady) KsGreenSurface else KsBackground,
                    border = if (isReady) BorderStroke(1.dp, KsGreenPrimary.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${item.quantity}×",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isReady) KsGreenText else KsAmberText
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.menuLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KsTextPrimary
                                )
                                Text(
                                    text = when (item.itemStatus) {
                                        ItemStatus.READY -> "Completed (${item.quantity}×)"
                                        ItemStatus.PREPARING -> "Cooking · Mark Ready When Done"
                                        ItemStatus.PENDING -> "Queued · Accept Ticket First"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (item.itemStatus) {
                                        ItemStatus.READY -> KsGreenText
                                        ItemStatus.PREPARING -> KsTextSecondary
                                        ItemStatus.PENDING -> KsTextMuted
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        when (item.itemStatus) {
                            ItemStatus.READY -> {
                                Text(
                                    text = "✓ READY",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = KsGreenText
                                )
                            }

                            ItemStatus.PREPARING -> {
                                PlainPrimaryButton(
                                    text = if (isItemPendingWrite) "Saving…" else "Mark Ready",
                                    onClick = { onMarkItemReady(item.id) },
                                    enabled = !isItemPendingWrite && !isOrderPending,
                                    minHeight = 38.dp
                                )
                            }

                            ItemStatus.PENDING -> {
                                Text(
                                    text = "Pending",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KsTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = true))
    }
}
