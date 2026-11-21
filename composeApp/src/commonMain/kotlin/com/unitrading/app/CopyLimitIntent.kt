package com.unitrading.app

import com.unitrading.broker.*

internal sealed interface CopyLimitPreparation {
    data class Ready(val sourceRevision: Long, val link: CopyOrderLink) : CopyLimitPreparation
    data class Stop(val reason: String) : CopyLimitPreparation
}

/**
 * Prepares a single broker-held limit/OSO order; never submits it. The executor must durably journal the returned intent,
 * recheck the leader's revision and the follower's revision, then re-evaluate after every broker event. Cross-login writes
 * cannot be atomic, so the resulting fills must still be reconciled independently.
 */
internal fun prepareCopyLimit(
    source: CopyAccountKey,
    follower: CopyFollowerConfiguration,
    sourceOrder: PendingOrder,
    sourceRevision: Long?,
    targetRevision: Long?,
    sourceSynchronized: Boolean,
    targetSynchronized: Boolean,
    sourceProtectionVerified: Boolean,
    targetProtectionSupported: Boolean,
    targetSpec: Instrument?,
    requestId: String,
): CopyLimitPreparation {
    if (!sourceSynchronized || !targetSynchronized || sourceRevision == null || targetRevision == null)
        return CopyLimitPreparation.Stop("账户数据未同步")
    if (source == follower.account || source.accountId == follower.account.accountId || requestId.isBlank())
        return CopyLimitPreparation.Stop("主账户、跟随账户或请求标识无效")
    val order = sourceOrder.request
    if (sourceOrder.role != OrderRole.ENTRY || order.kind != OrderKind.LIMIT || !sourceOrder.editable ||
        sourceOrder.version <= 0 || sourceOrder.filledLotsHundredths != 0L)
        return CopyLimitPreparation.Stop("主限价单已成交、不可修改或订单关系未确认")
    if (order.lotsHundredths <= 0 || order.lotsHundredths % 100L != 0L) return CopyLimitPreparation.Stop("主订单张数无效")
    val spec = targetSpec?.takeUnless { it.isContinuous } ?: return CopyLimitPreparation.Stop("跟随合约不可用")
    val mapping = CopyContractMappings.resolve(order.symbol, follower.contractSize, setOf(spec.symbol))
        ?: return CopyLimitPreparation.Stop("没有确认的同月份目标合约")
    val quantity = copyQuantity(order.lotsHundredths / 100, follower.multiplierHundredths, mapping, CopyQuantityBasis.MATCH_CONTRACTS)
        ?.roundedUpWithinCap(follower.maximumContracts) ?: return CopyLimitPreparation.Stop("跟随数量无效或超过最大张数")
    val hasProtection = order.stopLoss != null || order.takeProfit != null
    if (hasProtection && (!sourceProtectionVerified || !sourceOrder.protectionEditable || !targetProtectionSupported))
        return CopyLimitPreparation.Stop("止盈止损关联或跟随保护能力未确认")
    val prices = listOfNotNull(order.entryPrice, order.stopLoss, order.takeProfit)
    if (order.entryPrice == null || prices.any { !CopyContractMappings.priceFits(it, spec.tickSize) })
        return CopyLimitPreparation.Stop("目标合约不接受对应价格，未自动调整")
    val target = OrderRequest(spec.symbol, order.side, OrderKind.LIMIT, quantity * 100, entryPrice = order.entryPrice,
        stopLoss = order.stopLoss, takeProfit = order.takeProfit, automated = true, copyExpectedRevision = targetRevision)
    val link = CopyOrderLink(CopyOrderKey(source, sourceOrder.id, follower.account), requestId, order.symbol, spec.symbol,
        sourceOrderVersion = sourceOrder.version, targetRequest = target)
    return CopyLimitPreparation.Ready(sourceRevision, link)
}
