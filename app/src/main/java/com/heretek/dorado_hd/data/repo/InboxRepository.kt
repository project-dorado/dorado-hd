package com.heretek.dorado_hd.data.repo

import com.heretek.dorado_hd.cloud.CloudSocialClient
import com.heretek.dorado_hd.data.db.DoradoDatabase
import com.heretek.dorado_hd.data.db.InboxMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A cached Zune inbox message surfaced to the UI. */
data class InboxMessage(
    val id: String,
    val senderTag: String,
    val recipientTag: String,
    val subject: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: Long,
)

/**
 * Outcome of a sync attempt, including the label the UI should show. Local
 * reads never depend on this: [messages] streams straight from Room.
 */
data class InboxSyncResult(val synced: Boolean, val label: String)

/**
 * Local-first Zune inbox (roadmap D2). Room is the source of truth; [sync]
 * pulls the cloud inbox and merges it in, preserving on-device read state.
 * When the cloud is disabled or unreachable the repository is a pure cache and
 * reports an explicit offline label.
 */
class InboxRepository(
    private val db: DoradoDatabase,
    private val cloud: CloudSocialClient,
) {

    fun messages(): Flow<List<InboxMessage>> =
        db.inboxDao().messages().map { rows -> rows.map { it.toModel() } }

    fun unreadCount(): Flow<Int> = db.inboxDao().unreadCount()

    suspend fun message(id: String): InboxMessage? = db.inboxDao().message(id)?.toModel()

    /** Marks a message read locally; remote mark-read is best-effort. */
    suspend fun markRead(id: String) {
        db.inboxDao().markRead(id)
        runCatching { cloud.markRead(id) }
    }

    suspend fun clear() = db.inboxDao().clear()

    /**
     * Refreshes the local cache from the cloud inbox. New rows inherit local
     * read state when already present; vanished messages are kept (a cache, not
     * a mirror). Returns whether the cloud answered, plus the offline label.
     */
    suspend fun sync(): InboxSyncResult {
        if (!cloud.isEnabled()) return InboxSyncResult(synced = false, label = OFFLINE_DISABLED)
        val inbox = cloud.inbox() ?: return InboxSyncResult(synced = false, label = OFFLINE_UNREACHABLE)
        val existing = db.inboxDao().all().associateBy { it.id }
        val rows = inbox.messages.map { message ->
            InboxMessageEntity(
                id = message.id,
                senderAccountId = null,
                senderTag = message.senderTag,
                recipientTag = message.recipientTag.ifBlank { inbox.zuneTag },
                subject = message.subject,
                body = message.body,
                isRead = (existing[message.id]?.isRead ?: false) || message.isRead,
                createdAt = message.createdAt,
            )
        }
        if (rows.isNotEmpty()) db.inboxDao().upsertAll(rows)
        return InboxSyncResult(synced = true, label = SYNCED)
    }

    private fun InboxMessageEntity.toModel() = InboxMessage(
        id = id,
        senderTag = senderTag,
        recipientTag = recipientTag,
        subject = subject,
        body = body,
        isRead = isRead,
        createdAt = createdAt,
    )

    companion object {
        const val OFFLINE_DISABLED = "offline - cloud disabled"
        const val OFFLINE_UNREACHABLE = "offline - showing saved messages"
        const val SYNCED = "cloud inbox - synced"
    }
}
