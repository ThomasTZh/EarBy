package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.music.IntervalPresentation
import com.thomas.passiveeartraining.music.Tonality
import com.thomas.passiveeartraining.training.provider.TrainingKind
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.ChordSetup
import com.thomas.passiveeartraining.training.settings.ContextualIntervalSetup
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.NoteSetup
import com.thomas.passiveeartraining.training.settings.ProgressionSetup
import com.thomas.passiveeartraining.training.settings.ScaleDegreeSetup
import com.thomas.passiveeartraining.training.settings.ScaleSetup
import kotlin.time.Duration
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** One in-memory store with a controllable clock and identity sequence. */
internal class PresetStoreFixture : AutoCloseable {
    val database: JdbcPresetDatabase = JdbcPresetDatabase.inMemory()
    var now: Long = 1_700_000_000_000L
    private var idCounter = 0

    fun repository(newId: (() -> PresetId)? = null): SqlitePresetRepository =
        SqlitePresetRepository(
            database = database,
            ioDispatcher = UnconfinedTestDispatcher(),
            nowMs = { now },
            newId = newId ?: { PresetId("test-${++idCounter}") }
        )

    override fun close() {
        database.close()
    }
}

/** Fails a mutation's first transaction on the configured execute count. */
internal class FailingDatabase(
    private val delegate: JdbcPresetDatabase,
    var executeCount: Int = 0,
    var failOnExecute: Int = Int.MAX_VALUE
) : PresetDatabase by delegate {
    override fun execute(sql: String, bind: List<Any?>) {
        executeCount += 1
        if (executeCount == failOnExecute) {
            throw IllegalStateException("Simulated database failure")
        }
        delegate.execute(sql, bind)
    }
}

/** Expects [block] to fail with [T], in a coroutine. */
internal suspend inline fun <reified T : Throwable> fails(block: () -> Unit): T {
    try {
        block()
    } catch (failure: Throwable) {
        if (failure is T) return failure
        throw failure
    }
    throw AssertionError("Expected a ${T::class.java.simpleName} but nothing was thrown")
}

/** The intervals configuration a repository test saves. */
private fun intervals(setup: LastSetup): PresetConfiguration = PresetConfiguration.Intervals(setup)

/** The intervals setup of a preset under test. */
private val Preset.intervalSetup: LastSetup
    get() = (configuration as PresetConfiguration.Intervals).setup

/** The chords setup of a preset under test. */
private val Preset.chordsSetup: ChordSetup
    get() = (configuration as PresetConfiguration.Chords).setup

/** The scales setup of a preset under test. */
private val Preset.scalesSetup: ScaleSetup
    get() = (configuration as PresetConfiguration.Scales).setup

/** The notes setup of a preset under test. */
private val Preset.notesSetup: NoteSetup
    get() = (configuration as PresetConfiguration.Notes).setup

/** The scale-degrees setup of a preset under test. */
private val Preset.scaleDegreesSetup: ScaleDegreeSetup
    get() = (configuration as PresetConfiguration.ScaleDegrees).setup

/** The chord-progressions setup of a preset under test. */
private val Preset.progressionsSetup: ProgressionSetup
    get() = (configuration as PresetConfiguration.Progressions).setup

/** The intervals-in-context setup of a preset under test. */
private val Preset.contextualIntervalsSetup: ContextualIntervalSetup
    get() = (configuration as PresetConfiguration.ContextualIntervals).setup

/** The melodic-dictation setup of a preset under test. */
private val Preset.melodicDictationSetup: MelodicDictationSetup
    get() = (configuration as PresetConfiguration.MelodicDictation).setup

class SqlitePresetRepositoryTest {

    @Test
    fun failedLegacyAdoptionKeepsItsRetryMarkerAndEverySlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val existing = fixture.repository()
            val legacy = existing.create("Legacy", intervals(LastSetup.default()))
            fixture.database.execute(
                "INSERT INTO preset_meta (key, value) VALUES (?, ?)",
                listOf(PresetSchema.ADOPTION_KEY, "1")
            )
            val failing = object : PresetDatabase by fixture.database {
                override fun executeUpdate(sql: String, bind: List<Any?>): Int {
                    if (sql.startsWith("UPDATE preset_payload")) error("adoption write failed")
                    return fixture.database.executeUpdate(sql, bind)
                }
            }
            val repository =
                SqlitePresetRepository(
                    database = failing,
                    ioDispatcher = UnconfinedTestDispatcher()
                )
            fails<IllegalStateException> { repository.refresh() }
            assertTrue(
                "marker survives failure",
                fixture.database.query(
                    "SELECT * FROM preset_meta WHERE key = ?",
                    listOf(PresetSchema.ADOPTION_KEY)
                ).isNotEmpty()
            )
            val recovered = fixture.repository()
            recovered.refresh()
            assertTrue(recovered.presets.value.none { it.id == legacy.id })
            assertEquals(1, recovered.presets.value.count { it.id in PresetSlots.IDS && !it.empty })
        }
    }

    @Test
    fun startersAreSeededOnFirstUseAndEveryStarterIsBuiltIn() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val stored = repository.presets.value
            val starters = stored.filter { it.builtIn }
            assertEquals(PresetStarters.ALL.size, starters.size)
            assertEquals(
                PresetStarters.ALL.map { it.id }.toSet(),
                starters.map { it.id }.toSet()
            )
            val slots = stored.filterNot { it.builtIn }
            assertEquals(PresetSlots.IDS.toSet(), slots.map { it.id }.toSet())
            assertTrue("a fresh store's slots are empty", slots.all { it.empty })
        }
    }

    @Test
    fun upgradingFromV10ReplacesTheOldIntervalStartersAndKeepsUserPresets() = runTest {
        PresetStoreFixture().use { fixture ->
            PresetSchema.migrationsToUpgrade(0, 10).forEach { migration ->
                when (migration) {
                    is PresetMigration.Statements ->
                        migration.statements.forEach(fixture.database::execute)
                    is PresetMigration.Transform -> migration.transform(fixture.database)
                }
            }
            fixture.database.setUserVersion(10)
            // One old built-in interval row and one user preset beside the five empty slots.
            fixture.database.execute(
                "INSERT INTO preset_payload (" +
                    "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                    "created_at_epoch_ms, updated_at_epoch_ms" +
                    ") VALUES (?, 'intervals', NULL, ?, ?, 1, 0, 1, 0, 0)",
                listOf(
                    "old-builtin",
                    "Minor and major thirds",
                    PresetDocumentCodec.payloadJson(PresetStarters.MAJOR_TRIAD)
                )
            )
            fixture.database.execute(
                "INSERT INTO preset_payload (" +
                    "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                    "created_at_epoch_ms, updated_at_epoch_ms" +
                    ") VALUES (?, 'intervals', NULL, ?, ?, 1, 0, 0, 0, 0)",
                listOf(
                    "keep-me",
                    "Keep me",
                    PresetDocumentCodec.payloadJson(PresetStarters.MINOR_TRIAD)
                )
            )

            val repository = fixture.repository()
            repository.refresh()
            val stored = repository.presets.value
            assertTrue(
                "the old built-in is gone",
                stored.none { it.id.value == "old-builtin" }
            )
            assertEquals(
                "the four new interval starters are seeded",
                PresetStarters.ALL.map { it.id }.toSet(),
                stored.filter { it.builtIn }.map { it.id }.toSet()
            )
            assertEquals(
                "a user preset survives the starter replacement",
                "Keep me",
                stored.single { it.id.value == "keep-me" }.name
            )
            assertEquals(
                "the five user slots remain",
                5,
                stored.count { it.id in PresetSlots.IDS }
            )
        }
    }

    @Test
    fun seedingIsIdempotentAndSurvivesASecondRepository() = runTest {
        PresetStoreFixture().use { fixture ->
            val first = fixture.repository()
            first.refresh()
            val second = fixture.repository()
            second.refresh()

            assertEquals(first.presets.value, second.presets.value)
            assertEquals(
                PresetStarters.ALL.size + PresetSlots.IDS.size,
                second.presets.value.size
            )
        }
    }

    @Test
    fun creatingAPresetStoresItWithAFreshIdentityAndATrimmedName() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val created = repository.create(
                name = "  My preset  ",
                configuration = intervals(LastSetup.default())
            )

            assertFalse(created.builtIn)
            assertEquals("My preset", created.name)
            assertEquals(
                "the created preset is readable back",
                created,
                repository.presets.value.first { it.id == created.id }
            )
        }
    }

    @Test
    fun twoCreatesNeverShareAnIdentity() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val first = repository.create("First", intervals(LastSetup.default()))
            val second = repository.create("Second", intervals(LastSetup.default()))
            assertNotEquals(first.id, second.id)
        }
    }

    @Test
    fun orderIsDeterministicWithStartersFirst() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.create("Zeta", intervals(LastSetup.default()))
            repository.create("Alpha", intervals(LastSetup.default()))
            val names = repository.presets.value.map { it.builtIn to it.name }
            val builtInCount = PresetStarters.ALL.size
            assertTrue(
                "every starter precedes the first user preset",
                names.take(builtInCount).all { it.first }
            )
            assertEquals(
                "user presets are ordered by name",
                listOf("Alpha", "Zeta"),
                repository.presets.value
                    .filterNot { it.builtIn || it.id in PresetSlots.IDS }
                    .map { it.name }
            )
            val again = fixture.repository()
            again.refresh()
            assertEquals(repository.presets.value, again.presets.value)
        }
    }

    @Test
    fun updateKeepsIdentityAndCreationTimeAndRecordsTheChangeTime() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val created = repository.create("Original", intervals(LastSetup.default()))
            fixture.now += 5_000
            val updated = repository.update(
                created.copy(
                    name = "Changed",
                    configuration = intervals(
                        created.intervalSetup.copy(mode = RecipeMode.EXPOSURE)
                    ),
                    seed = 7L
                )
            )

            assertEquals(created.id, updated.id)
            assertEquals(created.createdAtEpochMs, updated.createdAtEpochMs)
            assertEquals(fixture.now, updated.updatedAtEpochMs)
            assertEquals(RecipeMode.EXPOSURE, updated.intervalSetup.mode)
            assertEquals(
                updated,
                repository.presets.value.first { it.id == created.id }
            )
        }
    }

    @Test
    fun renameTrimsTheNameAndRefusesABlankOrOverlongOne() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val created = repository.create("Original", intervals(LastSetup.default()))

            val renamed = repository.rename(created.id, "  Renamed  ")
            assertEquals("Renamed", renamed.name)

            fails<PresetValidationException> { repository.rename(created.id, "   ") }
            fails<PresetValidationException> {
                repository.rename(created.id, "x".repeat(PresetDocumentCodec.MAX_NAME_LENGTH + 1))
            }
        }
    }

    @Test
    fun deletingRemovesThePresetAndItsStoredLists() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val created = repository.create("Doomed", intervals(LastSetup.default()))

            repository.delete(created.id)

            assertTrue(repository.presets.value.none { it.id == created.id })
            assertEquals(
                "the stored payload row is gone with the preset",
                0,
                fixture.database
                    .query(
                        "SELECT COUNT(*) AS count FROM preset_payload WHERE id = ?",
                        listOf(created.id.value)
                    )
                    .single()
                    .intColumn("count")
            )
        }
    }

    @Test
    fun theStartersCannotBeEditedOrDeletedAndAreNotCountedAgainstTheUserCap() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val starter = repository.presets.value.first { it.builtIn }

            fails<ImmutablePresetException> { repository.rename(starter.id, "hijacked") }
            fails<ImmutablePresetException> { repository.delete(starter.id) }
            fails<ImmutablePresetException> {
                repository.update(starter.copy(name = "hijacked"))
            }
            assertEquals(
                "the starter is unchanged",
                starter,
                repository.presets.value.first { it.id == starter.id }
            )
        }
    }

    @Test
    fun duplicatingAStarterMakesAnEditableCopyWithAStableName() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val starter = repository.presets.value.first { it.builtIn }

            val copy = repository.duplicate(starter.id)

            assertEquals("${starter.name} copy", copy.name)
            assertFalse(copy.builtIn)
            assertEquals(starter.configuration, copy.configuration)
            assertEquals(starter.seed, copy.seed)
            assertTrue(repository.presets.value.any { it.id == copy.id && !it.builtIn })
        }
    }

    @Test
    fun duplicatingWithAnExplicitNameUsesItAndStaysInsideTheNameBound() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val created = repository.create(
                "A".repeat(PresetDocumentCodec.MAX_NAME_LENGTH),
                intervals(LastSetup.default())
            )

            val defaultCopy = repository.duplicate(created.id)
            assertEquals(
                PresetDocumentCodec.MAX_NAME_LENGTH,
                defaultCopy.name.length
            )
            assertTrue("a copy name stays inside the bound", defaultCopy.name.endsWith(" copy"))

            val named = repository.duplicate(created.id, name = "Explicit")
            assertEquals("Explicit", named.name)
        }
    }

    @Test
    fun seedsGainsAndEveryStopPolicyRoundTripThroughTheStore() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val policies = listOf(
                SessionStopPolicy.UntilStopped,
                SessionStopPolicy.AfterCycle,
                SessionStopPolicy.Duration(Duration.parse("90m"), DurationFinish.FINISH_CYCLE),
                SessionStopPolicy.CompletedItems(SessionStopPolicy.MAX_ITEMS)
            )
            policies.forEachIndexed { index, policy ->
                val setup = LastSetup.default().copy(
                    stopPolicy = policy,
                    noteDuration = Duration.parse("450ms"),
                    answerGap = Duration.parse("250ms"),
                    repeatGap = Duration.ZERO,
                    fixedRoot = 60
                )
                val created = repository.create(
                    name = "policy-$index",
                    configuration = intervals(setup),
                    seed = 42L,
                    musicGain = 0.5f,
                    voiceGain = 0.125f
                )
                val stored = repository.presets.value.first { it.id == created.id }
                assertEquals("stop policy $policy survives", intervals(setup), stored.configuration)
                assertEquals(42L, stored.seed)
                assertEquals(0.5f, stored.musicGain, 0f)
                assertEquals(0.125f, stored.voiceGain, 0f)
            }
        }
    }

    @Test
    fun aSetupThatCannotProduceOneOfItsIntervalsIsRefused() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            // A descending major seventh from A0 leaves the playable samples.
            val infeasible = LastSetup.default().copy(
                intervalIds = listOf("major_seventh"),
                rootValues = listOf(9),
                minimumNote = 21,
                maximumNote = 21,
                presentation = IntervalPresentation.DESCENDING
            )
            fails<PresetValidationException> {
                repository.create("Infeasible", intervals(infeasible))
            }
            assertEquals(
                "nothing was stored",
                PresetStarters.ALL.size + PresetSlots.IDS.size,
                repository.presets.value.size
            )
        }
    }

    @Test
    fun theEditableCapIsEnforcedAndDeletingMakesRoomAgain() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            // The five slots fill part of the editable capacity, exactly as they do on a device.
            val room = SqlitePresetRepository.MAX_EDITABLE_PRESETS - PresetSlots.IDS.size
            val created = (1..room).map { index ->
                repository.create("preset-$index", intervals(LastSetup.default()))
            }
            fails<PresetLimitException> {
                repository.create("one-too-many", intervals(LastSetup.default()))
            }
            assertEquals(
                SqlitePresetRepository.MAX_EDITABLE_PRESETS,
                repository.presets.value.count { !it.builtIn }
            )

            repository.delete(created.first().id)
            val replacement = repository.create("replacement", intervals(LastSetup.default()))
            assertTrue(repository.presets.value.any { it.id == replacement.id })
        }
    }

    @Test
    fun missingPresetsAreReportedRatherThanIgnored() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val missing = PresetId("does-not-exist")
            fails<PresetNotFoundException> { repository.rename(missing, "name") }
            fails<PresetNotFoundException> { repository.delete(missing) }
            fails<PresetNotFoundException> { repository.duplicate(missing) }
            fails<PresetNotFoundException> { repository.export(listOf(missing)) }
        }
    }

    @Test
    fun aSecondRepositoryOverTheSameDatabaseSeesTheWrites() = runTest {
        PresetStoreFixture().use { fixture ->
            val writer = fixture.repository()
            val created = writer.create("Written", intervals(LastSetup.default()))

            val reader = fixture.repository()
            reader.refresh()
            assertEquals(
                created,
                reader.presets.value.first { it.id == created.id }
            )
        }
    }

    @Test
    fun aFailedWriteInsideATransactionLeavesNothingBehind() = runTest {
        PresetStoreFixture().use { fixture ->
            val failing = FailingDatabase(fixture.database)
            val repository = SqlitePresetRepository(
                database = failing,
                ioDispatcher = UnconfinedTestDispatcher(),
                nowMs = { fixture.now },
                newId = { PresetId("failing-write") }
            )
            repository.refresh()
            val baseline = repository.presets.value
            // The payload insert itself is made to fail; the transaction must roll it back.
            failing.failOnExecute = failing.executeCount + 1

            fails<IllegalStateException> {
                repository.create("Half-written", intervals(LastSetup.default()))
            }

            assertEquals(
                "the rolled-back preset must not exist",
                baseline,
                repository.presets.value
            )
            assertTrue(
                "the rolled-back row must not exist",
                failing.query(
                    "SELECT * FROM preset_payload WHERE id = ?",
                    listOf("failing-write")
                ).isEmpty()
            )
        }
    }

    @Test
    fun theFiveSlotsStartEmptyWithFixedNames() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val slots = repository.presets.value.filter { it.id in PresetSlots.IDS }
            assertEquals(5, slots.size)
            assertTrue("every fresh slot is empty", slots.all { it.empty })
            assertEquals(
                (1..5).map { PresetSlots.defaultName(it) }.toSet(),
                slots.map { it.name }.toSet()
            )
        }
    }

    @Test
    fun aSlotCanBeFilledAndRenamedButNeverDeleted() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val slot = repository.presets.value.first { it.id in PresetSlots.IDS }
            assertTrue("the slot starts empty", slot.empty)

            val filled = repository.update(
                slot.copy(
                    name = "Scales",
                    configuration = intervals(LastSetup.default().copy(replayCount = 3))
                )
            )

            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals("Scales", filled.name)
            assertEquals(3, filled.intervalSetup.replayCount)

            val renamed = repository.rename(slot.id, "Warm-ups")
            assertEquals("Warm-ups", renamed.name)

            fails<ImmutablePresetException> { repository.delete(slot.id) }
            assertEquals(5, repository.presets.value.count { it.id in PresetSlots.IDS })
        }
    }

    @Test
    fun aSlotAcceptsASetupWithNoIntervalsOrRootsSelected() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val slot = repository.presets.value.first { it.id in PresetSlots.IDS }

            // None selected is the "all options included" state, so it must be storable.
            val allIncluded = LastSetup.default()
                .copy(intervalIds = emptyList(), rootValues = emptyList())
            val filled = repository.update(
                slot.copy(configuration = intervals(allIncluded))
            )

            assertEquals(intervals(allIncluded), filled.configuration)
            assertFalse("saving fills the slot", filled.empty)
        }
    }

    @Test
    fun aLegacyCustomIsAdoptedIntoTheFirstEmptySlotOnUpgrade() = runTest {
        PresetStoreFixture().use { fixture ->
            // Build a schema-6 store with one user-created preset: the state an installed app has
            // before the slot upgrade.
            PresetSchema.statementsToUpgrade(0, 6).forEach(fixture.database::execute)
            fixture.database.setUserVersion(6)
            fixture.database.execute(
                "INSERT INTO preset_entity (" +
                    "id, name, created_at_epoch_ms, updated_at_epoch_ms, built_in, " +
                    "minimum_note, maximum_note, presentation, mode, thinking_silence_ms, " +
                    "replay_count, music_gain, voice_gain, stop_kind, note_duration_ms, " +
                    "answer_gap_ms, repeat_gap_ms, melodic_gap_ms, final_silence_ms" +
                    ") VALUES ('legacy-1', 'My practice set', 123, 456, 0, 60, 72, " +
                    "'ASCENDING', 'RECOGNITION', 2000, 0, 1.0, 0.25, 'UNTIL_STOPPED', 700, " +
                    "600, 400, 100, 2000)"
            )
            fixture.database.execute(
                "INSERT INTO preset_interval (preset_id, position, interval_id) " +
                    "VALUES ('legacy-1', 0, 'minor_third')"
            )

            val repository = fixture.repository()
            repository.refresh()

            val slots = repository.presets.value.filter { it.id in PresetSlots.IDS }
            assertEquals(5, slots.size)
            val adopted = slots.single { !it.empty }
            assertEquals("My practice set", adopted.name)
            assertEquals(listOf("minor_third"), adopted.intervalSetup.intervalIds)
            assertEquals(123L, adopted.createdAtEpochMs)
            assertTrue(
                "the legacy row is gone",
                repository.presets.value.none { it.id.value == "legacy-1" }
            )
            assertEquals("the other four slots stay empty", 4, slots.count { it.empty })
        }
    }

    @Test
    fun aFreshStoreOwnsFortyUniqueSlotsAndKindScopedReadsStayDisjoint() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val slotRows = fixture.database.query(
                "SELECT kind, slot_number FROM preset_payload WHERE slot_number IS NOT NULL"
            )
            assertEquals("five slots for each of the nine kinds", 45, slotRows.size)
            TrainingKind.ALL.forEach { kind ->
                assertEquals(
                    "kind ${kind.id} owns slots 1..5 exactly",
                    listOf(1, 2, 3, 4, 5),
                    slotRows.filter { it.stringColumn("kind") == kind.id }
                        .map { it.intColumn("slot_number") }
                        .sorted()
                )
            }

            val chordSlots = repository.slotsOf(TrainingKind.CHORDS)
            assertEquals(5, chordSlots.size)
            assertTrue("another mode's slots start empty", chordSlots.all { it.empty })
            assertEquals(
                "the two kinds never share a slot identity",
                emptySet<PresetId>(),
                repository.slotsOf(TrainingKind.INTERVALS).map { it.id }.toSet() intersect
                    chordSlots.map { it.id }.toSet()
            )

            // A row of a kind this build has no codec for stays invisible and untouchable, so a
            // later milestone's data can never be mistaken for this build's.
            fixture.database.execute(
                "INSERT INTO preset_payload (" +
                    "id, kind, slot_number, name, payload, payload_version, is_empty, built_in, " +
                    "created_at_epoch_ms, updated_at_epoch_ms" +
                    ") VALUES ('future-1', 'rhythm_tapping', NULL, 'Future', NULL, NULL, " +
                    "1, 0, 0, 0)"
            )
            val foreign = PresetId("future-1")
            fails<PresetNotFoundException> { repository.rename(foreign, "Hijacked") }
            fails<PresetNotFoundException> { repository.clear(foreign) }
            fails<PresetNotFoundException> { repository.delete(foreign) }
        }
    }

    @Test
    fun chordsSlotsAreIsolatedFromIntervalsAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val intervalSlots = repository.slotsOf(TrainingKind.INTERVALS)
            val chordSlots = repository.slotsOf(TrainingKind.CHORDS)
            assertEquals(PresetSlots.IDS, intervalSlots.map { it.id })
            assertEquals(PresetSlots.slotIds(TrainingKind.CHORDS), chordSlots.map { it.id })

            // Fill one chords slot: the intervals library never sees it.
            val filled = repository.update(
                chordSlots.first().copy(
                    name = "Closed triads",
                    configuration = PresetConfiguration.Chords(
                        ChordSetup.default().copy(replayCount = 2)
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(2, filled.chordsSetup.replayCount)
            assertEquals(
                "the chords slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.CHORDS).count { !it.empty }
            )
            assertTrue(
                "the intervals list is untouched",
                repository.presets.value.filter { it.id in PresetSlots.IDS }.all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals("a cleared slot keeps its name", "Closed triads", cleared.name)
            assertEquals(
                "a cleared slot is its kind's default again",
                ChordSetup.default(),
                cleared.chordsSetup
            )
            assertTrue(
                "every chords slot is empty again",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertEquals(
                "the intervals starters are untouched",
                PresetStarters.ALL.size,
                repository.presets.value.count { it.builtIn }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.presets.value.filter { it.id in PresetSlots.IDS }.all { it.empty }
            )
        }
    }

    @Test
    fun scalesSlotsAreIsolatedFromIntervalsAndChordsAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val intervalSlots = repository.slotsOf(TrainingKind.INTERVALS)
            val chordSlots = repository.slotsOf(TrainingKind.CHORDS)
            val scaleSlots = repository.slotsOf(TrainingKind.SCALES)
            assertEquals(PresetSlots.IDS, intervalSlots.map { it.id })
            assertEquals(PresetSlots.slotIds(TrainingKind.CHORDS), chordSlots.map { it.id })
            assertEquals(PresetSlots.slotIds(TrainingKind.SCALES), scaleSlots.map { it.id })
            assertTrue("a fresh store's scales slots are empty", scaleSlots.all { it.empty })

            // Fill one scales slot: neither the intervals library nor a chords slot sees it.
            val filled = repository.update(
                scaleSlots.first().copy(
                    name = "Modes",
                    configuration = PresetConfiguration.Scales(
                        ScaleSetup.default().copy(replayCount = 3)
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(3, filled.scalesSetup.replayCount)
            assertEquals(
                "the scales slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.SCALES).count { !it.empty }
            )
            assertTrue(
                "the chords slots are untouched",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS).all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals("a cleared slot keeps its name", "Modes", cleared.name)
            assertEquals(
                "a cleared slot is its kind's default again",
                ScaleSetup.default(),
                cleared.scalesSetup
            )
            assertTrue(
                "every scales slot is empty again",
                repository.slotsOf(TrainingKind.SCALES).all { it.empty }
            )
        }
    }

    @Test
    fun theScalesStarterSeedsOnceStoresItsPayloadAndStaysImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1 AND kind = ?",
                listOf(TrainingKind.SCALES.id)
            )
            assertEquals("the scales starter seeds exactly once", 1, starterRows.size)
            val scalesStarter = starterRows.single()
            assertEquals(PresetStarters.SCALES.id.value, scalesStarter.stringColumn("id"))
            assertEquals(1L, scalesStarter.longColumn("payload_version"))
            assertTrue(
                "the scales starter stores its scale selection",
                scalesStarter.stringColumn("payload").contains("scale_ids")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.SCALES.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> { repository.clear(PresetStarters.SCALES.id) }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.SCALES.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.SCALES.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun notesSlotsAreIsolatedFromScalesChordsAndIntervalsAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val noteSlots = repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION)
            assertEquals(
                PresetSlots.slotIds(TrainingKind.NOTE_IDENTIFICATION),
                noteSlots.map { it.id }
            )
            assertTrue("a fresh store's note slots are empty", noteSlots.all { it.empty })

            // Fill one note slot: no other kind's slot space sees it.
            val filled = repository.update(
                noteSlots.first().copy(
                    name = "Three notes",
                    configuration = PresetConfiguration.Notes(
                        NoteSetup.default().copy(
                            pitchClassValues = listOf(0, 4, 7),
                            speakOctave = true,
                            replayCount = 3
                        )
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(3, filled.notesSetup.replayCount)
            assertEquals(listOf(0, 4, 7), filled.notesSetup.pitchClassValues)
            assertEquals(
                "the note slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION).count { !it.empty }
            )
            assertTrue(
                "the scales slots are untouched",
                repository.slotsOf(TrainingKind.SCALES).all { it.empty }
            )
            assertTrue(
                "the chords slots are untouched",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS).all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals("a cleared slot keeps its name", "Three notes", cleared.name)
            assertEquals(
                "a cleared slot is its kind's default again",
                NoteSetup.default(),
                cleared.notesSetup
            )
            assertTrue(
                "every note slot is empty again",
                repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION).all { it.empty }
            )
        }
    }

    @Test
    fun theNotesStarterSeedsOnceStoresItsPayloadAndStaysImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1 AND kind = ?",
                listOf(TrainingKind.NOTE_IDENTIFICATION.id)
            )
            assertEquals("the notes starter seeds exactly once", 1, starterRows.size)
            val notesStarter = starterRows.single()
            assertEquals(PresetStarters.NOTES.id.value, notesStarter.stringColumn("id"))
            assertEquals(1L, notesStarter.longColumn("payload_version"))
            assertTrue(
                "the notes starter stores its class selection",
                notesStarter.stringColumn("payload").contains("pitch_class_values")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.NOTES.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> { repository.clear(PresetStarters.NOTES.id) }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.NOTES.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.NOTES.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun scaleDegreesSlotsAreIsolatedFromEveryOtherKindAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val scaleDegreeSlots = repository.slotsOf(TrainingKind.SCALE_DEGREES)
            assertEquals(
                PresetSlots.slotIds(TrainingKind.SCALE_DEGREES),
                scaleDegreeSlots.map { it.id }
            )
            assertTrue(
                "a fresh store's scale-degree slots are empty",
                scaleDegreeSlots.all { it.empty }
            )

            // Fill one slot: no other kind's slot space sees it. C and E major with the tonic
            // triad fit the default C4..C5 range; the edited gap and repeats must survive.
            val filled = repository.update(
                scaleDegreeSlots.first().copy(
                    name = "C and E major degrees",
                    configuration = PresetConfiguration.ScaleDegrees(
                        ScaleDegreeSetup.default().copy(
                            keyValues = listOf(0, 4),
                            contextGap = Duration.parse("800ms"),
                            replayCount = 3
                        )
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(3, filled.scaleDegreesSetup.replayCount)
            assertEquals(listOf(0, 4), filled.scaleDegreesSetup.keyValues)
            assertEquals(Duration.parse("800ms"), filled.scaleDegreesSetup.contextGap)
            assertEquals(
                "the scale-degree slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.SCALE_DEGREES).count { !it.empty }
            )
            assertTrue(
                "the notes slots are untouched",
                repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION).all { it.empty }
            )
            assertTrue(
                "the scales slots are untouched",
                repository.slotsOf(TrainingKind.SCALES).all { it.empty }
            )
            assertTrue(
                "the chords slots are untouched",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS).all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals("a cleared slot keeps its name", "C and E major degrees", cleared.name)
            assertEquals(
                "a cleared slot is its kind's default again",
                ScaleDegreeSetup.default(),
                cleared.scaleDegreesSetup
            )
            assertTrue(
                "every scale-degree slot is empty again",
                repository.slotsOf(TrainingKind.SCALE_DEGREES).all { it.empty }
            )
        }
    }

    @Test
    fun theScaleDegreesStarterSeedsOnceStoresItsPayloadAndStaysImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1 AND kind = ?",
                listOf(TrainingKind.SCALE_DEGREES.id)
            )
            assertEquals("the scale-degrees starter seeds exactly once", 1, starterRows.size)
            val starter = starterRows.single()
            assertEquals(PresetStarters.SCALE_DEGREES.id.value, starter.stringColumn("id"))
            assertEquals(1L, starter.longColumn("payload_version"))
            assertTrue(
                "the starter stores its key and context selection",
                starter.stringColumn("payload").contains("key_values") &&
                    starter.stringColumn("payload").contains("context_gap_ms")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.SCALE_DEGREES.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> { repository.clear(PresetStarters.SCALE_DEGREES.id) }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.SCALE_DEGREES.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.SCALE_DEGREES.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun progressionsSlotsAreIsolatedFromEveryOtherKindAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val progressionSlots = repository.slotsOf(TrainingKind.CHORD_PROGRESSIONS)
            assertEquals(
                PresetSlots.slotIds(TrainingKind.CHORD_PROGRESSIONS),
                progressionSlots.map { it.id }
            )
            assertTrue(
                "a fresh store's progression slots are empty",
                progressionSlots.all { it.empty }
            )

            // Fill one slot: no other kind's slot space sees it. C and A major both realize the
            // three major templates in the default C3..C6 range; the edited edits must survive.
            val filled = repository.update(
                progressionSlots.first().copy(
                    name = "C and A major progressions",
                    configuration = PresetConfiguration.Progressions(
                        ProgressionSetup.default().copy(
                            replayCount = 3,
                            noteDuration = Duration.parse("800ms")
                        )
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(3, filled.progressionsSetup.replayCount)
            assertEquals(Duration.parse("800ms"), filled.progressionsSetup.noteDuration)
            assertEquals(
                "the progression slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.CHORD_PROGRESSIONS).count { !it.empty }
            )
            assertTrue(
                "the scale-degree slots are untouched",
                repository.slotsOf(TrainingKind.SCALE_DEGREES).all { it.empty }
            )
            assertTrue(
                "the notes slots are untouched",
                repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION).all { it.empty }
            )
            assertTrue(
                "the scales slots are untouched",
                repository.slotsOf(TrainingKind.SCALES).all { it.empty }
            )
            assertTrue(
                "the chords slots are untouched",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS).all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals(
                "a cleared slot keeps its name",
                "C and A major progressions",
                cleared.name
            )
            assertEquals(
                "a cleared slot is its kind's default again",
                ProgressionSetup.default(),
                cleared.progressionsSetup
            )
            assertTrue(
                "every progression slot is empty again",
                repository.slotsOf(TrainingKind.CHORD_PROGRESSIONS).all { it.empty }
            )
        }
    }

    @Test
    fun theProgressionsStarterSeedsOnceStoresItsPayloadAndStaysImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1 AND kind = ?",
                listOf(TrainingKind.CHORD_PROGRESSIONS.id)
            )
            assertEquals("the progressions starter seeds exactly once", 1, starterRows.size)
            val starter = starterRows.single()
            assertEquals(PresetStarters.PROGRESSIONS.id.value, starter.stringColumn("id"))
            assertEquals(1L, starter.longColumn("payload_version"))
            assertTrue(
                "the starter stores its template and voicing selection",
                starter.stringColumn("payload").contains("template_ids") &&
                    starter.stringColumn("payload").contains("allowed_inversions")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.PROGRESSIONS.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> { repository.clear(PresetStarters.PROGRESSIONS.id) }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.PROGRESSIONS.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.PROGRESSIONS.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun contextualIntervalsSlotsAreIsolatedFromEveryOtherKindAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val contextualSlots = repository.slotsOf(TrainingKind.INTERVALS_IN_CONTEXT)
            assertEquals(
                PresetSlots.slotIds(TrainingKind.INTERVALS_IN_CONTEXT),
                contextualSlots.map { it.id }
            )
            assertTrue(
                "a fresh store's intervals-in-context slots are empty",
                contextualSlots.all { it.empty }
            )

            // Fill one slot: no other kind's slot space sees it. C and D major both realize the
            // three default intervals over the tonic triad in the default C4..C5 range; the edits
            // must survive.
            val filled = repository.update(
                contextualSlots.first().copy(
                    name = "C and D major contextual intervals",
                    configuration = PresetConfiguration.ContextualIntervals(
                        ContextualIntervalSetup.default().copy(
                            keyValues = listOf(0, 2),
                            replayCount = 3,
                            noteDuration = Duration.parse("800ms")
                        )
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(3, filled.contextualIntervalsSetup.replayCount)
            assertEquals(listOf(0, 2), filled.contextualIntervalsSetup.keyValues)
            assertEquals(Duration.parse("800ms"), filled.contextualIntervalsSetup.noteDuration)
            assertEquals(
                "the contextual-interval slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.INTERVALS_IN_CONTEXT).count { !it.empty }
            )
            assertTrue(
                "the progression slots are untouched",
                repository.slotsOf(TrainingKind.CHORD_PROGRESSIONS).all { it.empty }
            )
            assertTrue(
                "the scale-degree slots are untouched",
                repository.slotsOf(TrainingKind.SCALE_DEGREES).all { it.empty }
            )
            assertTrue(
                "the notes slots are untouched",
                repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION).all { it.empty }
            )
            assertTrue(
                "the scales slots are untouched",
                repository.slotsOf(TrainingKind.SCALES).all { it.empty }
            )
            assertTrue(
                "the chords slots are untouched",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS).all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals(
                "a cleared slot keeps its name",
                "C and D major contextual intervals",
                cleared.name
            )
            assertEquals(
                "a cleared slot is its kind's default again",
                ContextualIntervalSetup.default(),
                cleared.contextualIntervalsSetup
            )
            assertTrue(
                "every intervals-in-context slot is empty again",
                repository.slotsOf(TrainingKind.INTERVALS_IN_CONTEXT).all { it.empty }
            )
        }
    }

    @Test
    fun theContextualIntervalsStarterSeedsOnceStoresItsPayloadAndStaysImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1 AND kind = ?",
                listOf(TrainingKind.INTERVALS_IN_CONTEXT.id)
            )
            assertEquals("the intervals-in-context starter seeds exactly once", 1, starterRows.size)
            val starter = starterRows.single()
            assertEquals(PresetStarters.CONTEXTUAL_INTERVALS.id.value, starter.stringColumn("id"))
            assertEquals(1L, starter.longColumn("payload_version"))
            assertTrue(
                "the starter stores its interval and context selection",
                starter.stringColumn("payload").contains("interval_ids") &&
                    starter.stringColumn("payload").contains("context_gap_ms")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.CONTEXTUAL_INTERVALS.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> {
                repository.clear(PresetStarters.CONTEXTUAL_INTERVALS.id)
            }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.CONTEXTUAL_INTERVALS.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.CONTEXTUAL_INTERVALS.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun melodicDictationSlotsAreIsolatedFromEveryOtherKindAndClearEmptiesASlot() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()

            val melodicSlots = repository.slotsOf(TrainingKind.MELODIC_DICTATION)
            assertEquals(
                PresetSlots.slotIds(TrainingKind.MELODIC_DICTATION),
                melodicSlots.map { it.id }
            )
            assertTrue(
                "a fresh store's melodic-dictation slots are empty",
                melodicSlots.all { it.empty }
            )

            // Fill one slot: no other kind's slot space sees it. C and A natural minor both
            // realize the default phrase shape inside C3..C6; the edits must survive.
            val filled = repository.update(
                melodicSlots.first().copy(
                    name = "C and A minor phrases",
                    configuration = PresetConfiguration.MelodicDictation(
                        MelodicDictationSetup.default().copy(
                            keyValues = listOf(0, 9),
                            tonalities = listOf(Tonality.NATURAL_MINOR),
                            minimumNote = com.thomas.passiveeartraining.music.MidiNote.of(57),
                            maximumNote = com.thomas.passiveeartraining.music.MidiNote.of(84),
                            replayCount = 3,
                            noteDuration = Duration.parse("800ms")
                        )
                    )
                )
            )
            assertFalse("filling clears the empty flag", filled.empty)
            assertEquals(3, filled.melodicDictationSetup.replayCount)
            assertEquals(listOf(0, 9), filled.melodicDictationSetup.keyValues)
            assertEquals(Duration.parse("800ms"), filled.melodicDictationSetup.noteDuration)
            assertEquals(
                "the melodic-dictation slot is visible only through its own kind",
                1,
                repository.slotsOf(TrainingKind.MELODIC_DICTATION).count { !it.empty }
            )
            assertTrue(
                "the contextual-interval slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS_IN_CONTEXT).all { it.empty }
            )
            assertTrue(
                "the progression slots are untouched",
                repository.slotsOf(TrainingKind.CHORD_PROGRESSIONS).all { it.empty }
            )
            assertTrue(
                "the scale-degree slots are untouched",
                repository.slotsOf(TrainingKind.SCALE_DEGREES).all { it.empty }
            )
            assertTrue(
                "the notes slots are untouched",
                repository.slotsOf(TrainingKind.NOTE_IDENTIFICATION).all { it.empty }
            )
            assertTrue(
                "the scales slots are untouched",
                repository.slotsOf(TrainingKind.SCALES).all { it.empty }
            )
            assertTrue(
                "the chords slots are untouched",
                repository.slotsOf(TrainingKind.CHORDS).all { it.empty }
            )
            assertTrue(
                "the intervals slots are untouched",
                repository.slotsOf(TrainingKind.INTERVALS).all { it.empty }
            )
            fails<ImmutablePresetException> { repository.delete(filled.id) }

            val cleared = repository.clear(filled.id)
            assertTrue("clearing empties the slot", cleared.empty)
            assertEquals(
                "a cleared slot keeps its name",
                "C and A minor phrases",
                cleared.name
            )
            assertEquals(
                "a cleared slot is its kind's default again",
                MelodicDictationSetup.default(),
                cleared.melodicDictationSetup
            )
            assertTrue(
                "every melodic-dictation slot is empty again",
                repository.slotsOf(TrainingKind.MELODIC_DICTATION).all { it.empty }
            )
        }
    }

    @Test
    fun theMelodicDictationStarterSeedsOnceStoresItsPayloadAndStaysImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1 AND kind = ?",
                listOf(TrainingKind.MELODIC_DICTATION.id)
            )
            assertEquals("the melodic-dictation starter seeds exactly once", 1, starterRows.size)
            val starter = starterRows.single()
            assertEquals(
                PresetStarters.MELODIC_DICTATION.id.value,
                starter.stringColumn("id")
            )
            assertEquals(1L, starter.longColumn("payload_version"))
            assertTrue(
                "the starter stores its phrase shape",
                starter.stringColumn("payload").contains("phrase_length") &&
                    starter.stringColumn("payload").contains("allowed_steps")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.MELODIC_DICTATION.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> {
                repository.clear(PresetStarters.MELODIC_DICTATION.id)
            }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.MELODIC_DICTATION.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.MELODIC_DICTATION.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun everyKindsStartersAreSeededOnceAndStayImmutable() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val second = fixture.repository()
            second.refresh()

            val starterRows = fixture.database.query(
                "SELECT * FROM preset_payload WHERE built_in = 1"
            )
            assertEquals(
                "each kind seeds its own starters exactly once",
                PresetStarters.ALL_KINDS.size,
                starterRows.size
            )
            assertEquals(
                PresetStarters.ALL_KINDS.map { it.id.value }.sorted(),
                starterRows.map { it.stringColumn("id") }.sorted()
            )
            val chordsStarter = starterRows.single {
                it.stringColumn("id") == PresetStarters.CHORDS.id.value
            }
            assertEquals(TrainingKind.CHORDS.id, chordsStarter.stringColumn("kind"))
            assertEquals(1L, chordsStarter.longColumn("payload_version"))
            assertTrue(
                "the chords starter stores its quality selection",
                chordsStarter.stringColumn("payload").contains("quality_ids")
            )

            fails<ImmutablePresetException> {
                repository.update(PresetStarters.CHORDS.copy(name = "Hijacked"))
            }
            fails<ImmutablePresetException> { repository.clear(PresetStarters.CHORDS.id) }
            assertEquals(
                "the refused writes changed nothing",
                PresetStarters.CHORDS.name,
                fixture.database.query(
                    "SELECT name FROM preset_payload WHERE id = ?",
                    listOf(PresetStarters.CHORDS.id.value)
                ).single().stringColumn("name")
            )
        }
    }

    @Test
    fun aSixthLegacyCustomBeyondTheSlotsSurvivesAdoptionUntouched() = runTest {
        PresetStoreFixture().use { fixture ->
            PresetSchema.statementsToUpgrade(0, 6).forEach(fixture.database::execute)
            fixture.database.setUserVersion(6)
            repeat(6) { index ->
                fixture.database.execute(
                    "INSERT INTO preset_entity (" +
                        "id, name, created_at_epoch_ms, updated_at_epoch_ms, built_in, " +
                        "minimum_note, maximum_note, presentation, mode, thinking_silence_ms, " +
                        "replay_count, music_gain, voice_gain, stop_kind, note_duration_ms, " +
                        "answer_gap_ms, repeat_gap_ms, melodic_gap_ms, final_silence_ms" +
                        ") VALUES (?, ?, ?, ?, 0, 60, 72, 'ASCENDING', 'RECOGNITION', 2000, 0, " +
                        "1.0, 0.25, 'UNTIL_STOPPED', 700, 600, 400, 100, 2000)",
                    listOf("legacy-$index", "Custom $index", index.toLong(), index.toLong())
                )
            }

            val repository = fixture.repository()
            repository.refresh()

            val slots = repository.presets.value.filter { it.id in PresetSlots.IDS }
            assertEquals("all five slots were adopted into", 0, slots.count { it.empty })
            val leftover = repository.presets.value.filter { it.id.value.startsWith("legacy-") }
            assertEquals("the sixth custom is not discarded", 1, leftover.size)
            assertEquals("Custom 5", leftover.single().name)
        }
    }

    @Test
    fun markUsedRecordsTheUseTimeAndSavingKeepsIt() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val starter = repository.presets.value.first { it.builtIn }
            val slot = repository.slotsOf(TrainingKind.INTERVALS).first { it.empty }

            fixture.now += 1_000
            repository.markUsed(starter.id)
            val usedStarter = repository.presets.value.first { it.id == starter.id }
            assertEquals(
                "a built-in starter can be marked as used",
                fixture.now,
                usedStarter.lastUsedAtEpochMs
            )
            assertEquals(
                "a use never rewrites the saved time",
                starter.updatedAtEpochMs,
                usedStarter.updatedAtEpochMs
            )

            fixture.now += 1_000
            repository.markUsed(slot.id)
            fixture.now += 1_000
            val saved = repository.update(slot.copy(empty = false))
            assertEquals(
                "saving keeps the stored use time",
                fixture.now - 1_000,
                saved.lastUsedAtEpochMs
            )
            assertEquals("saving moves the saved time", fixture.now, saved.updatedAtEpochMs)
            assertEquals(
                "the use time survives a reload",
                fixture.now - 1_000,
                repository.slotsOf(TrainingKind.INTERVALS)
                    .first { it.id == slot.id }
                    .lastUsedAtEpochMs
            )
        }
    }
}
