package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.PitchClass
import com.thomas.passiveeartraining.music.SimpleInterval
import com.thomas.passiveeartraining.training.BundledVoiceDurations
import com.thomas.passiveeartraining.training.IntervalDefinition
import com.thomas.passiveeartraining.training.VoiceAssetId
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupValidatorTest {

    private val valid = SetupDraft.from(LastSetup.default())

    @Test
    fun completeDurationIsCheckedBeforeSaving() {
        val base = valid.copy(intervalIds = listOf("minor_third"))
        // The boundary reads the same measured clip the compiler uses, so a re-recorded bundled
        // take cannot silently move the cap the test asserts.
        val minorThird = BundledVoiceDurations.durationOf(VoiceAssetId("interval.minor_third"))
        val atCapThinking = Duration.parse("60s") - Duration.parse("1500ms") -
            minorThird - base.finalSilence
        assertEquals(
            SetupValidation.Valid,
            SetupValidator.validate(base.copy(thinkingSilence = atCapThinking))
        )
        assertEquals(
            SetupValidation.Valid,
            SetupValidator.validate(
                base.copy(
                    thinkingSilence =
                    atCapThinking - Duration.parse("1us")
                )
            )
        )
        val above = SetupValidator.validate(
            base.copy(
                thinkingSilence =
                atCapThinking + Duration.parse("1us")
            )
        )
        assertTrue(
            "oversized recipes must be refused before save",
            above is SetupValidation.Invalid
        )
        assertTrue((above as SetupValidation.Invalid).byField.containsKey(SetupField.DURATION))
    }

    @Test
    fun maximumRepeatsAndEveryEnabledPresentationAreMeasured() {
        val mixed = valid.copy(
            noteDuration = Duration.parse("4s"),
            replayCount = RecipeDefinition.MAX_REPLAY,
            presentations = listOf(IntervalPresentation.ASCENDING, IntervalPresentation.HARMONIC)
        )
        assertTrue(SetupValidator.validate(mixed) is SetupValidation.Invalid)
        val harmonic = mixed.copy(
            presentation = IntervalPresentation.HARMONIC,
            presentations = null
        )
        assertEquals(SetupValidation.Valid, SetupValidator.validate(harmonic))
    }

    @Test
    fun anEmptyRootSelectionAllowsAFeasibleFixedNote() {
        assertEquals(
            SetupValidation.Valid,
            SetupValidator.validate(valid.copy(rootValues = emptyList(), fixedRoot = 60))
        )
        assertTrue(
            SetupValidator.validate(
                valid.copy(rootValues = listOf(1), fixedRoot = 60)
            ) is SetupValidation.Invalid
        )
    }

    @Test
    fun theDefaultSetupIsValid() {
        assertEquals(SetupValidation.Valid, SetupValidator.validate(valid))
    }

    @Test
    fun everyIssueNamesTheFieldTheUserMustFix() {
        val broken = valid.copy(
            minimumNote = 72,
            maximumNote = 60,
            replayCount = 99
        )
        val validation = SetupValidator.validate(broken)
        assertTrue(validation is SetupValidation.Invalid)
        val byField = (validation as SetupValidation.Invalid).byField
        assertTrue("the range field is explained", byField.containsKey(SetupField.RANGE))
        assertTrue("the replay field is explained", byField.containsKey(SetupField.REPLAY))
        // Every explanation is human-readable text rather than an exception name.
        byField.values.flatten().forEach { explanation ->
            assertTrue("an explanation must be a sentence: $explanation", explanation.length > 10)
            assertTrue("an explanation must end with a full stop", explanation.endsWith("."))
        }
    }

    @Test
    fun anEmptySelectionMeansEveryOption() {
        val empty = valid.copy(intervalIds = emptyList(), rootValues = emptyList())
        assertEquals(SetupValidation.Valid, SetupValidator.validate(empty))
        val definition = empty.toSetup().toDefinition()
        assertEquals(
            SimpleInterval.ALL.map { it.id }.toSet(),
            definition.selectedIntervalIds.toSet()
        )
        assertEquals(
            PitchClass.ALL.map { it.value }.toSet(),
            definition.selectedRoots.map { it.value }.toSet()
        )
    }

    @Test
    fun anInvertedRangeIsReportedAgainstTheRangeField() {
        val broken = valid.copy(minimumNote = 72, maximumNote = 60)
        val validation = SetupValidator.validate(broken)
        assertTrue(validation is SetupValidation.Invalid)
        val byField = (validation as SetupValidation.Invalid).byField
        assertTrue(byField.containsKey(SetupField.RANGE))
    }

    @Test
    fun anIntervalThatCannotBeProducedInTheChosenDirectionIsRejected() {
        // A descending major seventh from A0 would sound below the playable samples.
        val broken = valid.copy(
            intervalIds = listOf("major_seventh"),
            presentation = IntervalPresentation.DESCENDING,
            minimumNote = 21,
            maximumNote = 21,
            rootValues = listOf(9)
        )
        val validation = SetupValidator.validate(broken)
        assertTrue("an infeasible interval must be refused", validation is SetupValidation.Invalid)
        val byField = (validation as SetupValidation.Invalid).byField
        assertTrue(byField.containsKey(SetupField.INTERVALS))
    }

    @Test
    fun allProblemsAreReportedAtOnce() {
        val broken = valid.copy(
            minimumNote = 72,
            maximumNote = 60,
            replayCount = 99,
            noteDuration = Duration.ZERO
        )
        val validation = SetupValidator.validate(broken)
        val issues = (validation as SetupValidation.Invalid).issues
        assertTrue("a user should see every problem at once, saw ${issues.size}", issues.size >= 3)
    }

    @Test
    fun anOutOfRangeReplayCountIsReportedAgainstTheReplayField() {
        val broken = valid.copy(replayCount = 99)
        val validation = SetupValidator.validate(broken)
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue((validation as SetupValidation.Invalid).byField.containsKey(SetupField.REPLAY))
    }

    @Test
    fun aDurationBeyondTheMaximumIsReportedAgainstTheDurationField() {
        val broken = valid.copy(
            stopPolicy = SessionStopPolicy.Duration(Duration.parse("200m"))
        )
        val validation = SetupValidator.validate(broken)
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue((validation as SetupValidation.Invalid).byField.containsKey(SetupField.DURATION))
    }

    @Test
    fun anAcceptableDurationIsValid() {
        listOf("1m", "30m", "180m").forEach { limit ->
            val setup = valid.copy(stopPolicy = SessionStopPolicy.Duration(Duration.parse(limit)))
            assertEquals(
                "$limit must be accepted",
                SetupValidation.Valid,
                SetupValidator.validate(setup)
            )
        }
    }

    @Test
    fun aFinishCycleDrainThatCouldOverrunIsRefusedUpFront() {
        // The overrun is reported before the session starts rather than fifteen minutes in.
        val setup = valid.copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = Duration.parse("10m"),
                finish = DurationFinish.FINISH_CYCLE
            )
        )
        val validation = SetupValidator.validate(
            setup,
            worstCaseItemDuration = Duration.parse("2m")
        )
        assertTrue(
            "an oversized cycle drain must be refused",
            validation is SetupValidation.Invalid
        )
        val byField = (validation as SetupValidation.Invalid).byField
        assertTrue(byField.containsKey(SetupField.STOP_POLICY))
    }

    @Test
    fun aFinishCycleDrainInsideTheBoundIsAccepted() {
        val setup = valid.copy(
            stopPolicy = SessionStopPolicy.Duration(
                limit = Duration.parse("10m"),
                finish = DurationFinish.FINISH_CYCLE
            )
        )
        assertEquals(
            SetupValidation.Valid,
            SetupValidator.validate(setup, worstCaseItemDuration = Duration.parse("6s"))
        )
    }

    @Test
    fun onlyFeasibleFixedRootsAreOffered() {
        // A0 cannot descend inside the playable samples, but a middle A can.
        val descending = valid.copy(
            intervalIds = listOf("minor_third"),
            presentation = IntervalPresentation.DESCENDING,
            minimumNote = 21,
            maximumNote = 72,
            rootValues = listOf(9),
            fixedRoot = 21
        )
        val options = SetupValidator.feasibleFixedRoots(descending)
        assertTrue("A0 cannot descend inside the playable range", 21 !in options)
        assertTrue("a middle A can descend", 69 in options)
        assertTrue("an empty list would mean the selection is impossible", options.isNotEmpty())
    }

    @Test
    fun theReplayCountIsValidatedAgainstTheDomainBound() {
        assertTrue(SetupValidator.validateReplayCount(RecipeDefinition.MAX_REPLAY + 1).isNotEmpty())
        assertTrue(SetupValidator.validateReplayCount(0).isEmpty())
        assertTrue(SetupValidator.validateReplayCount(RecipeDefinition.MAX_REPLAY).isEmpty())
    }

    @Test
    fun theValidatorAgreesWithTheDomainOnTheDefaultDefinition() {
        // The UI and the domain share this rule set, so a setup the validator accepts must build a
        // definition the domain accepts.
        val definition: IntervalDefinition = valid.toSetup().toDefinition()
        assertEquals(
            definition.selectedIntervalIds,
            IntervalDefinition.default().selectedIntervalIds
        )
    }

    @Test
    fun invalidTimingValuesAreReportedAgainstTheirOwnFields() {
        val cases = listOf(
            valid.copy(noteDuration = Duration.ZERO) to SetupField.NOTE_DURATION,
            valid.copy(answerGap = Duration.parse("-1ms")) to SetupField.ANSWER_GAP,
            valid.copy(repeatGap = Duration.parse("-1ms")) to SetupField.REPEAT_GAP
        )
        cases.forEach { (draft, field) ->
            val validation = SetupValidator.validate(draft)
            assertTrue("$field must be rejected", validation is SetupValidation.Invalid)
            assertTrue(
                "the issue names $field",
                (validation as SetupValidation.Invalid).byField.containsKey(field)
            )
        }
    }

    @Test
    fun timingLengthsSurviveTheDraftConversion() {
        val edited = valid.copy(
            noteDuration = Duration.parse("450ms"),
            answerGap = Duration.parse("250ms"),
            repeatGap = Duration.ZERO
        )
        assertEquals(SetupValidation.Valid, SetupValidator.validate(edited))
        val setup = edited.toSetup()
        assertEquals(Duration.parse("450ms"), setup.noteDuration)
        assertEquals(Duration.parse("250ms"), setup.answerGap)
        assertEquals(Duration.ZERO, setup.repeatGap)
        assertEquals(
            "the draft is rebuilt from the validated setup",
            setup,
            SetupDraft.from(setup).toSetup()
        )
    }

    @Test
    fun aFixedNoteMustBeInsideTheRange() {
        assertEquals(SetupValidation.Valid, SetupValidator.validate(valid.copy(fixedRoot = 60)))

        val outside = SetupValidator.validate(valid.copy(fixedRoot = 40))
        assertTrue(outside is SetupValidation.Invalid)
        assertTrue(
            (outside as SetupValidation.Invalid).byField.containsKey(SetupField.FIXED_NOTE)
        )
    }

    @Test
    fun aFixedNoteMustMatchASelectedRootClass() {
        val validation = SetupValidator.validate(
            valid.copy(fixedRoot = 61, rootValues = listOf(0))
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).byField.containsKey(SetupField.FIXED_NOTE)
        )
    }

    @Test
    fun aFixedNoteThatCannotPlayTheDirectionIsReportedAgainstTheFixedNote() {
        // A0 as the root of a descending third would sound below the playable samples.
        val validation = SetupValidator.validate(
            valid.copy(
                presentation = IntervalPresentation.DESCENDING,
                minimumNote = 21,
                maximumNote = 72,
                rootValues = listOf(9),
                fixedRoot = 21
            )
        )
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).byField.containsKey(SetupField.FIXED_NOTE)
        )
    }

    @Test
    fun clearingTheFixedNoteRestoresTheUsableDefault() {
        val cleared = valid.copy(fixedRoot = null)
        assertEquals(SetupValidation.Valid, SetupValidator.validate(cleared))
        assertEquals(null, cleared.toSetup().fixedRoot)
    }
}
