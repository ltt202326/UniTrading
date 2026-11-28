package com.unitrading.ui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.unitrading.broker.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class OrderMarketClosedUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun closedShowsCenteredStatusButOtherErrorsRemainSpecific() {
        val alias = Instrument("@MCL", "Micro Crude Oil", "连续合约", 2, tickSize = 1,
            tickValueMicrosPerLot = 1_000_000, isContinuous = true, quoteSymbol = "MCLF7",
            tradeSymbolStatus = "收盘价匹配，待实时核对")
        val instrument = mutableStateOf(alias)
        val state = mutableStateOf(MarketSessionState.CLOSED)
        val preview = mutableStateOf<OrderPreview?>(null)
        compose.setContent { TradingTheme {
            OrderForm(instrument.value, null, BrokerCapabilities(), OrderDraft(), {}, preview.value, false, {},
                modifier = Modifier.requiredSize(400.dp, 700.dp), environment = BrokerEnvironment.REMOTE_DEMO,
                marketSession = state.value)
        } }
        compose.onNodeWithTag("order-error").assertTextEquals("已关盘")
        val error = compose.onNodeWithTag("order-error").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("order-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(error.width > actions.width * .8f, "closed status spans the order panel for centered text")
        compose.onNodeWithTag("review-order").assertIsNotEnabled()
        compose.runOnIdle { state.value = MarketSessionState.DELAYED }
        compose.onNodeWithTag("order-error").assertTextEquals("收盘价匹配，待实时核对，暂不可下单")
        compose.runOnIdle { state.value = MarketSessionState.DISCONNECTED }
        compose.onNodeWithTag("order-error").assertTextEquals("收盘价匹配，待实时核对，暂不可下单")
        compose.runOnIdle {
            instrument.value = alias.copy(tradeSymbol = "MCLF7", tradeSymbolStatus = null)
            state.value = MarketSessionState.CLOSED
            preview.value = OrderPreview(null, errors = listOf("保证金不足"))
        }
        compose.onNodeWithTag("order-error").assertTextEquals("保证金不足")
        compose.onNodeWithTag("review-order").assertIsNotEnabled()
    }
}
