package com.skydex.app.data.repository

import com.skydex.app.FakeServer
import com.skydex.app.TestStore
import com.skydex.app.data.remote.ApiException
import com.skydex.app.respondJson
import com.skydex.app.sampleProfile
import com.skydex.app.sampleSelection
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer()
    private val testStore by lazy { TestStore(folder) }
    private val repository by lazy { ProfileRepository(server.api, testStore.store) }

    @After fun tearDown() = testStore.close()

    @Test
    fun `a fetched profile is returned fresh`() = runTest {
        testStore.store.select(sampleSelection)
        server.handler = { respondJson(sampleProfile) }

        assertEquals(ProfileResult(sampleProfile, fromCache = false), repository.profile(sampleSelection))
    }

    @Test
    fun `when the server is unreachable the cached profile is returned`() = runTest {
        testStore.store.select(sampleSelection)
        server.handler = { respondJson(sampleProfile) }
        repository.profile(sampleSelection)

        server.handler = { throw IOException("offline") }

        assertEquals(ProfileResult(sampleProfile, fromCache = true), repository.profile(sampleSelection))
    }

    @Test
    fun `with nothing cached the error propagates`() = runTest {
        testStore.store.select(sampleSelection)
        server.handler = { respondError(HttpStatusCode.BadGateway) }

        val error = runCatching { repository.profile(sampleSelection) }.exceptionOrNull()

        assertTrue(error is ApiException)
    }

    @Test
    fun `selecting another profile drops the cache`() = runTest {
        testStore.store.select(sampleSelection)
        server.handler = { respondJson(sampleProfile) }
        repository.profile(sampleSelection)

        val other = sampleSelection.copy(profileId = "p2")
        testStore.store.select(other)
        server.handler = { throw IOException("offline") }

        assertTrue(runCatching { repository.profile(other) }.exceptionOrNull() is IOException)
    }
}
