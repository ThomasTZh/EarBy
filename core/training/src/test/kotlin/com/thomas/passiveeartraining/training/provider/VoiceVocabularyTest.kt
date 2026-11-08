package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.SimpleInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4.3: the semantic vocabulary is complete, unique and derived from the frozen catalogs. */
class VoiceVocabularyTest {

    @Test
    fun everyEntryBelongsToItsNamespaceAndHasText() {
        val ids = mutableSetOf<String>()
        VoiceVocabulary.entries.forEach { entry ->
            assertTrue(
                "duplicate voice id ${entry.id.value}",
                ids.add(entry.id.value)
            )
            assertTrue(
                "${entry.id.value} must start with ${entry.namespace.prefix}.",
                entry.id.value.startsWith("${entry.namespace.prefix}.")
            )
            assertTrue("${entry.id.value} needs spoken text", entry.spokenText.isNotBlank())
        }
    }

    @Test
    fun theIntervalEntriesAreTheThirteenFrozenPhrases() {
        val intervals = SimpleInterval.ALL
        assertEquals(13, intervals.size)
        intervals.forEach { interval ->
            val entry = VoiceVocabulary.intervalEntry(interval)
            assertEquals("interval.${interval.id}", entry.id.value)
            assertEquals(interval.englishAnswer, entry.spokenText)
        }
    }

    @Test
    fun everyChordQualityAndInversionIsSpokenUnambiguously() {
        ChordQuality.entries.forEach { quality ->
            val entry = VoiceVocabulary.chordQualityEntry(quality)
            assertEquals("chord.${quality.id}", entry.id.value)
            assertTrue("${quality.id} needs spoken text", entry.spokenText.isNotBlank())
        }
        assertEquals("sus two", VoiceVocabulary.chordQualityEntry(ChordQuality.SUS2).spokenText)
        assertEquals("sus four", VoiceVocabulary.chordQualityEntry(ChordQuality.SUS4).spokenText)
        assertEquals(
            "root position",
            VoiceVocabulary.chordInversionEntry(0).spokenText
        )
        assertEquals("first inversion", VoiceVocabulary.chordInversionEntry(1).spokenText)
        assertEquals("second inversion", VoiceVocabulary.chordInversionEntry(2).spokenText)
        assertEquals("third inversion", VoiceVocabulary.chordInversionEntry(3).spokenText)
        // An audible answer must not rely on capitalization to distinguish qualities.
        val qualities = ChordQuality.entries.map {
            VoiceVocabulary.chordQualityEntry(it).spokenText
        }
        assertEquals(qualities.size, qualities.map { it.lowercase() }.distinct().size)
    }

    @Test
    fun everyCanonicalScaleNameIsPresentAndAliasesAreNotEntries() {
        ScaleKind.entries.forEach { kind ->
            val entry = VoiceVocabulary.scaleEntry(kind)
            assertEquals("scale.${kind.id}", entry.id.value)
            assertTrue(entry.spokenText.isNotBlank())
        }
        assertTrue(
            "aliases must not create extra ids",
            VoiceVocabulary.entries.none { it.id.value == "scale.ionian" }
        )
        assertEquals(13, ScaleKind.entries.size)
    }

    @Test
    fun noteAndOctaveEntriesCoverTheDocumentedRange() {
        Letter.entries.forEach { letter ->
            for (accidental in -2..2) {
                val entry = VoiceVocabulary.noteEntry(letter, accidental)
                assertTrue(entry.id.value.startsWith("note."))
                assertTrue(entry.spokenText.startsWith(letter.name))
            }
        }
        assertEquals("C sharp", VoiceVocabulary.noteEntry(Letter.C, 1).spokenText)
        assertEquals("B double flat", VoiceVocabulary.noteEntry(Letter.B, -2).spokenText)
        assertEquals("minus one", VoiceVocabulary.octaveEntry(-1).spokenText)
        assertEquals("zero", VoiceVocabulary.octaveEntry(0).spokenText)
        assertEquals("nine", VoiceVocabulary.octaveEntry(9).spokenText)
        assertThrows(IllegalArgumentException::class.java) { VoiceVocabulary.octaveEntry(10) }
        assertThrows(IllegalArgumentException::class.java) {
            VoiceVocabulary.noteEntry(Letter.C, 3)
        }
    }

    @Test
    fun degreeNumbersAreTheOnlyDegreeEntries() {
        assertEquals("seven", VoiceVocabulary.degreeNumberEntry(7).spokenText)
        assertTrue(
            "the removed fixed-do namespace must not exist",
            VoiceVocabulary.entries.none { it.id.value.startsWith("fixeddo.") }
        )
        assertTrue(
            "the removed movable-do syllables must not exist",
            VoiceVocabulary.entries.none { it.id.value.startsWith("degree.syllable.") }
        )
    }

    @Test
    fun theConjunctionAndDirectionConnectorsExist() {
        assertEquals("or", VoiceVocabulary.OR.spokenText)
        assertEquals("connector.or", VoiceVocabulary.OR.id.value)
        assertEquals(
            setOf(
                "connector.or",
                "connector.ascending",
                "connector.descending",
                "connector.unison"
            ),
            setOf(
                VoiceVocabulary.OR.id.value,
                VoiceVocabulary.ASCENDING.id.value,
                VoiceVocabulary.DESCENDING.id.value,
                VoiceVocabulary.UNISON.id.value
            )
        )
    }

    @Test
    fun lookupsAreExactAndMissingIdsFailLoudly() {
        val known = VoiceVocabulary.intervalEntry(SimpleInterval.ALL.first())
        assertEquals(known, VoiceVocabulary.ofIdOrNull(known.id))
        assertEquals(known, VoiceVocabulary.requireEntry(known.id))
        assertEquals(
            null,
            VoiceVocabulary.ofIdOrNull(
                com.thomas.passiveeartraining.training
                    .VoiceAssetId("interval.not_a_thing")
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            VoiceVocabulary.requireEntry(
                com.thomas.passiveeartraining.training.VoiceAssetId("chord.nope")
            )
        }
    }
}
