# CodeCockpit Remote

[![CI](https://github.com/ITGuyVV/codecockpit-remote/actions/workflows/ci.yml/badge.svg)](https://github.com/ITGuyVV/codecockpit-remote/actions/workflows/ci.yml)
[![GitHub release](https://img.shields.io/github/v/release/ITGuyVV/codecockpit-remote?sort=semver)](https://github.com/ITGuyVV/codecockpit-remote/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/ITGuyVV/codecockpit-remote/total)](https://github.com/ITGuyVV/codecockpit-remote/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**[简体中文](README.zh-CN.md)**

The companion Android app for [CodeCockpit](https://github.com/ITGuyVV/CodeCockpit) — monitor and drive your AI coding CLI sessions (Claude Code / Codex / OpenCode) from your phone. Direct device-to-device connection over your LAN or Tailscale. **No cloud server, no relay, ever.**

## What it does

- **Live session list** — every running session on your desktop with its engine (claude / codex / opencode), title, working directory, busy/idle/exited state, model, and cost.
- **Chat-style monitor** — the session's conversation streams to your phone as bubbles; tool calls appear as compact, expandable rows.
- **Prompt from anywhere** — type into the chat box and your prompt goes straight into the session's PTY, exactly as if you had typed it on the desktop.
- **Permission mode switch** — flip between Ask / Accept edits / Plan / Bypass permissions per session.
- **Live stats** — model, tokens in/out, cost, and turn count update while the agent works.
- **Stays connected** — automatic reconnection with backoff; automatic re-pairing with your saved secret if the desktop revokes the token.

## Security model

The remote channel is designed for a developer's own devices on a trusted network:

1. **TLS, pinned by fingerprint.** The desktop serves a self-signed certificate. On first connect the app shows the certificate's SHA-256 fingerprint — compare it with the one displayed in the desktop's *Settings → Remote access* tab, then trust it (TOFU). The fingerprint is pinned per desktop afterwards; a changed fingerprint refuses the connection.
2. **Pairing secret → device token.** The first connect uses the pairing secret shown on the desktop. The desktop issues the device a random token and stores only its hash. The app reconnects with the token and never sends the secret again — unless the desktop revokes the token, in which case it re-pairs automatically.
3. **Secrets stay on the device.** The secret, token, and pinned fingerprint live in the app's private datastore, which is excluded from Android cloud backup and device transfer.
4. **No cloud, no relay.** Traffic only ever flows phone ↔ desktop on your LAN or tailnet. Both sides speak a [documented protocol](https://github.com/ITGuyVV/CodeCockpit/blob/main/docs/REMOTE_PROTOCOL.md).

## Setup

**Prerequisites**

- [CodeCockpit](https://github.com/ITGuyVV/CodeCockpit) on your desktop with the **Remote access** feature enabled
- Android 8.0+ on the phone

**Steps**

1. On the desktop, open *Settings → Remote access*, enable it, and copy the **pairing secret**. Note the port (default `47315`). On Windows, the first enable pops the firewall prompt — accept it.
2. Install the app ([releases](https://github.com/ITGuyVV/codecockpit-remote/releases) or build from source).
3. Tap **+**, enter the desktop's LAN IP (shown in the same settings tab), the port, and the secret. Give it a name.
4. On the first connect the app shows the **certificate fingerprint** — compare it with the fingerprint displayed in the desktop's Remote access tab, then tap *Trust*.
5. Done — the session list appears. Tap a session to open its chat.

**Away from home?**

Enable [Tailscale](https://tailscale.com) on both the desktop and the phone, then add the desktop with its tailnet IP (the settings tab marks Tailscale addresses for you). The connection works identically — same TLS pin, same secret, zero extra ports exposed to the internet.

## Building from source

```bash
git clone https://github.com/ITGuyVV/codecockpit-remote
cd codecockpit-remote
./gradlew assembleDebug     # app/build/outputs/apk/debug/
```

or open the project in Android Studio (Ladybug+) and press Run. Requirements: JDK 17, Android SDK 35, Android 8.0+ device (minSdk 26). See [CONTRIBUTING.md](CONTRIBUTING.md) for the project layout and conventions.

## Roadmap

- **M2** — conversation history for exited sessions (desktop-side disk snapshot endpoint)
- **M3** — create and kill sessions, change working directory from the phone
- **M4** — hole-punched connectivity beyond LAN/Tailscale (WebRTC). A public relay will **not** be built.

## License

[MIT](LICENSE)
