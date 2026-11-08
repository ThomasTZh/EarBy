package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.ChordVoicing
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.Key
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.ProgressionStep
import com.thomas.passiveeartraining.music.ProgressionTheory
import com.thomas.passiveeartraining.music.ProgressionVoicing
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.RootMotion
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.provider.ExerciseCompiler
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import com.thomas.passiveeartraining.training.recipe.RecipeTiming
import kotlin.time.Duration

/** The answer the chord-progression mode asks the listener to produce (M9A.1). */
enum class ProgressionAnswerFormat {
    /** Each chord's degree number and quality, for example "one major ... six minor". */
    ROMAN,

    /** The n-1 directed theoretical root motions, for example "ascending perfect fourth". */
    ROOT_MOTION,

    /** Each chord's harmonic function word, for example "tonic predominant dominant tonic". */
    FUNCTIONS
}

/**
 * One placeable key/anchor pair of a progression definition.
 *
 * [keyValue] is the tonic pitch class and [anchor] the tonic MIDI placement every selected chord
 * was checked against. The pair is immutable musical data, never live playback state.
 */
data class ProgressionPlacement(val keyValue: Int, val anchor: MidiNote)

/**
 * One drawn progression: its placement and the concrete chords in step order.
 *
 * The realization is immutable musical data, never live playback state.
 */
data class ProgressionRealization(
    val keyValue: Int,
    val anchor: MidiNote,
    private val chordList: List<ChordVoicing>
) {
    /** The concrete chords in step order; a defensive immutable copy. */
    val chords: List<ChordVoicing> = java.util.Collections.unmodifiableList(chordList.toList())
}

/**
 * Immutable, normalized chord-progression configuration (M9A/M9B).
 *
 * The selection is a set of key tonic pitch classes crossed with a pool of [DiatonicChord]s. Each
 * exercise plays [CHORD_COUNT] chords: a selected tonic chord (I or Imaj7) first, then three
 * chords drawn with replacement from the full pool. The pool therefore always contains at least
 * one tonic chord; the definition rejects a selection without one.
 *
 * [fixedRoot] pins the tonic MIDI note: the exercise always uses that key and anchor, and the key
 * selection is ignored. Without it, every anchor of a selected key that realizes the complete
 * pool is a legal placement, in the documented canonical order (key ascending, anchor ascending).
 *
 * Feasibility is judged for the whole pool under the configured [voicing] and
 * [allowedInversions], so any drawn sequence is realizable at every placement. A selection with no
 * placement is rejected; the provider additionally reports each individual chord/key pair that
 * cannot be realized.
 *
 * [spelling] selects the sharp or flat reading of a key's tonic and its diatonic letters. It never
 * changes the sounding class, exactly as in the scale-degree definition.
 *
 * @see ProgressionVoicing
 */
class ProgressionDefinition private constructor(
    selectedChords: List<DiatonicChord>,
    val fixedRoot: MidiNote?,
    val voicing: ProgressionVoicingMode,
    selectedAllowedInversions: List<Int>,
    val answerFormat: ProgressionAnswerFormat,
    val spelling: SpellingPreference,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote
) {
    /** Selected chords in catalog order; never empty. */
    val chords: List<DiatonicChord> =
        java.util.Collections.unmodifiableList(selectedChords.toList())

    /** The drawn chords' progression steps, in catalog order. */
    val steps: List<ProgressionStep> = chords.map { it.step }

    /**
     * The fixed opening chord plus the selected pool, in catalog order.
     *
     * Every exercise opens on [DiatonicChord.I]; the selection only feeds the three drawn slots.
     * A placement must realize this whole set, so the opening never fails at draw time.
     */
    val requiredChords: List<DiatonicChord> =
        java.util.Collections.unmodifiableList(
            DiatonicChord.entries.filter { it == DiatonicChord.I || it in chords }
        )

    /** The steps of [requiredChords], in catalog order. */
    val requiredSteps: List<ProgressionStep> = requiredChords.map { it.step }

    /** Allowed inversions 0..3, deduplicated and sorted; never empty. */
    val allowedInversions: List<Int> =
        java.util.Collections.unmodifiableList(selectedAllowedInversions.toList())

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range every chord member must fit; never silently widened. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

    /**
     * The key tonics this definition enumerates: the fixed root's class, or all twelve.
     *
     * There is no key selection any more; without a fixed root every key is used.
     */
    val effectiveKeyValues: List<Int>
        get() = fixedRoot?.let { listOf(it.pitchClass.value) } ?: ALL_KEY_VALUES

    /**
     * The spelled tonic of key pitch class [keyValue] under this definition's [spelling].
     *
     * The sharp table spells 1/3/6/8/10 as C#/D#/F#/G#/A# and the flat table as Db/Eb/Gb/Ab/Bb;
     * every other class is a natural. The spelling is always within one accidental.
     */
    fun spelledKeyTonic(keyValue: Int): SpelledPitch {
        require(keyValue in 0 until PitchClass.COUNT) {
            "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $keyValue"
        }
        return SpelledPitch.fromMidi(MidiNote.of(MidiNote.C4 + keyValue), spelling)
    }

    /** The theoretical [Key] of key pitch class [keyValue]; progressions are major-only. */
    fun keyFor(keyValue: Int): Key = Key(spelledKeyTonic(keyValue), Tonality.MAJOR)

    /** Diagnostic key name such as `C major`, for range errors. */
    fun keyDisplayName(keyValue: Int): String {
        val spelled = spelledKeyTonic(keyValue)
        val name = VoiceVocabulary.noteEntry(spelled.letter, spelled.accidental).spokenText
        return "$name major"
    }

    /**
     * The complete chord realization of [steps] for [keyValue] at [anchor].
     *
     * The anchor/tonic is constrained by the configured root range. The sounding chords may
     * extend beyond it, but never beyond the packaged instrument range. It never transposes or
     * clamps a chord to make it fit.
     */
    fun chordsFor(
        steps: List<ProgressionStep>,
        keyValue: Int,
        anchor: MidiNote
    ): List<ChordVoicing>? = ProgressionVoicing.realize(
        steps = steps,
        key = keyFor(keyValue),
        anchor = anchor,
        mode = voicing,
        allowedInversions = allowedInversions,
        range = PlayableRange.VALUES
    )

    /** The realization of one selected chord at [keyValue] and [anchor], if it fits. */
    fun chordFor(chord: DiatonicChord, keyValue: Int, anchor: MidiNote): ChordVoicing? =
        chordsFor(listOf(chord.step), keyValue, anchor)?.single()

    /** The anchors of key [keyValue] that realize the opening and the complete selected pool. */
    fun feasibleAnchors(keyValue: Int): List<MidiNote> {
        require(keyValue in 0 until PitchClass.COUNT) {
            "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $keyValue"
        }
        if (fixedRoot != null) {
            return listOf(fixedRoot).filter {
                it.pitchClass.value == keyValue &&
                    chordsFor(requiredSteps, keyValue, it) != null
            }
        }
        return (minimumNoteValue..maximumNoteValue)
            .map { MidiNote.of(it) }
            .filter { it.pitchClass.value == keyValue }
            .filter { anchor -> chordsFor(requiredSteps, keyValue, anchor) != null }
    }

    /** Every placement the generator may draw, in canonical key-then-anchor order. */
    fun feasiblePlacements(): List<ProgressionPlacement> = effectiveKeyValues.flatMap { keyValue ->
        feasibleAnchors(keyValue).map { anchor -> ProgressionPlacement(keyValue, anchor) }
    }

    /** True when [chord] has at least one complete realization for [keyValue]. */
    fun supports(chord: DiatonicChord, keyValue: Int): Boolean {
        if (fixedRoot != null) {
            return chordFor(chord, keyValue, fixedRoot) != null
        }
        return (minimumNoteValue..maximumNoteValue)
            .map { MidiNote.of(it) }
            .filter { it.pitchClass.value == keyValue }
            .any { anchor -> chordFor(chord, keyValue, anchor) != null }
    }

    /** The ordered answer tokens of [sequence] under this definition's answer format. */
    fun answerTokensFor(sequence: List<ProgressionStep>): List<VoiceAssetId> =
        answerTokens(answerFormat, sequence)

    /** The display label [sequence] reveals under this definition's answer format. */
    fun displayLabelFor(sequence: List<ProgressionStep>): String =
        displayLabel(answerFormat, sequence)

    /**
     * The example window of any exercise: [CHORD_COUNT] simultaneous chords separated by
     * [RecipeTiming.melodicGap], measured with the same timing the compiler consumes.
     */
    fun exampleDurationFor(timing: RecipeTiming = RecipeTiming()): Duration =
        timing.noteDuration * CHORD_COUNT + timing.melodicGap * (CHORD_COUNT - 1)

    /** The longest example window; every exercise has the same shape. */
    fun maximumExampleDuration(timing: RecipeTiming = RecipeTiming()): Duration =
        exampleDurationFor(timing)

    /**
     * The measured duration of [sequence]'s answer: every token clip in order plus the exactly
     * 80 ms inter-token gap.
     */
    fun answerDurationFor(
        sequence: List<ProgressionStep>,
        catalog: VoiceDurationCatalog
    ): Duration {
        val tokens = answerTokensFor(sequence)
        if (tokens.isEmpty()) return Duration.ZERO
        val audio = tokens.fold(Duration.ZERO) { total, token -> total + catalog.durationOf(token) }
        return audio + ExerciseCompiler.TOKEN_GAP * (tokens.size - 1)
    }

    /**
     * A conservative upper bound of any answer duration, used by validation.
     *
     * The token count is fixed by the answer format; the longest possible clip of that format is
     * used for every slot, so the bound never understates a real exercise.
     */
    fun maximumAnswerDuration(catalog: VoiceDurationCatalog): Duration {
        val tokens = possibleTokens(answerFormat)
        val tokenCount = answerTokenCount(answerFormat)
        val longest = tokens.maxOf { catalog.durationOf(it) }
        return longest * tokenCount + ExerciseCompiler.TOKEN_GAP * (tokenCount - 1)
    }

    /** Every answer token any draw can produce under [format]. */
    fun possibleTokens(format: ProgressionAnswerFormat = answerFormat): Set<VoiceAssetId> =
        when (format) {
            ProgressionAnswerFormat.ROMAN -> buildSet {
                requiredChords.forEach { chord ->
                    add(VoiceVocabulary.degreeNumberEntry(chord.degree).id)
                    add(VoiceVocabulary.chordQualityEntry(chord.quality).id)
                }
            }

            ProgressionAnswerFormat.ROOT_MOTION -> buildSet {
                requiredChords.forEach { from ->
                    requiredChords.forEach { to ->
                        val motion = RootMotion.betweenMajorDegrees(from.degree, to.degree)
                        add(VoiceVocabulary.rootMotionDirectionEntry(motion.direction).id)
                        add(VoiceVocabulary.intervalEntry(motion.interval).id)
                    }
                }
            }

            ProgressionAnswerFormat.FUNCTIONS -> requiredChords.mapTo(mutableSetOf()) { chord ->
                VoiceVocabulary.harmonyFunctionEntry(chord.function).id
            }
        }

    override fun equals(other: Any?): Boolean = other is ProgressionDefinition &&
        chords == other.chords &&
        fixedRoot == other.fixedRoot &&
        voicing == other.voicing &&
        allowedInversions == other.allowedInversions &&
        answerFormat == other.answerFormat &&
        spelling == other.spelling &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote

    override fun hashCode(): Int {
        var result = chords.hashCode()
        result = 31 * result + (fixedRoot?.hashCode() ?: 0)
        result = 31 * result + voicing.hashCode()
        result = 31 * result + allowedInversions.hashCode()
        result = 31 * result + answerFormat.hashCode()
        result = 31 * result + spelling.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        return result
    }

    override fun toString(): String = "ProgressionDefinition(chords=${chords.map { it.id }}, " +
        "fixedRoot=$fixedRoot, voicing=$voicing, inversions=$allowedInversions, " +
        "answer=$answerFormat, spelling=$spelling, " +
        "range=$minimumNoteValue..$maximumNoteValue)"

    companion object {
        /** How many chords one exercise plays. */
        const val CHORD_COUNT: Int = 4

        /** All twelve key pitch classes; used when no fixed root pins the key. */
        private val ALL_KEY_VALUES: List<Int> = java.util.Collections.unmodifiableList(
            (0 until PitchClass.COUNT).toList()
        )

        /** The default fixture: all seven triads, root position, any key, C3..C6. */
        fun default(): ProgressionDefinition = of(
            chords = DiatonicChord.TRIADS,
            fixedRoot = null,
            voicing = ProgressionVoicingMode.ROOT_POSITION,
            allowedInversions = listOf(0, 1, 2, 3),
            answerFormat = ProgressionAnswerFormat.ROMAN,
            spelling = SpellingPreference.SHARP,
            minimumNote = MidiNote.of(48),
            maximumNote = MidiNote.of(84)
        )

        /**
         * Normalizes and validates a definition.
         *
         * There is no key selection: without a [fixedRoot] every key tonic is used, and a fixed
         * root pins the key and the anchor. An empty chord or inversion collection is not valid.
         * The opening I is always required by the player but is not part of the selection; a
         * definition with no placement that realizes the opening I and the whole pool is rejected
         * with its field hint, and individual infeasible chord/key pairs are left to the
         * provider's `range` feedback, so an invalid draft stays constructible.
         *
         * @throws InvalidProgressionDefinitionException when the chord or inversion selection is
         *   empty, an inversion is not 0..3, the range is inverted, or the fixed root is outside
         *   the range or has no realization. The exception names the setup field (`chords`,
         *   `inversions`, `range` or `fixedRoot`) it belongs to.
         */
        fun of(
            chords: Collection<DiatonicChord>,
            fixedRoot: MidiNote?,
            voicing: ProgressionVoicingMode,
            allowedInversions: Collection<Int>,
            answerFormat: ProgressionAnswerFormat,
            spelling: SpellingPreference,
            minimumNote: MidiNote,
            maximumNote: MidiNote
        ): ProgressionDefinition {
            val selected = chords.toSet()
            val chordList = DiatonicChord.entries.filter { it in selected }
            if (chordList.isEmpty()) {
                throw InvalidProgressionDefinitionException(
                    "At least one chord must be selected",
                    field = "chords"
                )
            }
            val inversions = allowedInversions.toSet().sorted()
            if (inversions.isEmpty()) {
                throw InvalidProgressionDefinitionException(
                    "At least one inversion must be allowed",
                    field = "inversions"
                )
            }
            inversions.forEach { value ->
                if (value !in 0..3) {
                    throw InvalidProgressionDefinitionException(
                        "An allowed inversion must be 0..3 but was $value",
                        field = "inversions"
                    )
                }
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidProgressionDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            if (fixedRoot != null && fixedRoot.value !in minimumNote.value..maximumNote.value) {
                throw InvalidProgressionDefinitionException(
                    "The fixed root ${fixedRoot.value} must be inside the range " +
                        "${minimumNote.value}..${maximumNote.value}",
                    field = "fixedRoot"
                )
            }
            val definition = ProgressionDefinition(
                selectedChords = chordList,
                fixedRoot = fixedRoot,
                voicing = voicing,
                selectedAllowedInversions = inversions,
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = minimumNote,
                maximumNote = maximumNote
            )
            if (definition.feasiblePlacements().isEmpty()) {
                val voicingText = when (voicing) {
                    ProgressionVoicingMode.ROOT_POSITION -> "root-position voicing"
                    ProgressionVoicingMode.NEAREST_INVERSION ->
                        "nearest-inversion voicing with inversions $inversions"
                }
                throw InvalidProgressionDefinitionException(
                    "No key realizes the opening I and " +
                        "${chordList.joinToString(", ") { it.romanLabel }} inside " +
                        "${definition.noteRange} with the $voicingText",
                    field = "range"
                )
            }
            return definition
        }

        /**
         * The ordered answer tokens of one drawn sequence under an explicit answer format.
         *
         * ROMAN is one degree number followed by one quality per chord; ROOT_MOTION is one
         * direction connector and one interval phrase per adjacent pair (never a bass motion);
         * FUNCTIONS is one function word per chord. This function is the single source of truth
         * for both speech and display.
         */
        fun answerTokens(
            format: ProgressionAnswerFormat,
            sequence: List<ProgressionStep>
        ): List<VoiceAssetId> = when (format) {
            ProgressionAnswerFormat.ROMAN -> sequence.flatMap { step ->
                VoiceVocabulary.romanEntries(step).map { entry -> entry.id }
            }

            ProgressionAnswerFormat.ROOT_MOTION -> ProgressionTheory.rootMotions(sequence)
                .flatMap { motion ->
                    listOf(
                        VoiceVocabulary.rootMotionDirectionEntry(motion.direction).id,
                        VoiceVocabulary.intervalEntry(motion.interval).id
                    )
                }

            ProgressionAnswerFormat.FUNCTIONS -> sequence.map { step ->
                VoiceVocabulary.harmonyFunctionEntry(step.function).id
            }
        }

        /** The display label one sequence reveals under an explicit answer format. */
        fun displayLabel(format: ProgressionAnswerFormat, sequence: List<ProgressionStep>): String =
            when (format) {
                ProgressionAnswerFormat.ROMAN -> sequence.joinToString(" ") { it.romanLabel }
                ProgressionAnswerFormat.ROOT_MOTION ->
                    ProgressionTheory.rootMotions(sequence).joinToString(" ") { it.label }
                ProgressionAnswerFormat.FUNCTIONS ->
                    sequence.joinToString(" ") { it.function.englishAnswer }
            }

        /** How many tokens one answer of [format] speaks. */
        fun answerTokenCount(format: ProgressionAnswerFormat): Int = when (format) {
            ProgressionAnswerFormat.ROMAN -> CHORD_COUNT * 2
            ProgressionAnswerFormat.ROOT_MOTION -> (CHORD_COUNT - 1) * 2
            ProgressionAnswerFormat.FUNCTIONS -> CHORD_COUNT
        }
    }
}

/** Raised when a progression configuration is unknown, empty or internally contradictory. */
class InvalidProgressionDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
