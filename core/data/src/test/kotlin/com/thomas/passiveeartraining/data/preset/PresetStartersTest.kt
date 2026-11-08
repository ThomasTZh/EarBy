package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The intervals setup of a starter this suite inspects. */
private val Preset.intervalSetup: LastSetup
    get() = (configuration as PresetConfiguration.Intervals).setup

/** The chords setup of a starter this suite inspects. */
private val Preset.chordsSetup: ChordSetup
    get() = (configuration as PresetConfiguration.Chords).setup

/** The scales setup of a starter this suite inspects. */
private val Preset.scalesSetup: ScaleSetup
    get() = (configuration as PresetConfiguration.Scales).setup

/** The notes setup of a starter this suite inspects. */
private val Preset.notesSetup: NoteSetup
    get() = (configuration as PresetConfiguration.Notes).setup

/** The scale-degrees setup of a starter this suite inspects. */
private val Preset.scaleDegreesSetup: ScaleDegreeSetup
    get() = (configuration as PresetConfiguration.ScaleDegrees).setup

/** The chord-progressions setup of a starter this suite inspects. */
private val Preset.progressionsSetup: ProgressionSetup
    get() = (configuration as PresetConfiguration.Progressions).setup

/** The intervals-in-context setup of a starter this suite inspects. */
private val Preset.contextualIntervalsSetup: ContextualIntervalSetup
    get() = (configuration as PresetConfiguration.ContextualIntervals).setup

/** The melodic-dictation setup of a starter this suite inspects. */
private val Preset.melodicDictationSetup: MelodicDictationSetup
    get() = (configuration as PresetConfiguration.MelodicDictation).setup

/**
 * The starters must be usable as-is: a first run that offers a preset which cannot play one of its
 * intervals or chords would be a defect in the product's first screen.
 */
class PresetStartersTest {

    @Test
    fun everyStarterIsAValidFeasiblePreset() {
        PresetStarters.ALL_KINDS.forEach { starter ->
            PresetDocumentCodec.validatePreset(starter)
        }
    }

    @Test
    fun everyStarterIsBuiltInAndHasAStableDistinctIdentity() {
        val ids = PresetStarters.ALL_KINDS.map { it.id }
        val names = PresetStarters.ALL_KINDS.map { it.name }
        assertEquals("identities are distinct", ids.size, ids.distinct().size)
        assertEquals("names are distinct", names.size, names.distinct().size)
        assertTrue("starters are immutable", PresetStarters.ALL_KINDS.all { it.builtIn })
    }

    @Test
    fun theFourIntervalStartersCarryTheReviewedDefaults() {
        val starters = PresetStarters.ALL
        assertEquals(
            listOf("Major Pentatonic", "Minor Pentatonic", "Major Triad", "Minor Triad"),
            starters.map { it.name }
        )
        starters.forEach { starter ->
            val setup = starter.intervalSetup
            assertEquals("C4..C5", 60..72, setup.minimumNote..setup.maximumNote)
            assertEquals("a 3 s thinking pause", 3000L, setup.thinkingSilence.inWholeMilliseconds)
            assertEquals("the tonic is fixed at C4", 60, setup.fixedRoot)
            assertEquals("the separator cue is on", true, setup.separatorCue)
            assertEquals("the reviewed seed is kept", 776655L, starter.seed)
            assertEquals("the reviewed seed is in the setup too", 776655L, setup.seed)
        }
    }

    @Test
    fun theMajorPentatonicStarterSelectsItsSixIntervals() {
        assertEquals(
            listOf(
                "unison",
                "major_second",
                "major_third",
                "perfect_fifth",
                "major_sixth",
                "octave"
            ),
            PresetStarters.MAJOR_PENTATONIC.intervalSetup.intervalIds
        )
    }

    @Test
    fun theMinorPentatonicStarterSelectsItsSixIntervals() {
        assertEquals(
            listOf(
                "unison",
                "minor_third",
                "perfect_fourth",
                "perfect_fifth",
                "minor_seventh",
                "octave"
            ),
            PresetStarters.MINOR_PENTATONIC.intervalSetup.intervalIds
        )
    }

    @Test
    fun theTriadStartersSelectTheTriadIntervals() {
        assertEquals(
            listOf("unison", "major_third", "perfect_fifth", "octave"),
            PresetStarters.MAJOR_TRIAD.intervalSetup.intervalIds
        )
        assertEquals(
            listOf("unison", "minor_third", "perfect_fifth", "octave"),
            PresetStarters.MINOR_TRIAD.intervalSetup.intervalIds
        )
    }

    @Test
    fun theChordsStarterIsTheDefaultTableWithItsFrozenIdentity() {
        val starter = PresetStarters.CHORDS

        assertEquals(PresetId("builtin:chords:starter:v1"), starter.id)
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.CHORDS, starter.configuration.kind)
        assertEquals(
            "the starter opens the default chord table",
            ChordSetup.default(),
            starter.chordsSetup
        )
        assertTrue("major is selected", "major" in starter.chordsSetup.qualityIds)
        assertTrue("minor is selected", "minor" in starter.chordsSetup.qualityIds)
        assertEquals(listOf(0), starter.chordsSetup.inversions)
    }

    @Test
    fun theScalesStarterIsTheDefaultTableWithItsFrozenIdentity() {
        val starter = PresetStarters.SCALES

        assertEquals(PresetId("builtin:scales:starter:v1"), starter.id)
        assertEquals(
            "the starter opens the default scale table",
            ScaleSetup.default(),
            starter.scalesSetup
        )
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.SCALES, starter.configuration.kind)
        assertEquals(
            listOf("major", "natural_minor"),
            starter.scalesSetup.scaleIds
        )
        assertEquals(
            com.thomas.passiveeartraining.music.ScaleDirection.ASCENDING,
            starter.scalesSetup.direction
        )
        assertEquals(48, starter.scalesSetup.minimumNote)
        assertEquals(84, starter.scalesSetup.maximumNote)
    }

    @Test
    fun theNotesStarterIsTheDefaultTableWithItsFrozenIdentity() {
        val starter = PresetStarters.NOTES

        assertEquals(PresetId("builtin:note_identification:starter:v1"), starter.id)
        assertEquals("All twelve notes", starter.name)
        assertEquals(
            "the starter opens the default note table",
            NoteSetup.default(),
            starter.notesSetup
        )
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.NOTE_IDENTIFICATION, starter.configuration.kind)
        assertEquals((0..11).toList(), starter.notesSetup.pitchClassValues)
        assertEquals(60, starter.notesSetup.minimumNote)
        assertEquals(72, starter.notesSetup.maximumNote)
    }

    @Test
    fun theScaleDegreesStarterIsTheDefaultTableWithItsFrozenIdentity() {
        val starter = PresetStarters.SCALE_DEGREES

        assertEquals(PresetId("builtin:scale_degrees:starter:v1"), starter.id)
        assertEquals("C major scale degrees", starter.name)
        assertEquals(
            "the starter opens the default scale-degree table",
            ScaleDegreeSetup.default(),
            starter.scaleDegreesSetup
        )
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.SCALE_DEGREES, starter.configuration.kind)
        assertEquals(listOf(0), starter.scaleDegreesSetup.keyValues)
        assertEquals(
            com.thomas.passiveeartraining.training.context.ContextKind.TONIC_TRIAD,
            starter.scaleDegreesSetup.context
        )
        assertEquals(
            kotlin.time.Duration.parse("500ms"),
            starter.scaleDegreesSetup.contextGap
        )
    }

    @Test
    fun theProgressionsStarterIsTheDefaultTableWithItsFrozenIdentity() {
        val starter = PresetStarters.PROGRESSIONS

        assertEquals(PresetId("builtin:chord_progressions:starter:v1"), starter.id)
        assertEquals("Major chord progressions", starter.name)
        assertEquals(
            "the starter opens the default progression table",
            ProgressionSetup.default(),
            starter.progressionsSetup
        )
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.CHORD_PROGRESSIONS, starter.configuration.kind)
        assertEquals(
            listOf(
                com.thomas.passiveeartraining.music.DiatonicChord.I.id,
                com.thomas.passiveeartraining.music.DiatonicChord.II.id,
                com.thomas.passiveeartraining.music.DiatonicChord.III.id,
                com.thomas.passiveeartraining.music.DiatonicChord.IV.id,
                com.thomas.passiveeartraining.music.DiatonicChord.V.id,
                com.thomas.passiveeartraining.music.DiatonicChord.VI.id,
                com.thomas.passiveeartraining.music.DiatonicChord.VII.id
            ),
            starter.progressionsSetup.chordIds
        )
        assertEquals(
            com.thomas.passiveeartraining.training.ProgressionAnswerFormat.ROMAN,
            starter.progressionsSetup.answerFormat
        )
        assertEquals(
            com.thomas.passiveeartraining.music.ProgressionVoicingMode.ROOT_POSITION,
            starter.progressionsSetup.voicing
        )
    }

    @Test
    fun theContextualIntervalsStarterIsTheDefaultTableWithItsFrozenIdentity() {
        val starter = PresetStarters.CONTEXTUAL_INTERVALS

        assertEquals(PresetId("builtin:intervals_in_context:starter:v1"), starter.id)
        assertEquals("Intervals over the tonic triad", starter.name)
        assertEquals(
            "the starter opens the default intervals-in-context table",
            ContextualIntervalSetup.default(),
            starter.contextualIntervalsSetup
        )
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.INTERVALS_IN_CONTEXT, starter.configuration.kind)
        assertEquals(listOf(0), starter.contextualIntervalsSetup.keyValues)
        assertEquals(
            listOf("minor_third", "major_third", "perfect_fifth"),
            starter.contextualIntervalsSetup.intervalIds
        )
        assertEquals(
            com.thomas.passiveeartraining.music.IntervalPresentation.ASCENDING,
            starter.contextualIntervalsSetup.presentation
        )
        assertEquals(
            com.thomas.passiveeartraining.training.context.ContextKind.TONIC_TRIAD,
            starter.contextualIntervalsSetup.context
        )
    }

    @Test
    fun theMelodicDictationStarterIsTheDefaultPhraseWithItsFrozenIdentity() {
        val starter = PresetStarters.MELODIC_DICTATION

        assertEquals(PresetId("builtin:melodic_dictation:starter:v1"), starter.id)
        assertEquals("Tonic-to-tonic four-note phrases", starter.name)
        assertEquals(
            "the starter opens the default melodic-dictation phrase",
            MelodicDictationSetup.default(),
            starter.melodicDictationSetup
        )
        assertTrue("a starter is immutable", starter.builtIn)
        assertFalse("a starter is never an empty slot", starter.empty)
        assertEquals(TrainingKind.MELODIC_DICTATION, starter.configuration.kind)
        assertEquals(listOf(0), starter.melodicDictationSetup.keyValues)
        assertEquals(4, starter.melodicDictationSetup.phraseLength)
        assertEquals(1, starter.melodicDictationSetup.startDegree)
        assertEquals(1, starter.melodicDictationSetup.endDegree)
        assertEquals((-5..5).toList(), starter.melodicDictationSetup.allowedSteps)
        assertEquals(
            com.thomas.passiveeartraining.training.context.ContextKind.TONIC_TRIAD,
            starter.melodicDictationSetup.context
        )
    }

    @Test
    fun everyKindWithAStarterIsCoveredByItsGroup() {
        assertEquals(
            setOf(
                TrainingKind.INTERVALS,
                TrainingKind.INTERVAL_WALK,
                TrainingKind.CHORDS,
                TrainingKind.SCALES,
                TrainingKind.NOTE_IDENTIFICATION,
                TrainingKind.SCALE_DEGREES,
                TrainingKind.CHORD_PROGRESSIONS,
                TrainingKind.INTERVALS_IN_CONTEXT,
                TrainingKind.MELODIC_DICTATION
            ),
            PresetStarters.BY_KIND.keys
        )
        assertEquals(PresetStarters.ALL, PresetStarters.BY_KIND.getValue(TrainingKind.INTERVALS))
        assertEquals(
            listOf(PresetStarters.INTERVAL_WALK),
            PresetStarters.BY_KIND.getValue(TrainingKind.INTERVAL_WALK)
        )
        assertEquals(
            3,
            (PresetStarters.INTERVAL_WALK.configuration as PresetConfiguration.IntervalWalk)
                .setup.length
        )
        assertEquals(
            listOf(PresetStarters.CHORDS),
            PresetStarters.BY_KIND.getValue(TrainingKind.CHORDS)
        )
        assertEquals(
            listOf(PresetStarters.SCALES),
            PresetStarters.BY_KIND.getValue(TrainingKind.SCALES)
        )
        assertEquals(
            listOf(PresetStarters.NOTES),
            PresetStarters.BY_KIND.getValue(TrainingKind.NOTE_IDENTIFICATION)
        )
        assertEquals(
            listOf(PresetStarters.SCALE_DEGREES),
            PresetStarters.BY_KIND.getValue(TrainingKind.SCALE_DEGREES)
        )
        assertEquals(
            listOf(PresetStarters.PROGRESSIONS),
            PresetStarters.BY_KIND.getValue(TrainingKind.CHORD_PROGRESSIONS)
        )
        assertEquals(
            listOf(PresetStarters.CONTEXTUAL_INTERVALS),
            PresetStarters.BY_KIND.getValue(TrainingKind.INTERVALS_IN_CONTEXT)
        )
        assertEquals(
            listOf(PresetStarters.MELODIC_DICTATION),
            PresetStarters.BY_KIND.getValue(TrainingKind.MELODIC_DICTATION)
        )
        assertEquals(
            "the flat list holds every group",
            PresetStarters.BY_KIND.values.flatten(),
            PresetStarters.ALL_KINDS
        )
    }
}
