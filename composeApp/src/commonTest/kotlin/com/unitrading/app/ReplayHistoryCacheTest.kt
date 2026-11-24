package com.unitrading.app

import com.unitrading.broker.*
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private const val DAY = 86_400_000L

class ReplayHistoryCacheTest {
    private class Broker : BrokerPlugin {
        override val descriptor = BrokerDescriptor("test-replay", "Test", "", true, BrokerEnvironment.REMOTE_DEMO)
        override val capabilities = BrokerCapabilities()
        override val instruments = listOf(Instrument("MESZ6", "MES", "期货", 2, tickValueMicrosPerLot = 1),
            Instrument("@MGC", "MGC", "期货", 2, tickValueMicrosPerLot = 1, isContinuous = true, tradeSymbol = "MGCZ6"))
        override val accounts = emptyList<AccountInfo>()
        override val catalog = MutableStateFlow(BrokerCatalog(instruments, accounts))
        override val market = MutableStateFlow(MarketSnapshot())
        var active = 0
        var peak = 0
        var calls = 0
        var cacheable = true
        var aligned = false
        var mixedLeaders = false
        var concurrentGate: CompletableDeferred<Unit>? = null
        override fun openSession(accountId: String): AccountSession = error("not used")
        override fun history(symbol: String, timeframe: Timeframe) = emptyList<Candle>()
        override suspend fun historicalRange(symbol: String, timeframe: Timeframe, startMs: Long, endMs: Long): HistoricalRangeResult {
            active++; peak = maxOf(peak, active); calls++
            try {
                if (active == 2) concurrentGate?.complete(Unit)
                concurrentGate?.await()
                delay(5)
                if (aligned) {
                    fun bar(day: Long, price: Long) = Candle(day * DAY, price, price, price, price)
                    val alias = listOf(bar(1, 100), bar(31, 100), bar(61, 100), bar(91, 100))
                    val dated = listOf(bar(35, 200), bar(50, 200), bar(65, 200))
                    val inRange = dated.filter { it.timeMs in startMs until endMs }
                    val first = inRange.firstOrNull()?.timeMs
                    val combined = alias.filter { it.timeMs in startMs until endMs && (first == null || it.timeMs < first) } + inRange
                    val leader = if (mixedLeaders && startMs >= 60 * DAY) "MGCH7" else "MGCZ6"
                    return HistoricalRangeResult(combined, cacheable = true, leaderHistory = LeaderHistory(first, leader))
                }
                return HistoricalRangeResult(listOf(Candle(startMs, 100, 100, 100, 100)), cacheable = cacheable)
            }
            finally { active-- }
        }
    }

    @Test fun boundedParallelRequestsPersistAndReuseCompletedChunks() = runTest {
        val store = MemorySnapshotStore()
        val broker = Broker().apply { concurrentGate = CompletableDeferred() }
        val first = ReplayHistoryCache(store) { 200 * DAY }.load(broker, "MESZ6", Timeframe.M1, 0, 60 * DAY)
        assertEquals(2, first.downloaded)
        assertEquals(2, broker.peak)
        assertEquals(2, broker.calls)
        val second = ReplayHistoryCache(store) { 200 * DAY }.load(broker, "MESZ6", Timeframe.M1, 0, 60 * DAY)
        assertEquals(2, second.cacheHits)
        assertEquals(first.result.candles, second.result.candles)
        assertEquals(2, broker.calls)
    }

    @Test fun cacheCanBeDisabledAndRecentOrUnconfirmedDataIsNeverPersisted() = runTest {
        val store = MemorySnapshotStore()
        val broker = Broker()
        val cache = ReplayHistoryCache(store) { 100 * DAY }
        repeat(2) { cache.load(broker, "MESZ6", Timeframe.M1, 98 * DAY, 100 * DAY) }
        assertEquals(2, broker.calls)
        broker.cacheable = false
        repeat(2) { cache.load(broker, "MESZ6", Timeframe.M1, 30 * DAY, 60 * DAY) }
        assertEquals(4, broker.calls)
        broker.cacheable = true
        repeat(2) { cache.load(broker, "MESZ6", Timeframe.M1, 0, 30 * DAY, enabled = false) }
        assertEquals(6, broker.calls)
    }

    @Test fun oneRequestFailureDoesNotPersistItsUnfinishedChunk() = runTest {
        val store = MemorySnapshotStore()
        val broker = Broker().apply { cacheable = false }
        val cache = ReplayHistoryCache(store) { 200 * DAY }
        val first = async { cache.load(broker, "MESZ6", Timeframe.M1, 0, 30 * DAY) }
        first.await()
        cache.load(broker, "MESZ6", Timeframe.M1, 0, 30 * DAY)
        assertEquals(2, broker.calls)
    }

    @Test fun alignedReplayNeverFillsLaterLeaderGapsFromCachedContinuousChunks() = runTest {
        val store = MemorySnapshotStore()
        val broker = Broker().apply { aligned = true }
        val first = ReplayHistoryCache(store) { 200 * DAY }.load(broker, "@MGC", Timeframe.M1, 0, 120 * DAY)
        assertEquals(listOf(1L, 31L, 35L, 50L, 65L), first.result.candles.map { it.timeMs / DAY })
        val second = ReplayHistoryCache(store) { 200 * DAY }.load(broker, "@MGC", Timeframe.M1, 0, 120 * DAY)
        assertEquals(4, second.cacheHits)
        assertEquals(first.result.candles, second.result.candles)
    }

    @Test fun oldContinuousCacheIsNotReusedAfterTheLeaderAlignmentUpgrade() = runTest {
        val store = MemorySnapshotStore()
        val broker = Broker().apply { aligned = true }
        val end = 30 * DAY
        val id = "test-replay|@MGC|MGCZ6|${Timeframe.M1.seconds}|0|$end"
        val slot = (id.hashCode().toLong() and 0x7fffffffL).rem(64).toInt()
        store.write("replay-history-v1-$slot", """{"version":1,"broker":"test-replay","symbol":"@MGC","source":"MGCZ6","seconds":60,"start":0,"end":$end,"savedAt":${200 * DAY},"candles":[{"timeMs":0,"open":999,"high":999,"low":999,"close":999}],"warning":null}""")
        val result = ReplayHistoryCache(store) { 200 * DAY }.load(broker, "@MGC", Timeframe.M1, 0, end)
        assertEquals(0, result.cacheHits)
        assertEquals(1, broker.calls)
        assertEquals(100L, result.result.candles.single().close)
    }

    @Test fun replayRejectsChunksFromDifferentLeaders() = runTest {
        val broker = Broker().apply { aligned = true; mixedLeaders = true }
        assertFailsWith<IllegalStateException> {
            ReplayHistoryCache(MemorySnapshotStore()) { 200 * DAY }.load(broker, "@MGC", Timeframe.M1, 0, 90 * DAY)
        }
    }
}
