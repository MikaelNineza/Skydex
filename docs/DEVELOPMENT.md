# Skydex development

Details for building, configuring and releasing Skydex. See the [README](../README.md) for installing and using the app.

## Modules

| Module | What it is |
|---|---|
| `app/` | Android app (Kotlin, Jetpack Compose) |
| `server/` | Ktor backend that proxies the Hypixel API and sends push alerts |
| `shared/` | Plain Kotlin models and Skyblock calendar logic used by both |

## Running locally

```bash
docker compose up -d          # Postgres + Redis
./gradlew :server:run         # server on http://localhost:8080/health
./gradlew :app:installDebug   # app on a connected device or emulator
```

The debug app talks to `http://localhost:8080/`, which `adb reverse` forwards to the server on your machine (emulator or USB phone). The debug build sets that up automatically; if you restart the emulator, run `adb reverse tcp:8080 tcp:8080` or rebuild.

## Configuration

Secrets come from environment variables (server) or `local.properties` (app). Never commit them.

| Variable | Used by | Purpose |
|---|---|---|
| `HYPIXEL_API_KEY` | server | Hypixel API key. Player lookups fail without it. |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | server | Postgres. Defaults match `docker-compose.yml`. |
| `DATABASE_ENABLED` | server | `false` runs without Postgres: no device registration or push alerts. |
| `FIREBASE_CREDENTIALS` | server | Path to a Firebase service-account JSON. Without it, push alerts are only logged. |
| `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` | app | Firebase client config. Without them, push notifications are turned off in the app. |
| `GITHUB_REPO` | server | Repo whose latest release is offered as an app update. Default `MikaelNineza/Skydex`; empty turns `/v1/app/latest` off. The app only installs releases from `MikaelNineza/Skydex`, so a fork must also change `RELEASE_REPO` in `app/src/main/kotlin/com/skydex/app/updates/UpdateDownloader.kt`. |
| `SKYDEX_GITHUB_TOKEN` | server | Optional. Raises GitHub's rate limit from 60 to 5000 requests/hour. A fine-grained token with no permissions is enough. |
| `SKYDEX_BASE_URL` | app (release) | The server release builds talk to, e.g. `https://your-server.example.com/` (a placeholder: example domains are rejected). Required to build a release. |
| `SKYDEX_KEYSTORE_PATH`, `SKYDEX_KEYSTORE_PASSWORD`, `SKYDEX_KEY_ALIAS`, `SKYDEX_KEY_PASSWORD` | app (release) | Release signing. Without them, release builds come out unsigned. |
| `SKYDEX_VERSION_NAME` (or `-Pskydex.versionName=`) | app | Version `MAJOR.MINOR.PATCH`. Set by the release workflow from the tag; local builds default to `0.0.1`. |
| `SKYDEX_DEBUG_UPDATE_CHECK` | app (debug) | `true` makes debug builds check for updates on launch (release builds always do). |

`./gradlew build`, `assemble`, `assembleRelease` and `bundleRelease` fail without a real `https://` `SKYDEX_BASE_URL`, by design: a release APK must never point at a placeholder server. Debug builds and unit tests don't need it.

## Releasing

Releases are built and published by `.github/workflows/release.yml` when a `v*` tag is pushed.

One-time setup:

1. Generate a release keystore (and keep it out of the repo; `.gitignore` already covers `*.jks` and `*.keystore`):
   ```bash
   keytool -genkeypair -v -keystore skydex-release.jks -alias skydex -keyalg RSA -keysize 4096 -validity 10000
   base64 -w0 skydex-release.jks   # the value for SKYDEX_KEYSTORE_BASE64
   ```
   On Windows, use PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("skydex-release.jks"))`.
2. Create an environment named `release` (Settings > Environments). Add required reviewers and a deployment rule that only allows `v*` tags, then add these environment secrets: `SKYDEX_KEYSTORE_BASE64`, `SKYDEX_KEYSTORE_PASSWORD`, `SKYDEX_KEY_ALIAS`, `SKYDEX_KEY_PASSWORD`, `SKYDEX_BASE_URL`, `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID`. The workflow refuses to build without them.
3. Add the repository variable `SKYDEX_CERT_SHA256` (Settings > Secrets and variables > Actions > Variables): the signing certificate's SHA-256 from `keytool -list -v -keystore skydex-release.jks`. The workflow fails if the APK is signed with any other certificate.

Each release:

```bash
git tag v1.2.3 && git push origin v1.2.3
```

- Versions are `vMAJOR.MINOR.PATCH` with minor and patch below 100 and no leading zeros. The versionCode is `major*10000 + minor*100 + patch`, so a version must never go down.
- The workflow tests, builds and signs the APK, checks its signing certificate and version, and publishes it as `skydex.apk` with a `skydex.apk.sha256` next to it. A release is only marked latest when it's the highest published version.
- Re-running the workflow is safe: a published release's APK is never replaced, and a partly failed (draft) one is finished.

> **Keep the keystore safe.** Every update must be signed with the same key. If you lose the keystore or its passwords, existing users can't update and must uninstall, which loses their settings. Keep at least two offline backups (for example a password manager and an encrypted USB drive), and never commit it.

### Android developer verification

Since 2026-09-30, certified Android devices in Brazil, Indonesia, Singapore and Thailand only install sideloaded apps from registered developers, and this goes global in 2027. Register as a developer and register the package `com.skydex.app` with the release signing certificate (`keytool -list -v -keystore skydex-release.jks` shows its SHA-256).

## Credits

Skill, event and crop icons are item renders from SkyCrypt (sky.shiiyu.moe) of Minecraft and Hypixel SkyBlock textures. Carrot and potato icons are vanilla Minecraft item textures. Mayor faces are cropped from their Hypixel SkyBlock skins. All rights remain with their respective owners. Contest crop data from elitebot.dev. Skydex is not affiliated with or endorsed by Mojang or Hypixel.
