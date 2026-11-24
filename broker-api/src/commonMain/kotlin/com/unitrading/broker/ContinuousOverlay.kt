package com.unitrading.broker

/** Use the dated contract for matching timestamps; keep provider history elsewhere. */
fun overlayContinuousCandles(continuous: List<Candle>, current: List<Candle>): List<Candle> {
    fun ordered(bars: List<Candle>): List<Candle> =
        if ((1 until bars.size).all { bars[it - 1].timeMs <= bars[it].timeMs }) bars else bars.sortedBy(Candle::timeMs)
    val a = ordered(continuous)
    val b = ordered(current)
    val merged = ArrayList<Candle>(a.size + b.size)
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
        val time = when {
            i == a.size -> b[j].timeMs
            j == b.size -> a[i].timeMs
            else -> minOf(a[i].timeMs, b[j].timeMs)
        }
        var provider: Candle? = null
        var dated: Candle? = null
        while (i < a.size && a[i].timeMs == time) provider = a[i++]
        while (j < b.size && b[j].timeMs == time) dated = b[j++]
        merged.add(requireNotNull(dated ?: provider))
    }
    return merged
}
