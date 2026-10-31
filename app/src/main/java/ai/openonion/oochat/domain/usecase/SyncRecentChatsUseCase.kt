package ai.openonion.oochat.domain.usecase

import ai.openonion.oochat.data.repository.SessionSyncRepository
import ai.openonion.oochat.network.SessionSyncFetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The result of refreshing one agent's retained conversation index. */
sealed interface RecentChatsSyncResult {
    data object Synced : RecentChatsSyncResult
    data object Unsupported : RecentChatsSyncResult
    data class Failed(val message: String, val retryable: Boolean = false) : RecentChatsSyncResult
}

/** Small seam used by the UI and its tests. */
fun interface RecentChatsSync {
    suspend fun sync(agentId: String, agentAddress: String, directUrl: String?): RecentChatsSyncResult
}

/** Fetches and safely commits the current identity's Recent Chat index. */
class SyncRecentChatsUseCase(
    private val repository: SessionSyncRepository,
    private val identityAddress: suspend () -> String,
    private val fetch: suspend (agentAddress: String, directUrl: String?, cursor: String?) -> SessionSyncFetchResult
) : RecentChatsSync {
    // A reconnect and the screen joining that same connection can arrive
    // together. Serialize them so two snapshots cannot race their cursors.
    private val syncMutex = Mutex()

    override suspend fun sync(
        agentId: String,
        agentAddress: String,
        directUrl: String?
    ): RecentChatsSyncResult = syncMutex.withLock {
        try {
            require(agentId.isNotBlank()) { "agentId must not be blank" }
            require(agentAddress.isNotBlank()) { "agentAddress must not be blank" }

            val identity = identityAddress()
            require(identity.isNotBlank()) { "identityAddress must not be blank" }
            val cursor = repository.cursorFor(identity, agentId)

            when (val first = fetch(agentAddress, directUrl, cursor)) {
                is SessionSyncFetchResult.Complete -> commit(identity, agentId, first)
                is SessionSyncFetchResult.CursorExpired -> {
                    // A cursor only points into the Host's retained change
                    // history. If it has aged out, one full snapshot is the
                    // safe recovery; never loop if the Host rejects that too.
                    when (val retry = fetch(agentAddress, directUrl, null)) {
                        is SessionSyncFetchResult.Complete -> commit(identity, agentId, retry)
                        is SessionSyncFetchResult.Unsupported -> RecentChatsSyncResult.Unsupported
                        is SessionSyncFetchResult.Failed -> retry.toSyncResult()
                        is SessionSyncFetchResult.CursorExpired -> RecentChatsSyncResult.Failed(
                            retry.error.message.ifBlank { "Session Sync cursor recovery failed" },
                            retryable = retry.error.retryable
                        )
                    }
                }
                is SessionSyncFetchResult.Unsupported -> RecentChatsSyncResult.Unsupported
                is SessionSyncFetchResult.Failed -> first.toSyncResult()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            RecentChatsSyncResult.Failed(failure.message ?: "Recent Chat sync failed")
        }
    }

    private suspend fun commit(
        identityAddress: String,
        agentId: String,
        complete: SessionSyncFetchResult.Complete
    ): RecentChatsSyncResult {
        repository.reconcile(identityAddress, agentId, complete.snapshot)
        return RecentChatsSyncResult.Synced
    }

    private fun SessionSyncFetchResult.Failed.toSyncResult() =
        RecentChatsSyncResult.Failed(message, retryable)
}
