# Skydex

An Android app that tracks your Hypixel Skyblock profile and tells you about upcoming events.

> **Work in progress:** Skydex is still being built. Features may be incomplete and things may change or break.

## How to use

1. Open the app and pick your Minecraft player.
2. Browse your profile: skills, XP and more.
3. Check the Events tab for upcoming Skyblock events, the current mayor and farming contests.
4. Turn on alerts in Settings to get a notification before an event starts.

## Install

Download the latest APK from [GitHub Releases](https://github.com/MikaelNineza/Skydex/releases/latest/download/skydex.apk) on your phone and open it. Android will ask you to allow "Install unknown apps" for your browser, and may show a Play Protect warning, as it does for most apps installed outside the Play Store.

New versions appear as a banner in the app, or under Settings > App > Check for updates. Updates are never forced.

**Uninstall:** Settings > Apps > Skydex > Uninstall. A debug build must be uninstalled before installing a release APK, because they are signed with different keys.

## Build from source

```bash
docker compose up -d          # Postgres + Redis
./gradlew :server:run         # server on http://localhost:8080/health
./gradlew :app:installDebug   # app on a connected device or emulator
```

Configuration, the project layout and the release process are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## License

[MIT](LICENSE)

Skydex is not affiliated with or endorsed by Mojang or Hypixel. Icons and skins belong to their owners; see [Credits](docs/DEVELOPMENT.md#credits).
