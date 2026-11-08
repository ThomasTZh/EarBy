package com.thomas.passiveeartraining.music

import org.junit.Assert.assertThrows
import org.junit.Test

class PlatformBoundaryTest {
    @Test
    fun musicTestsRunWithoutAndroidRuntime() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("android.app.Application")
        }
    }
}
