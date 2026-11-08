package com.thomas.passiveeartraining.training.settings

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalWalkSetupValidatorTest {

    @Test
    fun theDefaultSetupIsValidAndStartsTheWalkProvider() {
        val setup = IntervalWalkSetup.default()
        assertEquals(3, setup.length)
        assertEquals(4, setup.length + 1)
        val draft = IntervalWalkSetupDraft.from(setup)
        assertEquals(SetupValidation.Valid, IntervalWalkSetupValidator.validate(draft))
        val request = setup.toStartRequest(seed = 7L)
        assertEquals(TrainingKind.INTERVAL_WALK, request.setup.provider.kind)
        assertEquals(7L, request.setup.seed)
    }

    @Test
    fun aStartRangeWithNoCompletePathIsARangeIssue() {
        val draft = IntervalWalkSetupDraft.from(
            IntervalWalkSetup.default().copy(
                intervalIds = listOf("perfect_fifth"),
                minimumNote = MidiNote.of(105),
                maximumNote = MidiNote.of(108),
                fixedStart = MidiNote.of(105),
                length = 4
            )
        )
        val validation = IntervalWalkSetupValidator.validate(draft)
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any {
                it.field == SetupField.RANGE && it.explanation.contains("No complete interval walk")
            }
        )
    }

    @Test
    fun aFixedStartOutsideTheRangeIsRejected() {
        val draft = IntervalWalkSetupDraft.from(
            IntervalWalkSetup.default().copy(
                minimumNote = MidiNote.of(60),
                maximumNote = MidiNote.of(72),
                fixedStart = MidiNote.of(84)
            )
        )
        assertTrue(IntervalWalkSetupValidator.validate(draft) is SetupValidation.Invalid)
    }

    @Test
    fun afterCycleIsNotAvailableForAWalk() {
        val draft = IntervalWalkSetupDraft.from(
            IntervalWalkSetup.default().copy(stopPolicy = SessionStopPolicy.AfterCycle)
        )
        val validation = IntervalWalkSetupValidator.validate(draft)
        assertTrue(validation is SetupValidation.Invalid)
        assertTrue(
            (validation as SetupValidation.Invalid).issues.any {
                it.field == SetupField.STOP_POLICY
            }
        )
    }

    @Test
    fun mixedDirectionAndAnExplicitFixedStartAreAccepted() {
        val setup = IntervalWalkSetup.default().copy(
            direction = WalkDirection.MIXED,
            fixedStart = MidiNote.of(60),
            length = 6
        )
        assertEquals(
            SetupValidation.Valid,
            IntervalWalkSetupValidator.validate(IntervalWalkSetupDraft.from(setup))
        )
    }

    @Test
    fun aDraftRoundTripKeepsEveryField() {
        val setup = IntervalWalkSetup.default().copy(
            intervalIds = listOf("major_third", "perfect_fifth"),
            rootValues = listOf(0, 7),
            minimumNote = MidiNote.of(48),
            maximumNote = MidiNote.of(84),
            fixedStart = MidiNote.of(60),
            direction = WalkDirection.DESCENDING,
            length = 7,
            stopPolicy = SessionStopPolicy.CompletedItems(4),
            seed = 99L
        )
        assertEquals(setup, IntervalWalkSetupDraft.from(setup).toSetup())
    }
}
