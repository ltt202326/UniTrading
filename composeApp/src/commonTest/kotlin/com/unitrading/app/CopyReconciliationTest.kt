package com.unitrading.app

import kotlin.test.*

class CopyReconciliationTest {
    @Test fun leaderClosingOverridesTheNormalWaitForAnUnfilledFollowerLimit() {
        val view = CopiedTradeExitView(true, false, true, 0, 0, emptySet(), false, true, ownedWorkingEntryId = "entry")
        assertEquals(CopyReconciliationDecision.Exit(CopyExitDecision.CancelOwnedEntry("entry")),
            reconcileCopyOrder(true, CopyEntryPolicyDecision.KeepLimitAndNotify, view))
    }

    @Test fun normalEntryPolicyRemainsInForceWhileLeaderIsOpen() {
        val view = CopiedTradeExitView(true, false, true, 0, 0, emptySet(), false, true)
        assertEquals(CopyReconciliationDecision.Entry(CopyEntryPolicyDecision.KeepLimitAndNotify),
            reconcileCopyOrder(false, CopyEntryPolicyDecision.KeepLimitAndNotify, view))
    }
}
