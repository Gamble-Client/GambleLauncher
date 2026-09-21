import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || "playwright");
const [base = "http://127.0.0.1:5187", output = "/tmp/launcher-shaders"] = process.argv.slice(2);
assert.ok(["localhost", "127.0.0.1"].includes(new URL(base).hostname));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
try {
  for (const width of [820, 1120, 1440]) {
    const page = await browser.newPage({ viewport: { width, height: 800 } });
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.route("https://**/*", route => route.abort());
    await page.goto(`${base}/?preview=owner`);
    const settings = async () => {
      await page.locator('[data-view="settings"]').first().click();
      await page.locator('[data-view-frame="settings"]').waitFor();
    };
    await settings();
    const toggle = page.getByRole("checkbox", { name: "Disable client shaders", exact: true });
    assert.equal(await toggle.isChecked(), false);
    await toggle.check();
    assert.equal(await page.evaluate(() => localStorage.getItem("gamble.launcher.disableClientShaders")), "true");
    await page.reload();
    await settings();
    assert.equal(await toggle.isChecked(), true);
    await page.getByRole("button", { name: "Client shaders help", exact: true }).click();
    assert.match(await page.locator('[data-help="client-shaders"]').innerText(), /next Minecraft start/);
    await page.screenshot({ path: `${output}/settings-${width}.png` });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await toggle.uncheck();
    await page.reload();
    await settings();
    assert.equal(await toggle.isChecked(), false);
    assert.deepEqual(errors, []);
    await page.close();
  }
  console.log("PASS: client shader preference default, persistence both directions, help, responsive layout");
} finally { await browser.close(); }
