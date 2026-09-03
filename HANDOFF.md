# Gamble Client Launcher — Launcher Handoff

Last updated: 2026-09-03 UTC

This document covers the launcher repository only. Client behavior is in `/home/theac/Desktop/GambleClient/HANDOFF.md`; Site/API and publishing are in `/home/theac/Desktop/cg-mod-release/HANDOFF.md` and `/home/theac/Desktop/RELEASE_HANDOFF.md`.

## Source and current release

- GitHub: [Gamble-Client/GambleLauncher](https://github.com/Gamble-Client/GambleLauncher)
- Repository: `/home/theac/Desktop/gamble-client-launcher`
- Current working branch: `codex/launcher-ui-security-pass-20260821`
- Published artifact source: `d91805a` (`Move sponsor verification to Dashboard`); the repository may advance with documentation-only handoff commits.
- Current public launcher version: `0.1.131`; the Dashboard sponsor migration is live.
- Current standalone-loader feed: `1.4.22`; the published loader now has the same trusted-origin backend failover as the launcher for connection failures.
- Release candidate: launcher `0.1.132` and standalone loader `1.4.23`. They are not public until source-matched Windows, Linux, and Flatpak workflows and the production audit pass.
- Native workflow runs: Windows `33437745051`; Linux `33437744351`
- Universal JAR and the Windows/Linux native packages are the current immutable artifacts. Versions `0.1.113` through `0.1.129` are superseded.

The launcher supports the managed native workflow, the universal JavaFX JAR, and the Swing fallback. The owner uses this standard Gamble Client launcher flow; old Prism directories are not current runtime evidence.

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
- Source version `0.1.131` removes the launcher media resolver, embedded video/player fallback, media CSP grant, sponsor overlay, and old launcher reward calls. The old launcher reward routes return `410` with Dashboard guidance.

## Security and platform notes

- Java network requests use HTTPS and explicit trusted-host allowlists; bearer-bearing redirects are disabled. Downloads validate bounded redirects and response sizes. Existing signed provenance and memory-only loader behavior remain required.
- The launcher verifies personalized-loader provenance, platform markers, immutable-core fingerprints, and enrollment before accepting managed jars. No executable payloads are expected in public loader jars.
- The JavaFX reflection bridge is kept through ProGuard. macOS JavaFX dylibs are merged as universal Intel/Apple-Silicon entries, and the native ACL/window capability is explicit.
- The Flatpak package bundles Java 21 and starts the Swing compatibility interface, avoiding host-Java and WebKit dependencies. Its sandbox grants network, X11, audio, DRI, and only the shared Gamble/Minecraft data paths.
- OS credential-store protection for Windows tokens, independently signed launcher artifacts, managed-Java digest pinning, and a packaged macOS native guard remain future hardening work.
- Do not add an unsigned or half-finished injection route. Any future injection architecture needs explicit trust, authorization, process compatibility, rollback, and support design.

## Verification state

Completed for `0.1.130`:

- Vite production build, Gradle tests, 24 launcher contract tests, ProGuard/hardened-JAR checks, Rust formatting, and packaged Linux/Windows network self-tests.
- Hosted Windows and Linux native tests/builds, including Windows fresh install, same-version reinstall/update, accelerated WebView/render smoke, and source-matched diagnostic DOM checks.
- Physical Linux GUI smoke: Play reached the Minecraft title screen; an empty profile remained responsive through Play/sign-in/Cancel. A second stored session returned HTTP 401, so a second authenticated-account launch was not available.
- Sponsor-media 200/range checks and public artifact metadata/byte/hash/provenance checks.

Published launcher release `0.1.131` (2026-08-31):

- Native offline launch fallback, startup-exit popup, dashboard-only sponsor verification, updated account copy, and 23 launcher contract tests are implemented.
- Launcher Vite build, Gradle tests, Rust formatting, and `git diff --check` pass. The clean release tree passed 194 Site/API tests plus the release audit, and the Pages build passed.
- Hosted Windows and Linux native tests/builds, packaged network self-tests, R2 uploads, and matching manifest checks passed. Local `cargo test --manifest-path src-tauri/Cargo.toml` remains unavailable on this host because GTK/WebKit development packages (`gdk-3.0`, `pango`, `libsoup-3.0`, and related `.pc` files) are missing.

Release-candidate verification for `0.1.132` (2026-09-03):

- 26 frontend/security tests, Gradle tests, hardened-JAR verification, Rust formatting, and 24 Rust tests passed; the one ignored Rust test separately downloaded 128 real Minecraft 1.21.11 asset objects successfully.
- Clean Ubuntu 24.04, Arch Linux, and installed Flatpak users passed all three Gamble gateways plus Mojang, Fabric, and the exact asset-CDN check. Each clean GUI stayed alive for the 15-second smoke window.
- The official Flatpak manifest lint passed. The bundle uses the latest available Java 21 extension runtime (`25.08`); the linter only notes that the platform has a newer `26.08` runtime whose Java 21 extension is not yet published.
- Real source-matched Windows, native Linux package, and hosted Flatpak workflow runs remain required before publication.

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
