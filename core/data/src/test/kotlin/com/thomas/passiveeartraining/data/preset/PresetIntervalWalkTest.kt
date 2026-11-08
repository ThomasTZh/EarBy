package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.music.MidiNote
import com.thomas.passiveeartraining.training.WalkDirection
import com.thomas.passiveeartraining.training.settings.IntervalWalkSetup
import com.thomas.passiveeartraining.training.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetIntervalWalkTest {

    private fun preset() = Preset(
        id = PresetId("test:interval-walk"),
        name = "Walk",
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
        configuration = PresetConfiguration.IntervalWalk(
            IntervalWalkSetup.default().copy(
                intervalIds = listOf("major_third", "perfect_fifth"),
                rootValues = listOf(0, 7),
                minimumNote = MidiNote.of(48),
                maximumNote = MidiNote.of(72),
                fixedStart = MidiNote.of(60),
                direction = WalkDirection.DESCENDING,
                length = 6,
                seed = 123L
            )
        ),
        seed = 123L
    )

    @Test
    fun anIntervalWalkPresetRoundTripsThroughItsDocument() {
        val original = preset()
        val document = PresetDocumentCodec.toDocument(original)
        val restored = PresetDocumentCodec.toPreset(document)
        val setup = (restored.configuration as PresetConfiguration.IntervalWalk).setup
        assertEquals("test:interval-walk", restored.id.value)
        assertEquals(original.configuration, restored.configuration)
        assertEquals(123L, restored.seed)
        assertEquals(listOf("major_third", "perfect_fifth"), setup.intervalIds)
        assertEquals(WalkDirection.DESCENDING, setup.direction)
        assertEquals(6, setup.length)
        assertEquals(MidiNote.of(60), setup.fixedStart)
    }

    @Test
    fun thePayloadVersionIsTheFrozenWalkVersion() {
        assertEquals(
            com.thomas.passiveeartraining.training.settings.INTERVAL_WALK_CONFIG_VERSION,
            PresetDocumentCodec.toDocument(preset()).configVersion
        )
    }
}
