package com.skydex.app.ui.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MayorFacesTest {
    private val mayors = listOf(
        "Aatrox", "Barry", "Cole", "Derpy", "Diana", "Diaz",
        "Finnegan", "Foxy", "Jerry", "Marina", "Paul", "Scorpius",
    )

    @Test
    fun `every mayor has its own face`() {
        val faces = mayors.map { name -> mayorFace(name).also { assertNotNull(name, it) } }

        assertEquals(mayors.size, faces.toSet().size)
    }

    @Test
    fun `names match in any case and with surrounding spaces`() {
        for (name in mayors) {
            val face = mayorFace(name)
            assertEquals(name, face, mayorFace(name.uppercase()))
            assertEquals(name, face, mayorFace(name.lowercase()))
            assertEquals(name, face, mayorFace(" $name "))
        }
    }

    @Test
    fun `unknown mayors have no face`() {
        assertNull(mayorFace("Unknown"))
        assertNull(mayorFace(""))
        // Matching is by name, not by the mayor's key.
        assertNull(mayorFace("economist"))
    }
}
