# Launcher security

## Reporting

Do not publish a launcher vulnerability or a token from a local diagnostics file. Report it privately through the Gamble Client support Discord and include the launcher version, operating system, reproduction steps, and the least sensitive log excerpt that demonstrates the problem.

## Current trust boundaries

- The native launcher accepts API calls only under the Gamble Client launcher, Spotify status, and friends routes.
- Browser links are restricted to HTTPS and an explicit host allowlist.
- Client and launcher update artifacts require a server-provided byte length and SHA-256 match before use.
- Downloads have compressed and expanded size limits, and archive paths are checked before extraction.
- Launcher and Microsoft session files use owner-only permissions on Unix.
- The Tauri webview uses a restrictive Content Security Policy; remote scripts, objects, frames, and forms are disabled.
- Remote Minecraft commands have matching client-side and server-side allowlists and require confirmation unless the user explicitly trusts the paired device.

## Open-source readiness items

The source tree contains no deployment secrets or private signing keys. Before a public release, the maintainers still need to:

1. Configure signed Windows and Linux release artifacts and publish signature verification instructions.
2. Pin the managed Java runtime to a reviewed release digest instead of relying only on the vendor HTTPS endpoint.
3. Protect locally stored Windows session and Microsoft refresh tokens with the operating-system credential store; file permissions alone are not equivalent to encryption at rest.
4. Add dependency review, `cargo audit`, lockfile review, and secret scanning to pull requests.
5. Commission an independent review of authentication, updater, archive extraction, local IPC, and release-publishing paths.

The launcher must not be described as fully audited until those remaining items are complete.
