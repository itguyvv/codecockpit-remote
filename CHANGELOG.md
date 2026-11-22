# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-11-22

### Added

- **Edit saved desktops** — every desktop card gains an edit action: rename it or change the address, port, or pairing secret. Changing the secret clears the stored pairing, so the next connection re-verifies the certificate and re-pairs; saving dials again right away (the button says *Save & connect*).

### Fixed

- **Sessions created or resumed from the phone appeared on neither end.** The phone now renders the new session immediately from the `session_created` summary (or synthesizes one from the create arguments on older desktops), and the desktop adopts it via `pty:created` (requires CodeCockpit v1.6.0+ on the desktop).
- **The first prompt after creating or resuming a session vanished.** It is now shown as an optimistic pending bubble — dimmed with a clock while in flight, red cross on error or after a 30-second timeout — and reconciled once the engine echoes it.
- **The chat did not follow new output.** Autoscroll now also tracks streaming growth of the last message (not just new messages), follows immediately on send, and a stale history snapshot can no longer roll the thread back during a reconnect.
- **The history sheet collapsed back to half-screen when tapping a folder group.** It now opens fullscreen and stays expanded; switching engines keeps the cached list visible instead of flashing a spinner.

### Changed

- Polish pass: typography scale and shape system, haptic feedback on send/confirm/destructive actions, animated chat rows, redesigned empty states, success snackbars for create/rename/stop, a reconnect banner with attempt counter, a send/stop button morph, and the sidebar became a real gesture drawer with edge swipe and back-button support. Requires CodeCockpit v1.6.0+ for the two-ended session sync fixes.

## [0.3.0] - 2026-11-21

### Added

- **IPv6 direct scenario** — the add-desktop dialog now offers *Same network* vs *IPv6 direct*. The IPv6 scenario takes the desktop's public IPv6 address (e.g. `2408:8256:…`) plus the pairing secret and warns up front when the phone's own network has no global IPv6 address, so the user knows before trying.
- **Workbench home screen** — the session list becomes a rail of compact tiles (title snippet + live status dot) with a slide-in drawer grouping sessions by folder: collapse groups, and open, rename, or stop sessions from each row's menu. Requires CodeCockpit v1.5.0+ on the desktop.
- **Create sessions from the phone** — pick an engine, a permission mode (full permissions is the default), and a folder (from your project history with name + full path, or free text), plus an optional title. Destructive actions (stop session, delete a history entry) ask for confirmation first.
- **Session history & resume** — browse the desktop's native session history per engine, grouped by folder, and resume any past session; the conversation and stats rebuild within seconds.
- **Tool output in chat** — tool cards show a command summary line, expand to the full input (selectable text, no line cap), and display the captured tool output.
- **Plan cards** — task/plan updates (TodoWrite, TaskCreate/TaskUpdate, update_plan) render as progress cards.
- **Follow the stream** — the chat auto-follows while output streams, yields when you scroll up, and a jump-to-latest button returns to the bottom; a busy pulse row and a red stop button (sends Esc) appear while the agent works.

### Changed

- Status badges match the desktop's four states (running / busy / exited / idle) and update in real time; the top bar is two-row with the live status line and a permission-mode menu.
- Enabling bypass permissions requires an explicit confirmation.

### Removed

- **Any-network link mode** (device ID + signaling relay + WebRTC DataChannel). Relayed fallbacks required either an untrusted public TURN or a paid one, and hole punching is unreliable when both ends sit behind carrier-grade NAT. The direct WebSocket protocol is unchanged; IPv6 direct covers the across-the-internet case whenever both ends have IPv6, and tunnel apps such as Tailscale remain the general answer. v0.2.0 remains available as the last link-mode release.

### Fixed

- Empty assistant bubbles (messages that only carried token usage) no longer render as blank rows.

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
