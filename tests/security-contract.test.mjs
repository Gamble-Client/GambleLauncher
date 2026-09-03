import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("Ad Tier launchers send users to the Dashboard instead of embedding sponsor playback", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const javaFx = await source("src/main/java/com/gambleclient/launcher/FxMain.java");

    assert.doesNotMatch(frontend, /\/api\/launcher\/ad-reward\//);
    assert.doesNotMatch(java, /\/api\/launcher\/ad-reward\//);
    assert.doesNotMatch(javaFx, /\/api\/launcher\/ad-reward\//);
    assert.match(frontend, /selectedBuild\.id === "ad_tier" && !state\.ads\?\.active/);
    assert.match(frontend, /DASHBOARD_FREE_URL/);
    assert.match(frontend, /data-action="open-dashboard"/);
    assert.match(java, /openDashboardForAds\(\)/);
    assert.match(java, /30-second sponsor break/);
    assert.doesNotMatch(java, /Watch Sponsor First/);
    assert.match(java, /dashboardSponsorRequired/);
    assert.match(javaFx, /openDashboardForAds\(\)/);
    assert.doesNotMatch(frontend, /<video|sponsor-media|resolveSponsorMediaUrl/);
    assert.doesNotMatch(javaFx, /MediaPlayer|MediaView|WebView|<video|cacheSponsorMedia/);
});

test("the native launcher security policy no longer grants embedded media access", async () => {
    const config = JSON.parse(await source("src-tauri/tauri.conf.json"));
    assert.doesNotMatch(config.app.security.csp, /media-src/);
});

test("both launcher implementations bind manifests to the requested tier and install the memory loader", async () => {
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(java, /Backend manifest was issued for a different client tier/);
    assert.match(java, /ensureLoaderJar\(\)/);
    assert.match(java, /isMemoryLoaderJar\(loader\)/);
    assert.match(java, /fetchStandaloneLoaderVersion\(\)/);
    assert.match(java, /isCurrentMemoryLoaderJar\(loader\)/);
    assert.match(java, /compareVersionStrings\(installed, latest\)/);
    assert.match(java, /removeManagedClientArtifactsForMemory\(\)/);
    assert.match(java, /cg-mod:build_variant/);
    assert.match(java, /standaloneLoaderPlatform\(\)/);
    assert.match(java, /\\"launcherManaged\\":true/);
    assert.match(java, /fresh one-time enrollment/);
    assert.match(java, /LOADER_PROVENANCE_ENTRY/);
    assert.match(java, /isExpectedLoaderFabricMetadata\(metadata\)/);
    assert.match(java, /gcclient\.loader\.StandaloneLoader/);
    assert.match(java, /verifyLoaderProvenance\(bytes, provenance, metadataVersion\)/);
    assert.match(java, /Managed loader immutable core was modified/);
    assert.doesNotMatch(java, /retaining the valid installed loader/);
    assert.doesNotMatch(java, /Launcher-managed memory bootstrap/);

    assert.match(rust, /Backend manifest was issued for a different client tier/);
    assert.match(rust, /ensure_loader_jar\(&profile, &token\)/);
    assert.match(rust, /is_memory_loader_jar\(&loader\)/);
    assert.match(rust, /fetch_standalone_loader_version\(\)/);
    assert.match(rust, /current_memory_loader_is_current\(&loader\)/);
    assert.match(rust, /compare_version_strings\(&installed, &latest\)/);
    assert.match(rust, /fetch_client_manifest\(&build, &token\)/);
    assert.match(rust, /"windows" => "windows"/);
    assert.match(rust, /"linux" => "linux"/);
    assert.match(rust, /"launcherManaged": true/);
    assert.match(rust, /LOADER_PROVENANCE_ENTRY/);
    assert.match(rust, /is_expected_loader_fabric_metadata\(&metadata\)/);
    assert.match(rust, /gcclient\.loader\.StandaloneLoader/);
    assert.match(rust, /verify_loader_provenance\(bytes, &provenance, metadata_version\)/);
    assert.match(rust, /Managed loader immutable core was modified/);
    assert.match(rust, /unwrap_or\(false\)/);
    assert.doesNotMatch(rust, /Ok\(true\) \| Err\(_\)/);
    assert.doesNotMatch(rust, /Launcher-managed memory bootstrap/);
});

test("launch authorization stays in the standalone loader instead of launcher files", async () => {
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(java, /Gamble Client will be authorized and loaded from memory by the standalone loader/);
    assert.doesNotMatch(java, /license\.txt|paste-your-license-key-here|readLegacyLicenseKey|readLicenseKey/);
    assert.doesNotMatch(java, /-Dgamble\.launchTicketFile=/);
    assert.doesNotMatch(java, /fabric\.addMods/);
    assert.doesNotMatch(java, /\/api\/launcher\/license/);
    assert.doesNotMatch(java, /requestLauncherLicense/);
    assert.doesNotMatch(java, /complete\.body\.get\("licenseKey"\)/);
    assert.match(rust, /Standalone loader will create the launch ticket/);
    assert.doesNotMatch(rust, /-Dgamble\.launchTicketFile=/);
    assert.doesNotMatch(rust, /fabric\.addMods/);
    assert.doesNotMatch(rust, /write_launch_ticket_file/);
});

test("launcher release checks cannot mint a direct client download", async () => {
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.doesNotMatch(java, /Backend manifest did not include a client download URL/);
    assert.doesNotMatch(rust, /Backend manifest did not include a client download URL/);
    assert.match(java, /gcclient-memory-loader\.txt/);
    assert.match(rust, /gcclient-memory-loader\.txt/);
    assert.match(java, /verified-memory-only-v1/);
    assert.match(rust, /verified-memory-only-v1/);
});

test("production launcher UI omits owner-specific preview data and sanitizes visible diagnostics", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const javaFx = await source("src/main/java/com/gambleclient/launcher/FxMain.java");
    const bootstrap = await source("src/main/java/com/gambleclient/launcher/LauncherBootstrap.java");
    const cargo = await source("src-tauri/Cargo.toml");

    assert.match(frontend, /VITE_LAUNCHER_TEST_FIXTURES === "true"/);
    assert.match(frontend, /const PREVIEW = TEST_ACCOUNT_FIXTURES \|\| \(import\.meta\.env\.DEV/);
    assert.doesNotMatch(frontend, /\/home\/theac|BaseToucher|8667ba71b85a4004af54457a9734eed7|preview-token/);
    assert.doesNotMatch(java, /BaseToucher/);
    assert.match(frontend, /function publicMessage\(/);
    assert.match(frontend, /error sending request/);
    assert.match(frontend, /Could not reach the Gamble Client backend/);
    assert.doesNotMatch(frontend, /log\(`Managed root:/);
    assert.match(java, /diagnosticLog\("  Full command:/);
    assert.doesNotMatch(java, /log\("  Full command:/);
    assert.match(java, /sanitizeVisibleMessage\(/);
    assert.match(javaFx, /String visible = sanitizeVisibleMessage\(message\)/);
    assert.match(javaFx, /return sanitizeVisibleMessage\(current\.getMessage\(\)/);
    assert.match(bootstrap, /if \(diagnostics\) cause\.printStackTrace/);
    assert.doesNotMatch(bootstrap, /showFallbackNotice\(cause\)/);
    assert.match(cargo, /\[profile\.release\][\s\S]*strip = "symbols"/);
    assert.match(cargo, /panic = "abort"/);
});

test("launcher frontend and native package versions stay synchronized", async () => {
    const packageJson = JSON.parse(await source("package.json"));
    const frontend = await source("src/main.js");
    const flatpak = await source("flatpak/org.gambleclient.Launcher.yml");
    const flatpakMetadata = await source("flatpak/org.gambleclient.Launcher.metainfo.xml");
    assert.match(frontend, new RegExp(`const LAUNCHER_VERSION = ["']${packageJson.version.replaceAll(".", "\\.")}["'];`));
    assert.match(flatpak, /build\/flatpak\/gamble-client-launcher\.jar/);
    assert.match(flatpakMetadata, new RegExp(`<release version="${packageJson.version.replaceAll(".", "\\.")}"`));
});

test("Flatpak bundles Java 21 and grants only launcher-required host capabilities", async () => {
    const manifest = await source("flatpak/org.gambleclient.Launcher.yml");
    const wrapper = await source("flatpak/gamble-client-launcher");

    assert.match(manifest, /org\.freedesktop\.Sdk\.Extension\.openjdk21/);
    assert.match(manifest, /\/usr\/lib\/sdk\/openjdk21\/install\.sh/);
    assert.match(manifest, /--share=network/);
    assert.match(manifest, /--socket=x11/);
    assert.match(manifest, /--socket=pulseaudio/);
    assert.match(manifest, /--device=dri/);
    assert.match(manifest, /--filesystem=~\/\.minecraft:create/);
    assert.match(manifest, /--filesystem=~\/\.local\/share\/gamble-client:create/);
    assert.doesNotMatch(manifest, /--filesystem=(?:host|home)|--device=all|--talk-name=|--socket=wayland/);
    assert.match(wrapper, /\/app\/jre\/bin\/java/);
    assert.match(wrapper, /if \[ -z "\$\{GAMBLE_CLIENT_GAME_DIR:-\}" \]/);
    assert.match(wrapper, /GAMBLE_CLIENT_GAME_DIR="\$HOME\/\.local\/share\/gamble-client\/minecraft"/);
    assert.match(wrapper, /--swing "\$@"/);
});

test("launcher settings survive unavailable WebView storage", async () => {
    const frontend = await source("src/main.js");

    assert.match(frontend, /function readStorage\(key, fallback = ""\)/);
    assert.match(frontend, /function writeStorage\(key, value\)/);
    assert.match(frontend, /WebKit private mode, profile permissions, or quota limits/);
    assert.match(frontend, /const value = readStorage\(key\);/);
    assert.doesNotMatch(frontend, /localStorage\.setItem\(/);
});

test("native production assets use relative URLs inside the embedded WebView", async () => {
    const packageJson = JSON.parse(await source("package.json"));
    assert.equal(packageJson.scripts.build, "vite build --base ./");
});

test("the native window retries embedded navigation only after the Windows event loop is ready", async () => {
    const rust = await source("src-tauri/src/main.rs");

    const config = JSON.parse(await source("src-tauri/tauri.conf.json"));
    assert.equal(config.app.windows[0].useHttpsScheme, true);
    assert.notEqual(config.app.windows[0].create, false);
    assert.match(rust, /matches!\(event, tauri::RunEvent::Ready\)/);
    assert.match(rust, /window\.navigate\(app_url\.clone\(\)\)/);
    assert.match(rust, /https:\/\/tauri\.localhost\/index\.html/);
});

test("automatic update prompts never cover an active sign-in flow", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");

    assert.match(frontend, /if \(signInInProgress\(\)\) return "";/);
    assert.match(frontend, /state\.signInActive \|\| Boolean\(state\.signIn\) \|\| Boolean\(state\.microsoftSignIn\)/);
    assert.match(java, /if \(isLauncherSignInActive\(\)\) \{[\s\S]*Launcher update prompt deferred/);
});

test("the hardened universal JAR keeps every JavaFX reflection bridge member", async () => {
    const proguard = await source("proguard-launcher.pro");
    const bridgeMethods = [
        "loadDisplayNames",
        "saveDisplayNames",
        "enableAntiScreenshareHud",
        "disableAntiScreenshareHud",
        "getModsFolder",
        "getResourcePacksFolder",
        "getMinecraftFolder"
    ];
    const bridgeFields = ["launcherDisplayName", "clientDisplayName", "graphicsMode", "gpuSelector"];

    for (const member of bridgeMethods) {
        assert.match(proguard, new RegExp(`${member}\\(`), `missing kept method: ${member}`);
    }
    for (const member of bridgeFields) {
        assert.match(proguard, new RegExp(`\\*\\*\\* ${member};`), `missing kept field: ${member}`);
    }
});

test("expired Microsoft refresh tokens reopen sign-in instead of exposing an HTTP URL", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(frontend, /microsoftReconnectRequired\(error\)[\s\S]*await startMicrosoftSignIn\(\)/);
    assert.match(frontend, /Microsoft reconnected[\s\S]*Press Launch to start Minecraft/);
    assert.match(java, /microsoftReconnectRequired\(message\)[\s\S]*startMicrosoftSignIn\(true\)/);
    assert.match(java, /"invalid_grant"[\s\S]*MICROSOFT_REAUTH_REQUIRED/);
    assert.match(rust, /fn microsoft_refresh_error\([\s\S]*"invalid_grant"[\s\S]*MICROSOFT_REAUTH_REQUIRED/);
    assert.doesNotMatch(rust, /fn refresh_microsoft_token\([\s\S]{0,900}\.error_for_status\(\)/);
});

test("browser Microsoft sign-in can be cancelled without leaving launch progress stuck", async () => {
    const frontend = await source("src/main.js");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(frontend, /cancel_microsoft_browser_sign_in/);
    assert.match(frontend, /state\.launchProgress = null;[\s\S]*await startMicrosoftSignIn\(\)/);
    assert.match(frontend, /message\.toLowerCase\(\)\.includes\("microsoft sign-in cancelled"\)/);
    assert.match(rust, /static MICROSOFT_BROWSER_SIGNIN_GENERATION: AtomicU32/);
    assert.match(rust, /fn cancel_microsoft_browser_sign_in\(\)/);
    assert.match(rust, /wait_for_microsoft_callback\(&listener, generation\)/);
    assert.match(rust, /microsoft_browser_sign_in_cancelled\(generation\)/);
});

test("default-size launcher keeps account sign-in controls inside the visible content column", async () => {
    const css = await source("src/styles.css");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(css, /\.topbar,\s*\n\.screen-band \{[\s\S]*display: grid;[\s\S]*grid-template-columns: minmax\(0, 1fr\);/);
    assert.match(css, /\.topbar \.top-actions,\s*\n\.screen-band \.top-actions \{[\s\S]*width: 100%;/);
    assert.match(css, /\.topbar,\s*\n\.screen-band \{[\s\S]*grid-template-columns: minmax\(0, 1fr\);[\s\S]*row-gap: 12px;/);
    assert.match(css, /\.topbar,\s*\n\.screen-band \{[\s\S]*flex: 0 0 auto;/);
    assert.match(css, /\.topbar \{\s*\n\s*min-height: 0;\s*\n\s*padding-bottom: 14px;/);
    assert.match(rust, /const VERSION: &str = env!\("CARGO_PKG_VERSION"\);/);
    assert.doesNotMatch(rust, /const VERSION: &str = "\d+\.\d+\.\d+";/);
});

test("cancelling browser sign-in invalidates its background poll", async () => {
    const frontend = await source("src/main.js");

    assert.match(frontend, /const generation = \+\+state\.signInGeneration;/);
    assert.match(frontend, /generation !== state\.signInGeneration \|\| !state\.signInActive/);
    assert.match(frontend, /action === "cancel-signin"[\s\S]*state\.signInGeneration \+= 1;[\s\S]*state\.signInActive = false;/);
});

test("native and universal launchers expose the role-gated Dev build", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const frontendPolicy = await source("src/access-policy.js");
    const javaPolicy = await source("src/main/java/com/gambleclient/launcher/LauncherAccessPolicy.java");

    assert.match(frontend, /\{ id: "dev", label: "Dev" \}/);
    assert.match(frontendPolicy, /if \(build === "dev"\) return account\.devAccess === true \|\| hasOwnerAccess\(account\);/);
    assert.match(java, /new Build\("Dev", "dev"\)/);
    assert.match(javaPolicy, /case "dev" -> account\.devAccess\(\) \|\| hasOwnerAccess\(account\);/);
});

test("custom launcher chrome exposes explicit drag and resize paths", async () => {
    const frontend = await source("src/main.js");
    const css = await source("src/styles.css");
    const config = JSON.parse(await source("src-tauri/tauri.conf.json"));
    const capability = JSON.parse(await source("src-tauri/capabilities/main-window.json"));

    assert.match(frontend, /data-window-drag/);
    assert.match(frontend, /data-window-resize="NorthEast"/);
    assert.match(frontend, /startDragging\(\)/);
    assert.match(frontend, /startResizeDragging\(resizeHandle\.dataset\.windowResize\)/);
    assert.match(css, /\.window-resize-n[\s\S]*cursor: ns-resize/);
    assert.match(css, /\.window-resize-se[\s\S]*cursor: nwse-resize/);
    assert.deepEqual(config.app.security.capabilities, ["main-window"]);
    assert.deepEqual(capability.windows, ["main"]);
    for (const permission of [
        "core:window:allow-start-dragging",
        "core:window:allow-start-resize-dragging",
        "core:window:allow-minimize",
        "core:window:allow-toggle-maximize",
        "core:window:allow-close"
    ]) {
        assert.ok(capability.permissions.includes(permission), `missing Tauri capability: ${permission}`);
    }
});

test("profiles use a compact switcher with clear account, build, and folder controls", async () => {
    const frontend = await source("src/main.js");
    const css = await source("src/styles.css");

    assert.match(frontend, /data-action="toggle-profile-create"/);
    assert.match(frontend, /data-profile-create-menu/);
    assert.match(frontend, /data-action="select-new-profile-type"/);
    assert.match(frontend, /data-field="profileAccount"/);
    assert.match(frontend, /data-action="select-profile-build"/);
    assert.match(frontend, /data-view="mods"/);
    assert.match(frontend, /data-view="packs"/);
    assert.match(frontend, /id === "profiles" && \["mods", "packs"\]\.includes\(state\.view\)/);
    assert.match(frontend, /state\.profileCreateOpen = false/);
    assert.match(css, /\.profile-add-button/);
    assert.match(css, /\.profile-switcher/);
    assert.match(css, /\.profile-workspace-grid/);
    assert.match(css, /\.profile-folder-row/);
    assert.match(css, /\.settings-button \{[\s\S]*font-weight: inherit;/);
});

test("profile launches use their account without rewriting the launcher default", async () => {
    const frontend = await source("src/main.js");
    const rust = await source("src-tauri/src/main.rs");
    const launchBranch = frontend.slice(frontend.indexOf('action === "launch"'), frontend.indexOf('action === "microsoft"'));

    assert.match(launchBranch, /accountUuid: selectedAccount\?\.uuid \|\| ""/);
    assert.match(launchBranch, /const selectedBuild = buildForAccount\(\);\s*const message = await invoke\("launch_game"/);
    assert.doesNotMatch(launchBranch, /message: "Switching Microsoft account"/);
    assert.match(rust, /microsoft_account_for_launch\(&input\.account_uuid\)/);
    assert.match(rust, /fn microsoft_account_for_launch\(requested_uuid: &str\)/);
});

test("native launches support offline Minecraft sessions and explain startup exits", async () => {
    const frontend = await source("src/main.js");
    const rust = await source("src-tauri/src/main.rs");
    const launchBranch = frontend.slice(frontend.indexOf('action === "launch"'), frontend.indexOf('action === "microsoft"'));

    assert.doesNotMatch(launchBranch, /!selectedAccount\)[\s\S]*startMicrosoftSignIn\(\)/);
    assert.match(frontend, /Launching an offline session/);
    assert.match(frontend, /showExitPopup/);
    assert.match(rust, /fn offline_minecraft_identity\(username: &str\)/);
    assert.match(rust, /offline_minecraft_identity\(&input\.username\)/);
    assert.match(rust, /replace\("\$\{user_type\}", &identity\.user_type\)/);
});

test("Microsoft browser and device authentication do not block the launcher UI thread", async () => {
  const rust = await source("src-tauri/src/main.rs");
  assert.match(rust, /async fn microsoft_browser_sign_in\([\s\S]*?run_blocking\(move \|\| microsoft_browser_sign_in_blocking/);
  assert.match(rust, /async fn microsoft_device_start\([\s\S]*?run_blocking\(move \|\| microsoft_device_start_blocking/);
  assert.match(rust, /async fn microsoft_device_poll\([\s\S]*?run_blocking\(move \|\| microsoft_device_poll_blocking/);
});

test("graphics safety settings stay scoped to Minecraft and retain GPU crash evidence", async () => {
    const frontend = await source("src/main.js");
    const rust = await source("src-tauri/src/main.rs");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");

    assert.match(frontend, /graphicsMode: state\.graphicsMode/);
    assert.match(frontend, /gpuSelector: state\.gpuSelector/);
    assert.match(frontend, /data-field="graphicsMode"/);
    assert.match(frontend, /data-field="gpuSelector"/);
    assert.match(rust, /fn configure_launcher_webkit_environment\(\)/);
    assert.match(rust, /fn webkit_safe_mode_enabled\(value: &str\)/);
    assert.match(rust, /env::remove_var\("WEBKIT_DISABLE_DMABUF_RENDERER"\)/);
    assert.match(rust, /fn apply_game_graphics_environment\(/);
    assert.match(rust, /fn host_has_amd_drm\(\)/);
    assert.match(rust, /fn should_apply_amd_guard\(/);
    assert.match(rust, /AMD guard pre-JVM/);
    assert.match(rust, /WEBKIT_DISABLE_DMABUF_RENDERER/);
    assert.match(rust, /command\.env_remove\(key\)/);
    assert.match(rust, /fn record_minecraft_exit\(/);
    assert.match(rust, /gpu_fault/);
    assert.match(rust, /GAMBLE_GRAPHICS_MODE/);
    assert.doesNotMatch(rust, /command\.env\("AMD_FORCE_SHADER_USE_ACO"/);
    assert.doesNotMatch(java, /environment\.put\("AMD_FORCE_SHADER_USE_ACO"/);
    assert.match(java, /Do not set AMD_FORCE_SHADER_USE_ACO/);
});

test("plain Fabric profiles do not lock the Gamble loader", async () => {
    const rust = await source("src-tauri/src/main.rs");

    assert.match(rust, /is_required_mod_for_profile\(&profile, &lower\)/);
    assert.match(rust, /kind == ProfileKind::Client && base == LOADER_JAR_NAME/);
});
