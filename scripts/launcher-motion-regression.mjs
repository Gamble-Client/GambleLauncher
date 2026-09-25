import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || 'playwright');
const [base = 'http://127.0.0.1:5187', output = '/tmp/launcher-motion'] = process.argv.slice(2);
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true,
  ...(process.env.LAUNCHER_CHROMIUM ? { executablePath: process.env.LAUNCHER_CHROMIUM } : {}) });
// Purposeful, flat motion: every one-shot entrance ends within 300ms; only
// progress, the launch spinner and the refresh skeleton may loop. Reduced
// motion and the Launcher animations setting must remove all of it.
const ONE_SHOT = ['view-enter', 'rise-in', 'pop-in', 'fade-in', 'knob-on', 'knob-off'];
const LOOPS = ['launch-orbit', 'launchProgressSweep', 'launch-sweep', 'skeleton-pulse'];
const MAX_ONE_SHOT_SECONDS = 0.3;
const seconds = (value) => parseFloat(String(value).split(',')[0]) || 0;
try {
  for (const mode of ['no-preference', 'reduce', 'disabled']) {
    const reducedMotion = mode === 'reduce' ? 'reduce' : 'no-preference';
    const motionExpected = mode === 'no-preference';
    const page = await browser.newPage({ viewport: { width: 1120, height: 720 }, reducedMotion });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('https://**/*', route => route.abort());
    // Delay only the existing development fixture so its real launch UI can be
    // inspected. The glob keeps matching after Vite appends an HMR ?t= stamp.
    await page.route('**/src/main.js*', async route => {
      const response = await route.fetch();
      const source = await response.text();
      assert.ok(source.includes('if (command === "launch_game") {'));
      await route.fulfill({ response, body: source.replace('if (command === "launch_game") {',
        'if (command === "launch_game") { await new Promise(resolve => setTimeout(resolve, 5000));') });
    });
    await page.addInitScript(disabled => {
      if (disabled) localStorage.setItem('gamble.launcher.animations', 'false');
      window.openingAnimations = 0;
      window.animatedTargets = [];
      window.motionClasses = {};
      const originalAnimate = Element.prototype.animate;
      Element.prototype.animate = function (...args) {
        if (this.id === 'app') window.openingAnimations++;
        window.animatedTargets.push(this.className || this.id || this.tagName);
        return originalAnimate.apply(this, args);
      };
      const originalAdd = DOMTokenList.prototype.add;
      DOMTokenList.prototype.add = function (...tokens) {
        for (const token of tokens) window.motionClasses[token] = (window.motionClasses[token] || 0) + 1;
        return originalAdd.apply(this, tokens);
      };
    }, mode === 'disabled');
    await page.goto(`${base}/?preview=owner`);
    await page.waitForFunction(() => !document.querySelector('[data-action="launch"]')?.disabled);
    assert.doesNotMatch(await page.locator('.play-overview').innerText(), /signed in/i);
    assert.equal(await page.locator('.play-overview .account-row').count(), 0);
    assert.equal(await page.locator('.nav-item.active .nav-bar').count(), 1, 'Selected nav item carries the silver bar');
    await page.screenshot({ path: `${output}/play-${mode}.png` });

    // First paint: nav items and home cards stagger in once, and only with motion allowed.
    const classes = () => page.evaluate(() => window.motionClasses);
    const afterBoot = await classes();
    if (motionExpected) assert.ok((afterBoot['rise-in'] || 0) >= 9, `first paint stagger: ${JSON.stringify(afterBoot)}`);
    else assert.equal(afterBoot['rise-in'] || 0, 0, `no first paint stagger in ${mode}`);

    // View switch: outgoing fade, incoming fade + slide, silver bar slides (FLIP), bounded durations.
    await page.locator('.nav-item[data-view="settings"]').click();
    await page.locator('[data-view-frame="settings"]').waitFor();
    const settingsMotion = await page.evaluate(() => {
      const frame = document.querySelector('.view-frame');
      const style = getComputedStyle(frame);
      return { classes: window.motionClasses, name: style.animationName, duration: style.animationDuration,
        bar: window.animatedTargets.filter(name => /nav-bar/.test(name)).length };
    });
    if (motionExpected) {
      assert.ok((settingsMotion.classes['view-leave'] || 0) >= 1, 'outgoing view fades');
      assert.ok((settingsMotion.classes['view-enter'] || 0) >= 1, 'incoming view enters');
      assert.equal(settingsMotion.name, 'view-enter');
      assert.ok(seconds(settingsMotion.duration) > 0 && seconds(settingsMotion.duration) <= MAX_ONE_SHOT_SECONDS, `view duration ${settingsMotion.duration}`);
      assert.ok(settingsMotion.bar >= 1, 'nav bar slides between items');
    } else {
      assert.equal(settingsMotion.classes['view-enter'] || 0, 0, `no view entrance in ${mode}`);
      assert.equal(settingsMotion.classes['view-leave'] || 0, 0, `no view exit in ${mode}`);
      assert.equal(settingsMotion.bar, 0, `nav bar does not animate in ${mode}`);
    }

    // Toggle: the knob replays its move on the re-rendered control.
    const toggle = page.locator('[data-setting-toggle="disableClientShaders"]');
    await toggle.click();
    await toggle.click();
    const knob = await classes();
    if (motionExpected) assert.ok((knob['knob-on'] || 0) >= 1 && (knob['knob-off'] || 0) >= 1, `knob animates: ${JSON.stringify(knob)}`);
    else assert.equal((knob['knob-on'] || 0) + (knob['knob-off'] || 0), 0, `knob static in ${mode}`);
    await page.screenshot({ path: `${output}/settings-${mode}.png` });

    // Back home, then Play: launching state on the button, dialog fades and scales in once.
    await page.locator('.nav-item[data-view="play"]').click();
    await page.locator('[data-view-frame="play"]').waitFor();
    await page.locator('[data-action="launch"]').click();
    const emblem = page.locator('.launch-emblem');
    await emblem.waitFor();
    assert.match(await page.locator('[data-action="launch"]').innerText(), /Launching/, 'Play shows its launching label');
    await page.waitForFunction(expected => {
      const el = document.querySelector('.launch-emblem');
      return el && getComputedStyle(el, '::before').animationName === expected;
    }, reducedMotion === 'reduce' ? 'none' : 'launch-orbit');
    const dialogMotion = await page.evaluate(() => {
      const dialog = document.querySelector('.launch-progress-modal');
      const scrim = dialog.closest('.modal-scrim');
      const emblemStyle = getComputedStyle(document.querySelector('.launch-emblem'), '::before');
      return { classes: window.motionClasses, dialog: getComputedStyle(dialog).animationName, dialogDuration: getComputedStyle(dialog).animationDuration,
        scrim: getComputedStyle(scrim).animationName, iterations: emblemStyle.animationIterationCount };
    });
    if (motionExpected) {
      assert.equal(dialogMotion.dialog, 'pop-in');
      assert.equal(dialogMotion.scrim, 'fade-in');
      assert.ok(seconds(dialogMotion.dialogDuration) <= MAX_ONE_SHOT_SECONDS, `dialog duration ${dialogMotion.dialogDuration}`);
      assert.equal(dialogMotion.classes['pop-in'], 1, 'dialog entrance plays once, not on every progress render');
    } else {
      assert.equal(dialogMotion.classes['pop-in'] || 0, 0, `no dialog entrance in ${mode}`);
    }
    if (mode === 'disabled') {
      // Read in one page task: progress renders replace the emblem node, so a
      // resolved locator handle can be detached before a second round trip.
      assert.equal(dialogMotion.iterations, '1');
      assert.equal(await page.evaluate(() => document.documentElement.classList.contains('animations-off')), true);
    }
    // Let the 160/180ms dialog entrance finish, then confirm it is fully opaque.
    await page.waitForTimeout(250);
    assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.modal-scrim')).opacity), '1');
    await page.screenshot({ path: `${output}/launch-${mode}.png` });
    await page.getByRole('button', { name: 'Stop Minecraft', exact: true }).waitFor();
    assert.equal(await emblem.count(), 0, 'Launch animation ends with real completion');
    assert.equal(await page.evaluate(() => window.openingAnimations), motionExpected ? 1 : 0,
      'Opening motion must not replay on account/progress renders');

    // Refresh: flat skeleton on the current-client card while it runs, then a toast.
    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    await page.locator('.launch-panel.is-loading').waitFor();
    if (motionExpected) {
      assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('.launch-panel.is-loading .client-facts dd')).animationName), 'skeleton-pulse');
    }
    await page.locator('.launch-panel.is-loading').waitFor({ state: 'detached' });
    await page.locator('.toast', { hasText: 'Launcher refreshed.' }).waitFor();
    await page.locator('.toast', { hasText: 'Launcher refreshed.' }).waitFor({ state: 'detached', timeout: 5000 });

    // Every one-shot animation and transition declared in the stylesheet is bounded.
    const declared = await page.evaluate(({ oneShot, loops }) => {
      const found = { oneShot: {}, loops: {}, transitions: [] };
      for (const sheet of document.styleSheets) {
        let rules;
        try { rules = sheet.cssRules; } catch { continue; }
        for (const rule of rules) {
          if (!rule.style) continue;
          const name = rule.style.animationName;
          if (name && name !== 'none') {
            const bucket = loops.includes(name) ? found.loops : oneShot.includes(name) ? found.oneShot : null;
            if (!bucket) found.oneShot[`unknown:${name}`] = rule.style.animationDuration;
            else bucket[name] = rule.style.animationDuration;
          }
          if (rule.style.transitionDuration) found.transitions.push(rule.style.transitionDuration);
        }
      }
      const resolve = (value) => value.startsWith('var(') ? getComputedStyle(document.documentElement).getPropertyValue(value.slice(4, -1)).trim() : value;
      return { ...found, resolved: Object.fromEntries(Object.entries(found.oneShot).map(([k, v]) => [k, resolve(v)])) };
    }, { oneShot: ONE_SHOT, loops: LOOPS });
    for (const [name, duration] of Object.entries(declared.resolved)) {
      assert.ok(!name.startsWith('unknown:'), `unexpected animation ${name}`);
      const value = duration.endsWith('ms') ? parseFloat(duration) / 1000 : parseFloat(duration);
      assert.ok(value > 0 && value <= MAX_ONE_SHOT_SECONDS, `${name} lasts ${duration}`);
    }
    assert.deepEqual(errors, []);
    await page.close();
  }
  console.log('Opening once, staggered first paint, view/nav/toggle/dialog motion, launch lifecycle, skeleton + toast, reduced motion and disabled animations, bounded durations: passed');
} finally { await browser.close(); }
