package com.unitrading.app

import kotlinx.serialization.Serializable

@Serializable
internal data class CopyFollowerConfiguration(
    val account: CopyAccountKey,
    /** Two decimal places: 100 means 1.00 times. */
    val multiplierHundredths: Int,
    val contractSize: CopyContractSize,
    val maximumContracts: Int,
)

@Serializable
internal data class CopyGroupConfiguration(val leader: CopyAccountKey, val followers: List<CopyFollowerConfiguration>) {
    fun valid(): Boolean = leader.loginId.isNotBlank() && leader.accountId.isNotBlank() && followers.isNotEmpty() &&
        followers.all { it.account != leader && it.account.loginId.isNotBlank() && it.account.accountId.isNotBlank() &&
            it.account.accountId != leader.accountId && it.multiplierHundredths in 1..10_000 && it.maximumContracts in 1..1_000 } &&
        followers.map { it.account.accountId }.distinct().size == followers.size
}

/** Both meanings of multiplier are explicit; no rounding or order is performed by this calculator. */
internal enum class CopyQuantityBasis { MATCH_EXPOSURE, MATCH_CONTRACTS }
internal data class ExactCopyQuantity(val numerator: Long, val denominator: Long) {
    val wholeContracts: Long get() = numerator / denominator
    val remainingNumerator: Long get() = numerator % denominator
    val exact: Boolean get() = remainingNumerator == 0L
    /** The user selected upward rounding, but the account cap is still a hard limit. */
    fun roundedUpWithinCap(maxContracts: Int): Long? {
        if (maxContracts <= 0) return null
        val rounded = wholeContracts + if (exact) 0 else 1
        return rounded.takeIf { it in 1..maxContracts.toLong() }
    }
}

internal fun copyQuantity(sourceContracts: Long, multiplierHundredths: Int, mapping: CopyContractMapping,
    basis: CopyQuantityBasis): ExactCopyQuantity? {
    if (sourceContracts !in 1..100_000 || multiplierHundredths !in 1..10_000 ||
        mapping.sourceUnits !in 1..100 || mapping.targetUnits !in 1..100) return null
    // The bounds above make both products safe; no floating-point exposure rounding is used.
    val sizeNumerator = if (basis == CopyQuantityBasis.MATCH_EXPOSURE) mapping.sourceUnits else 1
    val sizeDenominator = if (basis == CopyQuantityBasis.MATCH_EXPOSURE) mapping.targetUnits else 1
    return ExactCopyQuantity(sourceContracts * multiplierHundredths * sizeNumerator, 100L * sizeDenominator)
}
