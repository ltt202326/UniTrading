package com.unitrading.app

/** Decisions only; the executor must persist intents and verify broker identity before it writes. */
internal sealed interface CopyEntryPolicyDecision {
    data object Continue : CopyEntryPolicyDecision
    /** Do not place additional orders or remove the filled position or its protective legs. */
    data class PauseFollower(val reason: String) : CopyEntryPolicyDecision
    /** First verify that cancellation will not remove protection for the already filled portion. */
    data class CancelRemainderAndPause(val orderId: String) : CopyEntryPolicyDecision
    /** The follower's own working limit remains; no market chase. */
    data object KeepLimitAndNotify : CopyEntryPolicyDecision
}

internal fun copyEntryPolicy(divergence: CopyEntryDivergence, followerState: EntryOrderState,
    followerEntryId: String?, partialEntryCancellationPreservesProtection: Boolean): CopyEntryPolicyDecision = when (divergence) {
    CopyEntryDivergence.InSync -> CopyEntryPolicyDecision.Continue
    is CopyEntryDivergence.Unknown -> CopyEntryPolicyDecision.PauseFollower(divergence.reason)
    is CopyEntryDivergence.FollowerBehind -> if (followerState == EntryOrderState.WORKING && !followerEntryId.isNullOrBlank())
        CopyEntryPolicyDecision.KeepLimitAndNotify else CopyEntryPolicyDecision.PauseFollower("跟随订单不再有效，请核对；不自动追单")
    is CopyEntryDivergence.FollowerAhead -> if (followerState == EntryOrderState.WORKING && !followerEntryId.isNullOrBlank() &&
        partialEntryCancellationPreservesProtection) CopyEntryPolicyDecision.CancelRemainderAndPause(followerEntryId)
        else CopyEntryPolicyDecision.PauseFollower("跟随账户先成交，保留仓位和保护并核对未成交部分")
}
