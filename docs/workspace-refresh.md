# Launcher workspace refresh

Released in launcher 0.1.136, 2026-09-10 UTC, from immutable source `c511b8cc2165c2f8fe0c11917d8cb526aa2f164f`.

## Changes

- Removed repeated boxed page banners and the always-visible sponsor card. Ad-tier accounts see the ordinary Play action, then the existing Dashboard notice only if freshly checked access is missing. Backend authorization remains mandatory.
- Profiles use a scrollable vertical list and a separate account/build/folder editor. New profile is explicitly labeled; custom profiles can be renamed in place. Changes remain scoped to that profile, and deletion still requires confirmation.
- Settings lead with controls rather than paragraphs. Explanations support hover, keyboard focus and click. Memory is no longer hidden behind Advanced. Diagnostics/logs collapse unless results are available; startup-help still runs checks and opens results.
- The frontend-design skill guided compact hierarchy, consistent dark/amber styling and keyboard-accessible contextual help.

## Verification

Passed: `npm test` (62), `npm run build`, `./gradlew test --rerun-tasks` (42), Rust suite (58 passed; one existing live-network test ignored), `git diff --check`.

Browser suites (local synthetic accounts, no external traffic):

- `scripts/launcher-ui-smoke.mjs`: six tiers; Play/Stop, sponsor denial, plain-profile exception, diagnostics, sign-out and minimum-size CTA.
- `scripts/launcher-edge-ux-regression.mjs`: five focus, busy-control and long-path regressions.
- `scripts/launcher-ux-regression.mjs`: nine creation, deletion, Unicode, keyboard and settings regressions. Focus visibility now checks actual viewport bounds after focus, rather than scrolling an element that asynchronous rendering can replace.
- `scripts/launcher-layout-regression.mjs`: 820/1120/1440px, absence of sponsor messaging before Play, required notice afterward, vertical profile selection, create/rename/reload persistence, visible memory and keyboard/click help.

Run these with a local Vite server, e.g. `npm run dev -- --port 5187`, and a Playwright installation via `LAUNCHER_PLAYWRIGHT_MODULE` (optional `LAUNCHER_CHROMIUM`). Scripts accept local URL and screenshot output directory arguments. Screenshots from this run: `/tmp/launcher-layout`, `/tmp/launcher-refresh-smoke`, `/tmp/launcher-refresh-edge`, `/tmp/launcher-refresh-ux`; Play, Profiles and Settings screenshots personally inspected.

Windows startup run `34429550987` and six-account WebView run `34429623600` passed. Final publishable installer run `34430964295` and live public installer run `34431338322` passed clean install, same-version reinstall, packaged networking and nonblank rendering. Both screenshots were personally inspected. The public installer matches the approved bytes; the final public universal JAR passes hardened verification with 90 protected classes.

No real Microsoft sign-in, real ad-provider completion or authenticated Minecraft playthrough is claimed. Same-version reinstall is not proof of a 0.1.135-to-0.1.136 migration. No loader or payload changes were included.
