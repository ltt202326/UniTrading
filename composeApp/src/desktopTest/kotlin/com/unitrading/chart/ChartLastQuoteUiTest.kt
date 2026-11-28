package com.unitrading.chart

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.unitrading.broker.*
import com.unitrading.ui.TradingTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartLastQuoteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unresolvedContractShowsLastBidAskOnlyAsReference() {
        val instrument = Instrument("@MCL", "Micro Crude Oil", "连续合约", 2, tickSize = 1,
            tickValueMicrosPerLot = 1_000_000, isContinuous = true, quoteSymbol = "MCLF7",
            tradeSymbolStatus = "收盘价匹配，待实时核对")
        val at = 1_700_000_000_000L
        val quote = mutableStateOf<Quote?>(Quote(8332, 8336, at))
        var picked: Side? = null
        val actions = ChartOrderActions({ it to OrderPreview(null) }, {}, { _, _ -> }, false, { _, _ -> OrderPreview(null) },
            { _, _ -> }, false, { _, _, _ -> OrderPreview(null) }, { _, _, _ -> }, { _, _, _ -> })
        compose.setContent { TradingTheme {
            TradingChart("test", instrument, null, listOf(Candle(at, 8320, 8340, 8310, 8330)), Timeframe.M5,
                {}, emptyList(), emptyList(), null, null, null, null, emptyList(), actions,
                modifier = Modifier.size(1000.dp, 600.dp), environment = BrokerEnvironment.REMOTE_DEMO,
                onSide = { picked = it }, chrome = ChartChrome(sharedToolbar = true, showNavigation = false,
                    desktopAxisStyle = true, statusQuote = quote.value))
        } }
        compose.onNodeWithTag("legend-sell").assertTextContains("83.32")
        compose.onNodeWithTag("legend-buy").assertTextContains("83.36")
        compose.onNodeWithTag("trading-chart").assert(hasContentDescription("最后报价 Bid 83.32，Ask 83.36", substring = true))
        compose.onNodeWithTag("legend-last-quote").assertTextContains("最后报价 2023-11-15 06:13:20", substring = true)
        compose.onNodeWithTag("legend-buy").performClick()
        assertEquals(Side.BUY, picked)
        assertNull(instrument.executionSymbol)
        compose.runOnIdle { quote.value = null }
        compose.onNodeWithTag("legend-sell").assertTextContains("—")
        compose.onNodeWithTag("legend-buy").assertTextContains("—")
        compose.onNodeWithTag("legend-last-quote").assertDoesNotExist()
        compose.onNodeWithTag("trading-chart").assert(hasContentDescription("暂无 Bid/Ask 报价", substring = true))
    }
}
