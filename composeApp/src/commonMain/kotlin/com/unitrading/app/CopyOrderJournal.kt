package com.unitrading.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Durable, fail-closed outbox for copied orders. An intent must be saved before the broker call. INTENDED and UNCERTAIN
 * do not mean that no order exists: after a crash they require broker reconciliation, never automatic resubmission.
 */
internal class CopyOrderJournal(private val store: SnapshotStore) {
    private val key = "copy-limit-order-links-v1"
    private val codec = ListSerializer(CopyOrderLink.serializer())
    private val initial = runCatching { store.read(key)?.takeIf(String::isNotBlank)?.let { Json.decodeFromString(codec, it) }.orEmpty() }
    private val mutex = Mutex()
    private var unreadable = initial.isFailure
    private var links = initial.getOrDefault(emptyList())

    suspend fun read(): List<CopyOrderLink>? = mutex.withLock { if (unreadable) null else links.toList() }

    suspend fun begin(intent: CopyOrderLink): Boolean = mutex.withLock {
        if (unreadable || CopyOrderLinks.newIntent(links, intent) == null || intent.targetRequest == null || intent.sourceOrderVersion <= 0) return@withLock false
        persist(links + intent)
    }

    suspend fun accepted(key: CopyOrderKey, requestId: String, followerOrderId: String): Boolean = mutex.withLock {
        transition(key, requestId) { CopyOrderLinks.accepted(it, followerOrderId) }
    }

    suspend fun uncertain(key: CopyOrderKey, requestId: String): Boolean = mutex.withLock {
        transition(key, requestId) { CopyOrderLinks.uncertain(it) }
    }

    private fun transition(key: CopyOrderKey, requestId: String, change: (CopyOrderLink) -> CopyOrderLink?): Boolean {
        if (unreadable) return false
        val index = links.indexOfFirst { it.key == key && it.requestId == requestId }
        if (index < 0) return false
        val replacement = change(links[index]) ?: return false
        return persist(links.toMutableList().also { it[index] = replacement })
    }

    private fun persist(next: List<CopyOrderLink>): Boolean = try {
        store.write(key, Json.encodeToString(codec, next))
        links = next
        true
    } catch (_: Exception) { false }
}
