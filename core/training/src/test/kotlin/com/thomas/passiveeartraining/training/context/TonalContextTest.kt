package com.thomas.passiveeartraining.training.context

import com.thomas.passiveeartraining.music.InvalidMidiNoteException
import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.provider.GroupRole
import com.thomas.passiveeartraining.training.provider.NoteGroup
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration.Companion.milliseconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.1/M8-T02/T03: exact contents, roles and durations of the five reusable contexts.
 *
 * Every expectation is an independent musical statement (C major I-IV-V-I is C-E-G, F-A-C,
 * G-B-D), not merely the builder's own output restated.
 */
class TonalContextTest {

    private val cMajor = SpelledPitch.of(Letter.C, 0, 4)
    private val aMinor = SpelledPitch.of(Letter.A, 0, 4)

    private fun values(context: TonalContext): List<List<Int>> =
        context.groups.map { group -> group.notes.map { it.value } }

    private fun build(
        key: SpelledPitch = cMajor,
        anchor: Int = 60,
        tonality: Tonality = Tonality.MAJOR,
        kind: ContextKind
    ): TonalContext = ContextBuilder.build(key, MidiNote.of(anchor), tonality, kind)

    @Test
    fun theTonicNoteContextIsTheAnchorAlone() {
        val context = build(kind = ContextKind.TONIC_NOTE)
        assertEquals(listOf(listOf(60)), values(context))
        assertEquals(GroupRole.CONTEXT, context.groups.single().role)
        assertEquals(MidiNote.of(60), context.anchor)
        assertEquals(cMajor, context.key)
        assertEquals(Tonality.MAJOR, context.tonality)
        assertEquals(0, context.keyPitchClass)
        assertEquals(listOf(60), context.soundingValues)

        val minor = build(
            key = aMinor,
            anchor = 69,
            tonality = Tonality.NATURAL_MINOR,
            kind = ContextKind.TONIC_NOTE
        )
        assertEquals(listOf(listOf(69)), values(minor))
        assertEquals(9, minor.keyPitchClass)
    }

    @Test
    fun theTonicTriadIsRootPositionInBothTonalities() {
        val major = build(kind = ContextKind.TONIC_TRIAD)
        assertEquals(listOf(listOf(60, 64, 67)), values(major))
        assertEquals(major.anchor, major.groups.single().notes.first())

        val minor = build(
            key = aMinor,
            anchor = 69,
            tonality = Tonality.NATURAL_MINOR,
            kind = ContextKind.TONIC_TRIAD
        )
        assertEquals("A natural minor i is A-C-E", listOf(listOf(69, 72, 76)), values(minor))
    }

    @Test
    fun theAscendingScaleIsTheCompleteOctaveInBothTonalities() {
        val major = build(kind = ContextKind.ASCENDING_SCALE)
        assertEquals(
            listOf(
                listOf(60),
                listOf(62),
                listOf(64),
                listOf(65),
                listOf(67),
                listOf(69),
                listOf(71),
                listOf(72)
            ),
            values(major)
        )
        assertEquals(8, major.groups.size)
        assertTrue(major.groups.all { it.notes.size == 1 })

        val minor = build(
            key = aMinor,
            anchor = 69,
            tonality = Tonality.NATURAL_MINOR,
            kind = ContextKind.ASCENDING_SCALE
        )
        assertEquals(
            "A B C D E F G A with the lowered third, sixth and seventh",
            listOf(
                listOf(69),
                listOf(71),
                listOf(72),
                listOf(74),
                listOf(76),
                listOf(77),
                listOf(79),
                listOf(81)
            ),
            values(minor)
        )
        assertEquals(
            "the natural-minor scale keeps G natural",
            true,
            minor.soundingValues.contains(79)
        )
        assertTrue(
            "the natural-minor scale never contains G sharp",
            !minor.soundingValues.contains(80)
        )
    }

    @Test
    fun theMajorCadencesAreDiatonicRootPositionProgressions() {
        val tonicDominantTonic = build(kind = ContextKind.TONIC_DOMINANT_TONIC)
        assertEquals(
            "I-V-I in C major",
            listOf(listOf(60, 64, 67), listOf(67, 71, 74), listOf(60, 64, 67)),
            values(tonicDominantTonic)
        )

        val subdominantDominantTonic = build(kind = ContextKind.SUBDOMINANT_DOMINANT_TONIC)
        assertEquals(
            "IV-V-I in C major",
            listOf(listOf(65, 69, 72), listOf(67, 71, 74), listOf(60, 64, 67)),
            values(subdominantDominantTonic)
        )
        tonicDominantTonic.groups.forEach { chord ->
            assertEquals("each cadence chord is a closed triad", 3, chord.notes.size)
            assertEquals(
                "a major third above the root",
                4,
                chord.notes[1].value - chord.notes[0].value
            )
            assertEquals(
                "a perfect fifth above the root",
                7,
                chord.notes[2].value - chord.notes[0].value
            )
        }
    }

    @Test
    fun theMinorCadenceRaisesOnlyTheDominantsLeadingTone() {
        val tonicDominantTonic = build(
            key = aMinor,
            anchor = 69,
            tonality = Tonality.NATURAL_MINOR,
            kind = ContextKind.TONIC_DOMINANT_TONIC
        )
        assertEquals(
            "i-V-i in A minor with a major V: A-C-E, E-G#-B, A-C-E",
            listOf(listOf(69, 72, 76), listOf(76, 80, 83), listOf(69, 72, 76)),
            values(tonicDominantTonic)
        )
        assertEquals(
            "the dominant contains G sharp",
            true,
            tonicDominantTonic.groups[1].notes.map { it.value }.contains(80)
        )
        assertEquals(
            "the tonic chord keeps the natural leading-tone-free third",
            false,
            tonicDominantTonic.groups[0].notes.map { it.value }.contains(80)
        )

        val subdominantDominantTonic = build(
            key = aMinor,
            anchor = 69,
            tonality = Tonality.NATURAL_MINOR,
            kind = ContextKind.SUBDOMINANT_DOMINANT_TONIC
        )
        assertEquals(
            "iv-V-i in A minor",
            listOf(listOf(74, 77, 81), listOf(76, 80, 83), listOf(69, 72, 76)),
            values(subdominantDominantTonic)
        )
    }

    @Test
    fun everyGroupIsAContextGroupAndTheExposedListIsImmutable() {
        for (kind in ContextKind.entries) {
            val context = build(anchor = 60, kind = kind)
            assertTrue(
                "$kind must only carry context groups",
                context.groups.all { it.role == GroupRole.CONTEXT }
            )
            assertTrue("$kind must have at least one group", context.groups.isNotEmpty())
            @Suppress("UNCHECKED_CAST")
            val mutable = context.groups as MutableList<NoteGroup>
            assertThrows(UnsupportedOperationException::class.java) {
                mutable.add(context.groups.first())
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonalContext(
                cMajor,
                MidiNote.of(60),
                Tonality.MAJOR,
                ContextKind.TONIC_NOTE,
                listOf(NoteGroup(listOf(MidiNote.of(60)), GroupRole.TARGET))
            )
        }
    }

    @Test
    fun durationsFollowTheThreeContextFamilies() {
        val timing = RecipeTiming(
            noteDuration = 500.milliseconds,
            melodicGap = 50.milliseconds
        )
        assertEquals(
            500.milliseconds,
            ContextBuilder.duration(timing, ContextKind.TONIC_NOTE)
        )
        assertEquals(
            500.milliseconds,
            ContextBuilder.duration(timing, ContextKind.TONIC_TRIAD)
        )
        assertEquals(
            "8 * 500 ms + 7 * 50 ms",
            4350.milliseconds,
            ContextBuilder.duration(timing, ContextKind.ASCENDING_SCALE)
        )
        assertEquals(
            "3 * 500 ms + 2 * 50 ms",
            1600.milliseconds,
            ContextBuilder.duration(timing, ContextKind.TONIC_DOMINANT_TONIC)
        )
        assertEquals(
            1600.milliseconds,
            ContextBuilder.duration(timing, ContextKind.SUBDOMINANT_DOMINANT_TONIC)
        )

        val defaultTiming = RecipeTiming()
        assertEquals(
            "the default heptatonic scale lasts 6.3 s",
            6300.milliseconds,
            ContextBuilder.duration(defaultTiming, ContextKind.ASCENDING_SCALE)
        )
        assertEquals(
            "the default cadence lasts 2.3 s",
            2300.milliseconds,
            ContextBuilder.duration(defaultTiming, ContextKind.TONIC_DOMINANT_TONIC)
        )
    }

    @Test
    fun rootPositionConstructionsFailExplicitlyWhenTheyLeaveMidi() {
        assertEquals(
            "a triad at the top of the MIDI range still fits",
            listOf(listOf(120, 124, 127)),
            values(build(anchor = 120, kind = ContextKind.TONIC_TRIAD))
        )
        assertThrows(InvalidMidiNoteException::class.java) {
            build(anchor = 120, kind = ContextKind.TONIC_DOMINANT_TONIC)
        }
        assertThrows(InvalidMidiNoteException::class.java) {
            build(anchor = 125, kind = ContextKind.TONIC_TRIAD)
        }
        assertEquals(
            "a tonic note at MIDI 0 is valid",
            listOf(listOf(0)),
            values(build(anchor = 0, kind = ContextKind.TONIC_NOTE))
        )
    }

    @Test
    fun theCadenceRootsSitAtTheDiatonicOffsetsInTheAnchorsOctave() {
        val cadence = build(kind = ContextKind.SUBDOMINANT_DOMINANT_TONIC)
        assertEquals(
            "IV starts five semitones above the anchor",
            cadence.anchor.value + 5,
            cadence.groups[0].notes.first().value
        )
        assertEquals(
            "V starts seven semitones above the anchor",
            cadence.anchor.value + 7,
            cadence.groups[1].notes.first().value
        )
        assertEquals(
            "the final tonic returns to the anchor",
            cadence.anchor.value,
            cadence.groups[2].notes.first().value
        )
    }
}
