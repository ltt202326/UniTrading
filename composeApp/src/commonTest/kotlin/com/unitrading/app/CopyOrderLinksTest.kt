package com.unitrading.app

import kotlinx.serialization.json.Json
import kotlin.test.*

class CopyOrderLinksTest {
    private val leader = CopyAccountKey("demo-login-a", "1")
    private val follower = CopyAccountKey("demo-login-b", "2")
    private val initial = CopyOrderLink(CopyOrderKey(leader, "7", follower), "req-1", "CLZ6", "MCLZ6")

    @Test fun intentUniquenessIncludesBothLoginAndAccount() {
        assertNotNull(CopyOrderLinks.newIntent(emptyList(), initial))
        assertNull(CopyOrderLinks.newIntent(listOf(initial), initial.copy(requestId = "another")))
        assertNull(CopyOrderLinks.newIntent(listOf(initial), initial.copy(key = initial.key.copy(leaderOrderId = "8"))))
        assertNull(CopyOrderLinks.newIntent(emptyList(), initial.copy(key = initial.key.copy(follower = leader))))
        assertNull(CopyOrderLinks.newIntent(emptyList(), initial.copy(key = initial.key.copy(follower = CopyAccountKey("other-login", "1")))))
    }

    @Test fun anAcknowledgementDoesNotImplyACompletedTrade() {
        val accepted = assertNotNull(CopyOrderLinks.accepted(initial, "follower-order"))
        assertEquals(CopyLinkStatus.ACCEPTED, accepted.status)
        assertEquals("follower-order", accepted.followerOrderId)
        assertNull(CopyOrderLinks.accepted(accepted, "duplicate"))
    }

    @Test fun uncertainResultsStayBlockedAfterSerialization() {
        val uncertain = assertNotNull(CopyOrderLinks.uncertain(initial))
        val recovered = Json.decodeFromString<CopyOrderLink>(Json.encodeToString(CopyOrderLink.serializer(), uncertain))
        assertEquals(CopyLinkStatus.UNCERTAIN, recovered.status)
        assertNull(CopyOrderLinks.accepted(recovered, "guess"))
        assertNull(CopyOrderLinks.newIntent(listOf(recovered), initial.copy(requestId = "retry")))
    }
}
