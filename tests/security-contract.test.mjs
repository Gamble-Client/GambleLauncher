import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

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
});

test("both launcher implementations bind manifests and tickets to the requested tier", async () => {
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(java, /Backend manifest was issued for a different client tier/);
    assert.match(java, /Backend launch ticket was issued for a different client tier/);
    assert.match(java, /verifyFabricModIdentity\(file, MANAGED_CLIENT_MOD_ID, manifest\.build\)/);
    assert.match(java, /cg-mod:build_variant/);

    assert.match(rust, /Backend manifest was issued for a different client tier/);
    assert.match(rust, /Backend launch ticket was issued for a different client tier/);
    assert.match(rust, /verify_fabric_mod_identity\(&staging, MANAGED_CLIENT_MOD_ID, Some\(&manifest\.build\)\)/);
    assert.match(rust, /cg-mod:build_variant/);
});

test("launch authorization remains ticket-based instead of reusable local licenses", async () => {
    const java = await source("src/main/java/com/gambleclient/launcher/Main.java");
    const rust = await source("src-tauri/src/main.rs");

    assert.match(java, /Local license files cleared; launch tickets handle current client access/);
    assert.match(java, /-Dgamble\.launchTicketFile=/);
    assert.doesNotMatch(java, /\/api\/launcher\/license/);
    assert.doesNotMatch(java, /requestLauncherLicense/);
    assert.doesNotMatch(java, /complete\.body\.get\("licenseKey"\)/);
    assert.match(rust, /-Dgamble\.launchTicketFile=/);
    assert.match(rust, /write_private_file\(&path, payload\.as_bytes\(\)\)/);
});

test("Windows retries the embedded app navigation only while WebView2 is blank", async () => {
    const rust = await source("src-tauri/src/main.rs");

    assert.match(rust, /tauri::Url::parse\("http:\/\/tauri\.localhost\/"\)/);
    assert.match(rust, /\[250, 1_000, 2_500\]/);
    assert.match(rust, /url\.as_str\(\) != "about:blank"/);
    assert.match(rust, /window\.navigate\(app_url\.clone\(\)\)/);
});
