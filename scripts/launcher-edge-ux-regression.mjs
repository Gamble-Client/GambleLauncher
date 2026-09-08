import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || "playwright");
const [base = "http://127.0.0.1:5187", output = "/tmp/launcher-edge-ux"] = process.argv.slice(2);
assert.ok(["localhost", "127.0.0.1"].includes(new URL(base).hostname), "test hooks are local only");
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
const page = await browser.newPage({ viewport: { width: 820, height: 560 }, reducedMotion: "reduce" });
const results = [];
const errors = [];
page.on("pageerror", e => errors.push(e.message));
await page.route("https://**/*", route => route.abort());
// Add state seeding only to this browser's local development response. No
// runtime test API or synthetic account data is added to shipped source.
await page.route("**/src/main.js*", async route => {
  const response = await route.fetch();
  await route.fulfill({ response, body: (await response.text()) + "\nwindow.launcherEdgeFixture={state,render};" });
});
async function check(name, fn) {
  try { await fn(); results.push({ name, pass: true }); }
  catch (error) { results.push({ name, pass: false, error: error.message }); }
}
try {
  await page.goto(`${base}/?preview=owner`);
  await page.waitForFunction(() => window.launcherEdgeFixture && !window.launcherEdgeFixture.state.starting);
  await check("profile keyboard focus follows the selected profile, not the first button", async () => {
    await page.locator('button[data-view="profiles"]').first().click();
    await page.locator('[data-action="select-profile"][data-profile="vanilla"]').click();
    await page.waitForFunction(() => document.querySelector('[data-profile="vanilla"][aria-pressed="true"]'));
    assert.equal(await page.evaluate(() => document.activeElement.dataset.profile), "vanilla");
  });
  await check("settings checkbox keeps keyboard focus after rerender", async () => {
    await page.locator('button[data-view="settings"]').first().click();
    const toggle = page.locator('[data-setting-toggle="animationsEnabled"]');
    await toggle.focus();
    await page.keyboard.press("Space");
    assert.equal(await page.evaluate(() => document.activeElement.dataset.settingToggle), "animationsEnabled");
  });
  await check("busy settings visibly disable ignored checkbox changes", async () => {
    await page.evaluate(() => { launcherEdgeFixture.state.busy = true; launcherEdgeFixture.render(); });
    assert.equal(await page.locator('[data-setting-toggle="animationsEnabled"]').isDisabled(), true);
    await page.evaluate(() => { launcherEdgeFixture.state.busy = false; launcherEdgeFixture.render(); });
  });
  await check("long mod filenames keep Enable/Disable inside the visible row", async () => {
    await page.evaluate(() => {
      const { state, render } = launcherEdgeFixture;
      state.busy = false; state.selectedProfile = "gamble-client"; state.view = "mods";
      state.mods = [{ name: "long-mod-name-".repeat(40) + ".jar", path: "fixture.jar", size: 99, enabled: true }]; render();
    });
    await page.screenshot({ path: `${output}/long-filename.png` });
    const dimensions = await page.locator(".file-row").evaluate(row => ({ width: row.clientWidth, scroll: row.scrollWidth, height: row.clientHeight,
      buttonRight: row.querySelector("button").getBoundingClientRect().right, right: row.getBoundingClientRect().right }));
    assert.ok(dimensions.scroll <= dimensions.width + 2 && dimensions.buttonRight <= dimensions.right, JSON.stringify(dimensions));
    assert.ok(dimensions.height < 140, "a long filename must not turn one mod into a full-screen card");
    assert.ok(await page.locator(".file-row strong").getAttribute("title"));
  });
  await check("long diagnostic paths wrap without horizontal scrolling", async () => {
    await page.evaluate(() => {
      const { state, render } = launcherEdgeFixture; state.view = "settings";
      state.diagnostics = [{ ok: false, label: "Launch log", detail: "C:\\Users\\" + "long-folder-name".repeat(30) + "\\launch.log" }]; render();
    });
    const row = page.locator(".diagnostic-row"); await row.scrollIntoViewIfNeeded();
    await page.screenshot({ path: `${output}/long-diagnostic.png` });
    assert.equal(await row.evaluate(e => e.scrollWidth <= e.clientWidth + 2), true);
  });
  assert.deepEqual(errors, []);
} finally {
  await writeFile(`${output}/results.json`, JSON.stringify(results, null, 2));
  await browser.close();
}
console.log(JSON.stringify(results, null, 2));
assert.deepEqual(results.filter(r => !r.pass), []);
