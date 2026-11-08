package com.thomas.passiveeartraining.audio.pcm

import android.content.Context
import com.thomas.passiveeartraining.audio.MusicRenderer
import com.thomas.passiveeartraining.audio.RenderFormat
import java.io.File
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Copies the bundled SoundFont and voice clips into app-private storage.
 *
 * The renderer needs real file paths, so assets are staged once per version/hash. A partially
 * written file is never accepted: the copy goes to a temporary name and is renamed, and an
 * existing file is reused only when its size matches.
 */
object AssetPreparer {

    internal fun matches(file: File, expectedBytes: Long, expectedSha256: String?): Boolean =
        file.isFile && (expectedBytes <= 0 || file.length() == expectedBytes) &&
            (expectedSha256 == null || sha256(file) == expectedSha256)

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Copy block size for large assets. */
    private const val COPY_BUFFER_BYTES = 1 shl 20

    /** Directory for staged assets; lives under the app's no-backup cache area. */
    fun stagingDirectory(context: Context): File =
        File(context.noBackupFilesDir, "assets").apply { mkdirs() }

    /**
     * Ensures [assetPath] exists in staging and returns the staged file.
     *
     * @param expectedBytes size recorded for the asset, or -1 to skip the size check.
     */
    fun stage(
        context: Context,
        assetPath: String,
        expectedBytes: Long = -1L,
        expectedSha256: String? = null
    ): File {
        val name = assetPath.substringAfterLast('/')
        val target = File(stagingDirectory(context), name)
        if (matches(target, expectedBytes, expectedSha256)) {
            return target
        }
        val temporary = File(target.parentFile, "$name.part")
        // The bundled SoundFont is hundreds of megabytes: stream it with a transfer loop rather
        // than a byte-array copy so staging stays fast on a device.
        context.assets.open(assetPath).use { input ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        if (!matches(temporary, expectedBytes, expectedSha256)) {
            val actual = temporary.length()
            temporary.delete()
            throw AudioOutputException(
                "Staged $assetPath is $actual bytes but $expectedBytes were expected"
            )
        }
        // Atomic rename: a failed copy can never be mistaken for a valid asset.
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw AudioOutputException("Could not move the staged asset into place: ${target.name}")
        }
        return target
    }

    /** Reads the voice-pack manifest and returns asset path to staged file for every entry. */
    fun stageVoicePack(
        context: Context,
        manifestAssetPath: String,
        expectedBytes: Map<String, Long> = emptyMap()
    ): Map<String, File> {
        val manifestJson = context.assets.open(manifestAssetPath).bufferedReader().use {
            it.readText()
        }
        val entries = kotlinx.serialization.json.Json.parseToJsonElement(manifestJson)
            .jsonObject.getValue("entries").jsonArray
        val directory = manifestAssetPath.substringBeforeLast('/')
        return entries.associate { value ->
            val entry = value.jsonObject
            val id = entry.getValue("id").jsonPrimitive.content
            val relative = entry.getValue("file").jsonPrimitive.content
            require(!relative.startsWith('/') && ".." !in relative.split('/'))
            id to stage(
                context,
                "$directory/$relative",
                expectedBytes[id] ?: -1L,
                entry.getValue("sha256").jsonPrimitive.content
            )
        }
    }
}
