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
  for (const [width, height] of [[820, 560], [1120, 720], [1440, 900]]) {
    for (const [preview, sale, expected] of cases) {
      const { page, errors } = await open(preview, sale, { width, height });
      const banner = page.locator(".promo-banner");
      assert.equal(await banner.count(), expected === "none" ? 0 : 1, `${preview} sale=${sale} @${width}`);
      const home = await page.evaluate(() => {
        const content = document.querySelector(".content");
        const bottom = Math.max(...[...document.querySelectorAll(".play-overview > *")].map(node => node.getBoundingClientRect().bottom));
        return { overflow: content.scrollHeight - content.clientHeight, bottom, innerHeight };
      });
      // The default window shows all of Home, banner or not.
      if (width === 1120) {
        assert.equal(home.overflow, 0, `${preview} sale=${sale}: Home scrolls ${home.overflow}px at 1120x720`);
        assert.ok(home.bottom <= home.innerHeight, `${preview}: Home ends at ${home.bottom}`);
      }
      if (expected !== "none") {
        const text = (await banner.innerText()).replace(/\s+/g, " ");
        assert.equal(/(Release unlocks|Unlock) every module/.test(text), expected === "upgrade", `${preview} pitch`);
        assert.equal(/Ends in 1d 04h/.test(text), expected !== "upgrade", `${preview} countdown`);
        if (expected !== "upgrade") assert.match(text, /Weekend sale Lifetime \$15 \$10 Ends in 1d 04h/);
        const geometry = await banner.evaluate(node => {
          const box = node.getBoundingClientRect();
          const parts = [...node.querySelectorAll(".promo-tag, .promo-copy > *, .promo-actions > *")]
            .filter(part => part.getClientRects().length);
          const centers = parts.map(part => { const r = part.getBoundingClientRect(); return r.top + r.height / 2; });
          const title = node.querySelector(".promo-copy > strong");
          return {
            height: box.height,
            gap: document.querySelector(".launch-panel").getBoundingClientRect().top - box.bottom,
            spread: Math.max(...centers) - Math.min(...centers),
            clipped: parts.filter(part => { const r = part.getBoundingClientRect(); return r.left < box.left || r.right > box.right; }).length,
            truncated: title.scrollWidth > title.clientWidth,
            code: Boolean(node.querySelector(".promo-code")?.getClientRects().length),
            play: document.querySelector('[data-action="launch"]').getBoundingClientRect().width
          };
        });
        assert.ok(geometry.height <= 40, `banner is ${geometry.height}px tall`);
        assert.ok(geometry.gap >= 0 && geometry.gap <= 10, `gap to the current client is ${geometry.gap}px`);
        assert.ok(geometry.spread <= 2, `one row (centers spread ${geometry.spread}px)`);
        assert.equal(geometry.clipped, 0, "nothing clipped at the banner edge");
        assert.equal(geometry.truncated, false, "fixture title is not truncated");
        // The coupon chip is the first thing to go when space runs out.
        if (expected !== "upgrade") assert.equal(geometry.code, width !== 820, `code chip @${width}`);
        if (geometry.code) assert.match(text, /Code WEEKEND10 Copy/);
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
  console.log("PASS: upgrade/sale banner tier gating, combined rule, one row <=40px, Home fits at 1120x720, countdown, copy, session dismissal and motion at 820/1120/1440");
} finally { await browser.close(); }
