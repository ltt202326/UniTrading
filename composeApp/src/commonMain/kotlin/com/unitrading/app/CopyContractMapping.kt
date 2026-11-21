package com.unitrading.app

import kotlinx.serialization.Serializable

/** Known outright futures only; never derive a live order from an unverified or continuous symbol. */
@Serializable
internal enum class CopyContractSize { MICRO, STANDARD }
internal data class CopyContractMapping(
    val source: String,
    val target: String,
    /** Relative exposure in hundredths of a standard contract. */
    val sourceUnits: Int,
    val targetUnits: Int,
)

internal object CopyContractMappings {
    private data class Family(val standard: String, val micro: String, val nano: String? = null)
    private val families = listOf(
        Family("ES", "MES", "NES"), Family("NQ", "MNQ", "NNQ"),
        Family("RTY", "M2K", "N2K"), Family("YM", "MYM", "NDOW"),
        Family("CL", "MCL"), Family("GC", "MGC"),
    )
    private val contract = Regex("^([A-Z][A-Z0-9]*?)([FGHJKMNQUVXZ])([0-9]{1,4})$")

    /** Returns null unless the follower's catalog contains the exact dated target; no front-month substitution. */
    fun resolve(source: String, size: CopyContractSize, availableOutrightSymbols: Set<String>): CopyContractMapping? {
        val match = contract.matchEntire(source) ?: return null
        val root = match.groupValues[1]
        val family = families.firstOrNull { root == it.standard || root == it.micro || root == it.nano } ?: return null
        val targetRoot = if (size == CopyContractSize.MICRO) family.micro else family.standard
        val target = targetRoot + match.groupValues[2] + match.groupValues[3]
        if (target !in availableOutrightSymbols) return null
        val sourceUnits = when (root) { family.standard -> 100; family.micro -> 10; else -> 1 }
        return CopyContractMapping(source, target, sourceUnits, if (size == CopyContractSize.MICRO) 10 else 100)
    }

    /** Price is preserved only when the destination tick admits it; callers must not silently round an order. */
    fun priceFits(price: Long, targetTickSize: Long): Boolean = price > 0 && targetTickSize > 0 && price % targetTickSize == 0L
}
