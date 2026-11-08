package com.thomas.passiveeartraining.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** M5-T03: D08 equivalence over the frozen chord catalog. */
class ChordEquivalenceTest {

    private val allCategories: List<ChordInterpretation> = ChordQuality.entries.flatMap { quality ->
        (0..quality.maximumInversion).map { ChordInterpretation(quality, it) }
    }

    @Test
    fun offsetsMatchTheClosedVoicingsForEveryCatalogPair() {
        ChordQuality.entries.forEach { quality ->
            (0..quality.maximumInversion).forEach { inversion ->
                val voicing = ChordVoicing.of(quality, inversion, MidiNote.of(MidiNote.C4))
                assertEquals(
                    "${quality.id} inversion $inversion",
                    voicing.notes.map { it.value - voicing.bass.value },
                    ChordEquivalence.bassRelativeOffsets(quality, inversion)
                )
            }
        }
    }

    @Test
    fun aMajorTriadInRootPositionMatchesOnlyItself() {
        val voicing = ChordVoicing.of(ChordQuality.MAJOR, 0, MidiNote.of(MidiNote.C4))
        assertEquals(
            listOf(ChordInterpretation(ChordQuality.MAJOR, 0)),
            ChordEquivalence.interpretationsOf(voicing, allCategories)
        )
    }

    @Test
    fun aMajorTriadIsNeverReadAsAMinorInvestion() {
        val voicing = ChordVoicing.of(ChordQuality.MAJOR, 0, MidiNote.of(MidiNote.C4))
        val allowed = listOf(
            ChordInterpretation(ChordQuality.MINOR, 0),
            ChordInterpretation(ChordQuality.MINOR, 1),
            ChordInterpretation(ChordQuality.MAJOR, 0)
        )
        assertEquals(
            listOf(ChordInterpretation(ChordQuality.MAJOR, 0)),
            ChordEquivalence.interpretationsOf(voicing, allowed)
        )
    }

    @Test
    fun symmetricAugmentedTriadsMatchEveryInversion() {
        val voicing = ChordVoicing.of(ChordQuality.AUGMENTED, 0, MidiNote.of(MidiNote.C4))
        val allowed = (0..2).map { ChordInterpretation(ChordQuality.AUGMENTED, it) }
        assertEquals(allowed, ChordEquivalence.interpretationsOf(voicing, allowed))
    }

    @Test
    fun symmetricDiminishedSeventhsMatchEveryInversion() {
        val voicing = ChordVoicing.of(ChordQuality.DIMINISHED_SEVENTH, 2, MidiNote.of(MidiNote.C4))
        val allowed = (0..3).map { ChordInterpretation(ChordQuality.DIMINISHED_SEVENTH, it) }
        assertEquals(allowed, ChordEquivalence.interpretationsOf(voicing, allowed))
    }

    @Test
    fun suspendedTriadsAreInversionsOfEachOther() {
        // C sus2 sounds C D G; the same sound is G sus4 in first inversion.
        val voicing = ChordVoicing.of(ChordQuality.SUS2, 0, MidiNote.of(MidiNote.C4))
        val allowed = listOf(
            ChordInterpretation(ChordQuality.SUS2, 0),
            ChordInterpretation(ChordQuality.SUS4, 1)
        )
        // Catalog order: sus2 (ordinal 4) before sus4 (ordinal 5).
        assertEquals(allowed, ChordEquivalence.interpretationsOf(voicing, allowed))
    }

    @Test
    fun aNonEquivalentTriadIsNeverMatched() {
        val voicing = ChordVoicing.of(ChordQuality.MAJOR, 0, MidiNote.of(MidiNote.C4))
        val allowed = listOf(
            ChordInterpretation(ChordQuality.SUS4, 1),
            ChordInterpretation(ChordQuality.AUGMENTED, 2),
            ChordInterpretation(ChordQuality.MAJOR, 0)
        )
        assertEquals(
            listOf(ChordInterpretation(ChordQuality.MAJOR, 0)),
            ChordEquivalence.interpretationsOf(voicing, allowed)
        )
    }

    @Test
    fun alternativesComeBackInCatalogOrderThenInversion() {
        val voicing = ChordVoicing.of(
            ChordQuality.DIMINISHED_SEVENTH,
            0,
            MidiNote.of(MidiNote.C4)
        )
        val shuffled = listOf(
            ChordInterpretation(ChordQuality.DIMINISHED_SEVENTH, 2),
            ChordInterpretation(ChordQuality.DIMINISHED_SEVENTH, 0),
            ChordInterpretation(ChordQuality.DIMINISHED_SEVENTH, 3),
            ChordInterpretation(ChordQuality.DIMINISHED_SEVENTH, 1)
        )
        assertEquals(
            (0..3).map { ChordInterpretation(ChordQuality.DIMINISHED_SEVENTH, it) },
            ChordEquivalence.interpretationsOf(voicing, shuffled)
        )
    }

    @Test
    fun theInversionLabelsMatchTheFrozenVocabularyText() {
        assertEquals("root position", ChordInversions.answer(0))
        assertEquals("first inversion", ChordInversions.answer(1))
        assertEquals("second inversion", ChordInversions.answer(2))
        assertEquals("third inversion", ChordInversions.answer(3))
        assertEquals(
            listOf("root", "first", "second", "third"),
            (0..3).map { ChordInversions.idSuffix(it) }
        )
    }
}
