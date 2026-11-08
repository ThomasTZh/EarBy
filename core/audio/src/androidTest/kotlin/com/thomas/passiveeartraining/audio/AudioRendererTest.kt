package com.thomas.passiveeartraining.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private fun assetCopy(context: android.content.Context, assetPath: String, target: File): File {
    context.assets.open(assetPath).use { input ->
        target.parentFile?.mkdirs()
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

private fun bundledPack(context: android.content.Context): File? {
    val asset = "earby-string-pack.bin"
    val present = context.assets.list("soundfont")?.contains(asset) == true
    return if (present) {
        assetCopy(
            context,
            "soundfont/$asset",
            File(context.cacheDir, asset)
        )
    } else {
        null
    }
}

/**
 * Conformance tests for the native renderer on a real device.
 *
 * Create/destroy, invalid input, use-after-close and malformed-pack paths run everywhere.
 * The test APK reuses the app’s bundled reviewed EARP1 pack; missing assets fail the test.
 */
@RunWith(AndroidJUnit4::class)
class AudioRendererTest {

    private val context: android.content.Context
        get() = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext

    @Test
    fun wavBoundsAreRejectedBeforeAllocationOrNativeAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeAudioFiles.decodeMono("missing.wav", 48_000, 384_001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeAudioFiles.writeWav("unused.wav", FloatArray(4), 0, 48_000, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeAudioFiles.writeWav("unused.wav", FloatArray(4), 4, 48_000, Int.MAX_VALUE)
        }
    }

    @Test
    fun creatingAndClosingARendererIsIdempotent() {
        val renderer = MusicRenderer.create(RenderFormat.MONO_48K)
        assertEquals(RenderFormat.MONO_48K, renderer.format)
        assertTrue(renderer.reset().let { true })
        renderer.close()
        assertTrue(renderer.isClosed)
        renderer.close()
        assertTrue(renderer.isClosed)
    }

    @Test
    fun createSucceedsRepeatedlyWithoutLeakingAHandle() {
        repeat(8) {
            MusicRenderer.create(RenderFormat.DEFAULT).use { renderer ->
                assertTrue(!renderer.isClosed)
            }
        }
    }

    @Test
    fun everyCallAfterCloseFailsWithTheTypedException() {
        val renderer = MusicRenderer.create()
        renderer.close()
        assertThrows(RendererClosedException::class.java) { renderer.reset() }
        assertThrows(RendererClosedException::class.java) { renderer.noteOn(RenderNote(60)) }
        assertThrows(RendererClosedException::class.java) { renderer.noteOff(60) }
        assertThrows(RendererClosedException::class.java) { renderer.allNotesOff() }
        assertThrows(RendererClosedException::class.java) { renderer.render(FloatArray(64), 32) }
        assertThrows(RendererClosedException::class.java) {
            renderer.prepare(RenderRequest.DEFAULT)
        }
    }

    @Test
    fun missingPackFailsWithAnAssetErrorNamingThePath() {
        val renderer = MusicRenderer.create()
        val error = assertThrows(AudioAssetException::class.java) {
            renderer.loadSoundFont("/definitely/not/here/earby-string-pack.bin")
        }
        assertTrue(error.message!!.contains("earby-string-pack.bin"))
        renderer.close()
    }

    @Test
    fun malformedPackFailsWithoutEnteringAnAcceptedState() {
        val broken = File(context.cacheDir, "broken.pack")
        broken.writeBytes(ByteArray(64) { 0x7f.toByte() })
        val renderer = MusicRenderer.create()
        assertThrows(AudioAssetException::class.java) {
            renderer.loadSoundFont(broken.absolutePath)
        }
        renderer.close()
        assertEquals(64, broken.length())
    }

    @Test
    fun invalidArgumentsAreRejectedOnBothSidesOfTheBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            RenderFormat(sampleRate = 22_050, channels = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RenderFormat(sampleRate = 48_000, channels = 4)
        }
        assertThrows(IllegalArgumentException::class.java) { RenderNote(midiNote = 128) }
        assertThrows(IllegalArgumentException::class.java) {
            RenderNote(midiNote = 60, velocity = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProgramSelection(bank = 0, program = 200)
        }
        assertThrows(IllegalArgumentException::class.java) { RenderRequest(gain = 4.01f) }
        assertThrows(IllegalArgumentException::class.java) { RenderRequest(polyphony = 0) }

        val renderer = MusicRenderer.create()
        assertThrows(IllegalArgumentException::class.java) { renderer.render(FloatArray(64), 0) }
        assertThrows(IllegalArgumentException::class.java) { renderer.render(FloatArray(64), 2048) }
        assertThrows(IllegalArgumentException::class.java) { renderer.render(FloatArray(4), 32) }
        renderer.close()
    }

    @Test
    fun renderOfAnIdleRendererIsSilent() {
        val renderer = MusicRenderer.create(RenderFormat.MONO_48K)
        renderer.prepare(RenderRequest.DEFAULT)
        val block = FloatArray(RenderRequest.MAX_BLOCK_FRAMES)
        block.fill(0.25f)
        val frames = renderer.render(block, RenderRequest.MAX_BLOCK_FRAMES)
        assertEquals(RenderRequest.MAX_BLOCK_FRAMES, frames)
        assertTrue("an idle renderer must not emit sound", block.all { it == 0f })
        renderer.close()
    }

    @Test
    fun aBundledPackRendersNotesAndResetSilencesThem() {
        val font = bundledPack(context)
        assertTrue("reviewed EARP1 pack must be packaged for device render checks", font != null)
        MusicRenderer.create(RenderFormat.MONO_48K).use { renderer ->
            renderer.loadSoundFont(font!!.absolutePath)
            renderer.prepare(RenderRequest.DEFAULT)

            // Warm up: render one block before measuring the note.
            val block = FloatArray(RenderRequest.MAX_BLOCK_FRAMES)
            renderer.noteOn(RenderNote(60))
            renderer.render(block, RenderRequest.MAX_BLOCK_FRAMES)
            renderer.render(block, RenderRequest.MAX_BLOCK_FRAMES)
            val peak = block.maxOf { abs(it) }
            assertTrue("expected audible note PCM, peak was $peak", peak > 0.001f)

            renderer.reset()
            renderer.render(block, RenderRequest.MAX_BLOCK_FRAMES)
            renderer.render(block, RenderRequest.MAX_BLOCK_FRAMES)
            val afterReset = block.maxOf { abs(it) }
            assertTrue(
                "reset must silence the previous note, peak was $afterReset",
                afterReset < 0.01f
            )
        }
    }
}
