package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.InvalidMidiNoteException
import com.thomas.passiveeartraining.music.Key
import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.provider.MelodyNote
import com.thomas.passiveeartraining.training.provider.VoiceEntry
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The answer the melodic-dictation mode asks the listener to produce (M10.1). */
enum class MelodicAnswerFormat {
    /** One degree-number token per phrase note, in played order, for example "one five three". */
    DEGREE_SEQUENCE,

    /**
     * One complete interval phrase per transition, in played order.
     *
     * A non-zero step speaks its direction connector and the interval phrase of its absolute
     * distance; a zero step speaks the unison phrase alone, without a false direction.
     */
    DIRECTED_INTERVAL_SEQUENCE
}

/**
 * The transition weighting the melodic sampler applies among feasible choices (M10.2).
 *
 * The weights are positive integers, so a candidate list is never empty of weight and a singleton
 * list still consumes its documented draw. A bias changes only the likelihood between feasible
 * transitions; it never changes the graph, the phrase length or the endpoints, and it promises no
 * monotonicity.
 */
enum class MelodicDirectionBias(val upWeight: Int, val downWeight: Int, val repeatWeight: Int) {
    /** Equal up/down/repeat weights. */
    BALANCED(1, 1, 1),

    /** Up steps are three times as likely as down or repeat steps. */
    ASCENDING(3, 1, 1),

    /** Down steps are three times as likely as up or repeat steps. */
    DESCENDING(1, 3, 1);

    /** The integer weight of one signed transition step under this bias. */
    fun weightOf(signedStep: Int): Int = when {
        signedStep > 0 -> upWeight
        signedStep < 0 -> downWeight
        else -> repeatWeight
    }
}

/**
 * Immutable, normalized melodic-dictation configuration (M10.1/M10.2).
 *
 * The selection is a set of key pitch classes crossed with tonalities. An empty key selection
 * means all twelve key tonics, exactly like the scale-degree, progression and contextual-interval
 * definitions. [context] is one of the five M8 context kinds; the complete context is scheduled
 * before every phrase.
 *
 * The phrase itself is drawn over the finite [MelodyGraph] of the selected key and tonality:
 * [phraseLength] notes, whose first note has degree [startDegree] (null means any) and whose last
 * note has degree [endDegree] (null means any), built from the signed transitions in
 * [allowedSteps] intersected with [maximumLeap]. A step is a signed semitone count: a positive
 * value ascends, a negative value descends and zero repeats a note. Step zero is available only
 * when it is explicitly selected, so a repeat is always an explicit choice, not an implicit one.
 *
 * Degrees are theoretical scale degrees 1..7 relative to the key tonic and are derived from the
 * note's scale membership, never from a semitone distance. Two octaves of one degree are two
 * different notes. No chromatic note can enter a phrase: every graph node is a scale note of the
 * selected key and tonality.
 *
 * [spelling] selects the sharp or flat reading of the key tonic and its diatonic letters, exactly
 * as in the scale-degree and contextual-interval definitions. It never changes a sounding class,
 * so it can never change which phrases exist.
 *
 * The phrase note length and gap are shared recipe timing (RecipeTiming, 700 ms and 100 ms by
 * default), exactly as in the M8, M9A and M9B definitions; [contextGap] is the one timing this
 * definition owns because it separates the context section from the phrase in every example. The
 * spoken answer's inter-clip gap is the shared ExerciseCompiler.TOKEN_GAP (80 ms). The mode has no
 * label bag and no label cycle: phrase sampling consults only the context and target child
 * streams.
 *
 * Every feasibility question is answered against [noteRange] and the graph: a context construction
 * is eligible only when all its sounding notes fit the range, and a key/tonality is usable only
 * when at least one complete phrase exists. A selected key with no complete path is reported by
 * the provider, never silently dropped here or retried indefinitely.
 *
 * @see MelodyGraph
 * @see ContextBuilder
 */
class MelodicDictationDefinition private constructor(
    selectedKeyValues: List<Int>,
    selectedTonalities: List<Tonality>,
    val context: ContextKind,
    val contextGap: Duration,
    selectedPhraseLength: Int,
    selectedStartDegree: Int?,
    selectedEndDegree: Int?,
    selectedAllowedSteps: List<Int>,
    val maximumLeap: Int,
    val bias: MelodicDirectionBias,
    val answerFormat: MelodicAnswerFormat,
    val spelling: SpellingPreference,
    val minimumNote: MidiNote,
    val maximumNote: MidiNote
) {
    /**
     * Selected key tonic pitch classes in numeric order, possibly empty.
     *
     * An empty list means all twelve key tonics; [effectiveKeyValues] resolves that reading.
     */
    val keyValues: List<Int> = java.util.Collections.unmodifiableList(selectedKeyValues.toList())

    /** Selected tonalities in ordinal order; never empty. */
    val tonalities: List<Tonality> =
        java.util.Collections.unmodifiableList(selectedTonalities.toList())

    /** The exact phrase note count, including the start and the end note; 2..16. */
    val phraseLength: Int = selectedPhraseLength

    /**
     * The required start degree 1..7, or null for any degree.
     *
     * [default] reads the documented tonic start as degree 1; a null value is a deliberate "any
     * degree" choice and never means tonic.
     */
    val startDegree: Int? = selectedStartDegree

    /** The required end degree 1..7, or null for any degree. [default] uses the tonic. */
    val endDegree: Int? = selectedEndDegree

    /** Selected signed transition steps in ascending order; never empty. */
    val allowedSteps: List<Int> =
        java.util.Collections.unmodifiableList(selectedAllowedSteps.toList())

    /** The key tonics this definition actually enumerates: [keyValues], or all twelve when empty. */
    val effectiveKeyValues: List<Int>
        get() = if (keyValues.isEmpty()) ALL_KEY_VALUES else keyValues

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range every context note and every phrase note must fit. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

    /**
     * The transition steps this definition actually applies: [allowedSteps] intersected with
     * [maximumLeap], ascending.
     *
     * A step of exactly [maximumLeap] is allowed; there is no hidden endpoint exception.
     */
    val effectiveSteps: List<Int> get() = allowedSteps.filter { it in -maximumLeap..maximumLeap }

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
        val letter = when (spelling) {
            SpellingPreference.SHARP -> SHARP_KEY_LETTERS[keyValue]
            SpellingPreference.FLAT -> FLAT_KEY_LETTERS[keyValue]
        }
        return SpelledPitch.spellingFor(MidiNote.C4 + keyValue, letter)
    }

    /** The theoretical [Key] of key pitch class [keyValue] in [tonality]. */
    fun keyFor(keyValue: Int, tonality: Tonality): Key = Key(spelledKeyTonic(keyValue), tonality)

    /**
     * The seven scale pitch classes of [keyValue] in [tonality], in degree order 1..7.
     *
     * Major uses offsets 0,2,4,5,7,9,11; natural minor uses 0,2,3,5,7,8,10. The natural-minor
     * leading tone is 10, so a raised leading tone of a minor dominant context is never a member
     * of this list.
     */
    fun scalePitchClasses(keyValue: Int, tonality: Tonality): List<Int> {
        require(keyValue in 0 until PitchClass.COUNT) {
            "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $keyValue"
        }
        return tonality.degreeOffsets.map { offset -> (keyValue + offset).mod(PitchClass.COUNT) }
    }

    /**
     * The theoretical degree 1..7 of [pitchClass] in [keyValue]/[tonality], or null when the class
     * is not a scale member.
     */
    fun degreeOf(keyValue: Int, tonality: Tonality, pitchClass: Int): Int? {
        require(pitchClass in 0 until PitchClass.COUNT) {
            "A pitch class must be 0..${PitchClass.COUNT - 1} but was $pitchClass"
        }
        val index = scalePitchClasses(keyValue, tonality).indexOf(pitchClass)
        return if (index >= 0) index + 1 else null
    }

    /** The theoretical degree 1..7 of [note] in [keyValue]/[tonality], or null when chromatic. */
    fun degreeOfNote(keyValue: Int, tonality: Tonality, note: MidiNote): Int? =
        degreeOf(keyValue, tonality, note.pitchClass.value)

    /**
     * Every absolute scale pitch of [keyValue]/[tonality] inside the packaged instrument range,
     * ascending.
     *
     * Each node carries its sounding MIDI value, its theoretical degree 1..7 and the key's
     * theoretical spelling of that degree at the note's octave. A pitch class outside the scale is
     * never a node, so a context's chromatic tone can never become a phrase note. The configured
     * range constrains key-tonic anchors, not melody octaves.
     */
    fun scaleNodes(keyValue: Int, tonality: Tonality): List<MelodyNode> {
        val key = keyFor(keyValue, tonality)
        val degreeByPitchClass = scalePitchClasses(keyValue, tonality)
            .withIndex()
            .associate { (index, pitchClass) -> pitchClass to index + 1 }
        return PlayableRange.VALUES.mapNotNull { value ->
            val note = MidiNote.of(value)
            val degree = degreeByPitchClass[note.pitchClass.value] ?: return@mapNotNull null
            MelodyNode(
                midi = note,
                degree = degree,
                spelled = SpelledPitch.spellingFor(value, key.letterForDegree(degree))
            )
        }
    }

    /**
     * Every eligible context construction of ([keyValue], [tonality]), anchor ascending.
     *
     * A construction is eligible when every sounding note of the complete context fits the
     * packaged instrument range and the key-tonic anchor fits the configured root range. The
     * context is built exactly as playback will build it, so an anchor whose triad, scale or
     * cadence would leave the range is excluded here, never truncated.
     */
    fun eligibleConstructions(keyValue: Int, tonality: Tonality): List<TonalContext> {
        val key = spelledKeyTonic(keyValue)
        return (minimumNoteValue..maximumNoteValue)
            .map { MidiNote.of(it) }
            .filter { it.pitchClass.value == keyValue }
            .mapNotNull { anchor ->
                val context = try {
                    ContextBuilder.build(key, anchor, tonality, this.context)
                } catch (_: InvalidMidiNoteException) {
                    // The construction leaves the MIDI range; the range rule excludes it too.
                    return@mapNotNull null
                }
                if (context.groups.all { group ->
                        group.notes.all { it.value in PlayableRange.VALUES }
                    }
                ) {
                    context
                } else {
                    null
                }
            }
    }

    /** The finite phrase graph of ([keyValue], [tonality]) under this definition. */
    fun graphFor(keyValue: Int, tonality: Tonality): MelodyGraph =
        MelodyGraph.of(this, keyValue, tonality)

    /**
     * True when ([keyValue], [tonality]) has at least one eligible context and one complete phrase.
     *
     * A phrase without a complete context is not a completion: the example must sound both, so
     * both halves are required.
     */
    fun supports(keyValue: Int, tonality: Tonality): Boolean =
        graphFor(keyValue, tonality).isFeasible &&
            eligibleConstructions(keyValue, tonality).isNotEmpty()

    /** The ordered answer tokens of one phrase. */
    fun answerTokensFor(notes: List<MelodyNote>, steps: List<Int>): List<VoiceAssetId> =
        answerTokens(answerFormat, notes, steps)

    /** The words [answerTokensFor] speaks and the melodic answer display shows. */
    fun answerTextFor(notes: List<MelodyNote>, steps: List<Int>): String =
        answerTokensFor(notes, steps).joinToString(" ") {
            VoiceVocabulary.requireEntry(it).spokenText
        }

    /** Diagnostic key name such as `C major` or `A natural minor`, for range errors. */
    fun keyDisplayName(keyValue: Int, tonality: Tonality): String {
        val spelled = spelledKeyTonic(keyValue)
        val name = VoiceVocabulary.noteEntry(spelled.letter, spelled.accidental).spokenText
        val tonalityText = when (tonality) {
            Tonality.MAJOR -> "major"
            Tonality.NATURAL_MINOR -> "natural minor"
        }
        return "$name $tonalityText"
    }

    override fun equals(other: Any?): Boolean = other is MelodicDictationDefinition &&
        keyValues == other.keyValues &&
        tonalities == other.tonalities &&
        context == other.context &&
        contextGap == other.contextGap &&
        phraseLength == other.phraseLength &&
        startDegree == other.startDegree &&
        endDegree == other.endDegree &&
        allowedSteps == other.allowedSteps &&
        maximumLeap == other.maximumLeap &&
        bias == other.bias &&
        answerFormat == other.answerFormat &&
        spelling == other.spelling &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote

    override fun hashCode(): Int {
        var result = keyValues.hashCode()
        result = 31 * result + tonalities.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + contextGap.hashCode()
        result = 31 * result + phraseLength
        result = 31 * result + (startDegree ?: 0)
        result = 31 * result + (endDegree ?: 0)
        result = 31 * result + allowedSteps.hashCode()
        result = 31 * result + maximumLeap
        result = 31 * result + bias.hashCode()
        result = 31 * result + answerFormat.hashCode()
        result = 31 * result + spelling.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        return result
    }

    override fun toString(): String =
        "MelodicDictationDefinition(keys=$keyValues, tonalities=$tonalities, context=$context, " +
            "contextGap=$contextGap, length=$phraseLength, start=$startDegree, end=$endDegree, " +
            "steps=$allowedSteps, leap=$maximumLeap, bias=$bias, answer=$answerFormat, " +
            "spelling=$spelling, range=$minimumNoteValue..$maximumNoteValue)"

    companion object {
        /** The longest allowed context-to-phrase silence. */
        val MAX_CONTEXT_GAP: Duration = 30.seconds

        /** Shortest phrase; a one-note phrase would have no transition to speak. */
        const val MIN_PHRASE_LENGTH: Int = 2

        /** Longest phrase; the graph DP table stays 128 nodes x 16 rows. */
        const val MAX_PHRASE_LENGTH: Int = 16

        /** Every selectable phrase length, `MIN_PHRASE_LENGTH..MAX_PHRASE_LENGTH`. */
        val PHRASE_LENGTH_RANGE: IntRange = MIN_PHRASE_LENGTH..MAX_PHRASE_LENGTH

        /** Largest absolute signed step a phrase transition may select. */
        const val MAX_STEP: Int = 12

        /** Largest allowed maximum leap. */
        const val MAX_LEAP: Int = 12

        /** All twelve key pitch classes, used when the key selection is empty. */
        private val ALL_KEY_VALUES: List<Int> = java.util.Collections.unmodifiableList(
            (0 until PitchClass.COUNT).toList()
        )

        /** Sharp-table key letters per pitch class, C = 0. */
        private val SHARP_KEY_LETTERS = listOf(
            Letter.C, Letter.C, Letter.D, Letter.D, Letter.E, Letter.F,
            Letter.F, Letter.G, Letter.G, Letter.A, Letter.A, Letter.B
        )

        /** Flat-table key letters per pitch class, C = 0. */
        private val FLAT_KEY_LETTERS = listOf(
            Letter.C, Letter.D, Letter.D, Letter.E, Letter.E, Letter.F,
            Letter.G, Letter.G, Letter.A, Letter.A, Letter.B, Letter.B
        )

        /**
         * Default fixture: C major, tonic triad, C4..C5, four notes from tonic to tonic, steps
         * -5..5 including zero, maximum leap 5, balanced, degree answers.
         */
        fun default(): MelodicDictationDefinition = of(
            keyValues = listOf(0),
            tonalities = listOf(Tonality.MAJOR),
            context = ContextKind.TONIC_TRIAD,
            contextGap = 500.milliseconds,
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(72),
            phraseLength = 4,
            startDegree = 1,
            endDegree = 1,
            allowedSteps = (-5..5).toList(),
            maximumLeap = 5,
            bias = MelodicDirectionBias.BALANCED,
            answerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
            spelling = SpellingPreference.SHARP
        )

        /**
         * Normalizes and validates a definition.
         *
         * An empty [keyValues] collection is valid and means all twelve key tonics. An empty
         * tonality collection is not: a mode with nothing to draw cannot start. [allowedSteps] is
         * deduplicated and sorted; a step outside -12..12, an empty step selection, a phrase length
         * outside 2..16, an endpoint degree outside 1..7 and a leap outside 0..12 are rejected. A
         * selection whose graph has no complete phrase is deliberately not rejected here; the
         * provider reports it against `range`, `endpoints`, `steps`, `leap` or `length`, so an
         * invalid draft stays constructible for form feedback.
         *
         * @throws InvalidMelodicDictationDefinitionException when a key value is not a pitch class,
         *   a tonality selection is empty, the step selection is empty or out of -12..12, the
         *   phrase length is outside 2..16, an endpoint degree is outside 1..7, the maximum leap is
         *   outside 0..12, the context gap is outside 0..30 s, or the range is inverted. The
         *   exception names the setup field (`keys`, `tonalities`, `length`, `endpoints`, `steps`,
         *   `leap`, `contextGap` or `range`) the failure belongs to. The typed [bias] and
         *   [answerFormat] values need no structural check; the provider reports `answers` for a
         *   missing answer clip.
         */
        fun of(
            keyValues: Collection<Int>,
            tonalities: Collection<Tonality>,
            context: ContextKind,
            contextGap: Duration,
            minimumNote: MidiNote,
            maximumNote: MidiNote,
            phraseLength: Int = 4,
            startDegree: Int? = null,
            endDegree: Int? = null,
            allowedSteps: Collection<Int> = (-5..5).toList(),
            maximumLeap: Int = 5,
            bias: MelodicDirectionBias = MelodicDirectionBias.BALANCED,
            answerFormat: MelodicAnswerFormat = MelodicAnswerFormat.DEGREE_SEQUENCE,
            spelling: SpellingPreference = SpellingPreference.SHARP
        ): MelodicDictationDefinition {
            val keys = keyValues.toSet().sorted()
            keys.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidMelodicDictationDefinitionException(
                        "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $value",
                        field = "keys"
                    )
                }
            }
            val tonalityList = tonalities.toSet().sortedBy { it.ordinal }
            if (tonalityList.isEmpty()) {
                throw InvalidMelodicDictationDefinitionException(
                    "At least one tonality must be selected",
                    field = "tonalities"
                )
            }
            if (phraseLength !in MIN_PHRASE_LENGTH..MAX_PHRASE_LENGTH) {
                throw InvalidMelodicDictationDefinitionException(
                    "A phrase length must be $MIN_PHRASE_LENGTH..$MAX_PHRASE_LENGTH but was " +
                        phraseLength,
                    field = "length"
                )
            }
            listOf(startDegree, endDegree).forEach { degree ->
                if (degree != null && degree !in 1..7) {
                    throw InvalidMelodicDictationDefinitionException(
                        "An endpoint degree must be 1..7 or any but was $degree",
                        field = "endpoints"
                    )
                }
            }
            val steps = allowedSteps.toSet().sorted()
            steps.forEach { step ->
                if (step !in -MAX_STEP..MAX_STEP) {
                    throw InvalidMelodicDictationDefinitionException(
                        "A signed step must be -$MAX_STEP..$MAX_STEP but was $step",
                        field = "steps"
                    )
                }
            }
            if (steps.isEmpty()) {
                throw InvalidMelodicDictationDefinitionException(
                    "At least one signed step must be selected",
                    field = "steps"
                )
            }
            if (maximumLeap !in 0..MAX_LEAP) {
                throw InvalidMelodicDictationDefinitionException(
                    "A maximum leap must be 0..$MAX_LEAP but was $maximumLeap",
                    field = "leap"
                )
            }
            if (!contextGap.isFinite() || contextGap < Duration.ZERO ||
                contextGap > MAX_CONTEXT_GAP
            ) {
                throw InvalidMelodicDictationDefinitionException(
                    "A context gap must be 0..${MAX_CONTEXT_GAP.inWholeSeconds} s but was " +
                        contextGap,
                    field = "contextGap"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidMelodicDictationDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            return MelodicDictationDefinition(
                selectedKeyValues = keys,
                selectedTonalities = tonalityList,
                context = context,
                contextGap = contextGap,
                selectedPhraseLength = phraseLength,
                selectedStartDegree = startDegree,
                selectedEndDegree = endDegree,
                selectedAllowedSteps = steps,
                maximumLeap = maximumLeap,
                bias = bias,
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = minimumNote,
                maximumNote = maximumNote
            )
        }

        /**
         * The ordered answer tokens of one complete phrase under an explicit format.
         *
         * DEGREE_SEQUENCE speaks one token per note in played order; DIRECTED_INTERVAL_SEQUENCE
         * speaks the complete phrase of every transition in played order. This function is the
         * single source of truth for both speech and display, so the compiler inserts its
         * exactly-80 ms gaps between adjacent clip tokens and nothing else. [steps] must be the
         * consecutive differences of [notes].
         */
        fun answerTokens(
            format: MelodicAnswerFormat,
            notes: List<MelodyNote>,
            steps: List<Int>
        ): List<VoiceAssetId> = when (format) {
            MelodicAnswerFormat.DEGREE_SEQUENCE -> notes.map { note ->
                VoiceVocabulary.degreeNumberEntry(note.degree).id
            }

            MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE -> steps.flatMap { step ->
                intervalTokensForStep(step)
            }
        }

        /**
         * The simple interval phrase of an absolute signed step.
         *
         * Zero is the unison phrase and 12 is the octave phrase, exactly like every other interval
         * answer in the product.
         */
        fun intervalForStep(step: Int): SimpleInterval {
            require(step in -MAX_STEP..MAX_STEP) {
                "An interval step must be -$MAX_STEP..$MAX_STEP but was $step"
            }
            return SimpleInterval.of(abs(step))
        }

        /**
         * The direction connector a signed step names, exactly like the root-motion mapping.
         *
         * Zero returns the unison connector; a directed-interval answer never speaks it, because
         * the unison phrase alone states zero without a false direction.
         */
        fun directionConnectorEntry(signedStep: Int): VoiceEntry = when {
            signedStep > 0 -> VoiceVocabulary.ASCENDING
            signedStep < 0 -> VoiceVocabulary.DESCENDING
            else -> VoiceVocabulary.UNISON
        }

        /** The complete clip tokens of one transition: direction plus phrase, or unison alone. */
        fun intervalTokensForStep(signedStep: Int): List<VoiceAssetId> {
            val interval = VoiceVocabulary.intervalEntry(intervalForStep(signedStep)).id
            return if (signedStep == 0) {
                listOf(interval)
            } else {
                listOf(directionConnectorEntry(signedStep).id, interval)
            }
        }
    }
}

/** Raised when a melodic-dictation configuration is unknown, empty or internally contradictory. */
class InvalidMelodicDictationDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
