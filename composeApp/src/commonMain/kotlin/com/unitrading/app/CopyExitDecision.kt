package com.unitrading.app

import com.unitrading.broker.Side

/** Read-only decision for one attributed follower trade. It does not send, cancel or flatten any orders. */
internal data class CopiedTradeExitView(
    val brokerSynchronized: Boolean,
    val leaderExitConfirmed: Boolean,
    val followerPositionKnown: Boolean,
    /** Contracts proven to belong to this copy relationship, not the whole account's inferred position. */
    val attributedContracts: Int,
    /** Absolute size of the net position, regardless of side. */
    val absoluteNetContractsOnSymbol: Int,
    /** Only protective orders whose broker identity is positively mapped to this copied trade. */
    val ownedWorkingExitIds: Set<String>,
    val unrelatedWorkingOrdersOnSymbol: Boolean,
    val attributedExitIdsComplete: Boolean,
    /** Remainder of the linked follower entry, if it still exists. */
    val ownedWorkingEntryId: String? = null,
    /** Required before cancelling a partially filled entry; OSO cancellation may affect protective legs. */
    val partialEntryCancellationPreservesProtection: Boolean = false,
    val expectedSide: Side? = null,
    val actualSide: Side? = null,
    val positionId: String? = null,
    val positionVersion: Long? = null,
)

internal sealed interface CopyExitDecision {
    data object Wait : CopyExitDecision
    data object AlreadyClosed : CopyExitDecision
    data class CancelOwnedExits(val orderIds: Set<String>) : CopyExitDecision
    data class CancelOwnedEntry(val orderId: String) : CopyExitDecision
    /** Caller must use a checked close by position identity, not an opposite market order. */
    data class CloseCopiedPosition(val positionId: String, val positionVersion: Long, val contracts: Int) : CopyExitDecision
    data class Stop(val reason: String) : CopyExitDecision
}

internal object CopyExitPlanner {
    fun decide(view: CopiedTradeExitView): CopyExitDecision {
        if (!view.brokerSynchronized || !view.followerPositionKnown || !view.attributedExitIdsComplete)
            return CopyExitDecision.Stop("账户或保护单状态未确认，请核对")
        if (view.attributedContracts < 0 || view.absoluteNetContractsOnSymbol < 0 ||
            view.attributedContracts != view.absoluteNetContractsOnSymbol || view.unrelatedWorkingOrdersOnSymbol)
            return CopyExitDecision.Stop("仓位不能完整归属于此跟单，请核对")
        if (view.attributedContracts > 0 && (view.expectedSide == null || view.expectedSide != view.actualSide ||
                view.positionId.isNullOrBlank() || view.positionVersion == null || view.positionVersion <= 0))
            return CopyExitDecision.Stop("仓位方向、身份或版本未确认")
        if (view.ownedWorkingExitIds.any { it.isBlank() } || view.ownedWorkingEntryId?.isBlank() == true)
            return CopyExitDecision.Stop("订单身份未确认")
        if (!view.leaderExitConfirmed) return CopyExitDecision.Wait
        view.ownedWorkingEntryId?.let { id ->
            if (view.attributedContracts > 0 && !view.partialEntryCancellationPreservesProtection)
                return CopyExitDecision.Stop("跟随入场单部分成交，无法确认撤单后保护仍有效")
            return CopyExitDecision.CancelOwnedEntry(id)
        }
        if (view.attributedContracts == 0) {
            // Already closed: never sell/buy again. Only positively attributed orphan exits can be cancelled.
            return if (view.ownedWorkingExitIds.isEmpty()) CopyExitDecision.AlreadyClosed
                else CopyExitDecision.CancelOwnedExits(view.ownedWorkingExitIds)
        }
        return CopyExitDecision.CloseCopiedPosition(requireNotNull(view.positionId), requireNotNull(view.positionVersion), view.attributedContracts)
    }
}
