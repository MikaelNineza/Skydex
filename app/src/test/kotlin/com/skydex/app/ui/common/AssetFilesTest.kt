package com.skydex.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class AssetFilesTest {
    /** Gradle runs unit tests from the module directory; fall back to the repo root for IDE runs. */
    private val drawables = listOf("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi")
        .map(::File)
        .firstOrNull { it.isDirectory }
        ?: error("drawable-nodpi not found from ${File("").absolutePath}")

    private val mayors = listOf(
        "aatrox", "barry", "cole", "derpy", "diana", "diaz",
        "finnegan", "foxy", "jerry", "marina", "paul", "scorpius",
    )

    private fun assertLosslessWebp(file: File) {
        assertTrue("$file is missing", file.isFile)
        val header = file.inputStream().use { it.readNBytes(16) }.toString(Charsets.ISO_8859_1)
        assertEquals("$file is not RIFF", "RIFF", header.take(4))
        assertEquals("$file is not lossless WebP", "WEBPVP8L", header.substring(8))
    }

    private fun md5(file: File) =
        MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `every mayor face is a bundled lossless WebP`() {
        mayors.forEach { assertLosslessWebp(File(drawables, "mayor_$it.webp")) }
        // Each face is its own image.
        assertEquals(mayors.size, mayors.map { md5(File(drawables, "mayor_$it.webp")) }.toSet().size)
    }

    @Test
    fun `carrot and potato are the item icons, not the planted crops`() {
        val carrot = File(drawables, "crop_carrot.webp")
        val potato = File(drawables, "crop_potato.webp")
        assertLosslessWebp(carrot)
        assertLosslessWebp(potato)

        // The planted-crop art SkyCrypt uses for both, which looked alike.
        assertNotEquals("83b1ee9811e7696fa2591797a5bea246", md5(carrot))
        assertNotEquals("2b8f0b90e5d97f0eae944587e6973cc8", md5(potato))
        assertNotEquals(md5(carrot), md5(potato))
    }
}
