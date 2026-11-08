package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.music.SimpleInterval
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4.3: the declarative tool catalog is the production vocabulary.
 *
 * The voice tools in `tools/` consume `tools/voice-assets/vocabulary.json`; this test proves the
 * file and [VoiceVocabulary] cannot drift, and that only the frozen interval clips are marked as
 * bundled today.
 */
class VoiceCatalogTest {

    private fun catalogFile(): File {
        val candidates = listOf(
            File("../../tools/voice-assets/vocabulary.json"),
            File("tools/voice-assets/vocabulary.json")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error(
                "voice catalog not found; looked from ${File(".").absolutePath}"
            )
    }

    private fun catalogEntries(): List<Map<String, String>> {
        val root = Json.parseToJsonElement(catalogFile().readText()).jsonObject
        return root.getValue("entries").jsonArray.map { element ->
            val entry = element.jsonObject
            mapOf(
                "id" to entry.getValue("id").jsonPrimitive.content,
                "namespace" to entry.getValue("namespace").jsonPrimitive.content,
                "spokenText" to entry.getValue("spokenText").jsonPrimitive.content,
                "bundled" to (entry["bundled"]?.jsonPrimitive?.content ?: "false")
            )
        }
    }

    @Test
    fun theToolCatalogMatchesTheProductionVocabularyExactly() {
        val fromFile = catalogEntries().associate { entry ->
            entry.getValue("id") to (
                entry.getValue("namespace") to entry.getValue("spokenText")
                )
        }
        val fromCode = VoiceVocabulary.entries.associate { entry ->
            entry.id.value to (entry.namespace.prefix to entry.spokenText)
        }
        assertEquals(fromCode, fromFile)
    }

    @Test
    fun theBundledSetIsTheIntervalAndChordVocabulary() {
        val bundled = catalogEntries()
            .filter { it.getValue("bundled") == "true" }
            .map { it.getValue("id") }
            .toSet()
        val expected = buildSet {
            SimpleInterval.ALL.forEach { add("interval.${it.id}") }
            com.thomas.passiveeartraining.music.ChordQuality.entries.forEach {
                add(VoiceVocabulary.chordQualityEntry(it).id.value)
            }
            (0..3).forEach { add(VoiceVocabulary.chordInversionEntry(it).id.value) }
            add(VoiceVocabulary.OR.id.value)
            com.thomas.passiveeartraining.music.ScaleKind.entries.forEach {
                add(VoiceVocabulary.scaleEntry(it).id.value)
            }
            // M7 note identification: naturals, the sharp/flat chromatic tables (no doubles)
            // and spoken octaves.
            com.thomas.passiveeartraining.music.Letter.entries.forEach {
                add(VoiceVocabulary.noteEntry(it, 0).id.value)
            }
            listOf("c", "d", "f", "g", "a").forEach {
                add(
                    VoiceVocabulary.noteEntry(
                        com.thomas.passiveeartraining.music.Letter.valueOf(it.uppercase()),
                        1
                    ).id.value
                )
            }
            listOf("d", "e", "g", "a", "b").forEach {
                add(
                    VoiceVocabulary.noteEntry(
                        com.thomas.passiveeartraining.music.Letter.valueOf(it.uppercase()),
                        -1
                    ).id.value
                )
            }
            (-1..9).forEach { add(VoiceVocabulary.octaveEntry(it).id.value) }
            // M8 scale degrees: the number words.
            (1..7).forEach { add(VoiceVocabulary.degreeNumberEntry(it).id.value) }
            // M9A root-motion answers: the direction connectors ("or" is already bundled).
            add(VoiceVocabulary.ASCENDING.id.value)
            add(VoiceVocabulary.DESCENDING.id.value)
            add(VoiceVocabulary.UNISON.id.value)
            // M9A function answers: the three harmony words (owner-accepted 2026-10-14).
            com.thomas.passiveeartraining.music.HarmonicFunction.entries.forEach {
                add(VoiceVocabulary.harmonyFunctionEntry(it).id.value)
            }
        }
        assertEquals(
            "bundled assets are exactly the interval, chord, scale, note and progression vocabulary",
            expected,
            bundled
        )
        assertEquals("the reviewed pack declares exactly 83 bundled clips", 83, bundled.size)
        assertTrue(
            "every bundled id exists in the registry",
            bundled.all {
                VoiceVocabulary.ofIdOrNull(
                    com.thomas.passiveeartraining.training
                        .VoiceAssetId(it)
                ) != null
            }
        )
    }
}
