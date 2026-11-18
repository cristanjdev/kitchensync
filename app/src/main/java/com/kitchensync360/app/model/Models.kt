package com.kitchensync360.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.kitchensync360.app.BuildConfig
import java.time.Instant
import java.time.OffsetDateTime

const val DEMO_RESTAURANT_ID = "rest_demo_001"

/** Demonstration default; change UNACKNOWLEDGED_TIMEOUT_SECONDS in app/build.gradle.kts. */
val UNACKNOWLEDGED_TIMEOUT_SECONDS: Long = BuildConfig.UNACKNOWLEDGED_TIMEOUT_SECONDS

enum class AppRole(val id: String, val label: String) {
    CONTROLLER("controller", "Dashboard (Controller)"),
    KITCHEN("kitchen", "Kitchen Station"),
    WAITER("waiter", "Waiter");

    companion object {
        fun fromId(value: String): AppRole =
            entries.firstOrNull { it.id.equals(value.trim(), ignoreCase = true) } ?: CONTROLLER
    }
}

enum class StationType(val id: String, val displayName: String) {
    GRILL("grill", "Grill"),
    FRYER("fryer", "Fryer"),
    PASTRY("pastry", "Pastry");

    companion object {
        fun fromId(value: String): StationType =
            entries.firstOrNull { it.id.equals(value.trim(), ignoreCase = true) } ?: GRILL
    }
}

enum class OrderStatus(val id: String, val displayName: String) {
    PREPARING("preparing", "Preparing"),
    READY("ready", "Ready"),
    ACKNOWLEDGED("acknowledged", "Acknowledged"),
    PICKED_UP("picked_up", "Picked Up"),
    CANCELED("canceled", "Canceled");

    companion object {
        fun fromId(value: String): OrderStatus =
            entries.firstOrNull { it.id.equals(value.trim(), ignoreCase = true) } ?: PREPARING
    }
}

enum class ItemStatus(val id: String, val displayName: String) {
    PENDING("pending", "Pending"),
    PREPARING("preparing", "Preparing"),
    READY("ready", "Ready");

    companion object {
        fun fromId(value: String): ItemStatus =
            entries.firstOrNull { it.id.equals(value.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class StationSummaryState(val label: String) {
    NOT_REQUIRED("Not Required"),
    PENDING("Pending"),
    PREPARING("Preparing"),
    READY("Ready")
}

data class MenuItem(
    val id: String,
    val label: String,
    val station: StationType
)

object PresetMenu {
    val items: List<MenuItem> = listOf(
        // Grill Station
        MenuItem(id = "grill_chicken", label = "Grilled Chicken", station = StationType.GRILL),
        MenuItem(id = "grill_salmon", label = "Pan-Seared Salmon", station = StationType.GRILL),
        MenuItem(id = "grill_burger", label = "Beef Burger", station = StationType.GRILL),
        MenuItem(id = "grill_pasta", label = "Herb Charred Pasta", station = StationType.GRILL),
        // Fryer Station
        MenuItem(id = "fryer_fries", label = "French Fries", station = StationType.FRYER),
        MenuItem(id = "fryer_onion_rings", label = "Onion Rings", station = StationType.FRYER),
        MenuItem(id = "fryer_croquettes", label = "Potato Croquettes", station = StationType.FRYER),
        MenuItem(id = "fryer_calamari", label = "Crispy Calamari", station = StationType.FRYER),
        // Pastry Station
        MenuItem(id = "pastry_choco_cake", label = "Chocolate Cake", station = StationType.PASTRY),
        MenuItem(id = "pastry_cheesecake", label = "New York Cheesecake", station = StationType.PASTRY),
        MenuItem(id = "pastry_apple_tart", label = "Warm Apple Tart", station = StationType.PASTRY)
    )

    fun byStation(station: StationType): List<MenuItem> =
        items.filter { it.station == station }
}

data class PresetDemoAccount(
    val userId: String,
    val email: String,
    val password: String,
    val role: AppRole,
    val displayName: String,
    val description: String
)

object PresetAccounts {
    /** Shared demo password: set only in debug builds (see app/build.gradle.kts); empty in release. */
    val DEFAULT_DEMO_PASSWORD: String = BuildConfig.DEMO_PASSWORD

    val accounts = listOf(
        PresetDemoAccount(
            userId = "11111111-1111-1111-1111-111111111111",
            email = "controller@kitchensync360.com",
            password = DEFAULT_DEMO_PASSWORD,
            role = AppRole.CONTROLLER,
            displayName = "Central Controller",
            description = "Creates orders, monitors Grill/Fryer/Pastry status, tracks 30s acknowledgment timeout."
        ),
        PresetDemoAccount(
            userId = "22222222-2222-2222-2222-222222222222",
            email = "kitchen@kitchensync360.com",
            password = DEFAULT_DEMO_PASSWORD,
            role = AppRole.KITCHEN,
            displayName = "Kitchen Station Staff",
            description = "Switches between Grill, Fryer, and Pastry stations to accept orders and mark items ready."
        ),
        PresetDemoAccount(
            userId = "33333333-3333-3333-3333-333333333333",
            email = "waiter@kitchensync360.com",
            password = DEFAULT_DEMO_PASSWORD,
            role = AppRole.WAITER,
            displayName = "Assigned Waiter",
            description = "Receives phone & Wear OS watch notifications, acknowledges ready alerts, confirms pickup."
        )
    )
}

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    @SerialName("restaurant_id") val restaurantId: String = DEMO_RESTAURANT_ID,
    val role: String,
    @SerialName("display_name") val displayName: String = ""
) {
    val appRole: AppRole get() = AppRole.fromId(role)
}

@Serializable
data class OrderDto(
    val id: String,
    @SerialName("order_number") val orderNumber: Int,
    @SerialName("restaurant_id") val restaurantId: String = DEMO_RESTAURANT_ID,
    @SerialName("table_number") val tableNumber: Int,
    @SerialName("assigned_waiter_id") val assignedWaiterId: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    @SerialName("picked_up_at") val pickedUpAt: String? = null,
    @SerialName("canceled_at") val canceledAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val orderStatus: OrderStatus get() = OrderStatus.fromId(status)

    val createdAtMillis: Long get() = parseIsoMillis(createdAt)
    val readyAtMillis: Long? get() = readyAt?.let { parseIsoMillis(it) }
    val acknowledgedAtMillis: Long? get() = acknowledgedAt?.let { parseIsoMillis(it) }
    val pickedUpAtMillis: Long? get() = pickedUpAt?.let { parseIsoMillis(it) }
}

@Serializable
data class OrderItemDto(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("restaurant_id") val restaurantId: String = DEMO_RESTAURANT_ID,
    @SerialName("menu_item_id") val menuItemId: String,
    @SerialName("menu_label") val menuLabel: String,
    val quantity: Int,
    val station: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    val stationType: StationType get() = StationType.fromId(station)
    val itemStatus: ItemStatus get() = ItemStatus.fromId(status)
}

@Serializable
data class CreateOrderItemPayload(
    @SerialName("menu_item_id") val menuItemId: String,
    @SerialName("menu_label") val menuLabel: String,
    val quantity: Int,
    val station: String
)

data class OrderWithItems(
    val order: OrderDto,
    val items: List<OrderItemDto>
) {
    val displayOrderNumber: String
        get() = "#${order.orderNumber.toString().padStart(3, '0')}"

    fun itemsForStation(station: StationType): List<OrderItemDto> =
        items.filter { it.stationType == station }

    fun stationSummary(station: StationType): StationSummaryState {
        val stationItems = itemsForStation(station)
        if (stationItems.isEmpty()) return StationSummaryState.NOT_REQUIRED
        val allReady = stationItems.all { it.itemStatus == ItemStatus.READY }
        if (allReady) return StationSummaryState.READY
        val anyStarted = stationItems.any {
            it.itemStatus == ItemStatus.PREPARING || it.itemStatus == ItemStatus.READY
        }
        return if (anyStarted) StationSummaryState.PREPARING else StationSummaryState.PENDING
    }

    fun readyElapsedSeconds(nowEpochMillis: Long): Long {
        if (order.orderStatus != OrderStatus.READY) return 0L
        val readyMs = order.readyAtMillis ?: return 0L
        return ((nowEpochMillis - readyMs) / 1000L).coerceAtLeast(0L)
    }

    fun isUnacknowledgedOverdue(
        nowEpochMillis: Long,
        timeoutSeconds: Long = UNACKNOWLEDGED_TIMEOUT_SECONDS
    ): Boolean {
        if (order.orderStatus != OrderStatus.READY) return false
        return readyElapsedSeconds(nowEpochMillis) >= timeoutSeconds
    }

    val canCancel: Boolean
        get() = order.orderStatus in setOf(
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.ACKNOWLEDGED
        )
}

enum class ConnectionMode(val label: String) {
    SUPABASE_CLOUD("Supabase Cloud"),
    LOCAL_DEMO("On-Device Demo Engine")
}

enum class SyncStatus(val label: String) {
    CONNECTED("Connected"),
    SYNCING("Syncing..."),
    RECONNECTING("Reconnecting..."),
    DISCONNECTED("Connection Error")
}

/**
 * Parses backend timestamps. PostgREST returns offsets such as "+00:00"
 * ("2026-11-16T12:00:00.123456+00:00"), which Instant.parse rejects on Android 8-13
 * (java.time there predates JDK 12). OffsetDateTime accepts both "+00:00" and "Z".
 */
fun parseIsoMillis(isoString: String): Long {
    val value = isoString.trim()
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
