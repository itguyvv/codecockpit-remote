# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| latest release | ✅ |
| older releases | ❌ — please upgrade |

## Reporting a vulnerability

Please do **not** open a public issue for security vulnerabilities.

Use GitHub's [private vulnerability reporting](https://github.com/ITGuyVV/codecockpit-remote/security/advisories/new) to contact the maintainer directly. Please include:

- A description of the issue and its impact
- Steps to reproduce or a proof of concept
- The affected version(s)

You will get a response within a few days. Once a fix is released, you will be credited in the release notes unless you prefer otherwise.

## Scope notes

CodeCockpit Remote is a companion Android app that talks to a CodeCockpit desktop over a direct WebSocket connection. Its security model:

- TLS with a self-signed certificate pinned by fingerprint (TOFU — the fingerprint is shown for manual comparison on first connect and pinned afterwards).
- The pairing secret and the issued device token are stored in the app's private DataStore, which is excluded from Android cloud backup and device transfer.
- There is no cloud component. Traffic only ever flows between the phone and the desktop on the local network or a Tailscale tailnet.

Storing the pairing secret and token inside the app's private storage on the device itself is documented behavior, not a vulnerability. Anything that would let a remote attacker connect to a desktop without the secret, bypass the fingerprint pin, or extract the stored credentials outside the app sandbox is in scope.
