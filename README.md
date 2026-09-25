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

The debug app talks to the server at `http://10.0.2.2:8080/` (your machine, as seen from the emulator).

## Configuration

Secrets come from environment variables (server) or `local.properties` (app). Never commit them.

| Variable | Used by | Purpose |
|---|---|---|
| `HYPIXEL_API_KEY` | server | Hypixel API key. Player lookups fail without it. |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | server | Postgres. Defaults match `docker-compose.yml`. |
| `DATABASE_ENABLED` | server | `false` runs without Postgres: no device registration, history or push alerts. |
| `FIREBASE_CREDENTIALS` | server | Path to a Firebase service-account JSON. Without it, push alerts are only logged. |
| `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` | app | Firebase client config. Without them, push notifications are turned off in the app. |
