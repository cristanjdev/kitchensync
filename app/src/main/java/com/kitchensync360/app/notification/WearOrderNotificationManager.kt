package com.kitchensync360.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kitchensync360.app.MainActivity
import com.kitchensync360.app.R
import com.kitchensync360.app.model.OrderDto
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems

class WearOrderNotificationManager(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = systemManager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KitchenSync 360 Ready Orders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts waiter phone and paired Wear OS watch when all kitchen stations finish an order."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 350L, 150L, 350L)
                setBypassDnd(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            systemManager.createNotificationChannel(channel)
        }
    }

    fun isNotificationPermissionGranted(): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && notificationManager.areNotificationsEnabled()
    }

    /**
     * Reconciles active orders against saved notification identities (Implementation Plan lines 75-79, 103).
     * - Posts ready alert for READY orders (deduplicated so reconnects/polls do not duplicate alerts).
     * - Updates to Acknowledged state without alerting when an order that had an active ready alert is acknowledged.
     * - Removes notifications for CANCELED or PICKED_UP orders.
     * - Never produces a fresh ready alert for already ACKNOWLEDGED orders after reconnecting.
     */
    fun reconcileOrdersForWaiter(
        orders: List<OrderWithItems>,
        assignedWaiterId: String?
    ): NotificationReconcileResult {
        if (!isNotificationPermissionGranted()) {
            return NotificationReconcileResult(
                permissionGranted = false,
                activeReadyAlertCount = 0
            )
        }

        val alertedIds = getStringSet(KEY_ALERTED_READY_IDS).toMutableSet()
        val activeNotifiedIds = getStringSet(KEY_ACTIVE_NOTIFIED_IDS).toMutableSet()

        val relevantOrders = orders.filter { orderWithItems ->
            val waiterMatch = orderWithItems.order.assignedWaiterId == null ||
                assignedWaiterId == null ||
                orderWithItems.order.assignedWaiterId == assignedWaiterId
            waiterMatch
        }

        val currentOrderIds = relevantOrders.map { it.order.id }.toSet()

        // Clear notifications for orders that disappeared from snapshot
        for (staleId in (activeNotifiedIds - currentOrderIds).toList()) {
            cancelOrderNotification(staleId)
            activeNotifiedIds.remove(staleId)
        }

        var activeReadyCount = 0

        for (item in relevantOrders) {
            val order = item.order
            when (order.orderStatus) {
                OrderStatus.READY -> {
                    activeReadyCount++
                    val alreadyAlerted = alertedIds.contains(order.id)
                    postReadyNotification(
                        order = order,
                        onlyAlertOnce = alreadyAlerted,
                        retryErrorText = null
                    )
                    alertedIds.add(order.id)
                    activeNotifiedIds.add(order.id)
                }

                OrderStatus.ACKNOWLEDGED -> {
                    // Only update existing notification if this device had an active ready notification for it.
                    // Never fire a fresh alert on reconnect for an already-acknowledged order.
                    if (activeNotifiedIds.contains(order.id)) {
                        postAcknowledgedNotification(
                            orderId = order.id,
                            orderNumber = order.orderNumber,
                            tableNumber = order.tableNumber
                        )
                    }
                    alertedIds.add(order.id)
                }

                OrderStatus.PICKED_UP, OrderStatus.CANCELED, OrderStatus.PREPARING -> {
                    if (activeNotifiedIds.contains(order.id)) {
                        cancelOrderNotification(order.id)
                        activeNotifiedIds.remove(order.id)
                    }
                    if (order.orderStatus == OrderStatus.CANCELED) {
                        alertedIds.remove(order.id)
                    }
                }
            }
        }

        saveStringSet(KEY_ALERTED_READY_IDS, alertedIds)
        saveStringSet(KEY_ACTIVE_NOTIFIED_IDS, activeNotifiedIds)

        return NotificationReconcileResult(
            permissionGranted = true,
            activeReadyAlertCount = activeReadyCount
        )
    }

    fun postReadyNotification(
        order: OrderDto,
        onlyAlertOnce: Boolean,
        retryErrorText: String? = null
    ) {
        if (!isNotificationPermissionGranted()) return
        ensureNotificationChannel()

        val formattedNum = "#${order.orderNumber.toString().padStart(3, '0')}"
        val title = "KitchenSync 360 • Order $formattedNum"
        val body = if (retryErrorText.isNullOrBlank()) {
            "Table ${order.tableNumber} • Order Ready"
        } else {
            "Table ${order.tableNumber} • Order Ready ($retryErrorText)"
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ORDER_ID, order.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationIdForOrder(order.id),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ackIntent = Intent(context, OrderNotificationReceiver::class.java).apply {
            action = ACTION_ACKNOWLEDGE_ORDER
            putExtra(EXTRA_ORDER_ID, order.id)
            putExtra(EXTRA_ORDER_NUMBER, order.orderNumber)
            putExtra(EXTRA_TABLE_NUMBER, order.tableNumber)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationIdForOrder(order.id),
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val phoneAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_kitchensync,
            "Acknowledge",
            ackPendingIntent
        )
            .setContextual(false)
            .setShowsUserInterface(false)
            .build()

        val wearableAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_kitchensync,
            "Acknowledge",
            ackPendingIntent
        ).build()

        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(wearableAction)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitchensync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setVibrate(longArrayOf(0L, 350L, 150L, 350L))
            .setContentIntent(contentPendingIntent)
            .addAction(phoneAction)
            .extend(wearableExtender)

        try {
            notificationManager.notify(
                tagForOrder(order.id),
                notificationIdForOrder(order.id),
                builder.build()
            )
        } catch (_: SecurityException) {
            // Permission revoked at OS level
        }
    }

    fun postAcknowledgedNotification(
        orderId: String,
        orderNumber: Int,
        tableNumber: Int
    ) {
        if (!isNotificationPermissionGranted()) return
        ensureNotificationChannel()

        val formattedNum = "#${orderNumber.toString().padStart(3, '0')}"
        val title = "KitchenSync 360 • Order $formattedNum"
        val body = "Table $tableNumber • Acknowledged"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ORDER_ID, orderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationIdForOrder(orderId),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitchensync)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        try {
            notificationManager.notify(
                tagForOrder(orderId),
                notificationIdForOrder(orderId),
                builder.build()
            )
        } catch (_: SecurityException) {
            // Permission revoked
        }
    }

    /**
     * Phase 1 verification helper: sends an immediate test notification with Acknowledge action
     * to verify phone/Wear OS bridging before live pitch.
     */
    fun sendPhase1WatchVerificationAlert(): Boolean {
        if (!isNotificationPermissionGranted()) return false
        val testOrder = OrderDto(
            id = PHASE1_TEST_ORDER_ID,
            orderNumber = 0,
            tableNumber = 1,
            status = OrderStatus.READY.id,
            createdAt = java.time.Instant.now().toString(),
            readyAt = java.time.Instant.now().toString()
        )
        postReadyNotification(
            order = testOrder,
            onlyAlertOnce = false,
            retryErrorText = null
        )
        return true
    }

    fun cancelOrderNotification(orderId: String) {
        notificationManager.cancel(tagForOrder(orderId), notificationIdForOrder(orderId))
    }

    fun clearAllOrderNotifications() {
        notificationManager.cancelAll()
        saveStringSet(KEY_ALERTED_READY_IDS, emptySet())
        saveStringSet(KEY_ACTIVE_NOTIFIED_IDS, emptySet())
    }

    private fun tagForOrder(orderId: String): String = "ks360_order_$orderId"

    private fun notificationIdForOrder(orderId: String): Int =
        (orderId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1001)

    private fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun saveStringSet(key: String, values: Set<String>) {
        prefs.edit().putStringSet(key, values).apply()
    }

    companion object {
        const val CHANNEL_ID = "kitchensync360_ready_orders_v1"
        const val ACTION_ACKNOWLEDGE_ORDER = "com.kitchensync360.app.ACTION_ACKNOWLEDGE_ORDER"
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_ORDER_NUMBER = "extra_order_number"
        const val EXTRA_TABLE_NUMBER = "extra_table_number"
        const val PHASE1_TEST_ORDER_ID = "phase1-watch-test-order"

        private const val PREFS_NAME = "ks360_notification_state"
        private const val KEY_ALERTED_READY_IDS = "alerted_ready_ids"
        private const val KEY_ACTIVE_NOTIFIED_IDS = "active_notified_ids"
    }
}

data class NotificationReconcileResult(
    val permissionGranted: Boolean,
    val activeReadyAlertCount: Int
)
