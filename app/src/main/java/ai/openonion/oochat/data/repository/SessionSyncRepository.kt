package ai.openonion.oochat.data.repository

import ai.openonion.oochat.network.SessionSyncSnapshot

/** Persists complete retained-session snapshots and their incremental cursor. */
interface SessionSyncRepository {
    suspend fun activateIdentity(identityAddress: String)

    suspend fun cursorFor(identityAddress: String, agentId: String): String?

    suspend fun reconcile(
        identityAddress: String,
        agentId: String,
        snapshot: SessionSyncSnapshot
    )

    /**
     * Advances the Host cursor without importing any of the sessions in that
     * snapshot. Used for the first sync after Memory is re-enabled: sessions
     * created while Memory was off are intentionally skipped.
     */
    suspend fun advanceCursor(
        identityAddress: String,
        agentId: String,
        cursor: String
    ) {
        reconcile(
            identityAddress = identityAddress,
            agentId = agentId,
            snapshot = SessionSyncSnapshot(
                sessions = emptyList(),
                removedSessionIds = emptyList(),
                cursor = cursor
            )
        )
    }
}
