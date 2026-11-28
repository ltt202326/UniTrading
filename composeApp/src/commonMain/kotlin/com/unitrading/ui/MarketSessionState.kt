package com.unitrading.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.unitrading.broker.Instrument
import com.unitrading.broker.BrokerEnvironment
import com.unitrading.broker.MarketSnapshot
import com.unitrading.broker.Quote
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.time.Clock

/** An inference for display only. It must never authorize an order or resolve a continuous contract. */
internal enum class MarketSessionState(val label: String) {
    OPEN("开盘"), CLOSED("关盘"), DELAYED("行情延迟"), DISCONNECTED("未连接"), WAITING("等待行情")
}

private val chicago = TimeZone.of("America/Chicago")
private val regularCmeRoots = setOf("ES", "MES", "NQ", "MNQ", "CL", "MCL", "GC", "MGC")

/** CME's normal schedule for these products; holiday exceptions and other instruments are not guessed. */
internal fun scheduledCmeClosed(symbol: String, nowMs: Long): Boolean? {
    val name = symbol.removePrefix("@").uppercase()
    val root = regularCmeRoots.firstOrNull { name == it || name.matches(Regex("${it}[FGHJKMNQUVXZ]\\d{1,4}")) } ?: return null
    if (root !in regularCmeRoots || nowMs <= 0) return null
    val local = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(chicago)
    val hour = local.hour
    return when (local.date.dayOfWeek.name) {
        "SATURDAY" -> true
        "SUNDAY" -> hour < 17
        "FRIDAY" -> hour >= 16
        else -> hour == 16
    }
}

internal fun marketSessionState(instrument: Instrument, quote: Quote?, connected: Boolean, nowMs: Long, connectedAtMs: Long): MarketSessionState {
    if (!connected) return MarketSessionState.DISCONNECTED
    if (nowMs <= 0) return MarketSessionState.WAITING
    val age = quote?.let { nowMs - it.timeMs }
    if (quote != null && quote.bid > 0 && quote.ask >= quote.bid && age != null && age in -5_000L..15_000L)
        return MarketSessionState.OPEN
    if (quote == null && nowMs - connectedAtMs < 15_000L) return MarketSessionState.WAITING
    return if (scheduledCmeClosed(instrument.symbol, nowMs) == true) MarketSessionState.CLOSED else MarketSessionState.DELAYED
}

/** Display-only session status for order forms; historical quotes never enter order previews. */
@Composable
internal fun rememberOrderMarketSession(instrument: Instrument, market: MarketSnapshot?, environment: BrokerEnvironment): MarketSessionState? {
    if (market == null || environment == BrokerEnvironment.LOCAL_DEMO) return null
    var now by remember(instrument.symbol, market.connected) { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    val connectedAt = remember(instrument.symbol, market.connected) { Clock.System.now().toEpochMilliseconds() }
    LaunchedEffect(instrument.symbol, market.connected) {
        while (market.connected) { now = Clock.System.now().toEpochMilliseconds(); delay(1_000) }
    }
    return marketSessionState(instrument, market.quotes[instrument.priceSymbol], market.connected, now, connectedAt)
}

@Composable
internal fun MarketSessionBadge(state: MarketSessionState) {
    val colors = TradingColors
    val ink = if (state == MarketSessionState.OPEN) colors.Profit else colors.Muted
    Row(Modifier.background(ink.copy(alpha = .12f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp)
        .testTag("chart-market-session").semantics { contentDescription = "市场状态：${state.label}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(if (state == MarketSessionState.OPEN) 6.dp else 10.dp, if (state == MarketSessionState.OPEN) 6.dp else 3.dp)
            .background(ink, RoundedCornerShape(50)))
        Text(state.label, color = ink, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
