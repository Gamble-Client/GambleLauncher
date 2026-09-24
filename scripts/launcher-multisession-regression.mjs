import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || "playwright");
const [base = "http://127.0.0.1:5187", output = "/tmp/launcher-multisession"] = process.argv.slice(2);
assert.ok(["localhost", "127.0.0.1"].includes(new URL(base).hostname));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
try {
  for (const role of ["owner", "dev"]) for (const width of [820, 1120, 1440]) {
    const page = await browser.newPage({ viewport: { width, height: 800 } });
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.route("https://**/*", route => route.abort());
    await page.goto(`${base}/?preview=${role}`);
    await page.locator('[data-action="launch"]').click();
    await page.getByRole("button", { name: "Launch another", exact: true }).waitFor();
    await page.getByRole("button", { name: "Launch another", exact: true }).click();
    await page.getByRole("button", { name: "Stop all sessions", exact: true }).waitFor();
    assert.match(await page.locator(".launch-stack").innerText(), /2 running/);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: `${output}/${role}-two-sessions-${width}.png` });
    await page.getByRole("button", { name: "Stop all sessions", exact: true }).click();
    await page.getByRole("button", { name: "Launch another", exact: true }).waitFor({ state: "hidden" });
    assert.deepEqual(errors, []);
    await page.close();
  }
  for (const tier of ["giveaway", "beta-weekly", "media", "ad-tier-active"]) {
    const page = await browser.newPage();
    await page.route("https://**/*", route => route.abort());
    await page.goto(`${base}/?preview=${tier}`);
    await page.locator('[data-action="launch"]').click();
    await page.getByRole("button", { name: "Stop Minecraft", exact: true }).waitFor();
    assert.equal(await page.getByRole("button", { name: "Launch another", exact: true }).count(), 0);
    await page.close();
  }
  console.log("PASS: owner/dev two-session/stop-all lifecycle at three sizes; other roles have no multi-session action");
} finally { await browser.close(); }
