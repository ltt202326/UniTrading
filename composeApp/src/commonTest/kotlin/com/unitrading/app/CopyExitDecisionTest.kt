package com.unitrading.app

import com.unitrading.broker.Side
import kotlin.test.*

class CopyExitDecisionTest {
    private fun view(leaderExit: Boolean, owned: Int, net: Int = owned, exits: Set<String> = emptySet()) =
        CopiedTradeExitView(true, leaderExit, true, owned, net, exits, false, true,
            expectedSide = if (owned > 0) Side.BUY else null, actualSide = if (owned > 0) Side.BUY else null,
            positionId = if (owned > 0) "position-1" else null, positionVersion = if (owned > 0) 2 else null)

    @Test fun leaderExitClosesOnlyTheAttributableFollowerPosition() {
        assertEquals(CopyExitDecision.CloseCopiedPosition("position-1", 2, 2), CopyExitPlanner.decide(view(true, 2)))
        assertIs<CopyExitDecision.Stop>(CopyExitPlanner.decide(view(true, 2, net = 3)))
        assertIs<CopyExitDecision.Stop>(CopyExitPlanner.decide(view(true, 2).copy(actualSide = Side.SELL)))
    }

    @Test fun followerAlreadyStoppedOutDoesNotGetASecondExit() {
        assertEquals(CopyExitDecision.AlreadyClosed, CopyExitPlanner.decide(view(true, 0)))
        assertEquals(CopyExitDecision.CancelOwnedExits(setOf("owned-tp")), CopyExitPlanner.decide(view(true, 0, exits = setOf("owned-tp"))))
    }

    @Test fun waitsForLeaderExitAndStopsWhenOrderAttributionOrConnectionIsUnknown() {
        assertEquals(CopyExitDecision.Wait, CopyExitPlanner.decide(view(false, 1)))
        assertIs<CopyExitDecision.Stop>(CopyExitPlanner.decide(view(true, 1).copy(brokerSynchronized = false)))
        assertIs<CopyExitDecision.Stop>(CopyExitPlanner.decide(view(true, 1).copy(attributedExitIdsComplete = false)))
        assertIs<CopyExitDecision.Stop>(CopyExitPlanner.decide(view(true, 0).copy(unrelatedWorkingOrdersOnSymbol = true)))
    }

    @Test fun leaderExitCancelsUnfilledEntryAndDoesNotRemoveUnverifiedProtection() {
        assertEquals(CopyExitDecision.CancelOwnedEntry("entry"), CopyExitPlanner.decide(view(true, 0).copy(ownedWorkingEntryId = "entry")))
        assertIs<CopyExitDecision.Stop>(CopyExitPlanner.decide(view(true, 1).copy(ownedWorkingEntryId = "entry")))
        assertEquals(CopyExitDecision.CancelOwnedEntry("entry"), CopyExitPlanner.decide(view(true, 1).copy(
            ownedWorkingEntryId = "entry", partialEntryCancellationPreservesProtection = true)))
    }
}
