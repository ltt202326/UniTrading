package com.unitrading.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.unitrading.app.MemorySnapshotStore
import com.unitrading.app.ThemePreset
import com.unitrading.app.ThemeSettings
import com.unitrading.app.TradingRuntime
import com.unitrading.broker.BrokerPlugin
import com.unitrading.broker.Candle
import com.unitrading.broker.HistoricalRangeResult
import com.unitrading.broker.Timeframe
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.math.sin

/** Captures the real shared app UI with offline data and tablet platform settings. */
@OptIn(ExperimentalTestApi::class)
class TabletScreenshotUiTest {
    private fun capture(platform: ChartPlatform, width: Int, height: Int, name: String) =
        runDesktopComposeUiTest(width = width, height = height) {
            val source = OfflineOrderBroker()
            val broker = object : BrokerPlugin by source {
                override fun history(symbol: String, timeframe: Timeframe): List<Candle> {
                    fun level(i: Int): Long {
                        val value = 550_000.0 + (i - 100) * 35.0 +
                            1600.0 * (sin(i * .20) - sin(100 * .20)) + 500.0 * (sin(i * .71) - sin(100 * .71))
                        return (value.toLong() / 25) * 25
                    }
                    return source.history(symbol, timeframe).mapIndexed { i, bar ->
                        val open = level(i - 1)
                        val close = level(i)
                        bar.copy(open = open, close = close, high = maxOf(open, close) + 100 + i % 4 * 25,
                            low = minOf(open, close) - 100 - i % 3 * 25)
                    }
                }
                override suspend fun historicalRange(symbol: String, timeframe: Timeframe, startMs: Long, endMs: Long) =
                    HistoricalRangeResult(history(symbol, timeframe).filter { it.timeMs in startMs until endMs })
            }
            val runtime = TradingRuntime(MemorySnapshotStore(), listOf(broker), includeDemoBrokers = false).apply { paused.value = true }
            runBlocking { runtime.theme.apply(ThemeSettings(preset = ThemePreset.LIGHT)) }
            setContent { CompositionLocalProvider(LocalDensity provides Density(1f), LocalChartPlatform provides platform) {
                TradingTheme(ThemeSettings(preset = ThemePreset.LIGHT)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) { TradingScreen(runtime) }
                }
            } }
            val folder = File(System.getenv("GITHUB_WORKSPACE") ?: ".", "artifacts/tablet-screenshots").also { it.mkdirs() }
            fun save(suffix: String) {
                ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", File(folder, "$name-$suffix.png"))
            }
            onNodeWithTag("desktop-panel-rail").assertExists()
            save("normal")
            onNodeWithTag("open-historical-replay").performScrollTo().performClick()
            onNodeWithTag("replay-begin").performScrollTo().performClick()
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasTestTag("review-order") and hasAnyAncestor(hasTestTag("historical-replay"))).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("replay-start").performScrollTo().performTouchInput { click(Offset(size.width * .8f, center.y)) }
            ImageIO.write(onNodeWithTag("historical-replay").captureToImage().toAwtImage(), "png", File(folder, "$name-replay.png"))
            onNodeWithTag("replay-close").performClick()
            onNodeWithTag("chart-fullscreen").performScrollTo().performClick()
            onNodeWithTag("legend-buy").performClick()
            onNodeWithTag("fullscreen-order-ticket").assertExists()
            save("fullscreen-order")
        }

    @Test fun androidTablet() = capture(ChartPlatform.ANDROID, 1280, 800, "android-tablet")
    @Test fun ipad() = capture(ChartPlatform.IOS, 1194, 834, "ipad")
}
