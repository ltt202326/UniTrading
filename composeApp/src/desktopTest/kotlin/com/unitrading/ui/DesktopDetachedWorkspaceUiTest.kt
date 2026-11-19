package com.unitrading.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.unitrading.app.MemorySnapshotStore
import com.unitrading.app.TradingRuntime
import com.unitrading.broker.BrokerPlugin
import com.unitrading.broker.OrderFlowSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopDetachedWorkspaceUiTest {
    @Test fun detachedWindowHasIndependentRailAndOrdersFollowItsChart() = runDesktopComposeUiTest(width = 1600, height = 1000) {
        val source = OfflineOrderBroker()
        val depthWatches = mutableListOf<String?>()
        val broker = object : BrokerPlugin by source {
            override val orderFlow = MutableStateFlow(OrderFlowSnapshot())
            override fun watchOrderFlow(symbol: String?) { depthWatches += symbol }
        }
        val runtime = TradingRuntime(MemorySnapshotStore(), listOf(broker), includeDemoBrokers = false).apply { paused.value = true }
        val windows = FakePanelWindows(strip = 760.dp)
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalChartPlatform provides ChartPlatform.DESKTOP,
            LocalPanelWindowHost provides windows) {
            Box(Modifier.requiredSize(1600.dp, 1000.dp)) { TradingScreen(runtime) }
        } }
        onNodeWithTag("chart-new-workspace").performClick()
        waitUntil(10_000) { onAllNodesWithTag("workspace-tab-c1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("chart-symbol").performClick()
        onNodeWithTag("symbol-option-OTHRZ6").performClick()
        onNodeWithTag("detach-workspace-c1").performClick()
        waitUntil(10_000) { "chart-c1" in windows.open }
        val child = hasAnyAncestor(hasTestTag("fake-window-chart-c1"))
        onNode(hasTestTag("detached-account-toolbar-c1") and child).assertExists()
        onNode(hasTestTag("quick-login-picker") and child).assertExists()
        onNode(hasTestTag("quick-account-picker") and child).assertExists()
        onNodeWithText("账户与主窗口同步").assertExists()
        onNode(hasTestTag("market-watch") and child).assertExists()

        onNodeWithTag("detached-rail-order-c1").performScrollTo().performClick()
        waitUntil(5_000) { onAllNodes(hasTestTag("order-fields") and child).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("OTHRZ6", substring = true) and hasAnyAncestor(hasTestTag("detached-side-panel-c1"))).assertExists()
        onNode(hasTestTag("review-order") and child).performScrollTo().performClick()
        onNodeWithTag("desktop-order-confirmation").assertExists()
        onNode(hasText("OTHRZ6 ·", substring = true) and hasAnyAncestor(hasTestTag("desktop-order-confirmation"))).assertExists()
        onNodeWithTag("cancel-order-review").performClick()
        assertTrue(source.placed.isEmpty(), "Reviewing and cancelling a child-window order never submits it")

        onNodeWithTag("detached-rail-dom-c1").performScrollTo().performClick()
        onNode(hasTestTag("dom-panel") and child).assertExists()
        waitUntil(5_000) { depthWatches.contains("OTHRZ6") }
        onNodeWithTag("detached-rail-terminal-c1").performScrollTo().performClick()
        onNode(hasTestTag("desktop-trade-tab-POSITIONS") and child).assertExists()
        onNode(hasTestTag("desktop-trade-tab-ORDERS") and child).assertExists()
        onNode(hasTestTag("desktop-trade-tab-EXECUTIONS") and child).assertExists()
        onNode(hasTestTag("desktop-trade-tab-CLOSED_TRADES") and child).assertExists()
        assertTrue(source.placed.isEmpty())
    }

    @Test fun detachedAccountAndFlattenControlsUseTheSharedSelectedAccount() = runDesktopComposeUiTest(width = 1600, height = 1000) {
        val runtime = TradingRuntime(MemorySnapshotStore(), includeDemoBrokers = true).apply { paused.value = true }
        val broker = runtime.registry.plugins.first()
        val first = broker.accounts.first()
        val second = broker.accounts.last()
        val firstBefore = broker.openSession(first.id).state.value.positions.size
        val secondBefore = broker.openSession(second.id).state.value.positions.size
        val windows = FakePanelWindows(strip = 760.dp)
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalChartPlatform provides ChartPlatform.DESKTOP,
            LocalPanelWindowHost provides windows) {
            Box(Modifier.requiredSize(1600.dp, 1000.dp)) { TradingScreen(runtime) }
        } }
        onNodeWithTag("chart-new-workspace").performClick()
        waitUntil(10_000) { onAllNodesWithTag("workspace-tab-c1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("detach-workspace-c1").performClick()
        waitUntil(10_000) { "chart-c1" in windows.open }
        val child = hasAnyAncestor(hasTestTag("fake-window-chart-c1"))
        onNode(hasTestTag("quick-account-picker") and child).performClick()
        onNodeWithTag("quick-account-${second.id}").performClick()
        onNode(hasTestTag("flatten-selected-account") and child).assertIsEnabled().performMouseInput { click() }
        onNodeWithText("平仓此子账户？").assertExists()
        onNodeWithText(second.id, substring = true).assertExists()
        onNodeWithTag("cancel-bulk-flatten").performClick()
        onNode(hasTestTag("flatten-all-accounts") and child).performMouseInput { click() }
        onNodeWithText("平仓全部子账户？").assertExists()
        onNodeWithTag("cancel-bulk-flatten").performClick()
        assertEquals(firstBefore, broker.openSession(first.id).state.value.positions.size)
        assertEquals(secondBefore, broker.openSession(second.id).state.value.positions.size)
    }
}
