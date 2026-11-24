package com.unitrading.tradovate

/** Price is evidence, not an authoritative contract identity. All samples are arrival times. */
internal class ContinuousPriceMatcher {
    private data class Sample(val price: Long, val time: Long)
    private val chart = mutableListOf<Sample>()
    private val trades = mutableMapOf<String, MutableList<Sample>>()

    fun chart(price: Long, time: Long) {
        if (price <= 0 || chart.lastOrNull()?.price == price) return
        chart.add(Sample(price, time))
        if (chart.size > 20) chart.removeAt(0)
    }
    fun trade(symbol: String, price: Long, time: Long) {
        if (price <= 0) return
        val samples = trades.getOrPut(symbol) { mutableListOf() }
        samples.add(Sample(price, time))
        if (samples.size > 100) samples.removeAt(0)
    }
    /** Requires three *different* recent chart changes, each corroborated by a fresh trade. */
    fun unique(candidates: Set<String>, now: Long): String? {
        val samples = chart.filter { now - it.time in 0..30_000 }.takeLast(3)
        if (samples.size < 3 || samples.map { it.price }.distinct().size < 3 || now - samples.last().time !in 0..10_000) return null
        val winners = candidates.filter { symbol ->
            val ticks = trades[symbol].orEmpty()
            samples.all { c -> ticks.any { it.price == c.price && kotlin.math.abs(it.time - c.time) <= 3_000 && now - it.time in 0..30_000 } }
        }
        return winners.singleOrNull()
    }
}

private val indexRoots = setOf("ES", "MES", "NES", "NQ", "MNQ", "NNQ", "YM", "MYM", "NDOW", "RTY", "M2K", "NKD", "NIY", "EMD", "TWE", "VIX", "VX", "VXM")
internal fun usesContinuousPriceMatching(symbol: String): Boolean = symbol.startsWith("@") && symbol.removePrefix("@") !in indexRoots
