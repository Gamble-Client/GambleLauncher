# Owner/developer multiple launch sessions

The Play page offers **Launch another** while Minecraft is running, only for accounts with explicit server-issued `ownerAccess: true` or `devAccess: true`. Build labels, Media/Tester/Beta access and string-valued flags do not qualify. Banned/revoked accounts fail closed. Native and Java handlers refresh `/api/launcher/account` before allocating a secondary profile and again before spawning the game; the UI is not the authorization boundary.

The existing Play/Stop action becomes **Stop all sessions** when multiple games are running. Stop intent survives a child exiting during the click/status-refresh race. Each child retains its own process ownership and diagnostic state. A secondary launch failure or child exit must not discard or stop another running child.

## Isolation and limits

- Extra games use exclusively allocated `minecraft/profiles/<kind>-session-<random>` directories on this machine. They start clean: no saves, custom mods, resource packs, settings or credential files are copied. Initial setup may download Minecraft dependencies again.
- Ordinary fresh loader enrollment, per-profile sessions, one-use tickets, shared machine device identity, entitlements and device-slot enforcement remain unchanged. There is no new backend bypass or cross-device privilege. This restricts the new launcher action; it does not introduce a global session lease policy for third-party launchers.
- Profiles are retained after exit so saves and diagnostics are not silently deleted. Native extra-session console logs live in that profile's `cg-mod/launcher-session.log`; Java keeps per-process logs in launcher data `launch-logs/`. Minecraft also keeps its own profile `logs/` directory.
- Stop ends all games tracked by that launcher window. No unrelated Minecraft process is discovered or killed. Each additional game consumes its configured RAM. Minecraft servers may independently restrict concurrent logins using the same Minecraft account.
- Local bridge ports are not duplicated or reassigned by this feature. An extra client cannot bind a port already owned by another instance; that remains a separate limitation.

## Verification

- `npm test` covers strict role flags, the additional-launch request, explicit Stop intent, independent session count and secondary crash reporting.
- `cargo test --manifest-path src-tauri/Cargo.toml` covers strict server role policy, isolated profile paths and two actual Java children through the private argument-file boundary. Ending one retains the live sibling, attributes the correct log, then stops the survivor.
- Java tests cover equivalent role policy, independent children, Stop all and isolated profile allocation.
- `scripts/launcher-multisession-regression.mjs` checks owner and dev-only UI fixtures at 820/1120/1440 pixels, two starts and Stop all, plus absence of the action for four ordinary account fixtures. The existing six-account UI smoke also passes.

These checks are not an authenticated two-Minecraft playthrough or a Windows packaged-installer validation. Do not claim those without separate evidence. No launcher version was bumped and existing published packages must not be overwritten.
