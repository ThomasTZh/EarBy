package com.thomas.passiveeartraining.app

import com.thomas.passiveeartraining.music.ChordInversions
import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.ChordQuality
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.ScaleKind
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup

/**
 * How a setup's presentation reads on the Play card and in the preset details.
 *
 * A mixed set is a real state even though [LastSetup.presentation] still holds the primary
 * direction, so it must not be shown as plain "ascending".
 */
internal fun LastSetup.presentationLabel(): String =
    if (mixedPresentation) "random up/down" else presentation.name.lowercase()

/** How a chord presentation reads on the setup, play and preset surfaces. */
internal fun ChordPresentation.englishLabel(): String = when (this) {
    ChordPresentation.HARMONIC -> "harmonic"
    ChordPresentation.ARPEGGIO -> "ascending arpeggio"
}

/** How the chord answer format reads on the setup, play and preset surfaces. */
internal fun ChordAnswerMode.englishLabel(): String = when (this) {
    ChordAnswerMode.QUALITY -> "quality"
    ChordAnswerMode.QUALITY_AND_INVERSION -> "quality + inversion"
}

/** The selected chord qualities in catalog order, or "none" for an empty selection. */
internal fun ChordSetup.qualitiesLabel(): String {
    val names = ChordQuality.entries
        .filter { it.id in qualityIds }
        .map { it.englishAnswer }
    return if (names.isEmpty()) "none" else names.joinToString()
}

/** The selected inversions in numeric order. */
internal fun ChordSetup.inversionsLabel(): String =
    inversions.sorted().joinToString { ChordInversions.answer(it) }

/** How a scale direction reads on the play and preset surfaces. */
internal fun ScaleDirection.englishLabel(): String = name.lowercase()

/** The selected canonical scales in catalog order, or "none" for an empty selection. */
internal fun ScaleSetup.scalesLabel(): String {
    val names = ScaleKind.entries
        .filter { it.id in scaleIds }
        .map { VoiceVocabulary.scaleEntry(it).spokenText }
    return if (names.isEmpty()) "none" else names.joinToString()
}

/** The selected root classes, or the empty-selection meaning used everywhere else. */
internal fun ScaleSetup.rootsLabel(): String = if (rootValues.isEmpty()) {
    "all (none selected)"
} else {
    rootValues.sorted().joinToString { PitchClass.of(it).displayName() }
}

/** The selected root classes, or the empty-selection meaning used everywhere else. */
internal fun ChordSetup.rootsLabel(): String = if (rootValues.isEmpty()) {
    "all (none selected)"
} else {
    rootValues.sorted().joinToString { PitchClass.of(it).displayName() }
}

/**
 * The canonical letter label of one pitch class, C through B (M7).
 *
 * The twelve classes read with the sharp chromatic table, so the selection chips stay stable
 * when the answer's accidental style changes between sharp and flat.
 */
internal fun pitchClassLetterLabel(value: Int): String =
    spelledPitchClassLabel(value, SpellingPreference.SHARP)

/**
 * One key tonic's spelled label under the setup's sharp/flat spelling (M8).
 *
 * The sharp table reads 1/3/6/8/10 as C#/D#/F#/G#/A# and the flat table as Db/Eb/Gb/Ab/Bb; this
 * is the same chromatic reading [com.thomas.passiveeartraining.training.ScaleDegreeDefinition]
 * uses for keys and NOTE_NAME answers.
 */
internal fun spelledPitchClassLabel(value: Int, spelling: SpellingPreference): String {
    val spelled = SpelledPitch.fromMidi(MidiNote.of(MidiNote.C4 + value), spelling)
    val marks = when {
        spelled.accidental > 0 -> "\u266F".repeat(spelled.accidental)
        spelled.accidental < 0 -> "\u266D".repeat(-spelled.accidental)
        else -> ""
    }
    return spelled.letter.name + marks
}

/** How a tonality reads on the setup, play and preset surfaces (M8). */
internal fun Tonality.englishLabel(): String = when (this) {
    Tonality.MAJOR -> "major"
    Tonality.NATURAL_MINOR -> "natural minor"
}

/** How a scale-degree answer format reads on the setup, play and preset surfaces (M8). */
internal fun DegreeAnswerFormat.englishLabel(): String = when (this) {
    DegreeAnswerFormat.DEGREE -> "degree"
    DegreeAnswerFormat.NOTE_NAME -> "note name"
}

/** How a chord-progression answer format reads on the play and preset surfaces (M9A). */
internal fun ProgressionAnswerFormat.englishLabel(): String = when (this) {
    ProgressionAnswerFormat.ROMAN -> "roman sequence"
    ProgressionAnswerFormat.ROOT_MOTION -> "root motion"
    ProgressionAnswerFormat.FUNCTIONS -> "harmonic functions"
}

/**
 * The selected chords as their Roman labels, or "none" for a draft state.
 */
internal fun ProgressionSetup.chordsLabel(): String = if (chordIds.isEmpty()) {
    "none"
} else {
    chordIds.joinToString(", ") { id ->
        DiatonicChord.ofIdOrNull(id)?.romanLabel ?: id
    }
}

/** The fixed tonic note, when one is pinned. */
internal fun ProgressionSetup.fixedRootLabel(): String? =
    fixedRoot?.let { MidiNote.of(it).displayName() }

/** How the voicing reads on the play and preset surfaces, including the allowed inversions. */
internal fun ProgressionSetup.voicingLabel(): String = when (voicing) {
    ProgressionVoicingMode.ROOT_POSITION -> "root position"
    ProgressionVoicingMode.NEAREST_INVERSION ->
        "nearest inversion, allowed inversions " + allowedInversions.sorted().joinToString()
}

/** How an interval presentation reads on the setup, play and preset surfaces (M9B). */
internal fun IntervalPresentation.englishLabel(): String = when (this) {
    IntervalPresentation.ASCENDING -> "ascending"
    IntervalPresentation.DESCENDING -> "descending"
    IntervalPresentation.HARMONIC -> "harmonic"
}

/** How a contextual-interval answer format reads on the play and preset surfaces (M9B). */
internal fun ContextualIntervalAnswerFormat.englishLabel(): String = when (this) {
    ContextualIntervalAnswerFormat.INTERVAL -> "interval"
    ContextualIntervalAnswerFormat.DEGREE_PAIR -> "degree pair"
    ContextualIntervalAnswerFormat.INTERVAL_AND_DEGREES -> "interval and degrees"
}

/**
 * How a melodic-dictation answer format reads on the play and preset surfaces (M10).
 *
 * These are passive answers: the text only ever repeats the composed speech, and no correctness
 * state exists.
 */
internal fun MelodicAnswerFormat.englishLabel(): String = when (this) {
    MelodicAnswerFormat.DEGREE_SEQUENCE -> "degree sequence"
    MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE -> "directed interval sequence"
}

/**
 * How a melodic-dictation direction bias reads on the play and preset surfaces (M10).
 *
 * The weights themselves are explained on the setup screen; the summary keeps the plain name.
 */
internal fun MelodicDirectionBias.englishLabel(): String = when (this) {
    MelodicDirectionBias.BALANCED -> "balanced"
    MelodicDirectionBias.ASCENDING -> "ascending"
    MelodicDirectionBias.DESCENDING -> "descending"
}

/**
 * The selected key tonics under the setup's own sharp/flat spelling, or the empty-selection
 * meaning; identical to [ContextualIntervalSetup.keysLabel] for the melodic-dictation setup.
 */
internal fun MelodicDictationSetup.keysLabel(): String = if (keyValues.isEmpty()) {
    "all twelve"
} else {
    keyValues.sorted().joinToString { spelledPitchClassLabel(it, spelling) }
}

/** The selected tonalities in ordinal order, or "none" for a draft state (M10). */
internal fun MelodicDictationSetup.tonalitiesLabel(): String = if (tonalities.isEmpty()) {
    "none"
} else {
    tonalities.joinToString { it.englishLabel() }
}

/**
 * One phrase endpoint as the setup chips and summary read it: a degree 1..7 or "any degree".
 *
 * A null degree is a deliberate "any" choice and never means tonic, exactly like the domain.
 */
internal fun endpointDegreeLabel(degree: Int?): String =
    if (degree == null) "any degree" else "degree $degree"

/** The start and end degree of a melodic phrase, for example `start degree 1 · end any degree`. */
internal fun MelodicDictationSetup.endpointsLabel(): String =
    "start ${endpointDegreeLabel(startDegree)} · end ${endpointDegreeLabel(endDegree)}"

/**
 * The selected signed steps as one phrase: a contiguous run reads "a to b", otherwise the sorted
 * members are listed. An empty selection is a draft state the validator explains.
 */
internal fun MelodicDictationSetup.stepsLabel(): String {
    val steps = allowedSteps.sorted()
    if (steps.isEmpty()) return "none"
    val contiguous = steps == (steps.first()..steps.last()).toList()
    return if (contiguous) "${steps.first()} to ${steps.last()}" else steps.joinToString(", ")
}

/**
 * The selected key tonics under the setup's own sharp/flat spelling, or the empty-selection
 * meaning; identical to [ScaleDegreeSetup.keysLabel] for the contextual-interval setup (M9B).
 */
internal fun ContextualIntervalSetup.keysLabel(): String = if (keyValues.isEmpty()) {
    "all twelve"
} else {
    keyValues.sorted().joinToString { spelledPitchClassLabel(it, spelling) }
}

/** The selected tonalities in ordinal order, or "none" for a draft state (M9B). */
internal fun ContextualIntervalSetup.tonalitiesLabel(): String = if (tonalities.isEmpty()) {
    "none"
} else {
    tonalities.joinToString { it.englishLabel() }
}

/**
 * The selected allowed simple intervals in catalog order, or "none" for a draft state (M9B).
 *
 * This is safe configuration: the checklist can say which intervals may occur without revealing
 * which one the current exercise is.
 */
internal fun ContextualIntervalSetup.intervalsLabel(): String = if (intervalIds.isEmpty()) {
    "none"
} else {
    intervalIds.joinToString { id -> SimpleInterval.ofIdOrNull(id)?.englishAnswer ?: id }
}

/**
 * The selected key tonics under the setup's own spelling, or the empty-selection meaning.
 *
 * An empty key selection means all twelve keys; that reading is deliberately spelled out here so
 * the summary cannot look like "no keys".
 */
internal fun ScaleDegreeSetup.keysLabel(): String = if (keyValues.isEmpty()) {
    "all twelve"
} else {
    keyValues.sorted().joinToString { spelledPitchClassLabel(it, spelling) }
}

/** The selected tonalities in ordinal order, or "none" for a draft state. */
internal fun ScaleDegreeSetup.tonalitiesLabel(): String = if (tonalities.isEmpty()) {
    "none"
} else {
    tonalities.joinToString { it.englishLabel() }
}

/** The selected target degrees in numeric order, or "none" for a draft state. */
internal fun ScaleDegreeSetup.degreesLabel(): String = if (degrees.isEmpty()) {
    "none"
} else {
    degrees.sorted().joinToString()
}

/** The selected note classes in pitch-class order, or "none" for an empty selection. */
internal fun NoteSetup.notesLabel(): String = if (pitchClassValues.isEmpty()) {
    "none"
} else {
    pitchClassValues.sorted().joinToString { pitchClassLetterLabel(it) }
}

/** How the note-identification accidental style reads on the setup, play and preset surfaces. */
internal fun SpellingPreference.englishLabel(): String = when (this) {
    SpellingPreference.SHARP -> "sharp"
    SpellingPreference.FLAT -> "flat"
}
