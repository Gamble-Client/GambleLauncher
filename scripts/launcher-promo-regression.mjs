import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || "playwright");
const [base = "http://127.0.0.1:5187", output = "/tmp/launcher-promo"] = process.argv.slice(2);
assert.ok(["127.0.0.1", "localhost"].includes(new URL(base).hostname));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
// Upgrade/sale banner on Home, driven by the dev fixtures (?sale=1 stubs an
// active /api/sale). Ad Tier gets the upgrade pitch, a live sale wins and
// merges into one banner, paid tiers only see the sale, owner sees nothing.
const cases = [
  ["ad-tier", false, "upgrade"],
  ["ad-tier", true, "combined"],
  ["giveaway", true, "sale"],
  ["giveaway", false, "none"],
  ["beta-weekly", true, "none"],
  ["media", true, "none"],
  ["owner", true, "none"]
];
async function open(preview, sale, viewport, reducedMotion = "reduce") {
  const page = await browser.newPage({ viewport, reducedMotion });
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.route("https://**/*", route => route.abort());
  await page.goto(`${base}/?preview=${preview}${sale ? "&sale=1" : ""}`);
  await page.waitForFunction(() => !document.querySelector('[data-action="launch"]')?.disabled);
  return { page, errors };
}
try {
  for (const width of [820, 1120, 1440]) {
    for (const [preview, sale, expected] of cases) {
      const { page, errors } = await open(preview, sale, { width, height: 720 });
      const banner = page.locator(".promo-banner");
      assert.equal(await banner.count(), expected === "none" ? 0 : 1, `${preview} sale=${sale} @${width}`);
      if (expected !== "none") {
        const text = (await banner.innerText()).replace(/\s+/g, " ");
        assert.equal(/Release unlocks every module/.test(text), expected !== "sale", `${preview} pitch`);
        assert.equal(/Ends in 1d 04h/.test(text), expected !== "upgrade", `${preview} countdown`);
        if (expected !== "upgrade") assert.match(text, /Lifetime \$15 \$10 .*Code WEEKEND10 Copy/);
        const geometry = await banner.evaluate(node => ({ overflow: node.scrollWidth > node.clientWidth,
          play: document.querySelector('[data-action="launch"]').getBoundingClientRect().width }));
        assert.equal(geometry.overflow, false);
        assert.ok(geometry.play >= 150, "Play stays usable next to the banner");
        // The banner sits outside the current-client block; Play stays the only silver button.
        assert.equal(await page.locator(".launch-panel .promo-banner").count(), 0);
        assert.equal(await banner.locator(".launch-button").count(), 0);
        if (width === 1120) await page.screenshot({ path: `${output}/${preview}${sale ? "-sale" : ""}.png` });
      }
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      assert.deepEqual(errors, []);
      await page.close();
    }
  }

  // Actions and motion: copy toasts, dismiss fades and the cards slide back up,
  // the dismissal survives view switches, and nothing animates with motion off.
  for (const mode of ["no-preference", "reduce"]) {
    const { page, errors } = await open("ad-tier", true, { width: 1120, height: 720 }, mode);
    await page.evaluate(() => {
      window.motionClasses = {};
      const add = DOMTokenList.prototype.add;
      DOMTokenList.prototype.add = function (...tokens) {
        for (const token of tokens) window.motionClasses[token] = (window.motionClasses[token] || 0) + 1;
        return add.apply(this, tokens);
      };
    });
    await page.locator('[data-action="promo-copy"]').click();
    await page.locator(".toast").filter({ hasText: "WEEKEND10" }).waitFor();
    await page.locator('[data-action="promo-dismiss"]').click();
    await page.waitForFunction(() => !document.querySelector(".promo-banner"));
    const classes = await page.evaluate(() => window.motionClasses);
    if (mode === "no-preference") {
      assert.ok(classes["view-leave"] >= 1 && classes["shift-in"] >= 2, JSON.stringify(classes));
    } else {
      assert.equal((classes["view-leave"] || 0) + (classes["shift-in"] || 0), 0, JSON.stringify(classes));
    }
    await page.locator('.nav-item[data-view="settings"]').click();
    await page.locator('[data-view-frame="settings"]').waitFor();
    await page.locator('.nav-item[data-view="play"]').click();
    await page.locator('[data-view-frame="play"]').waitFor();
    assert.equal(await page.locator(".promo-banner").count(), 0, "dismissed for the session");
    assert.deepEqual(errors, []);
    await page.close();
  }
  console.log("PASS: upgrade/sale banner tier gating, combined rule, countdown, copy, session dismissal and motion at 820/1120/1440");
} finally { await browser.close(); }
