const [endpoint, pageUrl] = process.argv.slice(2);
if (!endpoint || !pageUrl) {
  throw new Error("Usage: windows-account-preview-smoke.mjs <websocket-url> <page-url>");
}

const socket = new WebSocket(endpoint);
let requestId = 0;
const pending = new Map();

const delay = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

socket.addEventListener("message", (event) => {
  const message = JSON.parse(String(event.data));
  if (!message.id || !pending.has(message.id)) return;
  const { resolve, reject } = pending.get(message.id);
  pending.delete(message.id);
  if (message.error) reject(new Error(message.error.message || JSON.stringify(message.error)));
  else resolve(message.result);
});

await new Promise((resolve, reject) => {
  socket.addEventListener("open", resolve, { once: true });
  socket.addEventListener("error", () => reject(new Error("Could not connect to the Windows WebView DevTools target.")), { once: true });
  setTimeout(() => reject(new Error("Timed out connecting to the Windows WebView DevTools target.")), 10_000);
});

function command(method, params = {}) {
  const id = ++requestId;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(expression) {
  const result = await command("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text || "WebView evaluation failed.");
  }
  return result.result?.value;
}

async function waitFor(expression, predicate, label, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  let last;
  while (Date.now() < deadline) {
    try {
      last = await evaluate(expression);
      if (predicate(last)) return last;
    } catch {
      // Navigation replaces the JavaScript context briefly. Retry until ready.
    }
    await delay(150);
  }
  throw new Error(`${label} timed out. Last result: ${JSON.stringify(last)}`);
}

await command("Runtime.enable");
await command("Page.enable");

const fixtures = [
  { id: "ad-tier", name: "River Cole", builds: ["ad_tier"], outcome: "dashboard" },
  { id: "ad-tier-active", name: "River Cole", builds: ["ad_tier"], outcome: "start" },
  { id: "giveaway", name: "Mason Reed", builds: ["release"], outcome: "start" },
  { id: "beta-weekly", name: "Nova Brooks", builds: ["release", "beta_plus"], outcome: "start" },
  { id: "media", name: "Harper Lane", builds: ["release", "beta_plus", "media"], outcome: "start" },
  { id: "owner", name: "Jordan Vale", builds: ["release", "beta_plus", "media", "dev"], outcome: "start" }
];

const results = [];
for (const fixture of fixtures) {
  const target = new URL(pageUrl);
  target.search = `?preview=${encodeURIComponent(fixture.id)}`;
  await command("Page.navigate", { url: target.href });
  await waitFor(
    `({ready:document.readyState,name:document.body?.innerText?.includes(${JSON.stringify(fixture.name)})})`,
    (value) => value?.ready === "complete" && value?.name,
    `${fixture.id} account render`
  );

  await evaluate(`document.querySelector('[data-action="dismiss-client-popup"]')?.click()`);
  await evaluate(`document.querySelector('button[data-view="profiles"]')?.click()`);
  const profileView = await waitFor(
    `({view:Boolean(document.querySelector('[data-view-frame="profiles"]')),builds:[...document.querySelectorAll('[data-action="select-profile-build"]')].map((node)=>node.dataset.build)})`,
    (value) => value?.view && JSON.stringify(value.builds) === JSON.stringify(fixture.builds),
    `${fixture.id} build choices`
  );
  const builds = profileView.builds;
  if (JSON.stringify(builds) !== JSON.stringify(fixture.builds)) {
    throw new Error(`${fixture.id} exposed ${JSON.stringify(builds)}; expected ${JSON.stringify(fixture.builds)}.`);
  }

  if (fixture.outcome === "dashboard") {
    await evaluate(`document.querySelector('button[data-view="play"]')?.click()`);
    await waitFor(
      `Boolean(document.querySelector('[data-view-frame="play"] [data-action="launch"]'))`,
      Boolean,
      `${fixture.id} play view`
    );
    await evaluate(`document.querySelector('[data-action="launch"]')?.click()`);
    await waitFor(
      `({heading:[...document.querySelectorAll('h2')].some((node)=>node.textContent.includes('Dashboard sponsor check')),running:document.body?.innerText?.includes('STOP MINECRAFT')})`,
      (value) => value?.heading && !value?.running,
      `${fixture.id} sponsor redirect`
    );
  } else {
    await evaluate(`document.querySelector('button[data-view="updates"]')?.click()`);
    await waitFor(
      `Boolean(document.querySelector('[data-view-frame="updates"] [data-action="install"]'))`,
      Boolean,
      `${fixture.id} updates view`
    );
    await evaluate(`document.querySelector('[data-view-frame="updates"] [data-action="install"]')?.click()`);
    await waitFor(
      `(()=>{const card=[...document.querySelectorAll('[data-view-frame="updates"] .update-card')].find((node)=>node.querySelector('span')?.textContent?.trim()==='Managed client');const button=card?.querySelector('[data-action="install"]');return {status:card?.querySelector('strong')?.textContent?.trim()||'',disabled:Boolean(button?.disabled)};})()`,
      (value) => value?.status === "1.21.11" && !value.disabled,
      `${fixture.id} client install`
    );
    await evaluate(`document.querySelector('button[data-view="play"]')?.click()`);
    await waitFor(
      `Boolean(document.querySelector('[data-view-frame="play"] [data-action="launch"]'))`,
      Boolean,
      `${fixture.id} play view`
    );
    await evaluate(`document.querySelector('[data-action="launch"]')?.click()`);
    await waitFor(
      `({text:document.querySelector('[data-action="launch"]')?.textContent?.trim().toUpperCase(),disabled:Boolean(document.querySelector('[data-action="launch"]')?.disabled),modal:document.querySelector('.update-modal h2')?.textContent?.trim()||"",status:document.querySelector('.status-line')?.textContent?.trim()||""})`,
      (value) => value?.text === "STOP MINECRAFT",
      `${fixture.id} process start`
    );
  }

  results.push({ fixture: fixture.id, builds, outcome: fixture.outcome });
}

socket.close();
process.stdout.write(`${JSON.stringify(results)}\n`);
