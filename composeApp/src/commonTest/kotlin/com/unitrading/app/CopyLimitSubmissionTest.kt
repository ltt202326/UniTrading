package com.unitrading.app

import com.unitrading.broker.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CopyLimitSubmissionTest {
    private class FakeSession(override val account: AccountInfo, initial: AccountSnapshot, var outcome: CommandOutcome = CommandOutcome.ACCEPTED) : AccountSession {
        override val state = MutableStateFlow(initial)
        val writes = mutableListOf<OrderRequest>()
        override fun preview(request: OrderRequest) = OrderPreview(request.entryPrice)
        override suspend fun placeOrder(requestId: String, request: OrderRequest): TradeResult {
            writes += request
            return if (outcome == CommandOutcome.ACCEPTED) TradeResult(true, "accepted", "follower-order")
                else TradeResult(false, "unknown", outcome = outcome)
        }
        override fun previewOrderModification(orderId: String, entryPrice: Long) = OrderPreview(entryPrice)
        override suspend fun modifyOrder(requestId: String, orderId: String, expectedVersion: Long, entryPrice: Long) = TradeResult(false, "unused")
        override suspend fun closePosition(requestId: String, positionId: String) = TradeResult(false, "unused")
        override suspend fun cancelOrder(requestId: String, orderId: String) = TradeResult(false, "unused")
        override suspend fun modifyProtection(requestId: String, positionId: String, expectedVersion: Long, stopLoss: Long?, takeProfit: Long?) = TradeResult(false, "unused")
    }

    private class FakeBroker(val id: String, val session: FakeSession, val specs: List<Instrument>) : BrokerPlugin {
        override val descriptor = BrokerDescriptor(id, id, "test", environment = BrokerEnvironment.REMOTE_DEMO)
        override val capabilities = BrokerCapabilities(hedging = false, nativeEntryOso = true)
        override val instruments = specs
        override val accounts = listOf(session.account)
        override val catalog = MutableStateFlow(BrokerCatalog(specs, accounts))
        override val market = MutableStateFlow(MarketSnapshot())
        override fun openSession(accountId: String): AccountSession { require(accountId == session.account.id); return session }
        override fun history(symbol: String, timeframe: Timeframe): List<Candle> = emptyList()
    }

    private val targetSpec = Instrument("MCLZ6", "Micro WTI", "futures", 6, tickSize = 10_000, tickValueMicrosPerLot = 1)
    private val sourceKey = CopyAccountKey("tradovate-demo-a", "1")
    private val follower = CopyFollowerConfiguration(CopyAccountKey("tradovate-demo-b", "2"), 150, CopyContractSize.MICRO, 3)
    private val order = PendingOrder("leader-order", OrderRequest("CLZ6", Side.BUY, OrderKind.LIMIT, 100,
        entryPrice = 70_000_000, stopLoss = 69_000_000, takeProfit = 72_000_000), 0, version = 7,
        stopLossOrderId = "sl", takeProfitOrderId = "tp")
    private fun leg(id: String, price: Long, role: OrderRole) = PendingOrder(id,
        OrderRequest("CLZ6", Side.SELL, if (role == OrderRole.STOP_LOSS) OrderKind.STOP else OrderKind.LIMIT, 100, entryPrice = price),
        0, role = role, ocoGroup = "oco-1")
    private fun state(id: String, revision: Long, orders: List<PendingOrder> = emptyList()) = AccountSnapshot(id, 0,
        orders = orders, copyRevision = revision, copyEpoch = 1, copyPositionsComplete = true, copyOrdersClear = orders.isEmpty(), copyOrdersComplete = true)
    private fun intent() = assertIs<CopyLimitPreparation.Ready>(prepareCopyLimit(sourceKey, follower, order, 10, 20, true, true, true, true, targetSpec, "request-1"))

    @Test fun duplicateAttemptCannotRepeatAnAcceptedLimitOrder() = runTest {
        val source = FakeSession(AccountInfo("1", "source"), state("1", 10, listOf(order, leg("sl", 69_000_000, OrderRole.STOP_LOSS), leg("tp", 72_000_000, OrderRole.TAKE_PROFIT))))
        val target = FakeSession(AccountInfo("2", "target"), state("2", 20))
        val sb = FakeBroker(sourceKey.loginId, source, emptyList())
        val tb = FakeBroker(follower.account.loginId, target, listOf(targetSpec))
        val store = MemorySnapshotStore()
        val journal = CopyOrderJournal(store)
        val service = CopyLimitSubmissionService(BrokerRegistry(listOf(sb, tb)), AccountRiskController(store), journal)
        assertIs<CopyLimitSubmission.AwaitingBrokerConfirmation>(service.submit(intent(), follower, sb, tb, true))
        assertIs<CopyLimitSubmission.Blocked>(service.submit(intent(), follower, sb, tb, true))
        assertEquals(1, target.writes.size)
        assertEquals(200L, target.writes.single().lotsHundredths)
        assertEquals(CopyLinkStatus.ACCEPTED, journal.read()!!.single().status)
    }

    @Test fun unresolvedProtectionOrUnknownOutcomeNeverTriggersASecondWrite() = runTest {
        val source = FakeSession(AccountInfo("1", "source"), state("1", 10, listOf(order, leg("sl", 69_000_000, OrderRole.STOP_LOSS), leg("tp", 72_000_000, OrderRole.TAKE_PROFIT))))
        val target = FakeSession(AccountInfo("2", "target"), state("2", 20), CommandOutcome.UNKNOWN)
        val sb = FakeBroker(sourceKey.loginId, source, emptyList())
        val tb = FakeBroker(follower.account.loginId, target, listOf(targetSpec))
        val store = MemorySnapshotStore()
        val journal = CopyOrderJournal(store)
        val service = CopyLimitSubmissionService(BrokerRegistry(listOf(sb, tb)), AccountRiskController(store), journal)
        assertIs<CopyLimitSubmission.Blocked>(service.submit(intent(), follower, sb, tb, false))
        assertTrue(target.writes.isEmpty())
        assertIs<CopyLimitSubmission.NeedsReconciliation>(service.submit(intent(), follower, sb, tb, true))
        assertIs<CopyLimitSubmission.Blocked>(service.submit(intent(), follower, sb, tb, true))
        assertEquals(1, target.writes.size)
        assertEquals(CopyLinkStatus.UNCERTAIN, CopyOrderJournal(store).read()!!.single().status)
    }

    @Test fun leaderChangeAfterJournalButBeforeSendAbortsWithoutPlacingAnOrder() = runTest {
        val source = FakeSession(AccountInfo("1", "source"), state("1", 10, listOf(order, leg("sl", 69_000_000, OrderRole.STOP_LOSS), leg("tp", 72_000_000, OrderRole.TAKE_PROFIT))))
        val target = FakeSession(AccountInfo("2", "target"), state("2", 20))
        val sb = FakeBroker(sourceKey.loginId, source, emptyList())
        val tb = FakeBroker(follower.account.loginId, target, listOf(targetSpec))
        val backing = MemorySnapshotStore()
        var mutateOnce = true
        val store = object : SnapshotStore {
            override fun read(key: String) = backing.read(key)
            override fun write(key: String, value: String) {
                backing.write(key, value)
                if (key == "copy-limit-order-links-v1" && mutateOnce) {
                    mutateOnce = false
                    source.state.value = source.state.value.copy(copyRevision = 11)
                }
            }
        }
        val journal = CopyOrderJournal(store)
        val service = CopyLimitSubmissionService(BrokerRegistry(listOf(sb, tb)), AccountRiskController(store), journal)
        assertIs<CopyLimitSubmission.Blocked>(service.submit(intent(), follower, sb, tb, true))
        assertTrue(target.writes.isEmpty())
        assertEquals(CopyLinkStatus.ABORTED_BEFORE_SEND, journal.read()!!.single().status)
    }
}
