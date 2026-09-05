import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const [baseUrl = "http://127.0.0.1:5176", outputDir = "/tmp/gamble-launcher-ui"] = process.argv.slice(2);
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || "playwright");
await mkdir(outputDir, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
const results = [];
try {
  for (const fixture of ["ad-tier", "ad-tier-active", "giveaway", "beta-weekly", "media", "owner"]) {
    const page = await browser.newPage({ viewport: { width: 1120, height: 720 }, reducedMotion: "reduce" });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    // These are UI fixtures. No production account or external service is used.
    await page.route("https://**/*", (route) => route.abort());
    await page.goto(`${baseUrl}/?preview=${fixture}`);
    const play = page.locator('[data-action="launch"]');
    await play.locator("visible=true").waitFor();
    await page.waitForFunction(() => !document.querySelector('[data-action="launch"]').disabled);
    assert.equal(await page.locator('[aria-modal="true"]').count(), 0, "First launch must remain unobstructed");
    await page.screenshot({ path: resolve(outputDir, `${fixture}.png`) });
    await play.click();
    if (fixture === "ad-tier") {
      await page.getByRole("dialog", { name: "Dashboard sponsor check" }).waitFor();
      await page.keyboard.press("Tab");
      assert.equal(await page.evaluate(() => Boolean(document.activeElement.closest('[role="dialog"]'))), true);
      await page.getByRole("button", { name: "OK", exact: true }).click();
      // Free account can still launch its plain profiles without sponsor access.
      for (const profile of ["vanilla", "fabric"]) {
        await page.locator('button[data-view="profiles"]').first().click();
        await page.locator('[data-view-frame="profiles"]').waitFor();
        await page.locator(`[data-action="select-profile"][data-profile="${profile}"]`).click();
        await page.locator(`[data-action="select-profile"][data-profile="${profile}"][aria-pressed="true"]`).waitFor();
        await page.locator('button[data-view="play"]').click();
        await page.locator('[data-view-frame="play"]').waitFor();
        assert.equal((await play.innerText()).trim().toLowerCase(), "play");
        await play.click();
        await page.getByRole("button", { name: "Stop Minecraft", exact: true }).waitFor();
        await play.click();
        await page.getByRole("button", { name: "Play", exact: true }).last().waitFor();
      }
    } else {
      await page.getByRole("button", { name: "Stop Minecraft", exact: true }).waitFor();
      await play.click();
      await page.waitForFunction(() => !document.querySelector('[data-action="launch"]').disabled);
    }
    await page.getByRole("button", { name: "Open diagnostics", exact: true }).click();
    await page.getByText("Java 21 available", { exact: true }).waitFor();
    await page.locator('button[data-view="play"]').click();
    await page.locator('[data-view-frame="play"]').waitFor();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await page.getByRole("button", { name: "Sign in to play", exact: true }).waitFor();
    await page.setViewportSize({ width: 820, height: 560 });
    await page.screenshot({ path: resolve(outputDir, `${fixture}-compact.png`) });
    const geometry = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth,
      button: (() => { const r = document.querySelector('[data-action="launch"]').getBoundingClientRect(); return { width: r.width, left: r.left, right: r.right }; })() }));
    assert.ok(geometry.scrollWidth <= geometry.width, "No horizontal page overflow");
    assert.ok(geometry.button.width >= 150 && geometry.button.left >= 0 && geometry.button.right <= geometry.width, "Play remains usable at minimum window width");
    assert.deepEqual(errors, [], "No unhandled browser errors");
    results.push({ fixture, firstLaunch: "passed", diagnostics: "passed", signedOut: "passed", compact: "passed" });
    await page.close();
  }
  console.log(JSON.stringify({ kind: "UI fixtures; not real account/Minecraft validation", results, outputDir }, null, 2));
} finally { await browser.close(); }
