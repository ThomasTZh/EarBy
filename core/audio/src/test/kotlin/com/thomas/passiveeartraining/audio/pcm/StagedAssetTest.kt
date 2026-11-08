package com.thomas.passiveeartraining.audio.pcm

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagedAssetTest {
    @Test
    fun sameLengthCorruptionDoesNotPassTheStagingCheck() {
        val file = File.createTempFile("voice", ".wav")
        try {
            file.writeText("original")
            val hash = MessageDigest.getInstance(
                "SHA-256"
            ).digest(file.readBytes()).joinToString("") {
                "%02x".format(it)
            }
            assertTrue(AssetPreparer.matches(file, 8, hash))
            file.writeText("modified")
            assertFalse(AssetPreparer.matches(file, 8, hash))
        } finally {
            file.delete()
        }
    }
}
