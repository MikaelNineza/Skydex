package com.skydex.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MayorSerializationTest {
    /** A mayor status as sent by servers (and cached by apps) from before lastElectionCandidates. */
    private val oldJson = """{"mayor":{"key":"economist","name":"Diaz","perks":[]},"electionYear":515,""" +
        """"termStartsAt":1,"termEndsAt":2,"votingYear":516,"candidates":[{"key":"pets","name":"Diana",""" +
        """"perks":[],"votes":62133}]}"""

    @Test
    fun oldJsonDecodesWithNoLastElection() {
        val status = Json.decodeFromString<MayorStatus>(oldJson)

        assertEquals(emptyList(), status.lastElectionCandidates)
        assertEquals(listOf("Diana"), status.candidates.map { it.name })
    }

    @Test
    fun lastElectionCandidatesRoundTrip() {
        val last = listOf(Candidate("economist", "Diaz", emptyList(), 529099), Candidate("mining", "Cole", emptyList(), 407934))
        val status = Json.decodeFromString<MayorStatus>(oldJson).copy(lastElectionCandidates = last)

        assertEquals(status, Json.decodeFromString<MayorStatus>(Json.encodeToString(status)))
    }

    @Test
    fun emptyLastElectionIsOmitted() {
        val json = Json.encodeToString(Json.decodeFromString<MayorStatus>(oldJson))

        assertFalse("lastElectionCandidates" in json, json)
        assertTrue("\"candidates\"" in json, json)
    }
}
