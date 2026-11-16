# Contributing to CodeCockpit Remote

Thank you for considering a contribution! Issues, bug reports, and pull requests are all welcome.

## Getting started

Requirements:

- Android Studio Ladybug or newer (AGP 8.7), or a local Android SDK 35 with JDK 17
- A device or emulator on Android 8.0+ (minSdk 26)
- A CodeCockpit desktop build with **Remote access** enabled, for end-to-end testing

```bash
git clone <your fork>
cd codecockpit-remote/android
./gradlew assembleDebug    # build the debug APK
./gradlew installDebug     # install on the connected device/emulator
```

Open the `android/` directory in Android Studio and press *Run* for the normal edit–build–test loop.

## Repository layout

This repository hosts **all CodeCockpit Remote clients**, one directory per platform:

```
android/   Android app (Kotlin + Jetpack Compose) — the first client
ios/       future iOS client
harmony/   future HarmonyOS client
```

The wire protocol all clients speak is documented in the desktop repository: [docs/REMOTE_PROTOCOL.md](https://github.com/ITGuyVV/CodeCockpit/blob/main/docs/REMOTE_PROTOCOL.md).

## Android project layout

```
android/app/src/main/java/io/github/itguyvv/codecockpitremote/
  protocol/   Wire contract with the desktop (message types, parsers, builders)
  net/        OkHttp WebSocket client, TOFU TLS fingerprint capture, auth state machine
  data/       DataStore persistence for desktop profiles (secret, token, pinned fingerprint)
  vm/         AppViewModel: navigation, connection lifecycle, chat state
  ui/         Compose screens (servers / sessions / chat) and theme
```

The protocol this app speaks is documented in the desktop repository: [docs/REMOTE_PROTOCOL.md](https://github.com/ITGuyVV/CodeCockpit/blob/main/docs/REMOTE_PROTOCOL.md).

## Conventions

- **English-only source.** All identifiers, comments, commit messages, and docs are English. No natural-language strings outside the resource files under `android/app/src/main/res/`.
- **No hardcoded UI strings.** Every user-facing string lives in `android/app/src/main/res/values/strings.xml`; screens reference it via `stringResource(...)`.
- **Kotlin.** Coroutines + StateFlow for async state; no new heavyweight dependencies without discussion.
- Keep PRs focused: one bug fix or one feature per PR.

## Commit messages

Use concise, imperative English subjects (`fix: ...`, `feat: ...`, `docs: ...`, `build: ...`), e.g.:

```
feat: show background task count on session cards
fix: re-pair when the desktop revokes the stored token
docs: document Tailscale setup in README
```

## Reporting bugs

Open an issue with the [bug report template](../../issues/new?template=bug_report.yml) — it asks for everything needed:

- Device model and Android version, connection type (LAN / Tailscale),
- app and desktop versions,
- steps to reproduce and what you expected vs. saw.

Security vulnerabilities go through [private vulnerability reporting](SECURITY.md) instead of public issues.

## License

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
