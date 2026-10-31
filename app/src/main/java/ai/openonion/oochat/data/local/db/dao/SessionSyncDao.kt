package ai.openonion.oochat.data.local.db.dao

import ai.openonion.oochat.data.local.db.entity.ChatSessionEntity
import ai.openonion.oochat.data.local.db.entity.SessionSyncStateEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Database operations used to merge the Host's Recent Chat list into Room. */
@Dao
interface SessionSyncDao {
    /** Returns the cursor for this exact identity and agent. */
    @Query(
        "SELECT cursor FROM session_sync_states " +
            "WHERE identity_address = :identityAddress AND agent_id = :agentId"
    )
    suspend fun getCursor(identityAddress: String, agentId: String): String?

    /** Replaces only the small cursor row; it cannot affect chat messages. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: SessionSyncStateEntity)

    /** Looks up the owner before an existing chat is changed. */
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    /** Keeps an existing chat row—and therefore its messages—when the ID already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    /** Updates only newer Host data for the expected agent, preserving local-only fields. */
    @Query(
        """
        UPDATE chat_sessions
        SET title = :title,
            updated_at = :updatedAt,
            last_message_preview = :preview,
            remote_revision = :revision
        WHERE id = :sessionId
          AND agent_id = :agentId
          AND (remote_revision IS NULL OR remote_revision < :revision)
        """
    )
    suspend fun updateRemoteMetadata(
        sessionId: String,
        agentId: String,
        title: String,
        updatedAt: Long,
        preview: String?,
        revision: Long
    ): Int

    /** A removal from one agent cannot delete another agent's chat. */
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId AND agent_id = :agentId")
    suspend fun deleteSessionForAgent(sessionId: String, agentId: String): Int
}
