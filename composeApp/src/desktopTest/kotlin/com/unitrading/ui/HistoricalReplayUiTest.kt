package com.unitrading.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unitrading.app.ChartTimeZone
import com.unitrading.broker.*
import org.junit.Rule
import org.junit.Test

class HistoricalReplayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replayTradesOnlyLocallyAndAppliesStopOnNextBar() {
        val instrument = Instrument("MES", "MES", "期货", 2, tickSize = 25, tickValueMicrosPerLot = 1_250_000,
            minLotsHundredths = 100, lotStepHundredths = 100, quantityDecimals = 0, quantityUnit = "张")
        val bars = listOf(Candle(1_700_000_000_000, 10_000, 10_025, 9_975, 10_000),
            Candle(1_700_000_060_000, 10_000, 10_075, 9_925, 10_000),
            Candle(1_700_000_120_000, 10_000, 10_025, 9_975, 10_000))
        compose.setContent { TradingTheme { HistoricalReplayDialog(ReplayInput(instrument, Timeframe.M1, false, ChartTimeZone.SHANGHAI, bars, emptyList())) {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("replay-buy").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("replay-stop").performTextInput("99.50")
        compose.onNodeWithTag("replay-target").performTextInput("100.50")
        compose.onNodeWithTag("replay-buy").performClick()
        compose.onNodeWithTag("replay-trade-count").assertTextContains("成交 0 笔")
        compose.onNodeWithTag("replay-next").performClick()
        compose.onNodeWithTag("replay-trade-count").assertTextContains("成交 1 笔")
        compose.onNodeWithTag("replay-realized").assertTextContains("-2.50")
    }
}
