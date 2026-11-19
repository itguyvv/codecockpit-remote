# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **IPv6 direct scenario** — the add-desktop dialog now offers *Same network* vs *IPv6 direct*. The IPv6 scenario takes the desktop's public IPv6 address (e.g. `2408:8256:…`) plus the pairing secret and warns up front when the phone's own network has no global IPv6 address, so the user knows before trying.

### Removed

- **Any-network link mode** (device ID + signaling relay + WebRTC DataChannel). Relayed fallbacks required either an untrusted public TURN or a paid one, and hole punching is unreliable when both ends sit behind carrier-grade NAT. The direct WebSocket protocol is unchanged; IPv6 direct covers the across-the-internet case whenever both ends have IPv6, and tunnel apps such as Tailscale remain the general answer. v0.2.0 remains available as the last link-mode release.

## [0.2.0] - 2026-11-19

### Added

- **Any-network connection (link mode)** — connect by the desktop's 8-digit device ID plus the pairing secret, from anywhere: a lightweight signaling relay (self-hostable Cloudflare Worker) introduces the peers, then all traffic flows over a WebRTC DataChannel with hole punching and a TURN fallback. The secret is never sent to the relay; authentication still happens end-to-end inside the DataChannel. Scenario picker in the add-desktop dialog: *Direct (ID + secret for any network)* vs *Same network (host & port)*.
- In-app language picker with 10 languages, applied immediately and persisted.
- Automatic self-update: the app checks GitHub Releases on launch, shows a banner when a new version is out, and installs the downloaded APK.
- The link-mode server address defaults to the production relay; release builds point at it out of the box.

### Fixed

- Crash on saving a desktop profile (a serializer was only resolved at runtime, so release builds crashed while debug builds worked).
- Error messages for link-mode desktops now name the profile instead of a host:port.

## [0.1.1] - 2026-11-18

### Fixed

- Sign-in and pairing hints no longer assume a local network; direct connections are described as direct (with Tailscale as one example).

## [0.1.0] - 2026-11-18

### Added

- Initial release in development: companion Android app for CodeCockpit (requires a CodeCockpit desktop with the Remote access feature enabled).
- Saved desktop profiles; secret-based first pairing that exchanges into a device token; TOFU certificate fingerprint verification on first connect with pinning afterwards.
- Live session list for the connected desktop: engine, title, working directory, busy/idle/exited state, model, cost.
- Chat-style session monitor: streamed conversation bubbles, expandable tool-call rows, live token/cost/turns stats.
- Send prompts into the session PTY and switch the permission mode from the phone.
- Automatic reconnection with backoff; automatic re-pairing with the stored secret when the desktop revokes the token.
- Direct connection only — LAN or Tailscale. No cloud server, no relay, ever.
