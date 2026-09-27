import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import test from "node:test";
import { canUseBuildForAccess, preferredBuildForAccess, canLaunchMultiple } from "../src/access-policy.js";
import { launchState } from "../src/launch-state.js";
import * as promoPolicy from "../src/promo-policy.js";

// Execute the actual event handler with a fake native boundary. Browser layout
// is covered by the separate UI smoke; no test hook ships in the app.
const source = (await readFile(new URL("../src/main.js", import.meta.url), "utf8"))
  .replaceAll("\r\n", "\n")
  .replace(/^import .*;\n/gm, "")
  .replaceAll("import.meta.env.VITE_LAUNCHER_TEST_FIXTURES", "undefined")
  .replaceAll("import.meta.env.DEV", "false")
  .replace(/\nboot\(\);\nrender\(\);\s*$/, "");

function harness(native) {
  const handlers = new Map();
  const app = { addEventListener: (name, handler) => handlers.set(name, handler), querySelector: () => null, querySelectorAll: () => [] };
  const context = vm.createContext({
    document: { querySelector: () => app }, window: {}, location: { search: "" },
    setTimeout, clearTimeout, requestAnimationFrame: (fn) => fn(), URLSearchParams,
    canUseBuildForAccess, preferredBuildForAccess, canLaunchMultiple, launchState, ...promoPolicy, tauriInvoke: native,
    logoUrl: "", navigator: {}, console
  });
  vm.runInContext(`${source}\nrender = () => {}; globalThis.apiForTest = { state, refreshManifest, refreshFiles, knownLaunchMessage, normalizeStoredProfiles, applyAccount, sponsorRemainingSeconds, refreshSponsorOnReturn, refreshMinecraftStatus, refreshSocial, refreshSpotifyStatus, refreshAccount, pollSignIn, refreshSale, currentPromo, promoBannerMarkup };`, context);
  const { state } = context.apiForTest;
  Object.assign(state, { starting: false, token: "test-session", account: {
    email: "player@example.test", accessStatus: "owned", selectedPlan: "lifetime"
  }, clientStatus: { updateAvailable: true } });
  return {
    ...context.apiForTest,
    click: (action = "launch") => handlers.get("click")({ target: { closest: (selector) => selector === "[data-action]" ? { dataset: { action } } : null } })
  };
}

test("Launch another forwards a separate action and keeps Stop explicit", async () => {
  let input;
  const ui = harness(async (command, args) => {
    if (command === "minecraft_status") return { running: true, sessionCount: input ? 2 : 1 };
    if (command === "launch_game") { input = args.input; return "Minecraft process started."; }
    throw new Error(command);
  });
  Object.assign(ui.state, { selectedProfile: "fabric", minecraftRunning: true,
    account: { ownerAccess: true } });
  await ui.click("launch-another");
  assert.equal(input.launchAnother, true);
  assert.equal(ui.state.minecraftSessionCount, 2);
  await ui.click();
  assert.equal(input.launchAnother, false);
});

test("ordinary accounts cannot invoke Launch another from the UI", async () => {
  const ui = harness(async () => { throw new Error("native must not be called"); });
  Object.assign(ui.state, { minecraftRunning: true });
  await ui.click("launch-another");
  assert.equal(ui.state.minecraftRunning, true);
});

test("Stop retains stop-only intent when the last child exits during status refresh", async () => {
  let input;
  const ui = harness(async (command, args) => {
    if (command === "minecraft_status") return { running: false, sessionCount: 0 };
    if (command === "launch_game") { input = args.input; return "Minecraft stop signal sent."; }
    throw new Error("Stop must not refresh access or start preparation: " + command);
  });
  ui.state.minecraftRunning = true;
  await ui.click();
  assert.equal(input.stopOnly, true);
  assert.equal(input.launchAnother, false);
  assert.equal(ui.state.minecraftRunning, false);
});

test("a secondary crash reports its own log without marking the surviving game closed", async () => {
  const ui = harness(async () => ({ running: true, pid: 101, sessionCount: 1,
    crashed: true, exitCode: 73, message: "Minecraft exited with code 73.",
    logPath: "/fixture/secondary/launcher-session.log" }));
  Object.assign(ui.state, { minecraftRunning: true, minecraftSessionCount: 2 });
  await ui.refreshMinecraftStatus({ render: false });
  assert.equal(ui.state.minecraftRunning, true);
  assert.equal(ui.state.minecraftSessionCount, 1);
  assert.equal(ui.state.minecraftExit.logPath, "/fixture/secondary/launcher-session.log");
  assert.match(ui.state.popup.message, /Other sessions are still running/);
  ui.state.popup = null;
  await ui.refreshMinecraftStatus({ render: false });
  assert.equal(ui.state.popup, null);
});

for (const disabled of [false, true]) test(`launch forwards client shaders preference ${disabled}`, async () => {
  let input;
  const ui = harness(async (command, args) => {
    if (command === "minecraft_status") return { running: !!input };
    if (command === "launch_game") { input = args.input; return "Minecraft process started."; }
    throw new Error(command);
  });
  Object.assign(ui.state, { selectedProfile: "fabric", disableClientShaders: disabled });
  await ui.click();
  assert.equal(input.disableClientShaders, disabled);
});

test("cancelling sign-in rejects an already in-flight ready response", async () => {
  let finish, started;
  const requested = new Promise(resolve => { started = resolve; });
  const calls = [];
  const ui = harness(async command => {
    calls.push(command);
    if (command === "launcher_api") return new Promise(resolve => { finish = resolve; started(); });
    throw new Error(`Cancelled sign-in must not invoke ${command}`);
  });
  ui.state.token = "";
  ui.state.signInGeneration = 1;
  ui.state.signInActive = true;
  const pending = ui.pollSignIn({ code: "fixture", expiresAt: Date.now() / 1000 + 60 }, 1);
  await requested;
  ui.state.signInGeneration++;
  ui.state.signInActive = false;
  finish({ status: "ready", token: "must-not-be-saved" });
  await pending;
  assert.equal(ui.state.token, "");
  assert.deepEqual(calls, ["launcher_api"]);
});

test("an older status poll cannot undo a newer running process", async () => {
  let finish;
  let calls = 0;
  const ui = harness(async () => ++calls === 1 ? new Promise(resolve => { finish = resolve; }) : { running: true, pid: 123 });
  const old = ui.refreshMinecraftStatus();
  await ui.refreshMinecraftStatus();
  finish({ running: false, crashed: true, message: "An earlier process exited." });
  await old;
  assert.equal(ui.state.minecraftRunning, true);
  assert.equal(ui.state.minecraftPid, 123);
  assert.equal(ui.state.popup, null);
});

for (const method of ["refreshSocial", "refreshSpotifyStatus", "refreshAccount", "refreshManifest"]) {
  test(`${method} discards a response from before sign-out`, async () => {
    let finish;
    const ui = harness(async () => new Promise(resolve => { finish = resolve; }));
    const pending = ui[method]();
    ui.state.token = "";
    ui.state.account = ui.state.social = ui.state.spotify = ui.state.manifest = ui.state.clientStatus = null;
    finish({ user: { username: "old-user" }, friends: [{ username: "old-friend" }], configured: true, message: "old metadata" });
    await pending;
    for (const field of ["account", "social", "spotify", "manifest", "clientStatus"]) assert.equal(ui.state[field], null, field);
  });
}

for (const code of [0, 1, -1073741819]) {
  test(`routine polling exposes delayed startup exit ${code} exactly once`, async () => {
    const ui = harness(async () => ({ running: false, exitCode: code,
      crashed: code !== 0, message: `Minecraft exited with code ${code}.`,
      logPath: "C:\\Users\\Test User\\launch.log" }));
    Object.assign(ui.state, { minecraftRunning: true, minecraftStartedAt: Date.now() - 15000 });
    await ui.refreshMinecraftStatus();
    assert.ok(ui.state.popup, "a delayed exit must not silently return Play to idle");
    assert.match(ui.state.popup.message, /Diagnostics/);
    ui.state.popup = null;
    await ui.refreshMinecraftStatus();
    assert.equal(ui.state.popup, null, "do not reopen the dismissed error every poll");
  });
}

test("a later crash is visible but a normal later exit or explicit Stop is not a startup failure", async () => {
  let crashed = false;
  const ui = harness(async () => ({ running: false, crashed,
    exitCode: crashed ? 1 : 0, message: "Minecraft exited." }));
  Object.assign(ui.state, { minecraftRunning: true, minecraftStartedAt: Date.now() - 600000 });
  await ui.refreshMinecraftStatus();
  assert.equal(ui.state.popup, null);
  crashed = true;
  ui.state.minecraftRunning = true;
  await ui.refreshMinecraftStatus();
  assert.ok(ui.state.popup);
  ui.state.popup = null;
  ui.state.minecraftRunning = false;
  await ui.refreshMinecraftStatus();
  assert.equal(ui.state.popup, null);
});

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

const FRESH_PLAY_HINT = "Press Play again: this attempt already used its one-use launch authorization, and the next Play requests a fresh one.";

test("a launch failing after the one-use authorization was issued asks for a fresh Play", async () => {
  const ui = harness(async (command) => {
    if (command === "launcher_api") return { user: ui.state.account, ads: { required: false } };
    if (command === "launch_game") {
      // Long enough that publicMessage() truncation would drop an appended hint.
      throw `Managed Java runtime could not start: ${"java detail ".repeat(40)}\n\n${FRESH_PLAY_HINT}`;
    }
    if (command === "minecraft_status") return { running: false };
    throw new Error(`Unexpected native call: ${command}`);
  });
  await ui.click();
  assert.equal(ui.state.popup.title, "Launch failed");
  assert.match(ui.state.popup.message, /^Press Play again\./);
  assert.match(ui.state.popup.message, /What failed: Managed Java runtime could not start/);
  assert.doesNotMatch(ui.knownLaunchMessage("Could not download Minecraft assets."), /Press Play again/);
});

test("a Gamble profile that exits shortly after start tells the user to press Play again", async () => {
  for (const client of [true, false]) {
    const ui = harness(async () => ({ running: false, exitCode: 1, crashed: true,
      message: "Minecraft exited with code 1.", logPath: "/fixture/launch.log" }));
    Object.assign(ui.state, { minecraftRunning: true, minecraftStartedAt: Date.now() - 5000, minecraftStartedClient: client });
    await ui.refreshMinecraftStatus();
    assert.equal(/Press Play again/.test(ui.state.popup.message), client, String(client));
  }
});

test("rate-limit recovery preserves the source and retry interval", () => {
  const ui = harness(() => {});
  const message = ui.knownLaunchMessage("HTTP 429: Gamble launcher limit. Retry after 600 seconds.");
  assert.match(message, /Gamble launcher limit/);
  assert.match(message, /600 seconds/);
  assert.doesNotMatch(message, /Microsoft|Wait a minute/);
});

test("stored profiles reject aliases, traversal, duplicate ids and invalid labels", () => {
  const ui = harness(() => {});
  const rows = ui.normalizeStoredProfiles([null, {}, { id: "../../outside", label: "bad" },
    { id: "fabric", label: "reserved" }, { id: "custom-test", label: "Équipe 日本" },
    { id: "custom-test", label: "duplicate" }, { id: "upperCase", label: "alias" }, { id: "empty", label: " " }]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].id, "custom-test");
});

test("sponsor display counts elapsed time without changing server authorization", () => {
  const ui = harness(() => {});
  ui.applyAccount({ user: ui.state.account, ads: { required: true, active: true, remainingSeconds: 30 } });
  ui.state.adsObservedAt -= 10_000;
  assert.ok(ui.sponsorRemainingSeconds() <= 20);
  ui.state.adsObservedAt -= 30_000;
  assert.equal(ui.sponsorRemainingSeconds(), 0);
  assert.equal(ui.state.ads.active, true, "display timer cannot grant or revoke server authorization");
});

test("Dashboard return coalesces focus events and rejects stale-session responses", async () => {
  let release;
  const barrier = new Promise(resolve => { release = resolve; });
  let requests = 0;
  const ui = harness(async command => {
    assert.equal(command, "launcher_api");
    requests++;
    await barrier;
    return { user: { email: "old@example.test" }, ads: { required: true, active: true } };
  });
  ui.state.ads = { required: true, active: false };
  const first = ui.refreshSponsorOnReturn();
  await ui.refreshSponsorOnReturn();
  ui.state.token = "different-session";
  release();
  await first;
  assert.equal(requests, 1);
  assert.equal(ui.state.ads.active, false);
  assert.notEqual(ui.state.account.email, "old@example.test");
});

for (const active of [true, false]) test(`fresh sponsor access ${active} overrides stale cached credit`, async () => {
  const calls = [];
  const free = { email: "free@example.test", accessStatus: "ad_tier", selectedPlan: "ad_tier", adTierAccess: true };
  const ui = harness(async (command) => {
    calls.push(command);
    if (command === "launcher_api") return { user: free, ads: { required: true, active } };
    if (command === "minecraft_status") return { running: calls.includes("launch_game") };
    if (command === "launch_game") return "Minecraft process started.";
    if (command === "open_url") return "";
    throw new Error(command);
  });
  Object.assign(ui.state, { account: free, selectedBuild: "ad_tier", ads: { required: true, active: !active } });
  await ui.click();
  assert.equal(calls.includes("launch_game"), active);
  assert.equal(calls.includes("open_url"), !active);
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

const liveSale = (overrides = {}) => {
  const now = Date.now();
  return { active: true, id: "weekend-1", title: "Weekend sale", plan: "lifetime", priceCents: 1000, listPriceCents: 1500,
    couponCode: "WEEKEND10", startsAt: new Date(now - 3_600_000).toISOString(),
    endsAt: new Date(now + 101_000_000).toISOString(), serverNow: new Date(now).toISOString(), ...overrides };
};
const freeAccount = { email: "free.river@example.test", selectedPlan: "ad_tier", accessStatus: "ad_tier", adTierAccess: true };

test("the sale poll reads the public endpoint without the launcher session and fails silently", async () => {
  const calls = [];
  let reply = liveSale();
  const ui = harness(async (command, args) => {
    calls.push([command, args.input]);
    if (reply instanceof Error) throw reply;
    return reply;
  });
  await ui.refreshSale();
  // The input object comes from the VM realm; compare its JSON shape.
  assert.equal(JSON.stringify(calls[0]), JSON.stringify(["launcher_api", { method: "GET", path: "/api/sale", token: "", body: {} }]));
  assert.equal(ui.state.sale.couponCode, "WEEKEND10");
  reply = new Error("error sending request for url (https://gambleclient.org/api/sale)");
  await ui.refreshSale();
  assert.equal(ui.state.sale.key, "weekend-1", "offline keeps the sale already shown");
  assert.equal(ui.state.popup, null);
  reply = { active: false, serverNow: new Date().toISOString() };
  await ui.refreshSale();
  assert.equal(ui.state.sale, null);
});

test("Home renders one banner: combined for Ad Tier, sale-only for paid, none for paid without a sale", async () => {
  const ui = harness(async () => liveSale());
  const markup = () => ui.promoBannerMarkup(ui.currentPromo());
  const count = (html) => (html.match(/class="promo-banner/g) || []).length;

  ui.state.account = freeAccount;
  assert.match(markup(), /<strong>Release unlocks every module<\/strong><span class="promo-dash" aria-hidden="true">—<\/span><span>\$2\.99\/week or \$15 lifetime<\/span>/);
  assert.match(markup(), /Get Release/);
  await ui.refreshSale();
  const combined = markup();
  assert.equal(count(combined), 1);
  assert.match(combined, /<strong>Weekend sale<\/strong>[\s\S]*<p class="promo-note">Release unlocks every module\.<\/p>/);
  assert.match(combined, /<s class="mono"[^>]*>\$15<\/s> <b class="mono">\$10<\/b>/);
  assert.match(combined, /Ends in <span class="mono" data-sale-countdown>1d 04h<\/span>/);
  assert.match(combined, /<code>WEEKEND10<\/code>/);

  ui.state.account = { email: "giveaway.mason@example.test", selectedPlan: "weekly", accessStatus: "owned" };
  const saleOnly = markup();
  assert.equal(count(saleOnly), 1);
  assert.doesNotMatch(saleOnly, /Release unlocks every module/);
  assert.match(saleOnly, /Get Lifetime/);

  ui.state.sale = null;
  assert.equal(markup(), "");
  ui.state.account = { ...freeAccount, accessStatus: "owner", ownerAccess: true };
  await ui.refreshSale();
  assert.equal(ui.state.sale?.key, "weekend-1");
  assert.equal(markup(), "", "owner never sees a banner");
});

test("banner actions open checkout and dismiss for the session only", async () => {
  const opened = [];
  const ui = harness(async (command, args) => {
    if (command === "open_url") { opened.push(args.url); return ""; }
    if (command === "launcher_api") return liveSale();
    throw new Error(command);
  });
  ui.state.account = freeAccount;
  ui.state.busy = true;
  await ui.click("promo-open");
  assert.deepEqual(opened, ["https://dash.gambleclient.org/dashboard.html?section=account"], "works while busy");
  ui.state.busy = false;
  await ui.refreshSale();
  await ui.click("promo-open");
  assert.equal(opened[1], "https://dash.gambleclient.org/dashboard.html?plan=lifetime");
  await ui.click("promo-dismiss");
  assert.equal(ui.currentPromo(), null, "the combined banner dismisses both pitches");
  assert.deepEqual({ ...ui.state.promoDismissed }, { upgrade: true, sale: "weekend-1" });
  await ui.refreshSale();
  assert.equal(ui.currentPromo(), null, "the same sale stays dismissed after the next poll");
});
