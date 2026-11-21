package com.unitrading.app

import com.unitrading.broker.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.TimeSource

internal data class CopyTradingStatus(val running: Boolean = false, val message: String = "已停止", val settings: CopyPlanSettings? = null)

/** Demo-only and in-memory. There is no automatic resume, replay, or retry after a process or connection interruption. */
internal class CopyTradingController(private val registry: BrokerRegistry, private val risk: AccountRiskController) {
    private val mutableStatus = MutableStateFlow(CopyTradingStatus())
    val status = mutableStatus.asStateFlow()
    private var generation = 0L
    private data class Group(val broker: BrokerPlugin, val settings: CopyPlanSettings, val sessions: Map<String, AccountSession>, val token: Long,
        val expected: MutableMap<String, Int>)
    private var group: Group? = null

    fun start(broker: BrokerPlugin, settings: CopyPlanSettings): String? {
        if (group != null) return "已有跟单组运行"
        if (broker.descriptor.environment != BrokerEnvironment.REMOTE_DEMO || !broker.descriptor.id.startsWith("tradovate-"))
            return "当前验证版仅支持 Tradovate Demo"
        if (registry.plugins.none { it === broker }) return "连接已变化，请重新选择"
        if (!broker.market.value.let { it.connected && it.tradingReady }) return "连接尚未准备好"
        val accounts = listOf(settings.leaderId) + settings.followers.map { it.accountId }
        if (accounts.distinct().size != accounts.size) return "账户不能重复"
        val sessions = try { accounts.associateWith { id -> risk.guard(broker, broker.openSession(id)) } }
            catch (_: Exception) { return "账户不可用，请重新选择" }
        if (settings.followers.any { risk.rules.value[broker.descriptor.id]?.get(it.accountId)?.brackets?.enabled == true })
            return "跟随账户启用了金额止盈止损，验证版暂不支持"
        val views = views(broker, sessions) ?: return "账户快照尚未同步，请稍后重试"
        val initial = CopyTradePlanner.initial(settings, views)
        if (initial is CopyPlan.Blocked) return initial.reason
        group = Group(broker, settings, sessions, ++generation, settings.followers.associate { it.accountId to 0 }.toMutableMap())
        mutableStatus.value = CopyTradingStatus(true, "运行中 · 仅 Demo，等待主账户成交", settings)
        return null
    }

    fun stop(reason: String = "已停止，请核对所有账户；停止不会撤销正在处理的订单") {
        generation++
        group = null
        mutableStatus.value = CopyTradingStatus(false, reason)
    }

    /** Lives with the main screen, never with the settings dialog. A cancelled screen always stops the group. */
    suspend fun monitor() {
        try {
            var previous = TimeSource.Monotonic.markNow()
            while (currentCoroutineContext().isActive) {
                delay(250)
                val now = TimeSource.Monotonic.markNow()
                if ((now - previous).inWholeMilliseconds > 5_000 && group != null) stop("程序暂停或系统休眠，已停止；请核对账户")
                previous = now
                val current = group ?: continue
                if (!authorized(current)) { stop("连接已断开或账户发生变化，请核对账户"); continue }
                // A local leader order temporarily makes tradingReady false. Wait for its broker update, but never
                // follow a working order, a stale account view or a disconnection.
                val snapshots = withTimeoutOrNull(5_000) {
                    var value: Map<String, CopyAccountView>? = null
                    while (authorized(current)) {
                        value = if (current.broker.market.value.tradingReady) views(current.broker, current.sessions) else null
                        if (value != null && value.values.all { it.positionsComplete && it.ordersComplete }) break
                        delay(100)
                    }
                    value?.takeIf { authorized(current) && it.values.all { row -> row.positionsComplete && row.ordersComplete } }
                }
                if (snapshots == null) { stop("账户快照未就绪或存在挂单，已停止；请核对账户"); continue }
                when (val plan = CopyTradePlanner.plan(current.settings, snapshots, current.expected)) {
                    is CopyPlan.Blocked -> stop(plan.reason + "；已停止，请核对账户")
                    is CopyPlan.Ready -> plan.adjustments.firstOrNull()?.let { execute(current, it) }
                }
                previous = TimeSource.Monotonic.markNow()
            }
        } finally { if (group != null) stop("程序界面关闭，已停止；请核对账户") }
    }

    private fun authorized(g: Group): Boolean = group?.token == g.token && registry.plugins.any { it === g.broker } &&
        g.broker.market.value.connected && g.sessions.keys.all { id -> g.broker.catalog.value.accounts.any { it.id == id && !it.readOnly } }

    private fun views(broker: BrokerPlugin, sessions: Map<String, AccountSession>): Map<String, CopyAccountView>? {
        val rows = sessions.mapValues { (_, session) -> session.state.value }
        val revisions = rows.values.map { it.copyRevision }.distinct()
        if (revisions.size != 1 || revisions.singleOrNull() == null) return null
        return rows.mapValues { (id, snapshot) ->
            CopyAccountView(snapshot, broker.market.value.connected, broker.market.value.tradingReady,
                snapshot.copyPositionsComplete, snapshot.copyOrdersClear, sessions.getValue(id).account.readOnly)
        }
    }

    private suspend fun execute(g: Group, adjustment: CopyAdjustment) {
        if (!authorized(g)) return stop("连接已变化，请核对账户")
        val session = g.sessions.getValue(adjustment.accountId)
        val request = OrderRequest(g.settings.symbol, adjustment.side, OrderKind.MARKET, adjustment.contracts.toLong() * 100, automated = true)
        val preview = session.preview(request)
        if (!preview.valid) return stop("跟随账户下单校验失败：${preview.errors.joinToString("；")}；请核对账户")
        // A fresh check immediately before submitting; the broker's command lock remains the final authority.
        val fresh = views(g.broker, g.sessions) ?: return stop("账户快照变化，请核对账户")
        val plan = CopyTradePlanner.plan(g.settings, fresh, g.expected)
        if (plan !is CopyPlan.Ready || plan.adjustments.firstOrNull() != adjustment || !authorized(g))
            return stop("账户或目标仓位已变化，请核对账户")
        val id = "copy-${requestId()}"
        val oldFills = session.state.value.executions.map { it.id }.toSet()
        mutableStatus.value = CopyTradingStatus(true, "正在提交 ${session.account.name} 的模拟订单", g.settings)
        val guardedRequest = request.copy(copyExpectedRevision = fresh.getValue(g.settings.leaderId).snapshot.copyRevision)
        val result = try { withTimeoutOrNull(20_000) { session.placeOrder(id, guardedRequest) } }
            catch (cancel: CancellationException) { stop("程序中断，订单结果待核对"); throw cancel }
            catch (_: Exception) { null }
        if (group?.token != g.token) return
        if (result?.outcome != CommandOutcome.ACCEPTED || result.entityId.isNullOrBlank())
            return stop("订单被拒绝或结果未知，未重发；请核对账户")
        val orderId = result.entityId
        val confirmed = withTimeoutOrNull(15_000) {
            while (true) {
                if (!authorized(g)) return@withTimeoutOrNull false
                val snapshot = session.state.value
                val fills = snapshot.executions.filter { it.orderId == orderId && it.id !in oldFills }
                if (fills.any { it.symbol != request.symbol || it.side != request.side } ||
                    fills.sumOf { it.lotsHundredths } > request.lotsHundredths) return@withTimeoutOrNull false
                val position = snapshot.positions.singleOrNull { it.symbol == request.symbol }
                val net = if (position == null) 0L else position.lotsHundredths * if (position.side == Side.BUY) 1 else -1
                if (g.broker.market.value.tradingReady && snapshot.copyPositionsComplete && snapshot.copyOrdersClear &&
                    fills.sumOf { it.lotsHundredths } == request.lotsHundredths && net == adjustment.targetContracts.toLong() * 100) return@withTimeoutOrNull true
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        if (group?.token != g.token) return
        if (confirmed != true) return stop("未确认全部成交和仓位，未重发；请核对账户")
        g.expected[adjustment.accountId] = adjustment.targetContracts
        mutableStatus.value = CopyTradingStatus(true, "模拟成交已确认 · ${session.account.name}", g.settings)
    }
}
