package com.unitrading.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unitrading.app.*
import com.unitrading.broker.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReplayFailureUiTest {
    @get:Rule val compose = createComposeRule()
    private val spec = Instrument("MES", "MES", "期货", 2, tickSize = 25, tickValueMicrosPerLot = 1_250_000,
        minLotsHundredths = 100, lotStepHundredths = 100, quantityDecimals = 0)
    private val bars = listOf(Candle(1_700_000_000_000, 100_000, 100_025, 99_975, 100_000),
        Candle(1_700_000_060_000, 100_000, 100_075, 99_975, 100_050),
        Candle(1_700_000_120_000, 50_000, 50_025, 49_975, 50_000))

    private fun prepare(records: ReplayRecords) {
        compose.setContent { TradingTheme { HistoricalReplayDialog(ReplayInput(spec, Timeframe.M1, false, ChartTimeZone.SHANGHAI,
            bars, emptyList(), skipConfirmation = true), records = records) {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("replay-buy").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("replay-buy").performScrollTo().performClick()
        compose.onNodeWithTag("replay-next").performClick()
        compose.onNodeWithText("模拟平仓").performScrollTo().performClick()
        compose.onNodeWithTag("replay-buy").performScrollTo().performClick()
        compose.onNodeWithTag("replay-next").performClick()
        compose.onNodeWithTag("replay-failure").assertExists()
    }

    @Test fun saveFailureKeepsOnlyCompletedTradeAndRestarts() {
        val records = ReplayRecords(MemorySnapshotStore())
        prepare(records)
        compose.onNodeWithTag("replay-failure-save").performClick()
        compose.onNodeWithTag("replay-failure").assertDoesNotExist()
        compose.runOnIdle {
            val saved = records.list().single()
            assertEquals("失败", saved.status)
            assertEquals(1, saved.trades.size)
            assertTrue(saved.equity.isNotEmpty())
        }
        compose.onNodeWithTag("replay-tab-1").performClick()
        compose.onNodeWithText("选择本地记录分析").performScrollTo().performClick()
        compose.onNodeWithText("MES · 1 笔", substring = true).performClick()
        compose.onNodeWithTag("replay-dashboard").assertExists()
    }

    @Test fun discardFailureDeletesOnlyCurrentSession() {
        val records = ReplayRecords(MemorySnapshotStore())
        records.save(ReplayRecord("old", "NQ", "USD", 50_000_000L, 1, emptyList()))
        prepare(records)
        compose.onNodeWithTag("replay-failure-discard").performClick()
        compose.onNodeWithTag("replay-failure").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("old"), records.list().map { it.id }) }
    }
}
