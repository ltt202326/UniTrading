package com.unitrading.tradovate

import kotlin.test.*

class ContinuousPriceMatcherTest {
    @Test fun matchesSeveralDifferentSynchronizedPricesAndRejectsTies() {
        val m = ContinuousPriceMatcher()
        for ((i, price) in listOf(100L, 101L, 102L).withIndex()) {
            m.chart(price, i * 1000L)
            m.trade("MGCH7", price, i * 1000L + 20)
        }
        assertEquals("MGCH7", m.unique(setOf("MGCZ6", "MGCH7"), 2100))
        for ((i, price) in listOf(100L, 101L, 102L).withIndex()) m.trade("MGCZ6", price, i * 1000L + 40)
        assertNull(m.unique(setOf("MGCZ6", "MGCH7"), 2100))
        assertNull(m.unique(setOf("MGCH7"), 32001))
    }
    @Test fun oneUnchangedPriceOrDelayedTicksNeverConfirms() {
        val m = ContinuousPriceMatcher()
        repeat(5) { m.chart(100, it * 1000L); m.trade("MGCZ6", 100, it * 1000L) }
        assertNull(m.unique(setOf("MGCZ6"), 5000))
        val delayed = ContinuousPriceMatcher()
        repeat(3) { delayed.chart(100L + it, it * 1000L); delayed.trade("MGCZ6", 100L + it, it * 1000L + 5000) }
        assertNull(delayed.unique(setOf("MGCZ6"), 7500))
    }
    @Test fun scopeCoversOilMetalsAndCryptoButPreservesIndexes() {
        for (root in listOf("GC", "MGC", "SI", "SIL", "SL", "CL", "MCL", "BTC", "MBT")) assertTrue(usesContinuousPriceMatching("@$root"))
        for (root in listOf("ES", "MES", "NQ", "MNQ", "YM", "MYM")) assertFalse(usesContinuousPriceMatching("@$root"))
    }
}
