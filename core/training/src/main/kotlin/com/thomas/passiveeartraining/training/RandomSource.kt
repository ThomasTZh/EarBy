package com.thomas.passiveeartraining.training

/**
 * Injectable randomness port.
 *
 * Generation must be reproducible, so the domain never touches a platform random source
 * directly; it draws through this narrow interface instead.
 */
fun interface RandomSource {
    /**
     * Returns a pseudorandom non-negative value below [bound].
     *
     * @throws IllegalArgumentException when [bound] is not positive.
     */
    fun nextInt(bound: Int): Int
}

/** Raised when a random source is asked for an invalid bound. */
class InvalidRandomBoundException(message: String) : IllegalArgumentException(message)

/**
 * Reproducible random source backed by [java.util.Random].
 *
 * The JDK specifies this algorithm exactly, so a seed produces the same sequence on any
 * platform with the same JVM specification version. This is sampling reproducibility,
 * never a security feature; seeds that differ only outside the generator's effective
 * internal state can produce the same sequence.
 */
class SeededRandomSource(seed: Long) : RandomSource {
    private val random = java.util.Random(seed)

    override fun nextInt(bound: Int): Int {
        if (bound <= 0) {
            throw InvalidRandomBoundException("Random bound must be positive but was $bound")
        }
        return random.nextInt(bound)
    }

    /** The seed this source was created with; useful for recording the concrete generator identity. */
    val seed: Long = seed

    companion object {
        /** Stable identifier of the reproducible algorithm behind [SeededRandomSource]. */
        const val RNG_ID: String = "java-random-48-v1"
    }
}
