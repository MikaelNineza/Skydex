# Skydex

An Android app that tracks your Hypixel Skyblock profile and tells you about upcoming events.

> **Work in progress:** Skydex is still being built. Features may be incomplete and things may change or break.

## Modules

| Module | What it is |
|---|---|
| `app/` | Android app (Kotlin, Jetpack Compose) |
| `server/` | Ktor backend that proxies the Hypixel API, stores snapshots, and sends push alerts |
| `shared/` | Plain Kotlin models and Skyblock calendar logic used by both |

## Running locally

```bash
docker compose up -d          # Postgres + Redis
./gradlew :server:run         # server on http://localhost:8080/health
./gradlew :app:installDebug   # app on a connected device or emulator
```

The server reads `HYPIXEL_API_KEY` and `DATABASE_URL` from environment variables.
