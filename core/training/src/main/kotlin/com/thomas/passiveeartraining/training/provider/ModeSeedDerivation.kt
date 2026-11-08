package com.thomas.passiveeartraining.training.provider

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * The versioned child-stream derivation `mode-seed-v1` (SC3).
 *
 * A switch activation gets its own recorded seed: the destination's fixed seed when it has one,
 * otherwise a value derived from the session's effective seed, the destination kind, the
 * activation ordinal and a stream label. The derivation is a pure SHA-256 over an explicit
 * UTF-8 sequence, so the same inputs always produce the same seed, independent of hash-map
 * iteration, localized names or system time.
 *
 * The first eight digest bytes are interpreted as a signed big-endian Long. Frozen test vectors
 * live under `.local/verification/mode-expansion/M3/mode-seed-v1-vectors.json`.
 */
object ModeSeedDerivation {

    /** The versioned label prefix; changing the algorithm means a new version string. */
    const val VERSION: String = "mode-seed-v1"

    /** The stream label of the activation seed itself. */
    const val ACTIVATION: String = "activation"

    /** The stream labels providers derive from a recorded activation seed. */
    const val LABELS: String = "labels"
    const val CONTEXT: String = "context"
    const val TARGET: String = "target"

    /**
     * Derives one child seed.
     *
     * The message is `mode-seed-v1`, the decimal [parentSeed], the stable [kind] id, the decimal
     * [activationOrdinal] and [label], separated by LF with a final LF.
     */
    fun derive(parentSeed: Long, kind: TrainingKind, activationOrdinal: Long, label: String): Long {
        require(activationOrdinal >= 0L) {
            "An activation ordinal cannot be negative but was $activationOrdinal"
        }
        require(label.isNotBlank() && label.none { it == '\n' }) {
            "A stream label must be non-blank and single-line but was '$label'"
        }
        val message = buildString {
            append(VERSION)
            append('\n')
            append(parentSeed)
            append('\n')
            append(kind.id)
            append('\n')
            append(activationOrdinal)
            append('\n')
            append(label)
            append('\n')
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(message.toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(digest, 0, 8).long
    }
}
