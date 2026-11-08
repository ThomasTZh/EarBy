package com.thomas.passiveeartraining.data.preset

import com.thomas.passiveeartraining.training.IntervalExercise
import com.thomas.passiveeartraining.training.IntervalGenerator
import com.thomas.passiveeartraining.training.SeededIntervalGenerator
import com.thomas.passiveeartraining.training.SeededIntervalGeneratorV2
import com.thomas.passiveeartraining.training.SeededRandomSource
import com.thomas.passiveeartraining.training.recipe.DurationFinish
import com.thomas.passiveeartraining.training.recipe.RecipeMode
import com.thomas.passiveeartraining.training.recipe.SessionStopPolicy
import com.thomas.passiveeartraining.training.settings.LastSetup
import com.thomas.passiveeartraining.training.settings.MelodicDictationSetup
import com.thomas.passiveeartraining.training.settings.UserSettings
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A JSON writer independent of the codec, so a test can produce documents the codec refuses. */
private val testJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

private fun envelopeOfDocuments(vararg documents: PresetDocumentV2): String =
    testJson.encodeToString(
        PresetExportEnvelope.serializer(),
        PresetExportEnvelope(
            presets = documents.map {
                testJson.encodeToJsonElement(PresetDocumentV2.serializer(), it)
            }
        )
    )

/** Builds a frozen schema-1 envelope, so the migration reader keeps its own fixture. */
private fun envelopeOfV1Documents(vararg documents: PresetDocumentV1): String =
    testJson.encodeToString(
        PresetExportEnvelope.serializer(),
        PresetExportEnvelope(
            schemaVersion = 1,
            presets = documents.map {
                testJson.encodeToJsonElement(PresetDocumentV1.serializer(), it)
            }
        )
    )

private fun presetDocument(
    id: String,
    name: String = "Imported",
    setup: LastSetup = LastSetup.default(),
    seed: Long? = null,
    musicGain: Float = UserSettings.DEFAULT_MUSIC_GAIN,
    voiceGain: Float = UserSettings.DEFAULT_VOICE_GAIN
): PresetDocumentV2 = PresetDocumentCodec.toDocument(
    Preset(
        id = PresetId(id),
        name = name,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 2_000L,
        configuration = PresetConfiguration.Intervals(setup),
        seed = seed,
        musicGain = musicGain,
        voiceGain = voiceGain
    )
)

/** The intervals setup of an import-test preset. */
private val Preset.intervalSetup: LastSetup
    get() = (configuration as PresetConfiguration.Intervals).setup

/** The intervals configuration an import test saves. */
private fun intervals(setup: LastSetup): PresetConfiguration = PresetConfiguration.Intervals(setup)

private fun seededSequence(preset: Preset): List<IntervalExercise> {
    val policy = preset.intervalSetup.toPolicy()
    val randomSource = SeededRandomSource(requireNotNull(preset.seed))
    val generator: IntervalGenerator = when (policy.generatorVersion) {
        1 -> SeededIntervalGenerator(policy.definition, randomSource)
        else -> SeededIntervalGeneratorV2(policy, randomSource)
    }
    return List(10) { generator.next() }
}

class PresetImportTest {

    @Test
    fun previewReportsAValidAndAnInvalidPresetOnTheirOwnRows() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val broken = presetDocument("11111111-2222-3333-4444-555555555555", "Broken").let {
                val payload = PresetDocumentCodec.intervalPayload(it)
                    .copy(intervalIds = listOf("not_an_interval"))
                PresetDocumentCodec.toDocument(
                    PresetDocumentCodec.toPreset(it, id = PresetId(it.id)),
                    payload
                )
            }
            val preview = repository.previewImport(
                envelopeOfDocuments(
                    presetDocument("22222222-3333-4444-5555-666666666666", "Fine"),
                    broken
                )
            )

            assertEquals(1, preview.ready.size)
            assertEquals("Fine", preview.ready.single().name)
            assertEquals(1, preview.rejected.size)
            assertEquals(1, preview.rejected.single().index)
            assertTrue(
                "the reason names the bad interval",
                preview.rejected.single().reason.contains("not_an_interval")
            )
        }
    }

    @Test
    fun previewSeparatesStoredConflictsFromDuplicatesInsideTheFile() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val existing = repository.create("Stored", intervals(LastSetup.default()))
            val shared = "9c2e4f6a-8b3d-4a1c-9e5b-0000000000aa"
            val preview = repository.previewImport(
                envelopeOfDocuments(
                    presetDocument(existing.id.value, "Stored"),
                    presetDocument(shared, "First"),
                    presetDocument(shared, "Second")
                )
            )

            assertEquals(
                ImportConflict.EXISTING_ID,
                preview.ready[0].conflict
            )
            assertNull(preview.ready[1].conflict)
            assertEquals(
                ImportConflict.DUPLICATE_IN_FILE,
                preview.ready[2].conflict
            )
            assertTrue(preview.hasConflicts)
        }
    }

    @Test
    fun commitKeepsFreeIdsAndMovesConflictsToFreshIdentities() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val existing = repository.create("Stored", intervals(LastSetup.default()))
            val freshId = "8b7a6c5d-4e3f-4a2b-9c1d-0000000000bb"
            val preview = repository.previewImport(
                envelopeOfDocuments(
                    presetDocument(existing.id.value, "Clash"),
                    presetDocument(freshId, "Fresh")
                )
            )

            val result = repository.commitImport(preview)

            assertEquals(2, result.imported.size)
            assertEquals(0, result.replaced.size)
            assertEquals("Clash", result.imported[0].name)
            assertNotEquals(
                "a conflicting id defaults to a fresh identity",
                existing.id,
                result.imported[0].id
            )
            assertEquals(PresetId(freshId), result.imported[1].id)
            assertEquals(
                "the stored original is untouched",
                "Stored",
                repository.presets.value.first { it.id == existing.id }.name
            )
        }
    }

    @Test
    fun commitReplacesAnExistingPresetOnlyWhenItsIdIsExplicitlySelected() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val existing = repository.create("Original", intervals(LastSetup.default()))
            fixture.now += 3_000
            val changed = presetDocument(
                existing.id.value,
                name = "Replaced",
                setup = existing.intervalSetup.copy(mode = RecipeMode.ASSOCIATION),
                seed = 9L
            ).copy(createdAtEpochMs = existing.createdAtEpochMs)
            val preview = repository.previewImport(envelopeOfDocuments(changed))

            val result = repository.commitImport(
                preview,
                replaceExistingIds = setOf(existing.id)
            )

            assertEquals(0, result.imported.size)
            assertEquals(1, result.replaced.size)
            val replaced = result.replaced.single()
            assertEquals("Replaced", replaced.name)
            assertEquals(RecipeMode.ASSOCIATION, replaced.intervalSetup.mode)
            assertEquals(9L, replaced.seed)
            assertEquals(
                "replacement keeps the original creation time",
                existing.createdAtEpochMs,
                replaced.createdAtEpochMs
            )
            assertEquals("replacement records the change", fixture.now, replaced.updatedAtEpochMs)
            assertEquals(
                1,
                repository.presets.value.count { !it.builtIn && it.id !in PresetSlots.IDS }
            )
        }
    }

    @Test
    fun aConflictThatAppearsAfterThePreviewIsStillReidentifiedAtCommit() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val shared = "7f6e5d4c-3b2a-4c1d-8e9f-0000000000cc"
            val first = repository.previewImport(
                envelopeOfDocuments(presetDocument(shared, "First"))
            )
            val second = repository.previewImport(
                envelopeOfDocuments(presetDocument(shared, "Second"))
            )
            // The preview of the second file was taken while the id was free; the first commit made
            // it clash, and the commit re-checks rather than trusting the stale preview.
            assertNull(second.ready.single().conflict)

            repository.commitImport(first)
            val result = repository.commitImport(second)

            assertEquals(
                2,
                repository.presets.value.count { !it.builtIn && it.id !in PresetSlots.IDS }
            )
            assertEquals(
                "the two imported presets have distinct identities",
                2,
                repository.presets.value
                    .filter { !it.builtIn && it.id !in PresetSlots.IDS }
                    .map { it.id }
                    .distinct()
                    .size
            )
            assertEquals(1, result.imported.size)
        }
    }

    @Test
    fun aFailedCommitLeavesTheStoreExactlyAsItWas() = runTest {
        PresetStoreFixture().use { fixture ->
            val failing = FailingDatabase(fixture.database)
            val repository = SqlitePresetRepository(
                database = failing,
                ioDispatcher = UnconfinedTestDispatcher(),
                nowMs = { fixture.now },
                newId = { PresetId.random() }
            )
            repository.refresh()
            val baseline = repository.presets.value
            val document = presetDocument(
                "1a2b3c4d-5e6f-4a7b-8c9d-0000000000dd",
                "Half-written"
            )
            val preview = repository.previewImport(envelopeOfDocuments(document))
            failing.failOnExecute = failing.executeCount + 1

            fails<IllegalStateException> { repository.commitImport(preview) }

            assertEquals(baseline, repository.presets.value)
            assertTrue(
                repository.presets.value.none { it.name == "Half-written" }
            )
        }
    }

    @Test
    fun aBatchThatWouldExceedTheCapIsRefusedBeforeAnythingIsWritten() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            // The five slots fill part of the editable capacity.
            repeat(
                SqlitePresetRepository.MAX_EDITABLE_PRESETS - PresetSlots.IDS.size - 1
            ) { index ->
                repository.create("full-$index", intervals(LastSetup.default()))
            }
            val preview = repository.previewImport(
                envelopeOfDocuments(
                    presetDocument("2b3c4d5e-6f7a-4b8c-9d0e-0000000000ee", "Over one"),
                    presetDocument("3c4d5e6f-7a8b-4c9d-0e1f-0000000000ff", "Over two")
                )
            )

            fails<PresetLimitException> { repository.commitImport(preview) }

            assertEquals(
                SqlitePresetRepository.MAX_EDITABLE_PRESETS - PresetSlots.IDS.size - 1,
                repository.presets.value.count { !it.builtIn && it.id !in PresetSlots.IDS }
            )
            assertTrue(
                repository.presets.value.none { it.name.startsWith("Over ") }
            )
        }
    }

    @Test
    fun exportContainsExactlyTheSelectedPresetsWithTheirConfiguration() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val setup = LastSetup.default().copy(
                mode = RecipeMode.EXPOSURE,
                replayCount = 2,
                stopPolicy = SessionStopPolicy.Duration(
                    kotlin.time.Duration.parse("45m"),
                    DurationFinish.FINISH_CURRENT_ITEM
                )
            )
            val first = repository.create(
                name = "First",
                configuration = PresetConfiguration.Intervals(setup),
                seed = 123L,
                musicGain = 0.75f,
                voiceGain = 0.5f
            )
            val second = repository.create(
                "Second",
                PresetConfiguration.Intervals(LastSetup.default())
            )

            val text = repository.export(listOf(second.id, first.id))
            val documents = PresetDocumentCodec.decode(text)

            assertEquals(
                listOf(second.id.value, first.id.value),
                documents.map { it.id }
            )
            val restored = PresetDocumentCodec.toPreset(documents[1])
            assertEquals(PresetConfiguration.Intervals(setup), restored.configuration)
            assertEquals(123L, restored.seed)
            assertEquals(0.75f, restored.musicGain, 0f)
            assertEquals(0.5f, restored.voiceGain, 0f)
        }
    }

    @Test
    fun clearAndImportReproducesTheSameSeededSequence() = runTest {
        val setup = LastSetup.default().copy(
            intervalIds = listOf("minor_third", "perfect_fifth"),
            replayCount = 1,
            fixedRoot = 60
        )
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val created = repository.create("Seed proof", intervals(setup), seed = 8_675_309L)
            val originalSequence = seededSequence(created)
            val text = repository.export(listOf(created.id))

            PresetStoreFixture().use { freshFixture ->
                val fresh = freshFixture.repository()
                fresh.refresh()
                val preview = fresh.previewImport(text)
                assertEquals(0, preview.rejected.size)
                val imported = fresh.commitImport(preview).imported.single()

                assertEquals(
                    "the configuration is equivalent",
                    PresetConfiguration.Intervals(setup),
                    imported.configuration
                )
                assertEquals(8_675_309L, imported.seed)
                assertEquals(
                    "the same seed and configuration produce the same exercises",
                    originalSequence,
                    seededSequence(imported)
                )
            }
        }
    }

    @Test
    fun aFileLevelProblemIsRejectedAtPreviewAndChangesNothing() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val before = repository.presets.value

            fails<PresetValidationException> { repository.previewImport("{ not json") }
            assertEquals(before, repository.presets.value)
        }
    }

    @Test
    fun aFutureSchemaFileIsRejectedAtPreview() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val future = envelopeOfDocuments(
                presetDocument("4d5e6f7a-8b9c-4d0e-1f2a-000000000000")
            ).replace("\"schemaVersion\":2", "\"schemaVersion\":3")

            val failure = fails<PresetValidationException> { repository.previewImport(future) }
            assertTrue(failure.message!!.contains("newer"))
        }
    }

    @Test
    fun anEmptyDocumentPreviewsAndCommitsNothing() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val preview = repository.previewImport(envelopeOfDocuments())
            assertTrue(preview.entries.isEmpty())

            val result = repository.commitImport(preview)
            assertEquals(0, result.imported.size)
            assertEquals(0, result.replaced.size)
        }
    }

    @Test
    fun anUnknownStopKindIsRejectedAtPreview() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val document = presetDocument("5e6f7a8b-9c0d-4e1f-2a3b-000000000000").let {
                val payload = PresetDocumentCodec.intervalPayload(it).copy(stopKind = "FOREVER")
                PresetDocumentCodec.toDocument(
                    PresetDocumentCodec.toPreset(it, id = PresetId(it.id)),
                    payload
                )
            }

            val preview = repository.previewImport(envelopeOfDocuments(document))
            assertEquals(1, preview.rejected.size)
        }
    }

    @Test
    fun anAttemptToReplaceAStarterIsRefusedAndChangesNothing() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            repository.refresh()
            val starter = repository.presets.value.first { it.builtIn }
            val document = presetDocument(starter.id.value, name = "Hijacked")
            val preview = repository.previewImport(envelopeOfDocuments(document))
            assertEquals(ImportConflict.EXISTING_ID, preview.ready.single().conflict)

            fails<ImmutablePresetException> {
                repository.commitImport(preview, replaceExistingIds = setOf(starter.id))
            }

            assertEquals(
                "the starter is unchanged",
                starter,
                repository.presets.value.first { it.id == starter.id }
            )
        }
    }

    @Test
    fun previewRejectsAMelodicDictationCycleStopPolicyWithTheExplanation() = runTest {
        PresetStoreFixture().use { fixture ->
            val repository = fixture.repository()
            val base = PresetDocumentCodec.toDocument(
                Preset(
                    id = PresetId("33333333-4444-4555-8666-777777777777"),
                    name = "Cycle phrases",
                    createdAtEpochMs = 1_000L,
                    updatedAtEpochMs = 2_000L,
                    configuration = PresetConfiguration.MelodicDictation(
                        MelodicDictationSetup.default()
                    )
                )
            )
            val cycleStop = base.copy(
                payload = PresetDocumentCodec.encodePayload(
                    PresetDocumentCodec.melodicDictationPayload(base)
                        .copy(stopKind = "AFTER_CYCLE")
                )
            )

            val preview = repository.previewImport(envelopeOfDocuments(cycleStop))
            assertEquals(1, preview.rejected.size)
            assertTrue(
                "the entry must be refused with the missing-cycle explanation: " +
                    preview.rejected.single().reason,
                preview.rejected.single().reason.contains("no label cycle")
            )
        }
    }
}
