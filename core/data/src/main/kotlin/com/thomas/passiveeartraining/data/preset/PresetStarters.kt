package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import kotlin.time.Duration

/**
 * The starter presets supplied with the app.
 *
 * They are immutable built-ins: stored once when the database is created and never edited in place.
 * Every identity is fixed - a built-in id names its kind and starter, for example
 * `builtin:intervals:starter:major_pentatonic:v1` - so a fresh install, a backup or a test sees the
 * same starters; a starter that changes in a later build must be updated by a migration rather
 * than by re-insertion, because seeding only fills absent rows.
 */
object PresetStarters {

    /** C4..C5, the range every current interval starter uses. */
    private val FIRST_OCTAVE = 60..72

    /** One note is a little longer than the default; the answer still leaves the default gaps. */
    private val STARTER_THINKING = Duration.parse("3s")

    /** The reviewed starter seed; keeps the shipped interval starters reproducible. */
    private const val STARTER_SEED: Long = 776655

    /** Major pentatonic intervals: the tonic, second, third, fifth, sixth and octave. */
    val MAJOR_PENTATONIC: Preset = starter(
        id = "builtin:intervals:starter:major_pentatonic:v1",
        name = "Major Pentatonic",
        intervalIds = listOf(
            "unison",
            "major_second",
            "major_third",
            "perfect_fifth",
            "major_sixth",
            "octave"
        )
    )

    /** Minor pentatonic intervals: the tonic, third, fourth, fifth, seventh and octave. */
    val MINOR_PENTATONIC: Preset = starter(
        id = "builtin:intervals:starter:minor_pentatonic:v1",
        name = "Minor Pentatonic",
        intervalIds = listOf(
            "unison",
            "minor_third",
            "perfect_fourth",
            "perfect_fifth",
            "minor_seventh",
            "octave"
        )
    )

    /** The major triad's intervals: the tonic, third, fifth and octave. */
    val MAJOR_TRIAD: Preset = starter(
        id = "builtin:intervals:starter:major_triad:v1",
        name = "Major Triad",
        intervalIds = listOf("unison", "major_third", "perfect_fifth", "octave")
    )

    /** The minor triad's intervals: the tonic, third, fifth and octave. */
    val MINOR_TRIAD: Preset = starter(
        id = "builtin:intervals:starter:minor_triad:v1",
        name = "Minor Triad",
        intervalIds = listOf("unison", "minor_third", "perfect_fifth", "octave")
    )

    /** The intervals starters, in the order a first run introduces them. */
    val ALL: List<Preset> =
        listOf(MAJOR_PENTATONIC, MINOR_PENTATONIC, MAJOR_TRIAD, MINOR_TRIAD)

    /** The interval-walk starter: a three-step ascending third/fifth walk from C4. */
    val INTERVAL_WALK: Preset = intervalWalkStarter(
        id = "builtin:interval_walk:starter:v1",
        name = "Ascending thirds and fifths",
        setup = IntervalWalkSetup.default().copy(
            intervalIds = listOf("major_third", "minor_third", "perfect_fifth"),
            fixedStart = MidiNote.of(60),
            direction = WalkDirection.ASCENDING,
            length = 3,
            thinkingSilence = STARTER_THINKING,
            separatorCue = true
        )
    )

    /** The chords starter (M5): the default chord table, immutable like every starter. */
    val CHORDS: Preset = chordsStarter(
        id = "builtin:chords:starter:v1",
        name = "Major and minor chords",
        setup = ChordSetup.default()
    )

    /** The scales starter (M6): the default scale table, immutable like every starter. */
    val SCALES: Preset = scalesStarter(
        id = "builtin:scales:starter:v1",
        name = "Major and natural minor scales",
        setup = ScaleSetup.default()
    )

    /** The notes starter (M7): the default note table, immutable like every starter. */
    val NOTES: Preset = notesStarter(
        id = "builtin:note_identification:starter:v1",
        name = "All twelve notes",
        setup = NoteSetup.default()
    )

    /** The scale-degrees starter (M8): C major with the tonic-triad context, like every starter. */
    val SCALE_DEGREES: Preset = scaleDegreesStarter(
        id = "builtin:scale_degrees:starter:v1",
        name = "C major scale degrees",
        setup = ScaleDegreeSetup.default()
    )

    /** The chord-progressions starter (M9B): C major with the seven diatonic triads. */
    val PROGRESSIONS: Preset = progressionsStarter(
        id = "builtin:chord_progressions:starter:v1",
        name = "Major chord progressions",
        setup = ProgressionSetup.default()
    )

    /** The intervals-in-context starter (M9B): C major over the tonic triad, three intervals. */
    val CONTEXTUAL_INTERVALS: Preset = contextualIntervalsStarter(
        id = "builtin:intervals_in_context:starter:v1",
        name = "Intervals over the tonic triad",
        setup = ContextualIntervalSetup.default()
    )

    /**
     * The melodic-dictation starter (M10): the default four-note tonic-to-tonic phrases with the
     * documented steps -5..5 including zero.
     */
    val MELODIC_DICTATION: Preset = melodicDictationStarter(
        id = "builtin:melodic_dictation:starter:v1",
        name = "Tonic-to-tonic four-note phrases",
        setup = MelodicDictationSetup.default()
    )

    /**
     * Every starter, grouped by the kind whose slot space it belongs to.
     *
     * The store seeds each group on its own, so adding a kind never rewrites another kind's rows.
     */
    val BY_KIND: Map<TrainingKind, List<Preset>> = mapOf(
        TrainingKind.INTERVALS to ALL,
        TrainingKind.INTERVAL_WALK to listOf(INTERVAL_WALK),
        TrainingKind.CHORDS to listOf(CHORDS),
        TrainingKind.SCALES to listOf(SCALES),
        TrainingKind.NOTE_IDENTIFICATION to listOf(NOTES),
        TrainingKind.SCALE_DEGREES to listOf(SCALE_DEGREES),
        TrainingKind.CHORD_PROGRESSIONS to listOf(PROGRESSIONS),
        TrainingKind.INTERVALS_IN_CONTEXT to listOf(CONTEXTUAL_INTERVALS),
        TrainingKind.MELODIC_DICTATION to listOf(MELODIC_DICTATION)
    )

    /** Every starter of every kind, in seeding order. */
    val ALL_KINDS: List<Preset> = BY_KIND.values.flatten()

    private fun intervalWalkStarter(id: String, name: String, setup: IntervalWalkSetup): Preset =
        Preset(
            id = PresetId(id),
            name = name,
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
            configuration = PresetConfiguration.IntervalWalk(setup),
            builtIn = true
        )

    private fun starter(
        id: String,
        name: String,
        intervalIds: List<String>,
        range: IntRange = FIRST_OCTAVE
    ): Preset = Preset(
        id = PresetId(id),
        name = name,
        // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        configuration = PresetConfiguration.Intervals(
            LastSetup.default().copy(
                intervalIds = intervalIds,
                minimumNote = range.first,
                maximumNote = range.last,
                thinkingSilence = STARTER_THINKING,
                fixedRoot = range.first,
                seed = STARTER_SEED,
                separatorCue = true
            )
        ),
        seed = STARTER_SEED,
        builtIn = true
    )

    private fun chordsStarter(id: String, name: String, setup: ChordSetup): Preset = Preset(
        id = PresetId(id),
        name = name,
        // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        configuration = PresetConfiguration.Chords(setup),
        builtIn = true
    )

    private fun scalesStarter(id: String, name: String, setup: ScaleSetup): Preset = Preset(
        id = PresetId(id),
        name = name,
        // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        configuration = PresetConfiguration.Scales(setup),
        builtIn = true
    )

    private fun notesStarter(id: String, name: String, setup: NoteSetup): Preset = Preset(
        id = PresetId(id),
        name = name,
        // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        configuration = PresetConfiguration.Notes(setup),
        builtIn = true
    )

    private fun scaleDegreesStarter(id: String, name: String, setup: ScaleDegreeSetup): Preset =
        Preset(
            id = PresetId(id),
            name = name,
            // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
            configuration = PresetConfiguration.ScaleDegrees(setup),
            builtIn = true
        )

    private fun progressionsStarter(id: String, name: String, setup: ProgressionSetup): Preset =
        Preset(
            id = PresetId(id),
            name = name,
            // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
            createdAtEpochMs = 0,
            updatedAtEpochMs = 0,
            configuration = PresetConfiguration.Progressions(setup),
            builtIn = true
        )

    private fun contextualIntervalsStarter(
        id: String,
        name: String,
        setup: ContextualIntervalSetup
    ): Preset = Preset(
        id = PresetId(id),
        name = name,
        // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        configuration = PresetConfiguration.ContextualIntervals(setup),
        builtIn = true
    )

    private fun melodicDictationStarter(
        id: String,
        name: String,
        setup: MelodicDictationSetup
    ): Preset = Preset(
        id = PresetId(id),
        name = name,
        // Built-ins predate any user edit; 0 keeps their dates meaningless rather than fabricated.
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
        configuration = PresetConfiguration.MelodicDictation(setup),
        builtIn = true
    )
}
