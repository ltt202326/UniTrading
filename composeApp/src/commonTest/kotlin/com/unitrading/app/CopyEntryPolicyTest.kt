package com.unitrading.app

import kotlin.test.*

class CopyEntryPolicyTest {
    @Test fun followerFirstCancelsOnlyWhenProtectionIsProvenSafe() {
        val ahead = CopyEntryDivergence.FollowerAhead(1, 0, 1)
        assertEquals(CopyEntryPolicyDecision.CancelRemainderAndPause("e1"), copyEntryPolicy(ahead, EntryOrderState.WORKING, "e1", true))
        assertIs<CopyEntryPolicyDecision.PauseFollower>(copyEntryPolicy(ahead, EntryOrderState.WORKING, "e1", false))
        assertIs<CopyEntryPolicyDecision.PauseFollower>(copyEntryPolicy(ahead, EntryOrderState.FINISHED, "e1", true))
    }

    @Test fun leaderFirstKeepsAWorkingLimitAndNeverChasesARejectedOrUnknownOrder() {
        val behind = CopyEntryDivergence.FollowerBehind(0, 1, 1)
        assertEquals(CopyEntryPolicyDecision.KeepLimitAndNotify, copyEntryPolicy(behind, EntryOrderState.WORKING, "e1", false))
        assertIs<CopyEntryPolicyDecision.PauseFollower>(copyEntryPolicy(behind, EntryOrderState.FINISHED, "e1", false))
        assertIs<CopyEntryPolicyDecision.PauseFollower>(copyEntryPolicy(CopyEntryDivergence.Unknown("离线"), EntryOrderState.UNKNOWN, null, false))
    }
}
