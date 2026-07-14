# Launcher security

## Reporting

Do not publish a launcher vulnerability or a token from a local diagnostics file. Report it privately through the Gamble Client support Discord with the launcher version, operating system, reproduction steps, and the least sensitive useful log excerpt.

## Current protections

- Launcher API paths and browser URLs are allowlisted.
- Client and launcher update artifacts require byte-length and SHA-256 verification.
- Downloads and archive extraction have compressed/expanded limits and path checks.
- The Tauri webview uses a restrictive Content Security Policy.
- Remote commands have matching server/client allowlists and require confirmation unless the user trusts the paired device.

## Work required before a public-source release

1. Sign Windows and Linux artifacts and document verification.
2. Pin the managed Java runtime to a reviewed digest.
3. Store Windows launcher and Microsoft tokens in the operating-system credential store.
4. Add dependency review, `cargo audit`, lockfile review, and secret scanning to pull requests.
5. Commission an independent authentication, updater, archive, local IPC, and publishing review.

Do not describe the launcher as fully audited until these items are complete.
