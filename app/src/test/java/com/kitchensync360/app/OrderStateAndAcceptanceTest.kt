package com.kitchensync360.app

import com.kitchensync360.app.model.ItemStatus
import com.kitchensync360.app.model.OrderDto
import com.kitchensync360.app.model.OrderItemDto
import com.kitchensync360.app.model.OrderStatus
import com.kitchensync360.app.model.OrderWithItems
import com.kitchensync360.app.model.StationSummaryState
import com.kitchensync360.app.model.StationType
import com.kitchensync360.app.model.parseIsoMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OrderStateAndAcceptanceTest {

    @Test
    fun `stationSummary reports Not required for unused stations and tracks Grill Fryer Pastry progress`() {
        val nowIso = Instant.now().toString()
        val order = OrderDto(
            id = "order-1",
            orderNumber = 28,
            tableNumber = 4,
            status = OrderStatus.PREPARING.id,
            createdAt = nowIso
        )
        val items = listOf(
            OrderItemDto(
                id = "i-1",
                orderId = "order-1",
                menuItemId = "grill_chicken",
                menuLabel = "Grilled Chicken",
                quantity = 2,
                station = StationType.GRILL.id,
                status = ItemStatus.READY.id
            ),
            OrderItemDto(
                id = "i-2",
                orderId = "order-1",
                menuItemId = "fryer_fries",
                menuLabel = "French Fries",
                quantity = 1,
                station = StationType.FRYER.id,
                status = ItemStatus.PREPARING.id
            )
        )
        val combined = OrderWithItems(order, items)

        assertEquals(StationSummaryState.READY, combined.stationSummary(StationType.GRILL))
        assertEquals(StationSummaryState.PREPARING, combined.stationSummary(StationType.FRYER))
        assertEquals(StationSummaryState.NOT_REQUIRED, combined.stationSummary(StationType.PASTRY))
    }

    @Test
    fun `30-second unacknowledged timeout warning triggers at 30s and clears on acknowledgment`() {
        val baseInstant = Instant.parse("2026-11-16T12:00:00Z")
        val readyOrder = OrderDto(
            id = "order-timeout",
            orderNumber = 29,
            tableNumber = 9,
            status = OrderStatus.READY.id,
            createdAt = baseInstant.minusSeconds(120).toString(),
            readyAt = baseInstant.toString()
        )
        val orderWithItems = OrderWithItems(readyOrder, emptyList())

        // At 29 seconds: not yet overdue
        val at29s = baseInstant.plusSeconds(29).toEpochMilli()
        assertFalse(orderWithItems.isUnacknowledgedOverdue(at29s, 30L))

        // At 30 seconds: overdue warning active
        val at30s = baseInstant.plusSeconds(30).toEpochMilli()
        assertTrue(orderWithItems.isUnacknowledgedOverdue(at30s, 30L))
        assertEquals(30L, orderWithItems.readyElapsedSeconds(at30s))

        // Once acknowledged: warning clears immediately
        val ackedOrder = readyOrder.copy(
            status = OrderStatus.ACKNOWLEDGED.id,
            acknowledgedAt = baseInstant.plusSeconds(35).toString()
        )
        val ackedWithItems = OrderWithItems(ackedOrder, emptyList())
        assertFalse(ackedWithItems.isUnacknowledgedOverdue(baseInstant.plusSeconds(45).toEpochMilli(), 30L))
    }

    @Test
    fun `backend timestamps with numeric offsets and microseconds parse to the exact instant`() {
        val expected = Instant.parse("2026-11-16T12:00:00.123Z").toEpochMilli()
        // PostgREST / Supabase format for timestamptz columns
        assertEquals(expected, parseIsoMillis("2026-11-16T12:00:00.123456+00:00"))
        assertEquals(expected, parseIsoMillis("2026-11-16T20:00:00.123+08:00"))
        assertEquals(expected, parseIsoMillis("2026-11-16T12:00:00.123Z"))

        val readyOrder = OrderDto(
            id = "order-cloud",
            orderNumber = 40,
            tableNumber = 3,
            status = OrderStatus.READY.id,
            createdAt = "2026-11-16T11:58:00.000000+00:00",
            readyAt = "2026-11-16T12:00:00.000000+00:00"
        )
        val readyMs = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
        val withItems = OrderWithItems(readyOrder, emptyList())
        assertEquals(31L, withItems.readyElapsedSeconds(readyMs + 31_000))
        assertTrue(withItems.isUnacknowledgedOverdue(readyMs + 31_000, 30L))
    }

    @Test
    fun `cancellation allowed before pickup and blocked after pickup`() {
        val nowIso = Instant.now().toString()
        val preparing = OrderWithItems(
            OrderDto("1", 30, tableNumber = 1, status = OrderStatus.PREPARING.id, createdAt = nowIso),
            emptyList()
        )
        val ready = OrderWithItems(
            OrderDto("2", 31, tableNumber = 2, status = OrderStatus.READY.id, createdAt = nowIso),
            emptyList()
        )
        val acked = OrderWithItems(
            OrderDto("3", 32, tableNumber = 3, status = OrderStatus.ACKNOWLEDGED.id, createdAt = nowIso),
            emptyList()
        )
        val pickedUp = OrderWithItems(
            OrderDto("4", 33, tableNumber = 4, status = OrderStatus.PICKED_UP.id, createdAt = nowIso),
            emptyList()
        )

        assertTrue(preparing.canCancel)
        assertTrue(ready.canCancel)
        assertTrue(acked.canCancel)
        assertFalse(pickedUp.canCancel)
    }
}
