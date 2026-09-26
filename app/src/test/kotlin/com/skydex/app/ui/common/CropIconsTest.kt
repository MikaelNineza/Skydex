package com.skydex.app.ui.common

import com.skydex.shared.model.Crop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CropIconsTest {
    /** Gradle runs unit tests from the module directory; fall back to the repo root for IDE runs. */
    private val drawables = listOf("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi")
        .map(::File)
        .firstOrNull { it.isDirectory }
        ?: error("drawable-nodpi not found from ${File("").absolutePath}")

    @Test
    fun `every crop has its own icon`() {
        val ids = Crop.entries.map(::cropIcon)

        ids.forEach { assertNotEquals(0, it) }
        assertEquals(Crop.entries.size, ids.toSet().size)
    }

    @Test
    fun `every crop icon is a bundled lossless WebP`() {
        Crop.entries.forEach { crop ->
            val file = File(drawables, "crop_${crop.name.lowercase()}.webp")
            assertTrue("$file is missing", file.isFile)
            val header = file.inputStream().use { it.readNBytes(16) }.toString(Charsets.ISO_8859_1)
            assertEquals("$file is not RIFF", "RIFF", header.take(4))
            assertEquals("$file is not lossless WebP", "WEBPVP8L", header.substring(8))
        }
    }

    @Test
    fun `crops label lists display names in contest order`() {
        assertEquals("Crops: Wheat, Cactus, Melon", cropsLabel(listOf(Crop.WHEAT, Crop.CACTUS, Crop.MELON)))
        assertEquals("Crops: Cocoa Beans, Nether Wart, Wild Rose", cropsLabel(listOf(Crop.COCOA_BEANS, Crop.NETHER_WART, Crop.WILD_ROSE)))
    }
}
