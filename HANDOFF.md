# Gamble Client Launcher — current handoff

Updated 2026-09-08 UTC. This chat owns the launcher **and standalone loader**. The client chat owns payload features. Do not route launcher/loader fixes to client or include unrelated payload changes in a launcher release.

## Current publication and source

- Repository: /home/theac/Desktop/gamble-client-launcher; GitHub: https://github.com/Gamble-Client/GambleLauncher.
- Branch: `codex/launcher-ui-security-pass-20260821`.
- Public launcher: **0.1.135**, immutable artifact source `9cde361e88d0d23890f77c79d4b5632de3a8c8ca`. Later documentation commits are not package sources. Do not rebuild or overwrite this version from a later HEAD.
- Unchanged client payload: `20260907233226` / version `1.250`, source `4782952f1cfa3cba75fa70b2f55d63a7b4bcca5d`.
- Unchanged standalone loader: `1.4.26`, source `f5ce16348f47b63edd2f3a8be3be075fda13d24d`. Source directory: /home/theac/Desktop/GambleClient/client/standalone-loader.
- Launcher-only Site metadata/state commits: `c7f6c56` / `c0a2f63`. Canonical production: https://gambleclient.org.
- Current artifacts: Windows NSIS installer, RPM, DEB, bundled-Java Flatpak and universal JavaFX JAR. MSI is intentionally unavailable.
- Public Windows and JAR downloads were independently compared byte-for-byte with the tested/staged artifacts. Final JAR was checked after the release workflow's macOS native merge, including a hardened verification of the downloaded public bytes.

## 0.1.135 fixes and proof

User explicitly authorized publishing independently verified fixes while the original affected-user crash remains unconfirmed. The earlier publication hold is lifted; this release is live.

- Delayed early Minecraft exits now show a warning and Diagnostics guidance. Normal later closure, intentional Stop and already-dismissed warnings do not produce repeated startup warnings.
- Out-of-order process polls cannot replace newer running state with stale state. Late account, friends, Spotify and manifest responses are discarded after sign-out.
- Cancelling sign-in while its poll response is in flight prevents that response from completing sign-in.
- Keyboard focus survives rendering on the exact selected profile and checkbox. Busy checkboxes look disabled when changes would be ignored.
- Long filenames use two-line titles, full hover text and descriptive toggle labels. Diagnostic rows have consistent padding and readable line spacing. Existing visual design retained.
- Windows packaging explicitly propagates failed frontend test exit codes; a later successful build cannot mask failure. The test-source harness also normalizes CRLF.
- Runtime/state/focus regressions were red before their correction and green afterward. Long-path layout checks are additional coverage, not a claim that previously working wrapping was broken.

Verification:

- Local: 62 Node tests, 42 Java tests, production frontend build, 58 Rust tests (one separate live-network test ignored), hardened JAR 90 protected classes.
- Browser: five edge regressions, nine existing interaction regressions, six account smoke fixtures and 96 account/view/size screens. No detected horizontal overflow, unhandled browser error or automated WCAG A/AA 2.1 violation. Before/after screenshots inspected.
- Windows startup run `34272906573`: 62 Node and 58 Rust passed, including a real Java21 child through production PrivateChild/private-argument handling. Spaces/accented profile path, escaped arguments, sustained readiness, stdout/stderr, exit0/73 and cleanup are covered.
- Source-matched builds: Windows `34272921414`, Linux `34272924077`, Flatpak `34272906615`, all passed. Windows Java: 39 passed / three expected platform skips. Flatpak branch build was artifact-only; release staged its exact bundle and manifest without rebuilding.
- Source-matched Windows WebView/six-account diagnostic run `34272926669` passed.
- Exact candidate installer `34273687905` and final live public installer `34274479726` passed clean install, same-version reinstall, network, ten render-proof cases and nonblank rendering. Both screenshots personally inspected.

**Limits:** Original Windows “launches then stops” cause is still unproven: no fresh affected-user log and unclear which process closes. Do not call this a proven Minecraft-crash fix. Real Windows hosted JVM/NTFS/installer/WebView checks were performed; account flows use fixtures. No real Microsoft login, authenticated Minecraft playthrough, real ad-provider completion or fully translated Windows OS test is claimed.

Evidence: /tmp/launcher-135-release.NrfoNQ. Full release report and hashes: /home/theac/Documents/Codex/2026-08-26/i-usually-will-have-one-chat-2/outputs/launcher-ux-audit/RELEASE-0.1.135.md. Reusable browser scripts: scripts/launcher-edge-ux-regression.mjs, scripts/launcher-ux-regression.mjs and scripts/launcher-ui-smoke.mjs.

Artifact SHA-256:

- Windows: `db73af98662a6a1528479c727cc522b9d8a8f4687fd0d349992ac7464b5fbf7e` (2733617 bytes).
- RPM: `0337230534389a2a73f6980fcbdac85130c164204507830ee3b9ad9e9388be53`.
- DEB: `998456f9e834d8e1f1719e27c7fb68da61dc2cdb14da0be6376d6656249fc327`.
- Flatpak: `8d0a536dd9a7dfc841f0d770cad89a5fb2bdd1f6eeca60e8759bd4e9bcb1bc3a`.
- Final universal JAR: `51751ed40eab05232f81f043992978e4d3502fefa1fbcf4a0c34114ea2078ed3` (11397979 bytes). Preflight JAR hash differed because the normal release repeated the ZIP/native merge; the final staged and public bytes were reverified.

## Earlier fixes retained

- 0.1.134 fixed first-click profile creation, profile radio arrows/Home/End and Escape dismissal, invalid/duplicate saved profile IDs, service-neutral429 advice preserving retry details, ticking sponsor displays and bounded Dashboard-return refresh, and Java build downgrade selection after fresh authorization.
- Windows render gates require nonblank client-area evidence or an actually successful installed-app DOM probe, and propagate probe failure. Merely creating a window is insufficient.
- Loader1.4.25 added sanitized console/Fabric diagnostics when private disk logging fails. An existing loader-error.log can be stale; use the current console diagnostic.
- Loader1.4.26 fixes localized Windows account recognition in private-storage/native-staging ACL checks. `BB6258E0F5` fingerprints the rejected parent-ACL check. Trust uses exact OS-derived SIDs from verified JDK principal classes, not translated names. Foreign writers, owner checks and unknown principal providers remain fail-closed; no permission bypass or user ACL weakening is required.
- Actual Windows loader run `34152170701` passed153 tests (6 native,74 baseline,73 production), including a genuinely renamed built-in Administrators group, native staging, hostile ACL controls and concurrent startup. Fresh rerun `34174824295` also passed153. This is not a fully French OS or authenticated gameplay test. Source evidence: GambleClient/docs/loader-1.4.26-windows-sid.md.
- Loader registration fixes preserve correct behavior after Mixin's pending queue is consumed.
- Site managed-launch preparation rate limit is12/15min per account; browser/standalone remains6/15min. The reported one-or-two-launch429 cause is unproven without a trace.

Earlier audit/release evidence: docs/audit-2026-09-05.md and workspace outputs/launcher-ux-audit/{REPORT.md,RELEASE-0.1.134.md}.

## Runtime and authorization boundaries

- Owner uses the standard Gamble Client/managed launcher flow, not Prism. Old Prism directories are not current runtime evidence.
- A Gamble profile installs the authenticated standalone loader into its selected profile's mods directory and removes only proven old cg-mod artifacts. Fabric API remains required for Fabric/Client profiles; Gamble loader only for Gamble profiles.
- Selected profile's account override applies to that launch only, never overwriting Accounts' global default. Inherited account selection is labeled Default (follows Accounts).
- Native and Java paths require fresh enrollment before and after managed-loader installation, quarantine duplicate active standalone loaders, and put the selected fabric.modsFolder after custom JVM arguments.
- Windows same-name replacement uses rollback-safe remove/rename handling for both managed jars and launcher updates.
- First-party bounded retries cover gambleclient.org, dash.gambleclient.org and the stable gamble-client-b67.pages.dev origin. Treat intentional routing as intentional; do not remove it casually. Packaged native executable has --network-self-test.
- Browser sign-in runs off the UI thread with cancellation. Native Play permits a launcher-authenticated offline Minecraft session when no Microsoft account is saved, matching Java's deterministic OfflinePlayer UUID/legacy auth. Online servers still require Microsoft; launcher/client authorization is unchanged.
- Early-exit timestamps are launcher observations, not proof that Minecraft displayed a window.

## Dashboard sponsor flow

- Launcher contains access state and opens https://dash.gambleclient.org/dashboard.html?section=free when sponsor time is missing. No embedded ad WebView/media resolver or launcher reward completion path.
- Dashboard/backend owns playback and verification: one-use challenge,30-second not-before,5-minute expiry,60-second account start cooldown,72-hour bank cap. Server is authoritative; UI countdowns do not grant access.
- Returning from Dashboard refreshes profile-scoped access in native and Java paths. Old launcher reward routes return410.

## Graphics and security

- Expose Automatic, Safe graphics, opt-in Software fallback and validated DRI_PRIME choices. Automatic keeps launcher UI accelerated and applies conservative pre-JVM Mesa flags on Linux AMD DRM hosts.
- Strip launcher WebKit/software-rendering variables from the Minecraft child. GAMBLE_WEBKIT_SAFE_MODE is launcher-UI-only.
- Record and classify actual AMD GPUVM/context-loss faults; do not automatically restart a wedged process or mislabel ordinary client exceptions. Automatic can select DRI_PRIME=1 after a known GPU failure when another device exists.
- Signed personalized-loader provenance, immutable-core/platform markers, enrollment and entitlements remain mandatory. Public loader jars must not contain executable payloads.
- Private credential/enrollment/Java-argument staging, bounded HTTP/ZIP reads and OAuth callbacks, trusted HTTPS hosts and disabled bearer redirects remain mandatory.
- JavaFX reflection bridge is preserved through ProGuard. Merge macOS dylibs for Intel and Apple Silicon and verify the final post-merge artifact.
- Flatpak bundles Java21/Freedesktop25.08 and starts Swing compatibility UI. Keep sandbox grants restricted to required network/X11/audio/DRI/shared Gamble/Minecraft paths.
- Deferred hardening: Windows credential-store tokens, independently signed launcher artifacts, managed-Java digest pinning and packaged macOS native guard. Do not add an unsigned/unfinished injection route.

## Release ownership and verification

Release chat owns publication/scripts. Runnable /home/theac/Desktop/release-gamble-client.sh and tracked Site copy must stay byte-identical. Native metadata requires exact source/version/hash/size manifests. Preserve unrelated changes; never rebuild/overwrite an already-published version. R2 read-before-put is not atomic across hosts.

Required launcher checks: npm run build; ./gradlew test; npm test; cargo test --manifest-path src-tauri/Cargo.toml; git diff --check. Use the existing .tauri-sysroot pkg-config configuration on this host. Real Java startup regression requires Java21 rather than silently skipping. For JAR release, perform macOS merge then standalone scripts/verify-hardened-jar.py.

Site/API and release context: /home/theac/Desktop/cg-mod-release/HANDOFF.md and /home/theac/Desktop/RELEASE_HANDOFF.md. Payload context: /home/theac/Desktop/GambleClient/HANDOFF.md. Keep this file synchronized with /home/theac/Desktop/GambleLauncher_HANDOFF.md. Never print or commit server/.env secrets.
