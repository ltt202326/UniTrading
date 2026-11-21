package com.unitrading.app

/** Exit decisions take priority: a follower limit must not remain open after the linked leader trade has ended. */
internal sealed interface CopyReconciliationDecision {
    data class Exit(val decision: CopyExitDecision) : CopyReconciliationDecision
    data class Entry(val decision: CopyEntryPolicyDecision) : CopyReconciliationDecision
}

internal fun reconcileCopyOrder(leaderExitConfirmed: Boolean, entry: CopyEntryPolicyDecision,
    exitView: CopiedTradeExitView): CopyReconciliationDecision = if (leaderExitConfirmed)
    CopyReconciliationDecision.Exit(CopyExitPlanner.decide(exitView.copy(leaderExitConfirmed = true)))
else CopyReconciliationDecision.Entry(entry)
