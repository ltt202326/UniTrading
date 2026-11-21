package com.unitrading.app

import com.unitrading.broker.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CopyOrderJournalTest {
    private val link = CopyOrderLink(
        CopyOrderKey(CopyAccountKey("a", "1"), "leader-order", CopyAccountKey("b", "2")),
        "request-1", "CLZ6", "MCLZ6", sourceOrderVersion = 1,
        targetRequest = OrderRequest("MCLZ6", Side.BUY, OrderKind.LIMIT, 100, entryPrice = 70_000_000, automated = true),
    )

    @Test fun savedIntentSurvivesRestartAndCannotBeResent() = runTest {
        val store = MemorySnapshotStore()
        assertTrue(CopyOrderJournal(store).begin(link))
        val restored = CopyOrderJournal(store)
        assertEquals(listOf(link), restored.read())
        assertFalse(restored.begin(link.copy(requestId = "second-attempt")))
        assertTrue(restored.uncertain(link.key, link.requestId))
        assertEquals(CopyLinkStatus.UNCERTAIN, CopyOrderJournal(store).read()!!.single().status)
        assertFalse(restored.accepted(link.key, link.requestId, "guessed-id"))
    }

    @Test fun aWriteFailureNeverAuthorizesAnOrderOrUpdatesMemory() = runTest {
        val store = object : SnapshotStore {
            override fun read(key: String): String? = null
            override fun write(key: String, value: String) { error("disk failure") }
        }
        val journal = CopyOrderJournal(store)
        assertFalse(journal.begin(link))
        assertEquals(emptyList(), journal.read())
    }

    @Test fun unreadableJournalBlocksAllWrites() = runTest {
        val store = MemorySnapshotStore().apply { write("copy-limit-order-links-v1", "{corrupt") }
        val journal = CopyOrderJournal(store)
        assertNull(journal.read())
        assertFalse(journal.begin(link))
    }
}
