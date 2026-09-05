import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import test from "node:test";
import { canUseBuildForAccess, preferredBuildForAccess } from "../src/access-policy.js";
import { launchState } from "../src/launch-state.js";

// Execute the actual event handler with a fake native boundary. Browser layout
// is covered by the separate UI smoke; no test hook ships in the app.
const source = (await readFile(new URL("../src/main.js", import.meta.url), "utf8"))
  .replace(/^import .*;\n/gm, "")
  .replaceAll("import.meta.env.VITE_LAUNCHER_TEST_FIXTURES", "undefined")
  .replaceAll("import.meta.env.DEV", "false")
  .replace(/\nboot\(\);\nrender\(\);\s*$/, "");

function harness(native) {
  const handlers = new Map();
  const app = { addEventListener: (name, handler) => handlers.set(name, handler) };
  const context = vm.createContext({
    document: { querySelector: () => app }, window: {}, location: { search: "" },
    setTimeout, clearTimeout, requestAnimationFrame: (fn) => fn(), URLSearchParams,
    canUseBuildForAccess, preferredBuildForAccess, launchState, tauriInvoke: native,
    logoUrl: "", navigator: {}, console
  });
  vm.runInContext(`${source}\nrender = () => {}; globalThis.apiForTest = { state, refreshManifest, refreshFiles };`, context);
  const { state } = context.apiForTest;
  Object.assign(state, { starting: false, token: "test-session", account: {
    email: "player@example.test", accessStatus: "owned", selectedPlan: "lifetime"
  }, clientStatus: { updateAvailable: true } });
  return {
    ...context.apiForTest,
    click: () => handlers.get("click")({ target: { closest: (selector) => selector === "[data-action]" ? { dataset: { action: "launch" } } : null } })
  };
}

test("first-run Play reaches native automatic installation without an update detour", async () => {
  const calls = [];
  const ui = harness(async (command) => {
    calls.push(command);
    if (command === "launcher_api") return { user: ui.state.account, ads: { required: false } };
    if (command === "launch_game") return "Minecraft process started.";
    if (command === "minecraft_status") return { running: calls.includes("launch_game"), pid: 42 };
    throw new Error(`Unexpected native call: ${command}`);
  });
  await ui.click();
  assert.equal(calls.filter((x) => x === "launch_game").length, 1);
  assert.equal(ui.state.popup, null);
  assert.equal(ui.state.busy, false);
});

test("plain profiles never request Gamble access or a sponsor", async () => {
  for (const profile of ["vanilla", "fabric"]) {
    const calls = [];
    const ui = harness(async (command, args) => {
      calls.push(command);
      if (command === "minecraft_status") return { running: calls.includes("launch_game") };
      assert.equal(command, "launch_game");
      assert.equal(args.input.profile, profile);
      return "Minecraft process started.";
    });
    Object.assign(ui.state, { selectedProfile: profile, selectedBuild: "ad_tier", account: {
      email: "player@example.test", accessStatus: "ad_tier", selectedPlan: "ad_tier", adTierAccess: true
    }, ads: { active: false } });
    await ui.click();
    assert.ok(calls.includes("launch_game"));
    assert.equal(ui.state.popup, null);
  }
});

test("failed account refresh stops launch and exposes a recovery message", async () => {
  let launched = false;
  const ui = harness(async (command) => {
    if (command === "minecraft_status") return { running: false };
    if (command === "launcher_api") throw new Error("HTTP 401: Sign in again");
    if (command === "launch_game") launched = true;
  });
  await ui.click();
  assert.equal(launched, false);
  assert.equal(ui.state.popup.title, "Launch failed");
  assert.equal(ui.state.busy, false);
});

test("repeated clicks cannot issue concurrent launches", async () => {
  let release;
  const barrier = new Promise((resolve) => { release = resolve; });
  let launches = 0;
  const ui = harness(async (command) => {
    if (command === "minecraft_status") { await barrier; return { running: false }; }
    if (command === "launcher_api") return { user: ui.state.account, ads: { required: false } };
    if (command === "launch_game") { launches++; return "Minecraft process started."; }
  });
  const first = ui.click();
  await ui.click();
  release();
  await first;
  assert.equal(launches, 1);
});

test("late profile metadata and file lists cannot overwrite a newly selected profile", async () => {
  let release;
  const barrier = new Promise((resolve) => { release = resolve; });
  const ui = harness(async (command) => {
    if (command === "ensure_profile") return;
    await barrier;
    return command === "client_install_status" ? { updateAvailable: true } : [{ name: "old-profile.jar" }];
  });
  const manifest = ui.refreshManifest();
  const files = ui.refreshFiles();
  ui.state.selectedProfile = "vanilla";
  ui.state.clientStatus = null;
  release();
  await Promise.all([manifest, files]);
  assert.equal(ui.state.clientStatus, null);
  assert.equal(ui.state.mods.length, 0);
});
