package com.skydex.app.ui.events

import com.skydex.shared.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EventIconsTest {
    /** Gradle runs unit tests from the module directory; fall back to the repo root for IDE runs. */
    private val drawables = listOf("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi")
        .map(::File)
        .firstOrNull { it.isDirectory }
        ?: error("drawable-nodpi not found from ${File("").absolutePath}")

    @Test
    fun `every event has its own icon`() {
        val ids = EventType.entries.map(::eventIcon)

        ids.forEach { assertNotEquals(0, it) }
        assertEquals(EventType.entries.size, ids.toSet().size)
    }

    @Test
    fun `every event icon is a bundled lossless WebP`() {
        EventType.entries.forEach { type ->
            val file = File(drawables, "event_${type.name.lowercase()}.webp")
            assertTrue("$file is missing", file.isFile)
            val header = file.inputStream().use { it.readNBytes(16) }.toString(Charsets.ISO_8859_1)
            assertEquals("$file is not RIFF", "RIFF", header.take(4))
            assertEquals("$file is not lossless WebP", "WEBPVP8L", header.substring(8))
        }
    }
}
