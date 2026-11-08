package com.thomas.passiveeartraining.audio.pcm

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHeadCounterTest {
    @Test
    fun wrapsAndFlushesKeepConsumedPositionMonotonic() {
        val subject = PlaybackHeadCounter()
        assertEquals(4_294_967_280L, subject.position(-16))
        assertEquals(4_294_967_300L, subject.position(4))
        subject.flush(9)
        subject.flush(9)
        assertEquals(4_294_967_305L, subject.position(9))
        subject.start()
        assertEquals(4_294_967_308L, subject.position(3))
        subject.reset()
        assertEquals(0L, subject.position(0))
    }
}
