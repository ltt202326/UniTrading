package com.unitrading.app

import kotlin.test.*

class CopyEntryDivergenceTest {
    private val cl = CopyContractMapping("CLZ6", "MCLZ6", 100, 10)
    private fun state(leader: Long, follower: Long) = EntryFillObservation(true, EntryOrderState.WORKING, EntryOrderState.WORKING, leader, follower)

    @Test fun identifiesFollowerFirstOrLeaderFirstUsingTheSelectedExposureBasis() {
        assertIs<CopyEntryDivergence.FollowerAhead>(entryDivergence(state(0, 1), cl, 100, CopyQuantityBasis.MATCH_EXPOSURE))
        assertIs<CopyEntryDivergence.FollowerBehind>(entryDivergence(state(1, 0), cl, 100, CopyQuantityBasis.MATCH_EXPOSURE))
        assertIs<CopyEntryDivergence.FollowerBehind>(entryDivergence(state(1, 5), cl, 100, CopyQuantityBasis.MATCH_EXPOSURE))
        assertEquals(CopyEntryDivergence.InSync, entryDivergence(state(1, 10), cl, 100, CopyQuantityBasis.MATCH_EXPOSURE))
    }

    @Test fun ignoresNoAmbiguousDisconnectOrUnknownOrder() {
        assertIs<CopyEntryDivergence.Unknown>(entryDivergence(state(0, 0).copy(connectedAndSynchronized = false), cl, 100, CopyQuantityBasis.MATCH_EXPOSURE))
        assertIs<CopyEntryDivergence.Unknown>(entryDivergence(state(0, 0).copy(followerState = EntryOrderState.UNKNOWN), cl, 100, CopyQuantityBasis.MATCH_EXPOSURE))
    }

    @Test fun selectedRoundingIsAppliedBeforeComparingFillProgress() {
        assertEquals(CopyEntryDivergence.InSync, entryDivergence(state(1, 1), cl, 50, CopyQuantityBasis.MATCH_CONTRACTS))
        assertIs<CopyEntryDivergence.FollowerAhead>(entryDivergence(state(0, 1), cl, 50, CopyQuantityBasis.MATCH_CONTRACTS))
    }
}
