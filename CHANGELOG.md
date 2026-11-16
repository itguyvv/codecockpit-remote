# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial release in development: companion Android app for CodeCockpit (requires a CodeCockpit desktop with the Remote access feature enabled).
- Saved desktop profiles; secret-based first pairing that exchanges into a device token; TOFU certificate fingerprint verification on first connect with pinning afterwards.
- Live session list for the connected desktop: engine, title, working directory, busy/idle/exited state, model, cost.
- Chat-style session monitor: streamed conversation bubbles, expandable tool-call rows, live token/cost/turns stats.
- Send prompts into the session PTY and switch the permission mode from the phone.
- Automatic reconnection with backoff; automatic re-pairing with the stored secret when the desktop revokes the token.
- Direct connection only — LAN or Tailscale. No cloud server, no relay, ever.
