package com.unitrading.app

import com.unitrading.broker.*
import kotlin.test.*

class CopyLimitIntentTest {
    private val leader = CopyAccountKey("leader-login", "1")
    private val follower = CopyFollowerConfiguration(CopyAccountKey("follower-login", "2"), 150, CopyContractSize.MICRO, 3)
    private val order = PendingOrder("parent-1", OrderRequest("CLZ6", Side.BUY, OrderKind.LIMIT, 100,
        entryPrice = 70_000_000, stopLoss = 69_000_000, takeProfit = 72_000_000), 0, version = 7)
    private val target = Instrument("MCLZ6", "Micro WTI", "futures", 6, tickSize = 10_000, tickValueMicrosPerLot = 1)

    private fun prepare(entry: PendingOrder = order, spec: Instrument? = target, protection: Boolean = true) =
        prepareCopyLimit(leader, follower, entry, 10, 20, true, true, protection, true, spec, "req-1")

    @Test fun carriesLimitAndBracketsWithPerAccountRoundedQuantity() {
        val intent = assertIs<CopyLimitPreparation.Ready>(prepare())
        val request = assertNotNull(intent.link.targetRequest)
        assertEquals("MCLZ6", request.symbol)
        assertEquals(200L, request.lotsHundredths)
        assertEquals(OrderKind.LIMIT, request.kind)
        assertEquals(order.request.entryPrice, request.entryPrice)
        assertEquals(order.request.stopLoss, request.stopLoss)
        assertEquals(order.request.takeProfit, request.takeProfit)
        assertTrue(request.automated)
        assertEquals(20L, request.copyExpectedRevision)
        assertEquals(10L, intent.sourceRevision)
    }

    @Test fun neverPlacesAStaleOrPartiallyFilledLeaderOrder() {
        assertIs<CopyLimitPreparation.Stop>(prepare(order.copy(filledLotsHundredths = 100)))
        assertIs<CopyLimitPreparation.Stop>(prepare(order.copy(editable = false)))
        assertIs<CopyLimitPreparation.Stop>(prepare(order.copy(request = order.request.copy(kind = OrderKind.MARKET))))
    }

    @Test fun missingProtectionWrongMonthOrIncompatibleTickStops() {
        assertIs<CopyLimitPreparation.Stop>(prepare(protection = false))
        assertIs<CopyLimitPreparation.Stop>(prepare(spec = target.copy(symbol = "MCLF7")))
        assertIs<CopyLimitPreparation.Stop>(prepare(order.copy(request = order.request.copy(entryPrice = 70_000_001))))
    }
}
