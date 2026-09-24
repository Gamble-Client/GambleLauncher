import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || 'playwright');
const [base = 'http://127.0.0.1:5187', output = '/tmp/launcher-motion'] = process.argv.slice(2);
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
try {
  for (const mode of ['no-preference', 'reduce', 'disabled']) {
    const reducedMotion = mode === 'reduce' ? 'reduce' : 'no-preference';
    const page = await browser.newPage({ viewport: { width: 1120, height: 720 }, reducedMotion });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('https://**/*', route => route.abort());
    // Delay only the existing development fixture so its real launch UI can be inspected.
    await page.route('**/src/main.js', async route => {
      const response = await route.fetch();
      const source = await response.text();
      assert.ok(source.includes('if (command === "launch_game") {'));
      await route.fulfill({ response, body: source.replace('if (command === "launch_game") {',
        'if (command === "launch_game") { await new Promise(resolve => setTimeout(resolve, 5000));') });
    });
    await page.addInitScript(disabled => {
      if (disabled) localStorage.setItem('gamble.launcher.animations', 'false');
      window.openingAnimations = 0;
      const original = Element.prototype.animate;
      Element.prototype.animate = function (...args) {
        if (this.id === 'app') window.openingAnimations++;
        return original.apply(this, args);
      };
    }, mode === 'disabled');
    await page.goto(`${base}/?preview=owner`);
    await page.waitForFunction(() => !document.querySelector('[data-action="launch"]')?.disabled);
    assert.doesNotMatch(await page.locator('.play-overview').innerText(), /signed in/i);
    assert.equal(await page.locator('.play-overview .account-row').count(), 0);
    await page.screenshot({ path: `${output}/play-${mode}.png` });
    await page.locator('[data-action="launch"]').click();
    const emblem = page.locator('.launch-emblem');
    await emblem.waitFor();
    await page.waitForFunction(expected => {
      const el = document.querySelector('.launch-emblem');
      return el && getComputedStyle(el, '::before').animationName === expected;
    }, reducedMotion === 'reduce' ? 'none' : 'launch-orbit');
    if (mode === 'disabled') {
      // Read in one page task: progress renders replace the emblem node, so a
      // resolved locator handle can be detached before a second round trip.
      assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.launch-emblem'), '::before').animationIterationCount), '1');
    }
    await page.screenshot({ path: `${output}/launch-${mode}.png` });
    await page.getByRole('button', { name: 'Stop Minecraft', exact: true }).waitFor();
    assert.equal(await emblem.count(), 0, 'Launch animation ends with real completion');
    assert.equal(await page.evaluate(() => window.openingAnimations), mode === 'no-preference' ? 1 : 0,
      'Opening motion must not replay on account/progress renders');
    assert.deepEqual(errors, []);
    await page.close();
  }
  console.log('Opening once, launch lifecycle, reduced motion, compact account: passed');
} finally { await browser.close(); }
