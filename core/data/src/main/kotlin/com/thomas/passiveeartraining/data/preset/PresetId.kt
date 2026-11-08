package com.thomas.passiveeartraining.data.preset

import java.util.UUID

/**
 * Stable identity of a preset.
 *
 * The stored form is opaque text so a document can name a preset without the store imposing its
 * generator; presets this build creates receive a UUID. Keeping the type distinct stops a preset id
 * and any other string from being passed to the wrong parameter.
 */
@JvmInline
value class PresetId(val value: String) {
    init {
        require(value.isNotBlank()) { "A preset id must not be blank" }
        require(value.length <= MAX_LENGTH) {
            "A preset id is at most $MAX_LENGTH characters but was ${value.length}"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 64

        /** A fresh identity for a preset this build creates. */
        fun random(): PresetId = PresetId(UUID.randomUUID().toString())
    }
}
