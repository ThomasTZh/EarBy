package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.music.ChordPresentation
import com.thomas.passiveeartraining.music.DiatonicChord
import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.music.ProgressionVoicingMode
import com.thomas.passiveeartraining.music.ScaleDirection
import com.thomas.passiveeartraining.music.SpellingPreference as MusicSpellingPreference
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.ChordAnswerMode
import com.thomas.passiveeartraining.training.ContextualIntervalAnswerFormat
import com.thomas.passiveeartraining.training.DegreeAnswerFormat
import com.thomas.passiveeartraining.training.MelodicAnswerFormat
import com.thomas.passiveeartraining.training.MelodicDirectionBias
import com.thomas.passiveeartraining.training.ProgressionAnswerFormat
import com.thomas.passiveeartraining.training.context.ContextKind
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.CHORDS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.CHORD_PROGRESSIONS_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.INTERVALS_IN_CONTEXT_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MELODIC_DICTATION_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NOTE_IDENTIFICATION_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.SCALES_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.SCALE_DEGREES_CONFIG_VERSION
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun preset(
    id: PresetId = PresetId("11111111-2222-3333-4444-555555555555"),
    name: String = "Minor thirds",
    seed: Long? = null
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.Intervals(
        LastSetup.default().copy(
            intervalIds = listOf("minor_third"),
            presentation = IntervalPresentation.ASCENDING,
            mode = RecipeMode.RECOGNITION,
            replayCount = 1
        )
    ),
    seed = seed
)

private fun chordsPreset(
    id: PresetId = PresetId("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
    name: String = "Major and minor triads",
    setup: ChordSetup = ChordSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.Chords(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

private fun scalesPreset(
    id: PresetId = PresetId("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff"),
    name: String = "Major and natural minor scales",
    setup: ScaleSetup = ScaleSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.Scales(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

private fun notesPreset(
    id: PresetId = PresetId("cccccccc-dddd-4eee-8fff-000000000000"),
    name: String = "All twelve notes",
    setup: NoteSetup = NoteSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.Notes(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

private fun scaleDegreesPreset(
    id: PresetId = PresetId("dddddddd-eeee-4fff-8000-111111111111"),
    name: String = "C major scale degrees",
    setup: ScaleDegreeSetup = ScaleDegreeSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.ScaleDegrees(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

private fun progressionsPreset(
    id: PresetId = PresetId("eeeeeeee-ffff-4000-8111-222222222222"),
    name: String = "Major chord progressions",
    setup: ProgressionSetup = ProgressionSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.Progressions(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

private fun contextualIntervalsPreset(
    id: PresetId = PresetId("ffffffff-0000-4111-8222-333333333333"),
    name: String = "Intervals over the tonic triad",
    setup: ContextualIntervalSetup = ContextualIntervalSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.ContextualIntervals(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

private fun melodicDictationPreset(
    id: PresetId = PresetId("00000000-1111-4222-8333-444444444444"),
    name: String = "Tonic-to-tonic four-note phrases",
    setup: MelodicDictationSetup = MelodicDictationSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
) = Preset(
    id = id,
    name = name,
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_100_000L,
    configuration = PresetConfiguration.MelodicDictation(setup),
    seed = seed,
    musicGain = musicGain,
    voiceGain = voiceGain
)

/** The intervals setup of a document-test preset. */
private val Preset.intervalSetup: LastSetup
    get() = (configuration as PresetConfiguration.Intervals).setup

/** The chords setup of a document-test preset. */
private val Preset.chordsSetup: ChordSetup
    get() = (configuration as PresetConfiguration.Chords).setup

/** The scales setup of a document-test preset. */
private val Preset.scalesSetup: ScaleSetup
    get() = (configuration as PresetConfiguration.Scales).setup

/** The notes setup of a document-test preset. */
private val Preset.notesSetup: NoteSetup
    get() = (configuration as PresetConfiguration.Notes).setup

/** The scale-degrees setup of a document-test preset. */
private val Preset.scaleDegreesSetup: ScaleDegreeSetup
    get() = (configuration as PresetConfiguration.ScaleDegrees).setup

/** The chord-progressions setup of a document-test preset. */
private val Preset.progressionsSetup: ProgressionSetup
    get() = (configuration as PresetConfiguration.Progressions).setup

/** The intervals-in-context setup of a document-test preset. */
private val Preset.contextualIntervalsSetup: ContextualIntervalSetup
    get() = (configuration as PresetConfiguration.ContextualIntervals).setup

/** The melodic-dictation setup of a document-test preset. */
private val Preset.melodicDictationSetup: MelodicDictationSetup
    get() = (configuration as PresetConfiguration.MelodicDictation).setup

private fun PresetDocumentV2.withPayload(
    transform: (PresetPayloadV1) -> PresetPayloadV1
): PresetDocumentV2 = copy(
    payload = PresetDocumentCodec.encodePayload(
        transform(PresetDocumentCodec.intervalPayload(this))
    )
)

class PresetDocumentTest {

    @Test
    fun aPresetRoundTripsThroughItsDocument() {
        val original = preset(seed = 42L)
        val text = PresetDocumentCodec.encode(listOf(original))
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val restored = PresetDocumentCodec.toPreset(decoded.first())
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.intervalSetup.intervalIds, restored.intervalSetup.intervalIds)
        assertEquals(original.intervalSetup.mode, restored.intervalSetup.mode)
        assertEquals(original.intervalSetup.replayCount, restored.intervalSetup.replayCount)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
    }

    @Test
    fun aSeedIsExportedAsADecimalStringSoItCannotBeCorrupted() {
        // A JSON reader that represents every number as a float would round this value; a decimal
        // string cannot be rounded, and the range is validated on the way back in.
        val large = 9_007_199_254_740_993L
        val text = PresetDocumentCodec.encode(listOf(preset(seed = large)))
        assertTrue("the seed must appear as a quoted string", text.contains("\"$large\""))
        val restored = PresetDocumentCodec.toPreset(PresetDocumentCodec.decode(text).first())
        assertEquals("the exact seed survives", large, restored.seed)
    }

    @Test
    fun anAbsentSeedMeansTheStartChoosesOne() {
        val text = PresetDocumentCodec.encode(listOf(preset(seed = null)))
        val restored = PresetDocumentCodec.toPreset(PresetDocumentCodec.decode(text).first())
        assertNull("no seed is recorded, so Start draws one", restored.seed)
        assertTrue("the field is simply absent", !text.contains("seed_decimal"))
    }

    @Test
    fun aSeedOutsideTheSignedSixtyFourBitRangeIsRejected() {
        val document = PresetDocumentCodec.toDocument(
            preset(),
            PresetDocumentCodec.intervalPayload(PresetDocumentCodec.toDocument(preset()))
                .copy(seedDecimal = "99999999999999999999999")
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(document)
        }
        assertTrue("the failure explains the range", error.message!!.contains("64-bit"))
    }

    @Test
    fun anUnknownModeOrPresentationIsRejectedByName() {
        val document = PresetDocumentCodec.toDocument(preset())
        val badMode = document.withPayload { it.copy(mode = "SINGING") }
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.toSetup(badMode)
            }.message!!.contains("SINGING")
        )

        val badPresentation = document.withPayload { it.copy(presentation = "SPIRAL") }
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.toSetup(badPresentation)
            }.message!!.contains("SPIRAL")
        )
    }

    @Test
    fun aFutureSchemaIsRejectedWithAUsefulMessage() {
        val text = PresetDocumentCodec.encode(listOf(preset()))
        val future = text.replace("\"schemaVersion\":2", "\"schemaVersion\":3")
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.decode(future)
        }
        assertTrue("the failure names both versions", error.message!!.contains("newer"))
        assertTrue(error.message!!.contains("3"))
    }

    @Test
    fun malformedJsonIsRejectedRatherThanPartlyImported() {
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.decode("{ this is not json")
        }
        assertTrue(
            "the failure says the document is not a valid export",
            error.message!!.contains("valid")
        )
    }

    @Test
    fun anUnknownFieldIsRejectedSoANewerDocumentIsNeverMisread() {
        val text = PresetDocumentCodec.encode(listOf(preset()))
        val withExtra = text.replaceFirst("{", "{\"unexpected\":true,")
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.decode(withExtra)
        }
    }

    @Test
    fun aNameIsTrimmedAndBounded() {
        PresetDocumentCodec.requireName("  Consonants  ")
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.requireName("   ")
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.requireName("x".repeat(PresetDocumentCodec.MAX_NAME_LENGTH + 1))
        }
    }

    @Test
    fun anUnusableMusicalConstraintIsRejectedAtImport() {
        // A preset whose range cannot produce its interval would fail at Start, so it is refused when
        // it is read rather than when it is used.
        // A descending major seventh needs its root at least eleven semitones above the top of the
        // range, which this narrow range cannot supply.
        val base = PresetDocumentCodec.toDocument(preset())
        val document = base.withPayload {
            it.copy(
                intervalIds = listOf("major_seventh"),
                presentation = "DESCENDING",
                rootValues = listOf(9),
                minimumNote = 21,
                maximumNote = 21
            )
        }
        assertThrows(IllegalArgumentException::class.java) { PresetDocumentCodec.toSetup(document) }
    }

    @Test
    fun gainsOutsideTheRangeOrNonFiniteAreRejected() {
        val base = PresetDocumentCodec.toDocument(preset())
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(base.withPayload { it.copy(musicGain = 9f) })
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload { it.copy(voiceGain = Float.NaN) }
            )
        }
    }

    @Test
    fun theExportBoundsAreEnforced() {
        val many = (1..PresetDocumentCodec.MAX_PRESETS + 1).map { preset(id = PresetId("id-$it")) }
        assertThrows(PresetValidationException::class.java) { PresetDocumentCodec.encode(many) }

        val atLimit = (1..PresetDocumentCodec.MAX_PRESETS).map { preset(id = PresetId("id-$it")) }
        PresetDocumentCodec.encode(atLimit)
    }

    @Test
    fun settingsThatAreNotMusicalAreDeliberatelyExcluded() {
        // A preset restores a musical configuration; appearance and display preferences stay with the
        // user rather than travelling inside a shared preset.
        val document = PresetDocumentCodec.toDocument(preset())
        val text = PresetDocumentCodec.encode(listOf(preset()))
        listOf("theme", "spelling", "answerVisibility", "dynamicColor").forEach { excluded ->
            assertTrue(
                "$excluded must not be exported",
                !text.contains(excluded, ignoreCase = true)
            )
        }
        assertTrue(
            "the musical configuration is exported",
            PresetDocumentCodec.intervalPayload(document).intervalIds.isNotEmpty()
        )
    }

    @Test
    fun aCopiedPresetGetsANewIdentityButKeepsItsConfiguration() {
        val original = preset(seed = 7L)
        val document = PresetDocumentCodec.toDocument(original)
        val copy = PresetDocumentCodec.toPreset(document, id = PresetId("new-id"))
        assertNotEquals("an import defaults to a new identity", original.id, copy.id)
        assertEquals("the configuration is unchanged", original.configuration, copy.configuration)
        assertEquals(original.seed, copy.seed)
    }

    @Test
    fun everyStopPolicySurvivesTheDocumentRoundTrip() {
        val policies = listOf(
            SessionStopPolicy.UntilStopped,
            SessionStopPolicy.AfterCycle,
            SessionStopPolicy.Duration(Duration.parse("30s"), DurationFinish.IMMEDIATE),
            SessionStopPolicy.Duration(Duration.parse("90m"), DurationFinish.FINISH_CYCLE),
            SessionStopPolicy.CompletedItems(10_000)
        )
        policies.forEach { policy ->
            val original = preset().let {
                it.copy(
                    configuration = PresetConfiguration.Intervals(
                        it.intervalSetup.copy(stopPolicy = policy)
                    )
                )
            }
            val text = PresetDocumentCodec.encode(listOf(original))
            val restored = PresetDocumentCodec.toPreset(
                PresetDocumentCodec.decode(text).single()
            )
            assertEquals("stop policy $policy survives", policy, restored.intervalSetup.stopPolicy)
        }
    }

    @Test
    fun aStopKindCarryingFieldsOfAnotherKindIsRejected() {
        val base = PresetDocumentCodec.toDocument(preset())
        val untilStoppedWithCount = base.withPayload {
            it.copy(stopKind = "UNTIL_STOPPED", stopCount = 5)
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(untilStoppedWithCount)
        }

        val countWithDuration = base.withPayload {
            it.copy(
                stopKind = "COMPLETED_ITEMS",
                stopCount = 5,
                stopDurationMs = 30_000
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(countWithDuration)
        }
    }

    @Test
    fun incompleteOrOutOfRangeStopValuesAreRejected() {
        val base = PresetDocumentCodec.toDocument(preset())
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload { it.copy(stopKind = "DURATION") }
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload {
                    it.copy(
                        stopKind = "DURATION",
                        stopDurationMs = 0,
                        stopDurationFinish = "IMMEDIATE"
                    )
                }
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload {
                    it.copy(
                        stopKind = "DURATION",
                        stopDurationMs = Duration.parse("181m").inWholeMilliseconds,
                        stopDurationFinish = "IMMEDIATE"
                    )
                }
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload {
                    it.copy(
                        stopKind = "COMPLETED_ITEMS",
                        stopCount = SessionStopPolicy.MAX_ITEMS + 1
                    )
                }
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload {
                    it.copy(
                        stopKind = "DURATION",
                        stopDurationMs = 30_000,
                        stopDurationFinish = "SOMETIME"
                    )
                }
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload { it.copy(stopKind = "FOREVER") }
            )
        }
    }

    @Test
    fun theEnvelopeDecoderLeavesPerPresetValidationToItsCaller() {
        // An import preview needs each failure on its own row, so the envelope decode must not throw
        // for an unusable payload; the plain decode still validates everything.
        val broken = PresetDocumentCodec.toDocument(preset()).let {
            it.withPayload { it.copy(intervalIds = listOf("not_an_interval")) }
        }
        val text = Json.encodeToString(
            PresetExportEnvelope.serializer(),
            PresetExportEnvelope(
                presets = listOf(Json.encodeToJsonElement(PresetDocumentV2.serializer(), broken))
            )
        )

        assertEquals(1, PresetDocumentCodec.decodeEnvelope(text).size)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.decode(text)
        }
    }

    @Test
    fun aBlankPresetIdIsRejectedAsADocumentProblem() {
        val document = PresetDocumentCodec.toDocument(preset()).copy(id = "  ")
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(document)
        }
    }

    @Test
    fun aNegativeThinkingTimeOrAnOutOfRangeReplayCountIsRejected() {
        val base = PresetDocumentCodec.toDocument(preset())
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload { it.copy(thinkingSilenceMs = -1) }
            )
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(
                base.withPayload { it.copy(replayCount = RecipeDefinition.MAX_REPLAY + 1) }
            )
        }
    }

    @Test
    fun aPathShapedUnknownFieldIsRejected() {
        // A document must not smuggle file paths or later-build fields past the strict reader.
        val text = PresetDocumentCodec.encode(listOf(preset()))
        val withPath = text.replaceFirst("{", "{\"payload.file_path\":\"/sdcard/secret\",")
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.decode(withPath)
        }
    }

    @Test
    fun anExportRefusesAPresetThisBuildCouldNotReadBack() {
        val broken = preset().copy(musicGain = Float.NaN)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.encode(listOf(broken))
        }
    }

    @Test
    fun everyTimingLengthSurvivesTheDocumentRoundTrip() {
        val original = preset().let {
            it.copy(
                configuration = PresetConfiguration.Intervals(
                    it.intervalSetup.copy(
                        noteDuration = Duration.parse("450ms"),
                        answerGap = Duration.parse("250ms"),
                        repeatGap = Duration.ZERO
                    )
                )
            )
        }
        val text = PresetDocumentCodec.encode(listOf(original))
        val restored = PresetDocumentCodec.toPreset(PresetDocumentCodec.decode(text).single())
        assertEquals(original.configuration, restored.configuration)
    }

    @Test
    fun invalidTimingValuesInADocumentAreRejected() {
        val base = PresetDocumentCodec.toDocument(preset())
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(base.withPayload { it.copy(noteDurationMs = 0) })
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(base.withPayload { it.copy(answerGapMs = -1) })
        }
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(base.withPayload { it.copy(repeatGapMs = -1) })
        }
    }

    @Test
    fun aFixedNoteSurvivesTheDocumentRoundTrip() {
        val original = preset().let {
            it.copy(
                configuration = PresetConfiguration.Intervals(
                    it.intervalSetup.copy(fixedRoot = 60)
                )
            )
        }
        val text = PresetDocumentCodec.encode(listOf(original))
        val restored = PresetDocumentCodec.toPreset(PresetDocumentCodec.decode(text).single())
        assertEquals(60, restored.intervalSetup.fixedRoot)
        assertEquals(original.configuration, restored.configuration)
    }

    @Test
    fun anImpossibleFixedNoteIsRejectedAtImport() {
        val base = PresetDocumentCodec.toDocument(preset())
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(base.withPayload { it.copy(fixedRoot = 200) })
        }
        assertThrows(PresetValidationException::class.java) {
            // A fixed root outside the configured root range is impossible.
            PresetDocumentCodec.validate(
                base.withPayload { it.copy(presentation = "DESCENDING", fixedRoot = 21) }
            )
        }
    }
}

/** The structural preflight is what makes an untrusted document safe to read at all. */
class PresetPreflightTest {

    @Test
    fun aWellFormedDocumentPasses() {
        PresetPreflight.check(PresetDocumentCodec.encode(listOf(preset())))
    }

    @Test
    fun aDuplicateKeyIsRejectedBecauseOneWouldSilentlyWin() {
        val duplicate = """{"schemaVersion":1,"schemaVersion":2,"presets":[]}"""
        val error =
            assertThrows(PresetValidationException::class.java) { PresetPreflight.check(duplicate) }
        // The streaming parser reports the repeated field, which is what a caller needs to fix it.
        assertTrue(
            "the failure names the repeated field: ${error.message}",
            error.message!!.contains("schemaVersion", ignoreCase = true)
        )
        assertTrue(
            "the failure says the document repeats a field: ${error.message}",
            error.message!!.contains("repeats") ||
                error.message!!.contains("Duplicate", ignoreCase = true)
        )
    }

    @Test
    fun nestingDeeperThanTheBoundIsRejected() {
        val deep = buildString {
            append("[".repeat(PresetPreflight.MAX_NESTING_DEPTH + 5))
            append("1")
            append("]".repeat(PresetPreflight.MAX_NESTING_DEPTH + 5))
        }
        assertThrows(PresetValidationException::class.java) { PresetPreflight.check(deep) }
    }

    @Test
    fun anOverLongStringIsRejected() {
        val long = "\"" + "x".repeat(PresetPreflight.MAX_STRING_LENGTH + 1) + "\""
        val error = assertThrows(PresetValidationException::class.java) {
            PresetPreflight.check("""{"presets":[$long]}""")
        }
        assertTrue("the failure names the limit", error.message!!.contains("limit"))
    }

    @Test
    fun truncatedAndEmptyDocumentsAreRejected() {
        assertThrows(PresetValidationException::class.java) { PresetPreflight.check("") }
        assertThrows(PresetValidationException::class.java) {
            PresetPreflight.check("""{"presets":[""")
        }
        assertThrows(PresetValidationException::class.java) { PresetPreflight.check("[1,2") }
    }

    @Test
    fun trailingContentAfterTheRootIsRejected() {
        // Two documents in one file would mean a reader silently ignores the second.
        assertThrows(PresetValidationException::class.java) {
            PresetPreflight.check("""{"a":1} {"b":2}""")
        }
    }

    @Test
    fun thePreflightRejectsWhatTheTypedDecoderWouldOtherwiseSee() {
        // The preflight runs inside decode, so a duplicate-field document never reaches the decoder.
        val duplicate = """{"schemaVersion":1,"presets":[],"presets":[]}"""
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.decode(duplicate)
        }
        assertTrue(
            "the decoder reports the structural defect: ${error.message}",
            error.message!!.contains("repeats") ||
                error.message!!.contains("Duplicate", ignoreCase = true)
        )
    }

    @Test
    fun aFrozenSchemaOneDocumentStillReadsAndIsReexportedAsSchemaTwo() {
        // The schema-1 writer's shape, built by hand so the fixture cannot drift with the codec.
        val source = preset(seed = 77L)
        val sourcePayload = PresetDocumentCodec.intervalPayload(
            PresetDocumentCodec.toDocument(source)
        )
        val v1 = PresetDocumentV1(
            schemaVersion = 1,
            id = source.id.value,
            name = source.name,
            createdAtEpochMs = source.createdAtEpochMs,
            updatedAtEpochMs = source.updatedAtEpochMs,
            payload = sourcePayload
        )
        val v1Text = Json.encodeToString(
            PresetExportEnvelope.serializer(),
            PresetExportEnvelope(
                schemaVersion = 1,
                presets = listOf(Json.encodeToJsonElement(PresetDocumentV1.serializer(), v1))
            )
        )

        val decoded = PresetDocumentCodec.decode(v1Text)
        val restored = PresetDocumentCodec.toPreset(decoded.first())
        assertEquals(source.id, restored.id)
        assertEquals(source.name, restored.name)
        assertEquals(77L, restored.seed)
        assertEquals(source.configuration, restored.configuration)

        // Re-exporting from this build writes schema 2 with the kind discriminator.
        val exported = PresetDocumentCodec.encode(listOf(restored))
        assertTrue(exported.contains("\"schemaVersion\":2"))
        assertTrue(exported.contains("\"kind\":\"intervals\""))
        assertEquals(
            source.configuration,
            PresetDocumentCodec.toPreset(PresetDocumentCodec.decode(exported).first())
                .configuration
        )
    }

    @Test
    fun aSchemaTwoDocumentOfAnUnknownKindOrConfigIsRefusedNotMisdecoded() {
        val document = PresetDocumentCodec.toDocument(preset())
        // A kind this build has no typed codec for is refused rather than decoded as intervals.
        val foreignKind = document.copy(kind = "rhythm_tapping")
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(foreignKind)
            }.message!!.contains("rhythm_tapping")
        )
        val futureConfig = document.copy(configVersion = 2)
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(futureConfig)
            }.message!!.contains("configuration version")
        )
    }

    @Test
    fun aChordProgressionsDocumentIsNeverDecodedAsAnotherKindsPayload() {
        // The kind discriminator is authoritative: an intervals payload tagged as chord
        // progressions must be refused, not silently read as the wrong mode's setup.
        val document = PresetDocumentCodec.toDocument(preset())
            .copy(kind = TrainingKind.CHORD_PROGRESSIONS.id)
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(document)
            }.message!!.contains("invalid payload")
        )
    }

    @Test
    fun aChordsPresetRoundTripsThroughItsDocument() {
        val setup = ChordSetup.default().copy(
            qualityIds = listOf("major", "minor_seventh"),
            inversions = listOf(0, 2),
            rootValues = listOf(0, 7),
            presentation = ChordPresentation.ARPEGGIO,
            answerMode = ChordAnswerMode.QUALITY_AND_INVERSION,
            replayCount = 2,
            fixedRoot = 60,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = chordsPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue("the document names the chords kind", text.contains("\"kind\":\"chords\""))
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.CHORDS.id, document.kind)
        assertEquals(CHORDS_CONFIG_VERSION, document.configVersion)
        assertEquals(
            "the chords payload is decodable without an interval shape",
            listOf("major", "minor_seventh"),
            PresetDocumentCodec.chordPayload(document).qualityIds
        )

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.Chords)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.chordsSetup.stopPolicy
        )
    }

    @Test
    fun aChordsDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(chordsPreset())
        val futureConfig = document.copy(configVersion = CHORDS_CONFIG_VERSION + 1)
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val intervalVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(intervalVersion)
        }
    }

    @Test
    fun aChordsDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(chordsPreset())
        val badQuality = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.chordPayload(base).copy(qualityIds = listOf("not_a_quality"))
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badQuality)
            }.message!!.contains("not_a_quality")
        )

        val badPresentation = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.chordPayload(base).copy(presentation = "SPIRAL")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badPresentation)
            }.message!!.contains("SPIRAL")
        )
    }

    @Test
    fun aScalesPresetRoundTripsThroughItsDocument() {
        val setup = ScaleSetup.default().copy(
            scaleIds = listOf("major", "dorian", "whole_tone"),
            direction = ScaleDirection.BOTH,
            rootValues = listOf(0, 7),
            replayCount = 2,
            fixedRoot = 60,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = scalesPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue("the document names the scales kind", text.contains("\"kind\":\"scales\""))
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.SCALES.id, document.kind)
        assertEquals(SCALES_CONFIG_VERSION, document.configVersion)
        assertEquals(
            "the scales payload is decodable without an interval shape",
            listOf("major", "dorian", "whole_tone"),
            PresetDocumentCodec.scalesPayload(document).scaleIds
        )
        assertEquals(
            "the direction is exported as its stable name",
            "BOTH",
            PresetDocumentCodec.scalesPayload(document).direction
        )

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.Scales)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.scalesSetup.stopPolicy
        )
    }

    @Test
    fun aScalesDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(scalesPreset())
        val futureConfig = document.copy(configVersion = SCALES_CONFIG_VERSION + 1)
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val olderVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(olderVersion)
        }
    }

    @Test
    fun aScalesDocumentOfAnUnknownKindIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(scalesPreset())
        val foreignKind = document.copy(kind = "rhythm_tapping")
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(foreignKind)
            }.message!!.contains("rhythm_tapping")
        )
    }

    @Test
    fun aScalesDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(scalesPreset())
        val badScale = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scalesPayload(base).copy(scaleIds = listOf("not_a_scale"))
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badScale)
            }.message!!.contains("not_a_scale")
        )

        val badDirection = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scalesPayload(base).copy(direction = "SIDEWAYS")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badDirection)
            }.message!!.contains("SIDEWAYS")
        )
    }

    @Test
    fun aScalesDisplayAliasIsCanonicalizedWhenTheDocumentIsRead() {
        val base = PresetDocumentCodec.toDocument(scalesPreset())
        val aliased = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scalesPayload(base)
                    .copy(scaleIds = listOf("ionian", "aeolian"))
            )
        )

        val restored = PresetDocumentCodec.toPreset(aliased)
        assertEquals(
            "the aliases resolve to their canonical scales",
            listOf("major", "natural_minor"),
            restored.scalesSetup.scaleIds
        )
        val reexported = PresetDocumentCodec.encode(listOf(restored))
        assertTrue("the canonical id is exported", reexported.contains("\"major\""))
        assertTrue(
            "the display alias is never re-exported",
            !reexported.contains("ionian") && !reexported.contains("aeolian")
        )
    }

    @Test
    fun aNotesPresetRoundTripsThroughItsDocument() {
        val setup = NoteSetup.default().copy(
            pitchClassValues = listOf(0, 1, 7),
            accidental = MusicSpellingPreference.FLAT,
            speakOctave = true,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = notesPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue(
            "the document names the note-identification kind",
            text.contains("\"kind\":\"note_identification\"")
        )
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.NOTE_IDENTIFICATION.id, document.kind)
        assertEquals(NOTE_IDENTIFICATION_CONFIG_VERSION, document.configVersion)
        assertEquals(
            "the notes payload is decodable without another kind's shape",
            listOf(0, 1, 7),
            PresetDocumentCodec.notesPayload(document).pitchClassValues
        )
        assertEquals(
            "the accidental style is exported as its stable name",
            "FLAT",
            PresetDocumentCodec.notesPayload(document).accidental
        )
        assertEquals(
            "a new document carries no fixed octave",
            null,
            PresetDocumentCodec.notesPayload(document).fixedOctave
        )

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.Notes)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.notesSetup.stopPolicy
        )
    }

    @Test
    fun aNotesDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(notesPreset())
        val futureConfig = document.copy(
            configVersion = NOTE_IDENTIFICATION_CONFIG_VERSION + 1
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val olderVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(olderVersion)
        }
    }

    @Test
    fun aNotesDocumentOfAnUnknownKindIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(notesPreset())
        val foreignKind = document.copy(kind = "rhythm_tapping")
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(foreignKind)
            }.message!!.contains("rhythm_tapping")
        )
    }

    @Test
    fun aNotesDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(notesPreset())
        val badClass = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.notesPayload(base).copy(pitchClassValues = listOf(12))
            )
        )
        val classError = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(badClass)
        }
        assertTrue(
            "the failure explains the pitch class: ${classError.message}",
            classError.message!!.contains("pitch class")
        )

        val badAccidental = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.notesPayload(base).copy(accidental = "SIDEWAYS")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badAccidental)
            }.message!!.contains("SIDEWAYS")
        )
    }

    @Test
    fun aLegacyFixedOctaveFieldIsIgnoredAtImport() {
        val base = PresetDocumentCodec.toDocument(notesPreset())
        val legacy = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.notesPayload(base).copy(fixedOctave = 2)
            )
        )
        PresetDocumentCodec.validate(legacy)
        val restored = PresetDocumentCodec.toPreset(legacy)
        assertEquals(
            "the removed octave choice cannot change the restored setup",
            notesPreset().configuration,
            restored.configuration
        )
    }

    @Test
    fun aScaleDegreesPresetRoundTripsThroughItsDocument() {
        val setup = ScaleDegreeSetup.default().copy(
            keyValues = listOf(0, 9),
            tonalities = listOf(Tonality.MAJOR, Tonality.NATURAL_MINOR),
            degrees = listOf(1, 3, 5),
            context = ContextKind.TONIC_DOMINANT_TONIC,
            contextGap = Duration.parse("750ms"),
            answerFormat = DegreeAnswerFormat.NOTE_NAME,
            spelling = MusicSpellingPreference.FLAT,
            minimumNote = 57,
            maximumNote = 84,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = scaleDegreesPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue(
            "the document names the scale-degrees kind",
            text.contains("\"kind\":\"scale_degrees\"")
        )
        assertTrue(
            "the context gap is stored with an explicit unit",
            text.contains("\"context_gap_ms\":750")
        )
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.SCALE_DEGREES.id, document.kind)
        assertEquals(SCALE_DEGREES_CONFIG_VERSION, document.configVersion)
        val payload = PresetDocumentCodec.scaleDegreesPayload(document)
        assertEquals(
            "the scale-degrees payload is decodable without another kind's shape",
            listOf(0, 9),
            payload.keyValues
        )
        assertEquals(listOf("MAJOR", "NATURAL_MINOR"), payload.tonalities)
        assertEquals(listOf(1, 3, 5), payload.degrees)
        assertEquals("TONIC_DOMINANT_TONIC", payload.context)
        assertEquals(750L, payload.contextGapMs)
        assertEquals("NOTE_NAME", payload.answerFormat)
        assertEquals("FLAT", payload.spelling)

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.ScaleDegrees)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.scaleDegreesSetup.stopPolicy
        )
    }

    @Test
    fun aStoredMovableDoAnswerFormatImportsAsDegree() {
        val base = PresetDocumentCodec.toDocument(scaleDegreesPreset())
        val legacy = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scaleDegreesPayload(base).copy(answerFormat = "MOVABLE_DO")
            )
        )
        val restored = PresetDocumentCodec.toPreset(legacy)
        assertEquals(
            "a removed stored format imports as its numeric equivalent",
            DegreeAnswerFormat.DEGREE,
            restored.scaleDegreesSetup.answerFormat
        )
    }

    @Test
    fun aStoredDegreeAndSyllableAnswerFormatImportsAsDegree() {
        val base = PresetDocumentCodec.toDocument(scaleDegreesPreset())
        val legacy = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scaleDegreesPayload(base)
                    .copy(answerFormat = "DEGREE_AND_SYLLABLE")
            )
        )
        val restored = PresetDocumentCodec.toPreset(legacy)
        assertEquals(
            "a removed stored format imports as its numeric equivalent",
            DegreeAnswerFormat.DEGREE,
            restored.scaleDegreesSetup.answerFormat
        )
    }

    @Test
    fun aScaleDegreesDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(scaleDegreesPreset())
        val futureConfig = document.copy(configVersion = SCALE_DEGREES_CONFIG_VERSION + 1)
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val olderVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(olderVersion)
        }
    }

    @Test
    fun aScaleDegreesDocumentOfAnUnknownKindIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(scaleDegreesPreset())
        val foreignKind = document.copy(kind = "rhythm_tapping")
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(foreignKind)
            }.message!!.contains("rhythm_tapping")
        )
    }

    @Test
    fun aScaleDegreesDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(scaleDegreesPreset())
        val badContext = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scaleDegreesPayload(base).copy(context = "SIDEWAYS")
            )
        )
        assertTrue(
            "the failure explains the context: ${badContext.name}",
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badContext)
            }.message!!.contains("SIDEWAYS")
        )

        val badTonality = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scaleDegreesPayload(base).copy(tonalities = listOf("LYDIAN"))
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badTonality)
            }.message!!.contains("LYDIAN")
        )
    }

    @Test
    fun aScaleDegreesDocumentWhoseContextCannotFitTheRangeIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(scaleDegreesPreset())
        // A complete ascending octave cannot sound inside C4..E4, so the default target degree
        // has no eligible construction and the validator must explain the impossibility.
        val impossible = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.scaleDegreesPayload(base).copy(
                    context = "ASCENDING_SCALE",
                    degrees = listOf(1),
                    minimumNote = 105,
                    maximumNote = 108
                )
            )
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(impossible)
        }
        assertTrue(
            "the failure connects the context to the range: ${error.message}",
            error.message!!.contains("ascending scale") && error.message!!.contains("105..108")
        )
    }

    @Test
    fun aProgressionsPresetRoundTripsThroughItsDocument() {
        val setup = ProgressionSetup.default().copy(
            chordIds = listOf(
                DiatonicChord.I.id,
                DiatonicChord.IV.id,
                DiatonicChord.V7.id,
                DiatonicChord.VII7.id
            ),
            voicing = ProgressionVoicingMode.NEAREST_INVERSION,
            allowedInversions = listOf(0, 1),
            answerFormat = ProgressionAnswerFormat.ROMAN,
            spelling = MusicSpellingPreference.FLAT,
            minimumNote = 57,
            maximumNote = 84,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = progressionsPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue(
            "the document names the chord-progressions kind",
            text.contains("\"kind\":\"chord_progressions\"")
        )
        assertTrue(
            "the chord ids are stored as stable strings",
            text.contains("\"chord_ids\"")
        )
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.CHORD_PROGRESSIONS.id, document.kind)
        assertEquals(CHORD_PROGRESSIONS_CONFIG_VERSION, document.configVersion)
        val payload = PresetDocumentCodec.progressionsPayload(document)
        assertEquals(
            "the removed key selection is written empty",
            emptyList<Int>(),
            payload.keyValues
        )
        assertEquals(
            listOf(
                DiatonicChord.I.id,
                DiatonicChord.IV.id,
                DiatonicChord.V7.id,
                DiatonicChord.VII7.id
            ),
            payload.chordIds
        )
        assertEquals("NEAREST_INVERSION", payload.voicing)
        assertEquals(listOf(0, 1), payload.allowedInversions)
        assertEquals("ROMAN", payload.answerFormat)
        assertEquals("FLAT", payload.spelling)

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.Progressions)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.progressionsSetup.stopPolicy
        )
    }

    @Test
    fun aProgressionsDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(progressionsPreset())
        val futureConfig = document.copy(configVersion = CHORD_PROGRESSIONS_CONFIG_VERSION + 1)
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val olderVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(olderVersion)
        }
    }

    @Test
    fun aProgressionsDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(progressionsPreset())
        val badVoicing = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.progressionsPayload(base).copy(voicing = "SIDEWAYS")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badVoicing)
            }.message!!.contains("SIDEWAYS")
        )

        val badTemplate = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.progressionsPayload(base).copy(
                    chordIds = listOf("locrian_chord")
                )
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badTemplate)
            }.message!!.contains("locrian_chord")
        )
    }

    @Test
    fun aProgressionsDocumentWhoseSelectionCannotFitTheRangeIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(progressionsPreset())
        // A triad cannot fit a three-semitone window, so no key realizes the opening I and the
        // document must be refused with the provider's exact explanation.
        val impossible = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.progressionsPayload(base).copy(
                    chordIds = listOf(
                        DiatonicChord.I.id,
                        DiatonicChord.V.id
                    ),
                    minimumNote = 105,
                    maximumNote = 108
                )
            )
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(impossible)
        }
        assertTrue(
            "the failure connects the selection to the range: ${error.message}",
            error.message!!.contains("opening I") && error.message!!.contains("105..108")
        )
    }

    @Test
    fun aContextualIntervalsPresetRoundTripsThroughItsDocument() {
        val setup = ContextualIntervalSetup.default().copy(
            keyValues = listOf(0, 9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            intervalIds = listOf("minor_third", "perfect_fifth"),
            presentation = IntervalPresentation.DESCENDING,
            context = ContextKind.TONIC_DOMINANT_TONIC,
            contextGap = Duration.parse("750ms"),
            answerFormat = ContextualIntervalAnswerFormat.DEGREE_PAIR,
            spelling = MusicSpellingPreference.FLAT,
            minimumNote = 57,
            maximumNote = 84,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = contextualIntervalsPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue(
            "the document names the intervals-in-context kind",
            text.contains("\"kind\":\"intervals_in_context\"")
        )
        assertTrue(
            "the interval ids are stored as stable strings",
            text.contains("\"interval_ids\"")
        )
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.INTERVALS_IN_CONTEXT.id, document.kind)
        assertEquals(INTERVALS_IN_CONTEXT_CONFIG_VERSION, document.configVersion)
        val payload = PresetDocumentCodec.contextualIntervalsPayload(document)
        assertEquals(
            "the contextual-interval payload is decodable without another kind's shape",
            listOf(0, 9),
            payload.keyValues
        )
        assertEquals(listOf("NATURAL_MINOR"), payload.tonalities)
        assertEquals(listOf("minor_third", "perfect_fifth"), payload.intervalIds)
        assertEquals("DESCENDING", payload.presentation)
        assertEquals("TONIC_DOMINANT_TONIC", payload.context)
        assertEquals("DEGREE_PAIR", payload.answerFormat)
        assertEquals("FLAT", payload.spelling)

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.ContextualIntervals)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the context gap survives",
            Duration.parse("750ms"),
            restored.contextualIntervalsSetup.contextGap
        )
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.contextualIntervalsSetup.stopPolicy
        )
    }

    @Test
    fun aContextualIntervalsDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(contextualIntervalsPreset())
        val futureConfig = document.copy(configVersion = INTERVALS_IN_CONTEXT_CONFIG_VERSION + 1)
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val olderVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(olderVersion)
        }
    }

    @Test
    fun aContextualIntervalsDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(contextualIntervalsPreset())
        val badPresentation = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.contextualIntervalsPayload(base)
                    .copy(presentation = "SPIRAL")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badPresentation)
            }.message!!.contains("SPIRAL")
        )

        val badInterval = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.contextualIntervalsPayload(base)
                    .copy(intervalIds = listOf("compound_ninth"))
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badInterval)
            }.message!!.contains("compound_ninth")
        )
    }

    @Test
    fun aContextualIntervalsDocumentWhoseSelectionCannotFitTheRangeIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(contextualIntervalsPreset())
        // C4..C#4 cannot hold a descending major third; the document must be refused with the
        // provider's exact explanation of the interval, presentation and key.
        val impossible = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.contextualIntervalsPayload(base).copy(
                    intervalIds = listOf("major_third"),
                    presentation = "DESCENDING",
                    minimumNote = 105,
                    maximumNote = 108
                )
            )
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(impossible)
        }
        assertTrue(
            "the failure connects the presentation to the range: ${error.message}",
            error.message!!.contains("C major") &&
                error.message!!.contains("descending major third") &&
                error.message!!.contains("105..108")
        )
    }

    @Test
    fun anIntervalsInContextDocumentIsNeverDecodedAsAnotherKindsPayload() {
        // The kind discriminator is authoritative: an intervals payload tagged as intervals in
        // context must be refused, not silently read as the wrong mode's setup.
        val document = PresetDocumentCodec.toDocument(preset())
            .copy(kind = TrainingKind.INTERVALS_IN_CONTEXT.id)
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(document)
            }.message!!.contains("invalid payload")
        )
    }

    @Test
    fun aMelodicDictationPresetRoundTripsThroughItsDocument() {
        val setup = MelodicDictationSetup.default().copy(
            keyValues = listOf(0, 9),
            tonalities = listOf(Tonality.NATURAL_MINOR),
            context = ContextKind.TONIC_TRIAD,
            contextGap = Duration.parse("750ms"),
            minimumNote = MidiNote.of(57),
            maximumNote = MidiNote.of(84),
            phraseLength = 5,
            startDegree = 1,
            endDegree = null,
            allowedSteps = listOf(-3, 0, 2),
            maximumLeap = 3,
            bias = MelodicDirectionBias.DESCENDING,
            answerFormat = MelodicAnswerFormat.DIRECTED_INTERVAL_SEQUENCE,
            spelling = MusicSpellingPreference.FLAT,
            mode = RecipeMode.EXPOSURE,
            replayCount = 2,
            stopPolicy = SessionStopPolicy.CompletedItems(5)
        )
        val original = melodicDictationPreset(
            setup = setup,
            seed = 42L,
            musicGain = 0.5f,
            voiceGain = 0.25f
        )

        val text = PresetDocumentCodec.encode(listOf(original))
        assertTrue(
            "the document names the melodic-dictation kind",
            text.contains("\"kind\":\"melodic_dictation\"")
        )
        assertTrue(
            "the signed steps are stored as a stable list",
            text.contains("\"allowed_steps\"")
        )
        val decoded = PresetDocumentCodec.decode(text)
        assertEquals(1, decoded.size)
        val document = decoded.single() as PresetDocumentV2
        assertEquals(TrainingKind.MELODIC_DICTATION.id, document.kind)
        assertEquals(MELODIC_DICTATION_CONFIG_VERSION, document.configVersion)
        val payload = PresetDocumentCodec.melodicDictationPayload(document)
        assertEquals(
            "the melodic-dictation payload is decodable without another kind's shape",
            listOf(0, 9),
            payload.keyValues
        )
        assertEquals(listOf("NATURAL_MINOR"), payload.tonalities)
        assertEquals(5, payload.phraseLength)
        assertEquals(1, payload.startDegree)
        assertNull("an absent endpoint is the any-degree choice", payload.endDegree)
        assertEquals(listOf(-3, 0, 2), payload.allowedSteps)
        assertEquals(3, payload.maximumLeap)
        assertEquals("DESCENDING", payload.bias)
        assertEquals("DIRECTED_INTERVAL_SEQUENCE", payload.answerFormat)
        assertEquals("FLAT", payload.spelling)

        val restored = PresetDocumentCodec.toPreset(document)
        assertTrue(restored.configuration is PresetConfiguration.MelodicDictation)
        assertEquals(original.configuration, restored.configuration)
        assertEquals("a seed must survive the round trip", 42L, restored.seed)
        assertEquals(0.5f, restored.musicGain, 0f)
        assertEquals(0.25f, restored.voiceGain, 0f)
        assertEquals(
            "the context gap survives",
            Duration.parse("750ms"),
            restored.melodicDictationSetup.contextGap
        )
        assertEquals(
            "the any-degree end choice survives",
            null,
            restored.melodicDictationSetup.endDegree
        )
        assertEquals(
            "the stop policy survives",
            SessionStopPolicy.CompletedItems(5),
            restored.melodicDictationSetup.stopPolicy
        )
    }

    @Test
    fun aStoredMovableDoSequenceAnswerFormatImportsAsDegreeSequence() {
        val base = PresetDocumentCodec.toDocument(melodicDictationPreset())
        val legacy = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.melodicDictationPayload(base)
                    .copy(answerFormat = "MOVABLE_DO_SEQUENCE")
            )
        )
        val restored = PresetDocumentCodec.toPreset(legacy)
        assertEquals(
            "a removed stored format imports as its numeric equivalent",
            MelodicAnswerFormat.DEGREE_SEQUENCE,
            restored.melodicDictationSetup.answerFormat
        )
    }

    @Test
    fun aMelodicDictationDocumentWithAWrongConfigVersionIsRefusedByName() {
        val document = PresetDocumentCodec.toDocument(melodicDictationPreset())
        val futureConfig = document.copy(configVersion = MELODIC_DICTATION_CONFIG_VERSION + 1)
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(futureConfig)
        }
        assertTrue("the failure names the preset", error.message!!.contains(document.name))
        assertTrue(
            "the failure names the versions",
            error.message!!.contains("configuration version")
        )

        val olderVersion = document.copy(configVersion = 0)
        assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(olderVersion)
        }
    }

    @Test
    fun aMelodicDictationDocumentWithAnUnknownSelectionIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(melodicDictationPreset())
        val badBias = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.melodicDictationPayload(base)
                    .copy(bias = "SPIRAL")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badBias)
            }.message!!.contains("SPIRAL")
        )

        val badAnswer = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.melodicDictationPayload(base)
                    .copy(answerFormat = "CHORDS")
            )
        )
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(badAnswer)
            }.message!!.contains("CHORDS")
        )
    }

    @Test
    fun aMelodicDictationDocumentWithACycleStopPolicyIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(melodicDictationPreset())
        val cycleStop = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.melodicDictationPayload(base)
                    .copy(stopKind = "AFTER_CYCLE")
            )
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(cycleStop)
        }
        assertTrue(
            "the mode owns no label cycle, and the import says so: ${error.message}",
            error.message!!.contains("no label cycle")
        )
    }

    @Test
    fun aMelodicDictationDocumentWhoseConstraintsCannotCompleteIsRefusedAtImport() {
        val base = PresetDocumentCodec.toDocument(melodicDictationPreset())
        // Length 2, tonic to tonic, with the whole tone as the only step: no complete phrase
        // exists, and the provider's suggestion must reach the import error.
        val impossible = base.copy(
            payload = PresetDocumentCodec.encodePayload(
                PresetDocumentCodec.melodicDictationPayload(base).copy(
                    phraseLength = 2,
                    startDegree = 1,
                    endDegree = 1,
                    allowedSteps = listOf(2),
                    maximumLeap = 2
                )
            )
        )
        val error = assertThrows(PresetValidationException::class.java) {
            PresetDocumentCodec.validate(impossible)
        }
        assertTrue(
            "the failure explains the impossible phrase: ${error.message}",
            error.message!!.contains("No 2-note C major phrase") &&
                error.message!!.contains("Allow step 0")
        )
    }

    @Test
    fun aMelodicDictationDocumentIsNeverDecodedAsAnotherKindsPayload() {
        // The kind discriminator is authoritative: an intervals payload tagged as melodic
        // dictation must be refused, not silently read as the wrong mode's setup.
        val document = PresetDocumentCodec.toDocument(preset())
            .copy(kind = TrainingKind.MELODIC_DICTATION.id)
        assertTrue(
            assertThrows(PresetValidationException::class.java) {
                PresetDocumentCodec.validate(document)
            }.message!!.contains("invalid payload")
        )
    }
}
