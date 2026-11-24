package com.unitrading.broker

import kotlin.test.Test
import kotlin.test.assertEquals

class ContinuousOverlayTest {
    private fun bar(t: Long, p: Long) = Candle(t, p, p, p, p)
    @Test fun currentContractReplacesOnlyMatchingBarsAndKeepsOlderHistory() {
        val continuous = listOf(bar(1, 100), bar(2, 101), bar(3, 102), bar(4, 103))
        val current = listOf(bar(3, 202), bar(4, 203), bar(5, 204))
        assertEquals(listOf(bar(1, 100), bar(2, 101), bar(3, 202), bar(4, 203), bar(5, 204)), overlayContinuousCandles(continuous, current))
    }
    @Test fun missingContractBarsDoNotInventOrShiftPrices() {
        assertEquals(listOf(bar(1, 100), bar(2, 101), bar(3, 202)),
            overlayContinuousCandles(listOf(bar(1, 100), bar(2, 101), bar(3, 102)), listOf(bar(3, 202))))
        assertEquals(listOf(bar(1, 100)), overlayContinuousCandles(listOf(bar(1, 100)), emptyList()))
    }
    @Test fun unsortedDuplicatesUseTheLastDatedValue() {
        assertEquals(listOf(bar(1, 100), bar(2, 202)), overlayContinuousCandles(
            listOf(bar(2, 101), bar(1, 100)), listOf(bar(2, 201), bar(2, 202))))
    }
}
