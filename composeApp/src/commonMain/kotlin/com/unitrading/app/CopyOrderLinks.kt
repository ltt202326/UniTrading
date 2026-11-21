package com.unitrading.app

import kotlinx.serialization.Serializable

/** The login is part of the identity: broker account and order numbers are not globally unique. */
@Serializable
internal data class CopyAccountKey(val loginId: String, val accountId: String)

@Serializable
internal data class CopyOrderKey(val leader: CopyAccountKey, val leaderOrderId: String, val follower: CopyAccountKey)

@Serializable
internal enum class CopyLinkStatus { INTENDED, ACCEPTED, UNCERTAIN, TERMINAL }

@Serializable
internal data class CopyOrderLink(
    val key: CopyOrderKey,
    val requestId: String,
    val sourceSymbol: String,
    val targetSymbol: String,
    val status: CopyLinkStatus = CopyLinkStatus.INTENDED,
    val followerOrderId: String? = null,
    val ownedExitIds: Set<String> = emptySet(),
)

/** Pure link checks; callers must durably commit the returned record before any network write. */
internal object CopyOrderLinks {
    fun newIntent(existing: List<CopyOrderLink>, intent: CopyOrderLink): CopyOrderLink? {
        val k = intent.key
        if (k.leader == k.follower || k.leader.accountId == k.follower.accountId || k.leader.loginId.isBlank() || k.leader.accountId.isBlank() ||
            k.follower.loginId.isBlank() || k.follower.accountId.isBlank() || k.leaderOrderId.isBlank() ||
            intent.requestId.isBlank() || intent.sourceSymbol.isBlank() || intent.targetSymbol.isBlank() ||
            intent.status != CopyLinkStatus.INTENDED || intent.followerOrderId != null || intent.ownedExitIds.isNotEmpty()) return null
        if (existing.any { it.key == k || it.requestId == intent.requestId }) return null
        return intent
    }

    /** A successful HTTP reply only establishes an order ID, not a fill or position. */
    fun accepted(intent: CopyOrderLink, followerOrderId: String): CopyOrderLink? =
        intent.takeIf { it.status == CopyLinkStatus.INTENDED && followerOrderId.isNotBlank() }
            ?.copy(status = CopyLinkStatus.ACCEPTED, followerOrderId = followerOrderId)

    /** An uncertain outcome is never converted to a fresh intent and never retried automatically. */
    fun uncertain(intent: CopyOrderLink): CopyOrderLink? =
        intent.takeIf { it.status == CopyLinkStatus.INTENDED }?.copy(status = CopyLinkStatus.UNCERTAIN)
}
