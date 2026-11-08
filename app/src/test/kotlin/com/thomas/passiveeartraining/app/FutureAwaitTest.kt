package com.thomas.passiveeartraining.app

import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@kotlinx.coroutines.ExperimentalCoroutinesApi
class FutureAwaitTest {
    @Test fun aFailedCommandIsObservable() = runTest {
        val future = SettableFuture.create<String>()
        future.setException(IllegalStateException("write failed"))
        var observed: String? = null
        try {
            future.awaitCommandResult()
        } catch (failure: IllegalStateException) {
            observed = failure.message
        }
        assertEquals("write failed", observed)
    }

    @Test fun cancellationCancelsThePendingCommand() = runTest {
        val future = SettableFuture.create<String>()
        val waiting = async { future.awaitCommandResult() }
        runCurrent()
        waiting.cancel()
        runCurrent()
        assertTrue(future.isCancelled)
    }
}
