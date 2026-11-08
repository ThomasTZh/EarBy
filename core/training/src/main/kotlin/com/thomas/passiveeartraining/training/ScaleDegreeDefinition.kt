package com.thomas.passiveeartraining.training

import com.thomas.passiveeartraining.music.InvalidMidiNoteException
import com.thomas.passiveeartraining.music.Key
import com.thomas.passiveeartraining.music.Letter
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SpelledPitch
import com.thomas.passiveeartraining.music.SpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.context.ContextBuilder
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.context.TonalContext
import com.thomas.passiveeartraining.training.provider.VoiceVocabulary
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The answer the scale-degree mode asks the listener to produce. */
enum class DegreeAnswerFormat {
    /** The degree number alone, for example "five". */
    DEGREE,

    /** The absolute note name of the degree, for example "F sharp". */
    NOTE_NAME
}

/**
 * Immutable, normalized scale-degree configuration (M8.1/M8.2).
 *
 * The selection is a set of key pitch classes crossed with tonalities and target degrees;
 * duplicates are removed and each list is ordered (keys and degrees numerically, tonalities by
 * ordinal). An empty key selection means all twelve key tonics (SC2/M8), exactly like the scale
 * setup's empty root selection. The spelled key tonic follows [spelling], so the two enharmonic
 * readings of a key pitch class produce the theoretical members of that reading; [spelling] also
 * selects which chromatic note entry NOTE_NAME speaks.
 *
 * Every feasibility question is answered against [noteRange]: a context construction is eligible
 * only when all its sounding notes and at least one target note of the requested degree are inside
 * the range. A selected combination with no eligible construction is reported by the provider
 * against `range`, never silently removed here.
 *
 * @see ContextBuilder
 */
class ScaleDegreeDefinition private constructor(
    selectedKeyValues: List<Int>,
    selectedTonalities: List<Tonality>,
    selectedDegrees: List<Int>,
    val context: ContextKind,
    val contextGap: Duration,
    val answerFormat: DegreeAnswerFormat,
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

    /** Selected target degrees 1..7 in numeric order; never empty. */
    val degrees: List<Int> = java.util.Collections.unmodifiableList(selectedDegrees.toList())

    /** The key tonics this definition actually enumerates: [keyValues], or all twelve when empty. */
    val effectiveKeyValues: List<Int>
        get() = if (keyValues.isEmpty()) ALL_KEY_VALUES else keyValues

    val minimumNoteValue: Int = minimumNote.value
    val maximumNoteValue: Int = maximumNote.value

    /** Inclusive sounding range as a MIDI value pair; covers every sounding context and target. */
    val noteRange: IntRange = minimumNoteValue..maximumNoteValue

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
     * The sounding pitch class of [degree] in [tonality], taken from the theoretical key spelling.
     *
     * This is the class the target notes must match; it is independent of the target's octave, so
     * a target above the anchor keeps the same theoretical degree (M8).
     */
    fun degreePitchClass(keyValue: Int, tonality: Tonality, degree: Int): PitchClass =
        PitchClass.ofMidiValue(keyFor(keyValue, tonality).degreeMidi(degree))

    /**
     * The spelled note name of [degree]: the letter comes from the key's theoretical progression
     * and the accidental from the key spelling, including double accidentals when the key needs
     * them.
     */
    fun spelledDegree(keyValue: Int, tonality: Tonality, degree: Int): SpelledPitch =
        keyFor(keyValue, tonality).degree(degree)

    /**
     * The ordered answer tokens for [degree] under this definition's answer format.
     *
     * The sequence is exactly what the answer speaks and what the display projects.
     */
    fun answerTokensFor(keyValue: Int, tonality: Tonality, degree: Int): List<VoiceAssetId> =
        answerTokens(
            format = answerFormat,
            degree = degree,
            pitchClass = degreePitchClass(keyValue, tonality, degree),
            spelling = spelling
        )

    /** The words [answerTokensFor] speaks and the scale-degree answer display shows. */
    fun answerTextFor(keyValue: Int, tonality: Tonality, degree: Int): String =
        answerTokensFor(keyValue, tonality, degree).joinToString(" ") {
            VoiceVocabulary.requireEntry(it).spokenText
        }

    /**
     * Every target MIDI note of [degree] inside the packaged instrument range, ascending.
     *
     * The target pitch class is the degree's, so the list can span several octaves; the provider
     * draws exactly one of them. The configured range constrains the key tonic anchors, not the
     * target octave.
     */
    fun targetNotesFor(keyValue: Int, tonality: Tonality, degree: Int): List<MidiNote> {
        val pitchClass = degreePitchClass(keyValue, tonality, degree)
        return PlayableRange.VALUES
            .map { MidiNote.of(it) }
            .filter { it.pitchClass == pitchClass }
    }

    /**
     * Every eligible context construction of ([keyValue], [tonality]) for [degree], anchor
     * ascending.
     *
     * A construction is eligible when the complete context and at least one target note fit the
     * packaged instrument range, and the key-tonic anchor fits the configured root range. The
     * context is built exactly as playback will build it, so an anchor whose triad, scale or
     * cadence would leave the instrument range is excluded here, never truncated.
     */
    fun eligibleConstructions(keyValue: Int, tonality: Tonality, degree: Int): List<TonalContext> {
        if (targetNotesFor(keyValue, tonality, degree).isEmpty()) return emptyList()
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

    /** True when ([keyValue], [tonality], [degree]) has at least one eligible construction. */
    fun supports(keyValue: Int, tonality: Tonality, degree: Int): Boolean =
        eligibleConstructions(keyValue, tonality, degree).isNotEmpty()

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

    override fun equals(other: Any?): Boolean = other is ScaleDegreeDefinition &&
        keyValues == other.keyValues &&
        tonalities == other.tonalities &&
        degrees == other.degrees &&
        context == other.context &&
        contextGap == other.contextGap &&
        answerFormat == other.answerFormat &&
        spelling == other.spelling &&
        minimumNote == other.minimumNote &&
        maximumNote == other.maximumNote

    override fun hashCode(): Int {
        var result = keyValues.hashCode()
        result = 31 * result + tonalities.hashCode()
        result = 31 * result + degrees.hashCode()
        result = 31 * result + context.hashCode()
        result = 31 * result + contextGap.hashCode()
        result = 31 * result + answerFormat.hashCode()
        result = 31 * result + spelling.hashCode()
        result = 31 * result + minimumNote.hashCode()
        result = 31 * result + maximumNote.hashCode()
        return result
    }

    override fun toString(): String =
        "ScaleDegreeDefinition(keys=$keyValues, tonalities=$tonalities, degrees=$degrees, " +
            "context=$context, contextGap=$contextGap, answer=$answerFormat, " +
            "spelling=$spelling, range=$minimumNoteValue..$maximumNoteValue)"

    companion object {
        /** The longest allowed context-to-target silence. */
        val MAX_CONTEXT_GAP: Duration = 30.seconds

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

        /** Default fixture: C major, all degrees, tonic triad, degree answers, C4..C5. */
        fun default(): ScaleDegreeDefinition = of(
            keyValues = listOf(0),
            tonalities = listOf(Tonality.MAJOR),
            degrees = (1..7).toList(),
            context = ContextKind.TONIC_TRIAD,
            contextGap = 500.milliseconds,
            answerFormat = DegreeAnswerFormat.DEGREE,
            spelling = SpellingPreference.SHARP,
            minimumNote = MidiNote.of(60),
            maximumNote = MidiNote.of(72)
        )

        /**
         * Normalizes and validates a definition.
         *
         * An empty [keyValues] collection is valid and means all twelve key tonics. An empty
         * tonality or degree collection is not: a mode with nothing to draw cannot start.
         * A selected key/tonality/degree whose context and target cannot fit is deliberately not
         * rejected here; the provider reports it against `range`, so an invalid draft stays
         * constructible for form feedback.
         *
         * @throws InvalidScaleDegreeDefinitionException when a key value is not a pitch class, a
         *   tonality or degree selection is empty, a degree is not 1..7, the context gap is
         *   outside 0..30 s, or the range is inverted. The exception names the setup field
         *   (`keys`, `tonalities`, `degrees`, `contextGap` or `range`) the failure belongs to.
         */
        fun of(
            keyValues: Collection<Int>,
            tonalities: Collection<Tonality>,
            degrees: Collection<Int>,
            context: ContextKind,
            contextGap: Duration,
            answerFormat: DegreeAnswerFormat,
            spelling: SpellingPreference,
            minimumNote: MidiNote,
            maximumNote: MidiNote
        ): ScaleDegreeDefinition {
            val keys = keyValues.toSet().sorted()
            keys.forEach { value ->
                if (value !in 0 until PitchClass.COUNT) {
                    throw InvalidScaleDegreeDefinitionException(
                        "A key pitch class must be 0..${PitchClass.COUNT - 1} but was $value",
                        field = "keys"
                    )
                }
            }
            val tonalityList = tonalities.toSet().sortedBy { it.ordinal }
            if (tonalityList.isEmpty()) {
                throw InvalidScaleDegreeDefinitionException(
                    "At least one tonality must be selected",
                    field = "tonalities"
                )
            }
            val degreeList = degrees.toSet().sorted()
            degreeList.forEach { value ->
                if (value !in 1..7) {
                    throw InvalidScaleDegreeDefinitionException(
                        "A target degree must be 1..7 but was $value",
                        field = "degrees"
                    )
                }
            }
            if (degreeList.isEmpty()) {
                throw InvalidScaleDegreeDefinitionException(
                    "At least one target degree must be selected",
                    field = "degrees"
                )
            }
            if (!contextGap.isFinite() || contextGap < Duration.ZERO ||
                contextGap > MAX_CONTEXT_GAP
            ) {
                throw InvalidScaleDegreeDefinitionException(
                    "A context gap must be 0..${MAX_CONTEXT_GAP.inWholeSeconds} s but was $contextGap",
                    field = "contextGap"
                )
            }
            if (minimumNote.value > maximumNote.value) {
                throw InvalidScaleDegreeDefinitionException(
                    "Minimum note ${minimumNote.value} must not exceed maximum note " +
                        "${maximumNote.value}",
                    field = "range"
                )
            }
            return ScaleDegreeDefinition(
                selectedKeyValues = keys,
                selectedTonalities = tonalityList,
                selectedDegrees = degreeList,
                context = context,
                contextGap = contextGap,
                answerFormat = answerFormat,
                spelling = spelling,
                minimumNote = minimumNote,
                maximumNote = maximumNote
            )
        }

        /**
         * Builds the ordered answer tokens of one degree under an explicit projection.
         *
         * DEGREE answers with the number token; NOTE_NAME picks the chromatic note entry of
         * [pitchClass] under [spelling] (naturals and the sharp/flat tables, never double
         * accidentals). This function is the single source of truth for both speech and display.
         */
        fun answerTokens(
            format: DegreeAnswerFormat,
            degree: Int,
            pitchClass: PitchClass,
            spelling: SpellingPreference
        ): List<VoiceAssetId> = when (format) {
            DegreeAnswerFormat.DEGREE ->
                listOf(VoiceVocabulary.degreeNumberEntry(degree).id)

            DegreeAnswerFormat.NOTE_NAME ->
                listOf(NoteDefinition.nameOf(pitchClass, spelling).entry.id)
        }
    }
}

/** Raised when a scale-degree configuration is unknown, empty or internally contradictory. */
class InvalidScaleDegreeDefinitionException(
    message: String,
    cause: Throwable? = null,
    /** The setup field the failure belongs to, for form feedback. */
    val field: String? = null
) : IllegalArgumentException(message, cause)
