import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { resolveSponsorMediaUrl } from "../src/sponsor-media.js";

const source = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("sponsor rewards use launcher-only API routes", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");

    assert.match(frontend, /\/api\/launcher\/ad-reward\/start/);
    assert.match(frontend, /\/api\/launcher\/ad-reward\/complete/);
    assert.doesNotMatch(frontend, /\/api\/license\/ad-reward\//);
    assert.match(frontend, /selectedBuild\.id === "ad_tier" && !state\.ads\?\.active/);
    assert.match(java, /sponsoredAccessActiveFor\(build\)/);
    assert.match(java, /Watch Sponsor First/);
    assert.match(frontend, /muted loop playsinline/);
    assert.match(frontend, /Sponsor media did not begin playing/);
});

test("sponsor media resolves against the production site instead of the embedded app origin", () => {
    assert.equal(
        resolveSponsorMediaUrl("/assets/placeholder-ad.mp4"),
        "https://gamble-client.store/assets/placeholder-ad.mp4"
    );
    assert.equal(
        resolveSponsorMediaUrl("/api/launcherads/demo"),
        "https://gamble-client.store/api/launcherads/demo"
    );
    assert.equal(resolveSponsorMediaUrl("http://gamble-client.store/assets/ad.mp4"), "");
    assert.equal(resolveSponsorMediaUrl("https://example.com/ad.mp4"), "");
    assert.equal(resolveSponsorMediaUrl("https://user@gamble-client.store/ad.mp4"), "");
});

test("JavaFX sponsor fallback advances only while media is actually playing", async () => {
    const fx = await source("src/main/java/com/gambleclient/launcher/FxMain.java");
    assert.match(fx, /SponsorPlaybackState playback = sponsorPlaybackState\(\)/);
    assert.match(fx, /document\.querySelector\('video, audio'\)/);
    assert.match(fx, /waitingSeconds\[0\] >= 15/);
    assert.match(fx, /Media unavailable/);
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

    assert.match(rust, /Backend manifest was issued for a different client tier/);
    assert.match(rust, /ensure_loader_jar\(&profile, &token\)/);
    assert.match(rust, /is_memory_loader_jar\(&loader\)/);
    assert.match(rust, /fetch_standalone_loader_version\(\)/);
    assert.match(rust, /current_memory_loader_is_current\(&loader\)/);
    assert.match(rust, /compare_version_strings\(&installed, &latest\)/);
    assert.match(rust, /fetch_client_manifest\(&build, &token\)/);
});

test("launch authorization stays in the standalone loader instead of launcher files", async () => {
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(java, /Local license files cleared; launch tickets handle current client access/);
    assert.match(java, /Gamble Client will be authorized and loaded from memory by the standalone loader/);
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

test("Windows retries the embedded app navigation only while WebView2 is blank", async () => {
    const rust = await source("src-tauri/src/main.rs");

    assert.match(rust, /tauri::Url::parse\("http:\/\/tauri\.localhost\/"\)/);
    assert.match(rust, /\[250, 1_000, 2_500\]/);
    assert.match(rust, /url\.as_str\(\) != "about:blank"/);
    assert.match(rust, /window\.navigate\(app_url\.clone\(\)\)/);
});

test("automatic update prompts never cover an active sign-in flow", async () => {
    const frontend = await source("src/main.js");
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");

    assert.match(frontend, /if \(signInInProgress\(\)\) return "";/);
    assert.match(frontend, /state\.signInActive \|\| Boolean\(state\.signIn\) \|\| Boolean\(state\.microsoftSignIn\)/);
    assert.match(java, /if \(isLauncherSignInActive\(\)\) \{[\s\S]*Launcher update prompt deferred/);
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

    assert.match(frontend, /\{ id: "dev", label: "Dev" \}/);
    assert.match(frontend, /if \(buildId === "dev"\) return Boolean\(account\.devAccess\);/);
    assert.match(java, /new Build\("Dev", "dev"\)/);
    assert.match(java, /if \("dev"\.equals\(buildId\)\) return user\.devAccess \|\| hasOwnerAccess\(user\);/);
});
