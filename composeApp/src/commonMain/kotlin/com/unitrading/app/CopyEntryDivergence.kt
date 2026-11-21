package com.unitrading.app

internal enum class EntryOrderState { WORKING, FINISHED, UNKNOWN }
internal data class EntryFillObservation(
    val connectedAndSynchronized: Boolean,
    val leaderState: EntryOrderState,
    val followerState: EntryOrderState,
    val leaderFilledContracts: Long,
    val followerFilledContracts: Long,
)
internal sealed interface CopyEntryDivergence {
    data object InSync : CopyEntryDivergence
    data class FollowerAhead(val followerFilled: Long, val targetNumerator: Long, val targetDenominator: Long) : CopyEntryDivergence
    data class FollowerBehind(val followerFilled: Long, val targetNumerator: Long, val targetDenominator: Long) : CopyEntryDivergence
    data class Unknown(val reason: String) : CopyEntryDivergence
}

/** Classifies actual broker fills for a linked entry only. It never chooses a cancel, market order or retry policy. */
internal fun entryDivergence(observation: EntryFillObservation, mapping: CopyContractMapping,
    multiplierHundredths: Int, basis: CopyQuantityBasis): CopyEntryDivergence {
    if (!observation.connectedAndSynchronized || observation.leaderState == EntryOrderState.UNKNOWN ||
        observation.followerState == EntryOrderState.UNKNOWN) return CopyEntryDivergence.Unknown("订单或连接状态未确认")
    if (observation.leaderFilledContracts !in 0..100_000 || observation.followerFilledContracts !in 0..100_000)
        return CopyEntryDivergence.Unknown("成交数量无效")
    if (copyQuantity(1, multiplierHundredths, mapping, basis) == null)
        return CopyEntryDivergence.Unknown("倍率或合约无法换算")
    val target = if (observation.leaderFilledContracts == 0L) ExactCopyQuantity(0, 1) else {
        val exact = copyQuantity(observation.leaderFilledContracts, multiplierHundredths, mapping, basis)
            ?: return CopyEntryDivergence.Unknown("倍率或合约无法换算")
        val rounded = exact.roundedUpWithinCap(Int.MAX_VALUE) ?: return CopyEntryDivergence.Unknown("目标数量超限")
        ExactCopyQuantity(rounded, 1)
    }
    val actualNumerator = observation.followerFilledContracts * target.denominator
    return when {
        actualNumerator > target.numerator -> CopyEntryDivergence.FollowerAhead(observation.followerFilledContracts, target.numerator, target.denominator)
        actualNumerator < target.numerator -> CopyEntryDivergence.FollowerBehind(observation.followerFilledContracts, target.numerator, target.denominator)
        else -> CopyEntryDivergence.InSync
    }
}
