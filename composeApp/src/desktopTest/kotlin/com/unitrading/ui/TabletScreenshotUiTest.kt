package com.unitrading.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.unitrading.app.MemorySnapshotStore
import com.unitrading.app.ThemePreset
import com.unitrading.app.ThemeSettings
import com.unitrading.app.TradingRuntime
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test

/** Captures the real shared app UI with offline data and tablet platform settings. */
@OptIn(ExperimentalTestApi::class)
class TabletScreenshotUiTest {
    private fun capture(platform: ChartPlatform, width: Int, height: Int, name: String) =
        runDesktopComposeUiTest(width = width, height = height) {
            val broker = OfflineOrderBroker()
            val runtime = TradingRuntime(MemorySnapshotStore(), listOf(broker), includeDemoBrokers = false).apply { paused.value = true }
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
            onNodeWithTag("chart-fullscreen").performScrollTo().performClick()
            onNodeWithTag("legend-buy").performClick()
            onNodeWithTag("fullscreen-order-ticket").assertExists()
            save("fullscreen-order")
        }

    @Test fun androidTablet() = capture(ChartPlatform.ANDROID, 1280, 800, "android-tablet")
    @Test fun ipad() = capture(ChartPlatform.IOS, 1194, 834, "ipad")
}
