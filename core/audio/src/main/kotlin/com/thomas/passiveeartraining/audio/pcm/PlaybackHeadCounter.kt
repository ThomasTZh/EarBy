package com.thomas.passiveeartraining.audio.pcm

/** Unsigned playback-head accounting across wraps, pauses and flushes. */
internal class PlaybackHeadCounter {
    private var origin = 0L
    private var wraps = 0L
    private var lastRaw = 0L
    private var frozen = false

    fun position(raw: Int): Long {
        if (frozen) return origin
        val unsigned = raw.toLong() and 0xFFFF_FFFFL
        if (unsigned < lastRaw) wraps += 1L shl 32
        lastRaw = unsigned
        return origin + wraps + unsigned
    }

    fun flush(raw: Int) {
        if (frozen) return
        origin = position(raw)
        lastRaw = 0L
        wraps = 0L
        frozen = true
    }

    fun start() {
        frozen = false
    }
    fun reset() {
        origin = 0L
        wraps = 0L
        lastRaw = 0L
        frozen = false
    }
}
