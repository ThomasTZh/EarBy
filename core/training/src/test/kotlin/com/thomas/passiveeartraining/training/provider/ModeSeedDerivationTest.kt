package com.thomas.passiveeartraining.training.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The frozen `mode-seed-v1` vectors.
 *
 * These values were computed independently (SHA-256 of the documented UTF-8 message, first eight
 * bytes big-endian signed) before the Kotlin implementation existed, so the test cannot be
 * satisfied by re-implementing a mistaken formula.
 */
class ModeSeedDerivationTest {

    @Test
    fun theFrozenVectorsMatch() {
        assertEquals(
            5372097555021080418L,
            ModeSeedDerivation.derive(42L, TrainingKind.CHORDS, 0L, ModeSeedDerivation.LABELS)
        )
        assertEquals(
            6886745695080700039L,
            ModeSeedDerivation.derive(42L, TrainingKind.CHORDS, 0L, ModeSeedDerivation.CONTEXT)
        )
        assertEquals(
            -8783133494365262860L,
            ModeSeedDerivation.derive(42L, TrainingKind.CHORDS, 1L, ModeSeedDerivation.LABELS)
        )
        assertEquals(
            -7530698687206135243L,
            ModeSeedDerivation.derive(-1L, TrainingKind.INTERVALS, 0L, ModeSeedDerivation.LABELS)
        )
        assertEquals(
            4799027951399081341L,
            ModeSeedDerivation.derive(
                9223372036854775807L,
                TrainingKind.MELODIC_DICTATION,
                7L,
                ModeSeedDerivation.TARGET
            )
        )
    }

    @Test
    fun everyInputChangesTheResult() {
        val base = ModeSeedDerivation.derive(7L, TrainingKind.CHORDS, 0L, ModeSeedDerivation.LABELS)
        assertNotEquals(
            base,
            ModeSeedDerivation.derive(8L, TrainingKind.CHORDS, 0L, ModeSeedDerivation.LABELS)
        )
        assertNotEquals(
            base,
            ModeSeedDerivation.derive(7L, TrainingKind.SCALES, 0L, ModeSeedDerivation.LABELS)
        )
        assertNotEquals(
            base,
            ModeSeedDerivation.derive(7L, TrainingKind.CHORDS, 1L, ModeSeedDerivation.LABELS)
        )
        assertNotEquals(
            base,
            ModeSeedDerivation.derive(7L, TrainingKind.CHORDS, 0L, ModeSeedDerivation.CONTEXT)
        )
    }

    @Test
    fun invalidInputsAreRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            ModeSeedDerivation.derive(7L, TrainingKind.CHORDS, -1L, ModeSeedDerivation.LABELS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModeSeedDerivation.derive(7L, TrainingKind.CHORDS, 0L, "two\nlines")
        }
    }
}
