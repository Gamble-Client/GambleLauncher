# Launcher security

## Reporting

Do not publish a launcher vulnerability or a token from a local diagnostics file. Report it privately through the Gamble Client support Discord and include the launcher version, operating system, reproduction steps, and the least sensitive log excerpt that demonstrates the problem.

## Current trust boundaries

- The native launcher accepts API calls only under the Gamble Client launcher, Spotify status, and friends routes.
- Browser links use an explicit host allowlist. Java's browser helper also permits loopback HTTP for local workflows; the native filesystem opener is a separate command, not the browser-URL guard.
- Native API and metadata requests use an explicit HTTPS origin allowlist; bearer-token requests do not follow redirects.
- Runtime downloads validate every redirect destination against the same allowlist and cap text responses before parsing them.
- Personalized loaders require signed immutable-core provenance, platform validation and fresh enrollment. Native launcher updates require exact size/SHA-256 metadata. Java reports update links rather than installing updates itself; ordinary Mojang/Fabric caches are not equivalent to signed loader artifacts.
- Downloads bound actual response bytes and archive expansion. Native/JRE extraction stages output before replacing active files; Java native extraction rejects bad lengths/checksums before publishing that archive. JavaFX resource-pack listing does not inflate Fabric mod metadata.
- Launcher/Microsoft session and enrollment files are staged privately before sensitive bytes are written, using Unix owner-only permissions or Windows ACLs. Filesystems unable to protect secrets fail closed. These permissions are not encryption or protection from code running as the same user.
- Minecraft authentication arguments use a private Java argument file, not OS-visible token arguments. Files are cleaned after child exit or failed spawn; the next startup/launch reclaims marked dead-child leftovers. Unknown/incomplete process records are retained conservatively for manual cleanup. Platform-native encoding must preserve the arguments exactly.
- The Tauri webview uses a restrictive Content Security Policy; remote scripts, objects, frames, and forms are disabled.
- Protected-client delivery, launch-ticket verification and entitlement enforcement belong to the separately reviewed standalone loader/backend, not this repository. The launcher has no remote-command feature.
- The launcher does not hide module flags or implement scanner-evasion behavior. False-positive reports should be handled with provenance, reproducible source, and signed release artifacts.

## Remaining hardening work

The public source tree contains no deployment secrets or private signing keys.
The following defense-in-depth work remains:

1. Configure signed Windows and Linux release artifacts and publish signature verification instructions.
2. Pin the managed Java runtime to a reviewed release digest instead of relying only on the vendor HTTPS endpoint.
3. Protect locally stored Windows session and Microsoft refresh tokens with the operating-system credential store; file permissions alone are not equivalent to encryption at rest.
4. Keep dependency review, lockfile review, and secret scanning enabled for pull requests.
5. Commission an independent review of authentication, updater, archive extraction, local IPC, and release-publishing paths.

The launcher must not be described as fully audited until those remaining items are complete.
The local September 2026 audit patches are not a published release; Windows ACL,
code-page, installer and real-account verification remain required before promotion.
