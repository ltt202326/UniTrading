package com.unitrading.broker

/** Use the dated contract for matching timestamps; keep provider history elsewhere. */
fun overlayContinuousCandles(continuous: List<Candle>, current: List<Candle>): List<Candle> =
    (continuous + current).associateBy(Candle::timeMs).values.sortedBy(Candle::timeMs)
