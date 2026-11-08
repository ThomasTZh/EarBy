package com.thomas.passiveeartraining.audio.pcm

/**
 * One rendered exercise held as interleaved float PCM.
 *
 * Current and next exercises are cached; anything else is re-rendered on demand. The cache
 * accounting uses the exact byte count so the combined budget is enforced before allocation.
 */
class RenderedExercise(
    val identity: String,
    val sequenceIndex: Int,
    val sampleRate: Int,
    val channels: Int,
    val totalFrames: Long,
    val timeline: FrameTimeline
) {
    init {
        require(identity.isNotBlank()) { "Exercise identity must not be blank" }
        require(sequenceIndex >= 0) { "Sequence index must not be negative but was $sequenceIndex" }
        require(sampleRate > 0) { "Sample rate must be positive but was $sampleRate" }
        require(channels in 1..2) { "Channels must be 1 or 2 but was $channels" }
        require(totalFrames >= 0) { "Total frames must not be negative but was $totalFrames" }
    }

    /** Interleaved float PCM; length is `totalFrames * channels`. */
    val samples: FloatArray = FloatArray(size(totalFrames, channels))

    val byteCount: Long get() = totalFrames * channels.toLong() * FrameTimeline.FLOAT_BYTES

    /** Seconds this exercise occupies at its own sample rate. */
    val seconds: Double get() = totalFrames.toDouble() / sampleRate.toDouble()

    companion object {
        /** Element count of an interleaved buffer, range-checked for Int indexing. */
        fun size(frames: Long, channels: Int): Int {
            require(frames >= 0) { "Frames must not be negative but was $frames" }
            require(channels in 1..2) { "Channels must be 1 or 2 but was $channels" }
            val elements = frames * channels
            require(elements <= Int.MAX_VALUE) {
                "Buffer of $elements floats exceeds the largest addressable array"
            }
            return elements.toInt()
        }
    }
}

/**
 * Bounded current/next PCM cache.
 *
 * Only the exercise being played and the one after it are held, and the combined size is checked
 * against a byte budget before the new buffer is allocated. A replaced entry is dropped after its
 * reader stops; the caller decides when that is safe.
 */
class BoundedPcmCache(private val budgetBytes: Long = DEFAULT_BUDGET_BYTES) {

    init {
        require(budgetBytes > 0) { "Cache budget must be positive but was $budgetBytes" }
    }

    private var current: RenderedExercise? = null
    private var next: RenderedExercise? = null

    val currentExercise: RenderedExercise? get() = current
    val nextExercise: RenderedExercise? get() = next

    val usedBytes: Long get() = (current?.byteCount ?: 0L) + (next?.byteCount ?: 0L)

    /**
     * Records [exercise] as the next item, evicting the current one.
     *
     * @throws PcmBudgetExceededException when the two items would exceed the budget.
     */
    fun promoteToCurrent(exercise: RenderedExercise) {
        requireFits(exercise)
        current = exercise
        next = null
    }

    /** Stages [exercise] as the following item. */
    fun stageNext(exercise: RenderedExercise) {
        requireFits(exercise, replacingNext = true)
        next = exercise
    }

    /** Drops the staged next item, for example when navigation changed direction. */
    fun clearNext() {
        next = null
    }

    /** Drops everything; used on stop and on error. */
    fun clear() {
        current = null
        next = null
    }

    /**
     * Verifies that [exercise] plus what stays cached fits the budget.
     *
     * The check happens before allocation so an oversized request fails without allocating.
     */
    fun requireFits(exercise: RenderedExercise, replacingNext: Boolean = false) {
        val retained = if (replacingNext) (current?.byteCount ?: 0L) else 0L
        val projected = retained + exercise.byteCount
        if (exercise.byteCount > budgetBytes) {
            throw PcmBudgetExceededException(
                "Exercise '${exercise.identity}' needs ${exercise.byteCount} bytes, " +
                    "more than the whole $budgetBytes-byte cache budget"
            )
        }
        if (projected > budgetBytes) {
            throw PcmBudgetExceededException(
                "Caching '${exercise.identity}' would use $projected bytes, above the " +
                    "$budgetBytes-byte budget"
            )
        }
    }

    companion object {
        /** 48 MiB: two 60 s stereo float32 exercises at 48 kHz fit inside this. */
        const val DEFAULT_BUDGET_BYTES: Long = 48L * 1024L * 1024L

        /** Longest single exercise the roadmap allows. */
        const val MAX_EXERCISE_SECONDS: Int = 60
    }
}

/** Raised when a rendered exercise cannot fit the PCM cache budget. */
class PcmBudgetExceededException(message: String) : IllegalStateException(message)
