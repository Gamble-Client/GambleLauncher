# Gamble Client Launcher — Launcher Handoff

Last updated: 2026-09-05 UTC

This document covers the launcher repository only. Client behavior is in `/home/theac/Desktop/GambleClient/HANDOFF.md`; Site/API and publishing are in `/home/theac/Desktop/cg-mod-release/HANDOFF.md` and `/home/theac/Desktop/RELEASE_HANDOFF.md`.

## Source and current release

- GitHub: [Gamble-Client/GambleLauncher](https://github.com/Gamble-Client/GambleLauncher)
- Repository: `/home/theac/Desktop/gamble-client-launcher`
- Current working branch: `codex/launcher-ui-security-pass-20260821`
- Published artifact source: `209aff4319e331dbbf3198347f4a70842589f027`; later handoff-only commits do not change the package bytes.
- Current public launcher: `0.1.133`; current standalone loader: `1.4.24`; current client build: `20260905052753` (`1.248`).
- Source-matched publication runs: Windows `33946836353`, Linux DEB/RPM `33946838214`, and Flatpak `33946839381`. Prepublication runs: Windows `33945840421`, Linux `33945841278`, Flatpak `33945842206`, and six-fixture WebView/diagnostic matrix `33945842974`.
- Final staged installer smoke: `33947183098`; final live public-installer smoke: `33947469178`.
- The universal JAR, Windows NSIS installer, DEB, RPM, and bundled-Java Flatpak are the current immutable artifacts. MSI remains intentionally unavailable.

The launcher supports the managed native workflow, the universal JavaFX JAR, and the Swing fallback. The owner uses this standard Gamble Client launcher flow; old Prism directories are not current runtime evidence.

## Released changes (2026-09-05)

- Coordinated publication delivered launcher `0.1.133`, loader `1.4.24`, and client build `20260905052753`/version `1.248`. Artifact sources: launcher `209aff4`, client `db37985c9` (client code identical to tested `0aad5422`), and site `5c09863`; site metadata/state commits are `2707eea`/`9f06d6c`. Later documentation-only commits do not change artifact identity.
- Reworked first-run Play and account/profile presentation; removed the blocking client-update detour; added accessible dialogs/progress, direct diagnostics/mod/pack actions and minimum-size coverage. Fresh sponsor access is profile-scoped and rechecked when returning from the Dashboard, including Java/Flatpak.
- Added private credential/enrollment staging, bounded HTTP/ZIP reads and safe failed-download handling, non-inflating resource-pack previews, private Java argument files with exact platform encoding, and bounded/cancellable native OAuth callback reads. Existing provenance, entitlement and host restrictions remain mandatory.
- Real Windows gates caught and fixed elevated-token file ownership, canonical Windows ACL descriptor comparisons, and cleanup when a terminated child still has an open process handle. Exact current-user ownership/permissions remain enforced; foreign writers and uncertain process identities do not get accepted.
- Coordinated loader fixes address the consumed-Mixin-queue registration race and Windows staging ownership/ACL handling. Complete local client/loader suite: 676 tests, zero failures/errors/skips; focused baseline/production loader matrix: 112 cases plus a packaged-entrypoint test. Hosted Windows run `33946761073` passed 126 native/JNI and real Fabric/Knot loader checks, zero failures/errors/skips.
- All five independently obfuscated tiers and three loader platforms were released. The exact `client/scripts/build-tiers.sh` replay passed in `/tmp/gamble-client-final-tiers.bzm4HK`, without changing canonical `/home/theac/Desktop/Built Jars`. All tier entry names and uncompressed contents match the release; four ZIP containers differ only in entry timestamps. Release tier and three loaders are byte-identical.
- Site/backend: 199 tests and static build passed. Integrated release and remote byte/hash/provenance audits passed. Current Pages deployment: `https://85504fd0.gamble-client-b67.pages.dev`; canonical production: `https://gambleclient.org`.
- See `docs/audit-2026-09-05.md` for scoped security findings, tests, published hashes and remaining hardening work.

## Current launch flow

- A Gamble Client profile installs the authenticated standalone loader into the selected profile’s `mods` folder and removes only proven old `cg-mod` artifacts. Fabric API remains required for plain Fabric/Client profiles; the Gamble loader is required only for Gamble Client profiles.
- The selected profile’s account override is passed to that launch only. It does not rewrite the global Accounts default; reconnect restores the prior default when needed and labels an inherited choice `Default (follows Accounts)`.
- Native and Java paths require fresh launcher enrollment before and after managed-loader installation, quarantine duplicate active standalone loaders, and append the selected profile’s `fabric.modsFolder` after custom JVM arguments.
- Windows same-name replacement uses a rollback-safe remove/rename sequence because Windows rename does not overwrite an existing target. The same helper protects launcher installer updates.
- First-party API and signed-download calls use bounded retries across `https://gambleclient.org`, `https://dash.gambleclient.org`, and the independently routed stable Pages origin `https://gamble-client-b67.pages.dev`. Transport errors are mapped to recovery guidance, not opaque Java stack traces. The packaged binary exposes `--network-self-test`.
- Browser Microsoft sign-in runs off the UI thread and has a real cancellation path. Play/Cancel leaves the UI rendered and does not leave a stale callback worker or dead launch modal.
- Native Play permits a launcher-authenticated offline Minecraft session when no Microsoft account is saved, using the same deterministic `OfflinePlayer` UUID and `legacy` auth type as the universal Java launcher. Microsoft authentication remains required for online servers; launcher/client authorization is unchanged.
- If Minecraft exits before its window appears, the native UI now shows the exit reason and points to Settings → Diagnostics instead of silently returning to idle.

## Graphics and crash containment

Launcher settings expose four separate choices: `Automatic`, `Safe graphics`, `Software fallback`, and a validated `DRI_PRIME` selector.

- Automatic keeps the launcher UI hardware-accelerated. On Linux AMD DRM hosts it applies the pre-JVM conservative environment (`MESA_GLTHREAD=0` and the bounded AMD Mesa debug flags) before Java starts.
- Safe graphics disables risky Mesa paths without forcing all rendering through software. Software is opt-in. `GAMBLE_WEBKIT_SAFE_MODE=1` is launcher-UI-only.
- `WEBKIT_DISABLE_DMABUF_RENDERER` and other launcher WebKit variables are stripped from the Minecraft child. This prevents the launcher workaround from making the game software-rendered or unnecessarily laggy.
- In a real AMDGPU fault, the launcher records the marker, classifies GPUVM/context-loss output, retains the game exit result, and does not auto-restart a wedged Minecraft process. The next automatic launch selects `DRI_PRIME=1` when another DRM GPU is available.
- Startup diagnostics include detected DRM devices, requested graphics mode/GPU, environment, and the client’s later OpenGL/Mesa renderer record. A normal client exception, such as the Donut SMP scoreboard NPE, must not be mislabeled as a GPU reset.

The known RX 6800 incident is a Mesa/AMDGPU GPUVM page fault triggered by Java rendering, followed by a ring timeout, reset, lost VRAM, and context loss in KDE/Xwayland/WebKit. It is not a normal launcher Java exception. The client-side report and current fix are documented in `/home/theac/Desktop/GambleClient/HANDOFF.md`.

## Dashboard sponsor verification

- Ad Tier sponsor playback is no longer embedded in the launcher. The Tauri frontend and universal Java Swing/JavaFX paths receive access state only and send users to `https://dash.gambleclient.org/dashboard.html?section=free` when sponsored time is missing.
- The Dashboard owns the normal-browser media session. The backend issues a one-use challenge with a 30-second not-before time, a five-minute expiry window, a 60-second per-account start cooldown, and a 72-hour bank cap. The page tracks forward visible playback and the server remains authoritative at completion.
- The current release contains no launcher media resolver, embedded video/player fallback, media CSP grant, sponsor overlay, or old launcher reward calls. The old launcher reward routes return `410` with Dashboard guidance.

## Security and platform notes

- Java network requests use HTTPS and explicit trusted-host allowlists; bearer-bearing redirects are disabled. Downloads validate bounded redirects and response sizes. Existing signed provenance and memory-only loader behavior remain required.
- The launcher verifies personalized-loader provenance, platform markers, immutable-core fingerprints, and enrollment before accepting managed jars. No executable payloads are expected in public loader jars.
- The JavaFX reflection bridge is kept through ProGuard. macOS JavaFX dylibs are merged as universal Intel/Apple-Silicon entries, and the native ACL/window capability is explicit.
- The Flatpak package bundles Java 21 and starts the Swing compatibility interface, avoiding host-Java and WebKit dependencies. Its sandbox grants network, X11, audio, DRI, and only the shared Gamble/Minecraft data paths.
- OS credential-store protection for Windows tokens, independently signed launcher artifacts, managed-Java digest pinning, and a packaged macOS native guard remain future hardening work.
- Do not add an unsigned or half-finished injection route. Any future injection architecture needs explicit trust, authorization, process compatibility, rollback, and support design.

## Verification state

Published and verified for `0.1.133` (2026-09-05):

- Vite production build, 36 Node tests, 41 local Java tests, hardened-JAR verification (88 protected classes), Rust formatting and 57 Linux Rust tests passed. The separately run live-network test downloaded 128 real Minecraft `1.21.11` asset objects.
- Windows hosted VMs passed 57 Rust tests and 38 Java tests; three Java skips are expected for POSIX symlinks, procfs argv and the full-Unicode fixture under legacy CP1252. Native-code-page argument round-trip checks passed.
- The source-matched Windows WebView/DOM matrix passed six synthetic account fixtures: inactive and active Ad Tier, giveaway, Beta Weekly, Media and Owner. First Play, sponsor/plain-profile behavior, diagnostics, sign-out, dialog focus and 820×560 layout passed.
- Windows, DEB/RPM and Flatpak publication manifests match exact source `209aff4319e331dbbf3198347f4a70842589f027`. Packaged network checks and Flatpak Swing GUI startup smoke passed.
- Final staged installer smoke `33947183098` and live public-installer smoke `33947469178` passed clean install, same-version reinstall, packaged network and nonblank render. The independently downloaded public Windows installer is 2,732,056 bytes, SHA-256 `50a7cf2ef4f28dfeb01cbdf0bd3a99395a174cc6e681286029ef769186aee635`.
- All public launcher downloads passed the release remote byte/hash/provenance audit. Published package hashes are recorded in the audit report.
- Flatpak bundles Freedesktop/OpenJDK `25.08` and starts the Swing interface; update guidance opens the download page. The native RPM/DEB selector is not the Flatpak entrypoint.
- These are real Windows JVM/NTFS/JNI, packaged installer and WebView checks, but account flows use fixtures. They are **not** an authenticated interactive Minecraft desktop or real Microsoft-account login/playthrough.

Useful live checks still worth repeating after launcher changes:

1. Fresh profile with no launch data, normal profile, and a profile-specific account override.
2. Windows same-version update, interrupted replacement recovery, and a clean new-machine install.
3. macOS JavaFX launch plus Swing fallback, including executable permissions and universal native loading.
4. AMD automatic/safe/DRI_PRIME modes while confirming the launcher remains accelerated and the game child does not inherit WebKit software-rendering flags.
5. Two real Microsoft accounts with global default/profile override independence.

## Verification commands

```bash
cd /home/theac/Desktop/gamble-client-launcher
npm run build
./gradlew test
git diff --check
```

Native packaging is performed by the GitHub Actions workflows. The release procedure waits for matching Windows, Linux, and Flatpak manifests before publishing native metadata; see `/home/theac/Desktop/RELEASE_HANDOFF.md`.

## Operating constraints

- Preserve unrelated changes and inspect Git status before editing or releasing.
- Never print or commit secrets from `/home/theac/Desktop/cg-mod-release/server/.env`.
- Publish only the current universal JAR and the native artifacts supplied by matching Actions manifests. MSI is intentionally `404`; do not create duplicate ZIP/TAR/macOS packages.
