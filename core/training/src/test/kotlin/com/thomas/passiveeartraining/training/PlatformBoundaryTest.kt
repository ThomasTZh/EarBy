package com.thomas.passiveeartraining.training

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlatformBoundaryTest {
    @Test
    fun trainingTestsRunWithoutAndroidRuntime() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("android.app.Application")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun virtualTimeIsAvailableWithoutAnAndroidLooper() = runTest {
        val started = testScheduler.currentTime
        delay(1_000)
        assertEquals(1_000L, testScheduler.currentTime - started)
    }
}
