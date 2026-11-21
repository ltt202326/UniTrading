package com.unitrading.app

import com.unitrading.broker.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface CopyLimitSubmission {
    data class AwaitingBrokerConfirmation(val followerOrderId: String) : CopyLimitSubmission
    data class Blocked(val reason: String) : CopyLimitSubmission
    data class NeedsReconciliation(val reason: String) : CopyLimitSubmission
}

/**
 * Submits one prepared Demo limit order, not a running copier. No UI calls this until lifecycle and exit handling have
 * passed end-to-end checks. Persistence and both broker views are checked before the only network write.
 */
internal class CopyLimitSubmissionService(private val registry: BrokerRegistry, private val risk: AccountRiskController,
    private val journal: CopyOrderJournal) {
    suspend fun submit(prepared: CopyLimitPreparation.Ready, config: CopyFollowerConfiguration,
        sourceBroker: BrokerPlugin, targetBroker: BrokerPlugin, targetOsoOcoVerified: Boolean): CopyLimitSubmission {
        val link = prepared.link
        val key = link.key
        if (sourceBroker.descriptor.id != key.leader.loginId || targetBroker.descriptor.id != key.follower.loginId ||
            sourceBroker.descriptor.environment != BrokerEnvironment.REMOTE_DEMO || targetBroker.descriptor.environment != BrokerEnvironment.REMOTE_DEMO ||
            !sourceBroker.descriptor.id.startsWith("tradovate-") || !targetBroker.descriptor.id.startsWith("tradovate-") ||
            config.account != key.follower) return CopyLimitSubmission.Blocked("只允许所选 Tradovate Demo 账户")
        val source = try { sourceBroker.openSession(key.leader.accountId) } catch (_: Exception) { return CopyLimitSubmission.Blocked("主账户不可用") }
        val target = try { risk.guard(targetBroker, targetBroker.openSession(key.follower.accountId)) } catch (_: Exception) { return CopyLimitSubmission.Blocked("跟随账户不可用") }

        fun validate(): String? {
            if (registry.plugins.none { it === sourceBroker } || registry.plugins.none { it === targetBroker }) return "连接已被替换"
            if (!sourceBroker.market.value.let { it.connected && it.tradingReady } || !targetBroker.market.value.let { it.connected && it.tradingReady }) return "连接未就绪"
            val leader = source.state.value
            val follower = target.state.value
            if (!leader.copyPositionsComplete || !leader.copyOrdersComplete || !follower.copyPositionsComplete || !follower.copyOrdersComplete ||
                leader.copyRevision != prepared.sourceRevision || follower.copyRevision != link.targetRequest?.copyExpectedRevision) return "账户快照已变化或不完整"
            if (source.account.readOnly || target.account.readOnly || key.leader.accountId == key.follower.accountId) return "账户不可交易或重复"
            val sourceOrder = leader.orders.singleOrNull { it.id == key.leaderOrderId } ?: return "主限价单已不在活动订单中"
            if (sourceOrder.version != link.sourceOrderVersion || sourceOrder.filledLotsHundredths != 0L) return "主限价单已变化或成交"
            val targetSpec = targetBroker.catalog.value.instruments.singleOrNull { it.symbol == link.targetSymbol && !it.isContinuous }
            if (leader.positions.any { it.symbol == link.sourceSymbol } || follower.positions.any { it.symbol == link.targetSymbol } ||
                follower.orders.any { it.request.symbol == link.targetSymbol }) return "同合约已有仓位或挂单，无法确认归属"
            val hasPair = sourceOrder.request.stopLoss != null && sourceOrder.request.takeProfit != null
            val protectionVerified = sourceProtectionVerified(sourceOrder, leader)
            val targetProtected = targetBroker.capabilities.nativeEntryOso && (!hasPair || targetOsoOcoVerified)
            val rebuilt = prepareCopyLimit(key.leader, config, sourceOrder, leader.copyRevision, follower.copyRevision,
                true, true, protectionVerified, targetProtected, targetSpec, link.requestId)
            if (rebuilt != prepared) return (rebuilt as? CopyLimitPreparation.Stop)?.reason ?: "订单内容已变化"
            val preview = target.preview(requireNotNull(link.targetRequest))
            if (!preview.valid) return preview.errors.firstOrNull() ?: "跟随订单校验失败"
            return null
        }

        validate()?.let { return CopyLimitSubmission.Blocked(it) }
        if (!journal.begin(link)) return CopyLimitSubmission.Blocked("订单已记录、日志不可用或无法保存，未发送")
        validate()?.let {
            journal.abortedBeforeSend(key, link.requestId)
            return CopyLimitSubmission.Blocked("$it；未发送，须核对后处理")
        }
        val result = try { withTimeoutOrNull(20_000) { target.placeOrder(link.requestId, requireNotNull(link.targetRequest)) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
        if (result?.outcome != CommandOutcome.ACCEPTED || result.entityId.isNullOrBlank()) {
            journal.uncertain(key, link.requestId)
            return CopyLimitSubmission.NeedsReconciliation("下单被拒绝或结果不明，未自动重发；请核对")
        }
        val id = result.entityId
        if (!journal.accepted(key, link.requestId, id)) return CopyLimitSubmission.NeedsReconciliation("订单已受理但记录未能保存，请核对")
        return CopyLimitSubmission.AwaitingBrokerConfirmation(id)
    }

    private fun sourceProtectionVerified(entry: PendingOrder, snapshot: AccountSnapshot): Boolean {
        val required = listOfNotNull(
            entry.request.stopLoss?.let { Triple(entry.stopLossOrderId, OrderRole.STOP_LOSS, it) },
            entry.request.takeProfit?.let { Triple(entry.takeProfitOrderId, OrderRole.TAKE_PROFIT, it) },
        )
        val children = required.map { (id, role, price) ->
            snapshot.orders.singleOrNull { it.id == id && it.role == role && it.request.entryPrice == price }
                ?: return false
        }
        if (children.size == 2 && (children[0].ocoGroup == null || children[0].ocoGroup != children[1].ocoGroup)) return false
        return true
    }
}
