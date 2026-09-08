import assert from 'node:assert/strict';
const {chromium} = await import(process.env.LAUNCHER_PLAYWRIGHT_MODULE || 'playwright');
import {writeFile, mkdir} from 'node:fs/promises';
const [baseUrl='http://127.0.0.1:5187', root='/tmp/launcher-ux-regression'] = process.argv.slice(2);
await mkdir(root, {recursive:true});
const browser=await chromium.launch({...(process.env.LAUNCHER_CHROMIUM ? {executablePath:process.env.LAUNCHER_CHROMIUM} : {}),headless:true});
const results=[];
async function check(name,fn){try{results.push({name,status:'pass',detail:await fn()});}catch(e){results.push({name,status:'fail',error:e.message});}}
const page=await browser.newPage({viewport:{width:820,height:560},locale:'de-DE',reducedMotion:'reduce'});
page.setDefaultTimeout(5000);
await page.route('https://**/*',r=>r.abort());
await page.goto(`${baseUrl}/?preview=owner`);
await page.waitForFunction(()=>document.querySelector('[data-action="launch"]')&&!document.querySelector('[data-action="launch"]').disabled);
const profiles=async()=>{await page.locator('button[data-view="profiles"]').first().click();await page.locator('[data-view-frame="profiles"]').waitFor();};
try{
 await check('profile type radio arrow navigation',async()=>{
  await profiles();await page.getByRole('button',{name:'Create profile',exact:true}).click();
  const radio=page.locator('[data-profile-type="client"]');await radio.click();await radio.focus();await page.keyboard.press('ArrowRight');
  assert.equal(await page.locator('[data-profile-type="fabric"]').getAttribute('aria-checked'),'true');
 });
 await check('profile create popup Escape dismissal',async()=>{await page.keyboard.press('Escape');assert.equal(await page.locator('[data-profile-create-menu]').count(),0);});
 await check('empty profile name explains recovery and traps modal focus',async()=>{
  if(!await page.locator('[data-profile-create-menu]').count())await page.getByRole('button',{name:'Create profile',exact:true}).click();
  await page.locator('[data-field="newProfileName"]').fill('');await page.locator('[data-action="create-profile"]').click();
  await page.getByRole('dialog',{name:'Profile name needed'}).waitFor();
  for(let i=0;i<8;i++){await page.keyboard.press(i%2?'Shift+Tab':'Tab');assert.equal(await page.evaluate(()=>!!document.activeElement.closest('[aria-modal="true"]')),true);}
  await page.getByRole('button',{name:'OK',exact:true}).click();
 });
 await check('Unicode profile create survives reload without HTML injection',async()=>{
  if(!await page.locator('[data-profile-create-menu]').count())await page.getByRole('button',{name:'Create profile',exact:true}).click();
  const label='Équipe 日本 <img src=x onerror=alert(1)>';
  await page.locator('[data-field="newProfileName"]').fill(label);await page.locator('[data-action="create-profile"]').click();
  if(await page.locator('[data-profile-create-menu]').count()) {
   results.push({name:'first Create click after editing name is swallowed by blur rerender',status:'fail',detail:'The menu remained open after a valid name and first click. Retrying same control for subsequent checks.'});
   await page.locator('[data-action="create-profile"]').click();
  }
  await page.locator('[data-action="select-profile"][aria-pressed="true"]').filter({hasText:label}).waitFor();
  assert.equal(await page.locator('img[src="x"]').count(),0);
  await page.reload();await profiles();await page.locator('[data-action="select-profile"]').filter({hasText:label}).waitFor();
 });
 await check('duplicate profile names create distinct ids',async()=>{
  const before=await page.locator('[data-action="select-profile"]').evaluateAll(es=>es.map(e=>e.dataset.profile));
  await page.getByRole('button',{name:'Create profile',exact:true}).click();await page.locator('[data-field="newProfileName"]').fill('Équipe 日本 <img src=x onerror=alert(1)>');await page.locator('[data-field="newProfileName"]').press('Tab');await page.locator('[data-action="create-profile"]').click();
  await page.locator('[data-profile-create-menu]').waitFor({state:'detached'});
  const after=await page.locator('[data-action="select-profile"]').evaluateAll(es=>es.map(e=>e.dataset.profile));
  assert.equal(after.length,before.length+1);assert.equal(new Set(after).size,after.length);
 });
 await check('delete cancel preserves profile',async()=>{
  const before=await page.locator('[data-action="select-profile"]').count();
  await page.locator('[data-action="request-delete-profile"]').click();await page.getByRole('button',{name:'Cancel',exact:true}).click();assert.equal(await page.locator('[data-action="select-profile"]').count(),before);
 });
 await check('long branding remains editable and no page overflow',async()=>{
  await page.locator('button[data-view="settings"]').first().click();
  const input=page.locator('[data-field="launcherDisplayName"]');await input.fill('W'.repeat(40));await input.press('Tab');
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:`root/long-branding.png`.replace('root',root)});
 });
 await check('advanced settings reachable at minimum size',async()=>{await page.getByRole('button',{name:'Show Advanced',exact:true}).click();const input=page.locator('[data-field="javaArgs"]');await input.focus();assert.equal(await input.isVisible(),true);});
 await check('all settings inputs have a visible focus destination',async()=>{
  const inputs=page.locator('[data-view-frame="settings"] input,[data-view-frame="settings"] select');
  for(let i=0;i<await inputs.count();i++){const e=inputs.nth(i);if(!await e.isVisible()||await e.isDisabled())continue;await e.focus();await e.scrollIntoViewIfNeeded();assert.equal(await e.evaluate(n=>n===document.activeElement),true);}return await inputs.count();
 });
}finally{await writeFile(`${root}/interaction-results.json`,JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));await browser.close();}

assert.deepEqual(results.filter(result => result.status !== 'pass'), [], 'All interaction regressions must pass');
