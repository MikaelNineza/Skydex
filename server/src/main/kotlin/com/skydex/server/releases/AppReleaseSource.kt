package com.skydex.server.releases

import com.skydex.server.hypixel.SingleValueCache
import com.skydex.shared.model.AppRelease
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.log

/** The newest published app release. */
interface AppReleaseSource {
    /** Null when there is no published release. Throws [com.skydex.server.hypixel.UpstreamException]. */
    suspend fun latest(): AppRelease?
}

/**
 * Caches GitHub's answer (including "none") for [ttlMillis], so the server makes at most a few requests an hour.
 * When a refresh fails the last answer is served, and GitHub isn't retried for [failureBackoffMillis] (or its
 * Retry-After / reset, if longer).
 */
class CachedAppReleaseSource(
    private val client: GitHubReleaseClient,
    clock: () -> Long = System::currentTimeMillis,
    ttlMillis: Long = 15 * 60_000,
    failureBackoffMillis: Long = 60_000,
) : AppReleaseSource {
    // SingleValueCache needs a non-null value, so "no release" is wrapped.
    private class Latest(val release: AppRelease?)

    private val cache = SingleValueCache<Latest>(ttlMillis, failureBackoffMillis, clock)

    override suspend fun latest(): AppRelease? = cache.get { Latest(client.latestRelease()) }.release
}

private val REPO = Regex("""[A-Za-z0-9-]+/[A-Za-z0-9._-]+""")

/** From `github.repo` / `github.token` in application.conf. Null (endpoint off, logged) when the repo is blank or malformed. */
fun Application.appReleaseSource(http: HttpClient): AppReleaseSource? {
    val repo = environment.config.propertyOrNull("github.repo")?.getString().orEmpty().trim()
    if (!REPO.matches(repo)) {
        log.warn("github.repo is not owner/name ('$repo'): GET /v1/app/latest is off")
        return null
    }
    val token = environment.config.propertyOrNull("github.token")?.getString().orEmpty().trim()
    return CachedAppReleaseSource(GitHubReleaseClient(http, repo, token))
}
