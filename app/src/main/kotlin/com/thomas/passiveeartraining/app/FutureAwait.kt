package com.thomas.passiveeartraining.app

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Propagates command failures and cancels a pending future with its caller. */
internal suspend fun <T> ListenableFuture<T>.awaitCommandResult(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                val result = try {
                    Result.success(get())
                } catch (failure: ExecutionException) {
                    Result.failure(failure.cause ?: failure)
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
                continuation.resumeWith(result)
            },
            MoreExecutors.directExecutor()
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
