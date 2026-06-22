import { invoke } from "@tauri-apps/api/core";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import "./styles.css";

const SITE = "https://gamble-client.store";
const DASH = "https://dash.gamble-client.store";
const TOKEN_KEY = "gamble.launcher.token";

const app = document.querySelector("#app");

const profiles = [
  { id: "gamble-client", label: "With Gamble Client", fabric: true, client: true },
  { id: "vanilla", label: "Vanilla", fabric: false, client: false },
  { id: "fabric", label: "Fabric", fabric: true, client: false }
];

const builds = [
  { id: "release", label: "Release" },
  { id: "beta_plus", label: "Beta++" },
  { id: "media", label: "Media" },
  { id: "ad_tier", label: "Ad Tier" }
];

const state = {
  view: "play",
  info: null,
  version: null,
  token: "",
  account: null,
  microsoft: null,
  ads: null,
  selectedProfile: "gamble-client",
  selectedBuild: "ad_tier",
  memory: "4",
  username: defaultUsername(),
  javaArgs: "",
  status: "Starting",
  busy: false,
  signIn: null,
  signInError: "",
  microsoftSignIn: null,
  microsoftError: "",
  microsoftPollCancelled: false,
  sponsor: null,
  mods: [],
  packs: [],
  diagnostics: [],
  manifest: null,
  log: []
};

function defaultUsername() {
  const value = (globalThis.localStorage?.getItem("gamble.launcher.username") || "").trim();
  return value || "Player";
}

function render() {
  const profile = profiles.find((item) => item.id === state.selectedProfile) || profiles[0];
  const signedIn = Boolean(state.account && state.token);
  const selectedBuild = buildForAccount();
  const canInstall = signedIn && profile.client;
  app.innerHTML = `
    <section class="shell">
      <aside class="rail">
        <div class="brand">
          <div class="brand-mark">GC</div>
          <div>
            <strong>Gamble Client</strong>
            <span>Launcher ${escapeHtml(state.info?.version || "0.1.62")}</span>
          </div>
        </div>
        <nav>
          ${navButton("play", "Play")}
          ${navButton("mods", "Mods")}
          ${navButton("packs", "Resource Packs")}
          ${navButton("diagnostics", "Diagnostics")}
          <button class="nav-item" type="button" data-open="${DASH}/dashboard.html">Dashboard</button>
          <button class="nav-item" type="button" data-open="${SITE}/download">Downloads</button>
          <button class="nav-item" type="button" data-open="https://discord.gg/YPescfEt">Discord</button>
        </nav>
        <div class="rail-card">
          <span>Account</span>
          <strong>${escapeHtml(accountTitle())}</strong>
          <small>${escapeHtml(accountMeta())}</small>
        </div>
        <div class="rail-card">
          <span>Launcher</span>
          <strong>${escapeHtml(state.version?.version || "Checking")}</strong>
          <small>${escapeHtml(state.status)}</small>
        </div>
      </aside>

      <section class="content">
        ${topbar(signedIn)}
        ${state.view === "play" ? playView(profile, selectedBuild, canInstall, signedIn) : ""}
        ${state.view === "mods" ? fileView("mods", profile, state.mods) : ""}
        ${state.view === "packs" ? fileView("packs", profile, state.packs) : ""}
        ${state.view === "diagnostics" ? diagnosticsView() : ""}
        ${state.signIn ? signInPanel() : ""}
        ${state.microsoftSignIn ? microsoftPanel() : ""}
      </section>
    </section>
  `;
}

function navButton(id, label) {
  return `<button class="nav-item ${state.view === id ? "active" : ""}" type="button" data-view="${id}">${label}</button>`;
}

function topbar(signedIn) {
  return `
    <header class="topbar">
      <div>
        <span class="eyebrow">${escapeHtml(profileLabel())}</span>
        <h1>${viewTitle()}</h1>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="refresh" ${state.busy ? "disabled" : ""}>Refresh</button>
        <button class="${signedIn ? "ghost" : "primary-small"}" type="button" data-action="${signedIn ? "signout" : "signin"}" ${state.busy ? "disabled" : ""}>${signedIn ? "Sign out" : "Sign in"}</button>
      </div>
    </header>
  `;
}

function playView(profile, selectedBuild, canInstall, signedIn) {
  const launchLabel = state.microsoft ? "Launch" : "Microsoft Sign In";
  const clientStatus = state.manifest ? displayManifest(state.manifest) : "Not checked";
  return `
    <section class="play-layout">
      <section class="launch-panel">
        <div>
          <span class="eyebrow">${escapeHtml(profile.label)}</span>
          <h2>${escapeHtml(selectedBuild.label)}</h2>
          <p>${escapeHtml(playCopy(profile, signedIn))}</p>
        </div>
        <div class="launch-actions">
          <button class="launch-button" type="button" data-action="launch" ${state.busy ? "disabled" : ""}>${escapeHtml(launchLabel)}</button>
          <button class="ghost" type="button" data-action="install" ${!canInstall || state.busy ? "disabled" : ""}>Update Client</button>
        </div>
      </section>

      <aside class="play-stack">
        <article class="status-card">
          <span>Launcher account</span>
          <strong>${escapeHtml(accountTitle())}</strong>
          <small>${escapeHtml(accountMeta())}</small>
        </article>
        <article class="status-card">
          <span>Minecraft account</span>
          <strong>${escapeHtml(microsoftTitle())}</strong>
          <button type="button" data-action="${state.microsoft ? "switch-microsoft" : "microsoft"}" ${state.busy ? "disabled" : ""}>${state.microsoft ? "Switch" : "Sign in"}</button>
        </article>
      </aside>
    </section>

    <section class="control-grid">
      <label>
        <span>Profile</span>
        <select data-field="selectedProfile">
          ${profiles.map((item) => `<option value="${item.id}" ${item.id === state.selectedProfile ? "selected" : ""}>${item.label}</option>`).join("")}
        </select>
      </label>
      <label>
        <span>Build</span>
        <select data-field="selectedBuild" ${!profile.client || adTierOnly() ? "disabled" : ""}>
          ${builds.map((item) => `<option value="${item.id}" ${item.id === selectedBuild.id ? "selected" : ""}>${item.label}</option>`).join("")}
        </select>
      </label>
      <label>
        <span>Memory</span>
        <select data-field="memory">
          ${["2", "3", "4", "5", "6", "7", "8", "10", "12", "16"].map((item) => `<option value="${item}" ${item === state.memory ? "selected" : ""}>${item} GB</option>`).join("")}
        </select>
      </label>
      <label>
        <span>Username</span>
        <input data-field="username" value="${escapeAttr(state.username)}" placeholder="Offline name">
      </label>
    </section>

    <section class="grid">
      <article>
        <span>Client</span>
        <strong>${escapeHtml(clientStatus)}</strong>
        <button type="button" data-action="install" ${!canInstall || state.busy ? "disabled" : ""}>Update</button>
      </article>
      <article>
        <span>Sponsor</span>
        <strong>${escapeHtml(sponsorTitle())}</strong>
        <button type="button" data-action="sponsor" ${!signedIn || !state.ads?.canWatch || state.busy ? "disabled" : ""}>Watch</button>
      </article>
      <article>
        <span>Mods</span>
        <strong>${profile.fabric ? `${state.mods.filter((item) => item.enabled).length} enabled` : "Vanilla"}</strong>
        <button type="button" data-view="mods">Manage</button>
      </article>
      <article>
        <span>Resource packs</span>
        <strong>${state.packs.filter((item) => item.enabled).length} enabled</strong>
        <button type="button" data-view="packs">Manage</button>
      </article>
    </section>

    ${state.sponsor ? sponsorOverlay() : ""}
    ${logView()}
  `;
}

function signInPanel() {
  return `
    <section class="signin-panel">
      <div>
        <span class="eyebrow">Browser sign-in</span>
        <h2>${escapeHtml(state.signInError ? "Open this link" : "Waiting for sign-in")}</h2>
        <p>${escapeHtml(state.signInError || "Finish sign-in in your browser. This window will update automatically.")}</p>
        <code>${escapeHtml(state.signIn.loginUrl || "")}</code>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="open-signin-link">Open</button>
        <button class="ghost" type="button" data-action="copy-signin-link">Copy</button>
        <button class="ghost" type="button" data-action="cancel-signin">Cancel</button>
      </div>
    </section>
  `;
}

function microsoftPanel() {
  const userCode = microsoftUserCode(state.microsoftSignIn);
  const url = microsoftVerificationUrl(state.microsoftSignIn);
  return `
    <section class="signin-panel">
      <div>
        <span class="eyebrow">Microsoft sign-in</span>
        <h2>${escapeHtml(userCode || "Code")}</h2>
        <p>${escapeHtml(state.microsoftError || state.microsoftSignIn.message || "Open Microsoft sign-in and enter the code.")}</p>
        <code>${escapeHtml(url)}</code>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="open-microsoft-link">Open</button>
        <button class="ghost" type="button" data-action="copy-microsoft-code">Copy Code</button>
        <button class="ghost" type="button" data-action="cancel-microsoft">Cancel</button>
      </div>
    </section>
  `;
}

function fileView(kind, profile, files) {
  const isPacks = kind === "packs";
  const disabled = kind === "mods" && !profile.fabric;
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">${escapeHtml(profile.label)}</span>
        <h2>${isPacks ? "Resource Packs" : "Mods"}</h2>
      </div>
      <div class="top-actions">
        ${isPacks ? `<button class="ghost" type="button" data-action="add-packs">Add</button>` : ""}
        <button class="ghost" type="button" data-action="open-${isPacks ? "packs" : "mods"}">Open Folder</button>
        <button class="ghost" type="button" data-action="reload-files">Refresh</button>
      </div>
    </section>
    ${disabled ? `<p class="empty">Vanilla has no mods folder. Switch to a Fabric profile to manage jar files.</p>` : ""}
    <section class="file-list">
      ${files.length ? files.map((file) => fileRow(kind, file)).join("") : `<p class="empty">${isPacks ? "No resource packs yet." : "No mod jars yet."}</p>`}
    </section>
    ${logView()}
  `;
}

function fileRow(kind, file) {
  return `
    <article class="file-row">
      <div>
        <strong>${escapeHtml(file.name)}</strong>
        <span>${escapeHtml(file.enabled ? "Enabled" : "Disabled")} · ${formatBytes(file.size)}</span>
      </div>
      <button type="button" data-action="toggle-file" data-kind="${kind}" data-path="${escapeAttr(file.path)}" ${file.locked ? "disabled" : ""}>${file.enabled ? "Disable" : "Enable"}</button>
    </article>
  `;
}

function diagnosticsView() {
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Local checks</span>
        <h2>Diagnostics</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="open-data">Data Folder</button>
        <button class="ghost" type="button" data-action="run-diagnostics">Run</button>
      </div>
    </section>
    <section class="diagnostics-list">
      ${state.diagnostics.length ? state.diagnostics.map((check) => `
        <article class="diagnostic-row ${check.ok ? "ok" : "warn"}">
          <strong>${escapeHtml(check.label)}</strong>
          <span>${escapeHtml(check.detail)}</span>
        </article>
      `).join("") : `<p class="empty">Run diagnostics to check folders, session files, Microsoft cache, Java, and latest launch log.</p>`}
    </section>
    ${logView()}
  `;
}

function sponsorOverlay() {
  const url = state.sponsor.adUrl || "";
  return `
    <section class="sponsor-panel">
      <div>
        <span class="eyebrow">Sponsor break</span>
        <h2>${state.sponsor.remaining}s</h2>
        <p>${escapeHtml(state.sponsor.message || "Keep the launcher open until the timer finishes.")}</p>
      </div>
      ${url ? `<video src="${escapeAttr(url)}" controls autoplay muted></video>` : `<div class="sponsor-fallback">Sponsor media is playing as a timed break on this device.</div>`}
    </section>
  `;
}

function logView() {
  return `
    <section class="log-card">
      <div class="log-head">
        <strong>Launcher Log</strong>
        <button type="button" data-action="clear-log">Clear</button>
      </div>
      <pre>${escapeHtml(state.log.slice(-80).join("\n"))}</pre>
    </section>
  `;
}

function viewTitle() {
  if (state.view === "mods") return "Manage Mods";
  if (state.view === "packs") return "Manage Resource Packs";
  if (state.view === "diagnostics") return "Launcher Diagnostics";
  return "Launch Setup";
}

function profileLabel() {
  return (profiles.find((item) => item.id === state.selectedProfile) || profiles[0]).label;
}

function playCopy(profile, signedIn) {
  if (!signedIn) return "Sign in to check access, refresh Ad Tier, and install the managed client payload.";
  if (!profile.client) return `${profile.label} uses the same managed folders without installing the Gamble Client jar.`;
  return "Install and verify the managed payload, keep mods/resource packs organized, and prepare the native launch path.";
}

function accountTitle() {
  if (!state.account) return "Not signed in";
  return state.account.displayName || state.account.discordUsername || state.account.email || "Signed in";
}

function accountMeta() {
  if (!state.account) return "Launcher account required";
  const plan = state.account.selectedPlan || "ad_tier";
  const status = state.account.accessStatus || "ad_tier";
  return `${plan.replaceAll("_", " ")} · ${status.replaceAll("_", " ")}`;
}

function microsoftTitle() {
  if (state.microsoft?.name) return state.microsoft.name;
  const userCode = microsoftUserCode(state.microsoftSignIn);
  if (userCode) return `Code ${userCode}`;
  return "Microsoft required";
}

function adTierOnly() {
  return state.account && (state.account.selectedPlan === "ad_tier" || state.account.accessStatus === "ad_tier") && !state.account.ownerAccess;
}

function buildForAccount() {
  if (adTierOnly()) return builds.find((item) => item.id === "ad_tier");
  return builds.find((item) => item.id === state.selectedBuild) || builds[0];
}

function sponsorTitle() {
  if (!state.account) return "Sign in";
  if (!state.ads) return "Checking";
  if (!state.ads.required) return "Ads off";
  if (state.ads.remainingSeconds > 0) return `${formatDuration(state.ads.remainingSeconds)} left`;
  return state.ads.canWatch ? "Ready" : "Capped";
}

function displayManifest(manifest) {
  return manifest.buildVersion || manifest.fileName || "Available";
}

function log(message) {
  const time = new Date().toLocaleTimeString();
  state.log.push(`[${time}] ${message}`);
  state.status = message;
}

async function api(path, options = {}) {
  const method = String(options.method || "GET").toUpperCase();
  const body = options.body ? JSON.parse(options.body) : {};
  return await invoke("launcher_api", {
    input: {
      method,
      path,
      token: state.token || "",
      body
    }
  });
}

async function boot() {
  try {
    state.info = await invoke("launcher_info");
    log(`Managed root: ${state.info.managed_root}`);
  } catch (error) {
    log(`Launcher info failed: ${error}`);
  }

  state.token = localStorage.getItem(TOKEN_KEY) || await invoke("read_launcher_token").catch(() => "");
  state.microsoft = await invoke("read_microsoft_account").catch(() => null);
  await Promise.allSettled([refreshVersion(), refreshFiles(), restoreSession()]);
  await invoke("ensure_profile", { profile: state.selectedProfile }).catch(() => {});
  render();
}

async function restoreSession() {
  if (!state.token) {
    log("Sign in to continue.");
    return;
  }
  try {
    const body = await api("/api/launcher/session");
    applyAccount(body);
    await invoke("save_launcher_token", { token: state.token });
    log(`Restored account: ${accountTitle()}`);
  } catch (error) {
    state.token = "";
    state.account = null;
    state.ads = null;
    localStorage.removeItem(TOKEN_KEY);
    await invoke("delete_launcher_token").catch(() => {});
    log(`Stored sign-in expired: ${error.message}`);
  }
}

async function refreshVersion() {
  try {
    state.version = await api("/api/launcher/version");
    log(`Launcher latest: ${state.version.version}`);
  } catch (error) {
    log(`Version check failed: ${error.message}`);
  }
}

async function refreshAccount() {
  if (!state.token) return;
  const body = await api("/api/launcher/account");
  applyAccount(body);
}

function applyAccount(body) {
  state.account = body.user || null;
  state.ads = body.ads || body.adReward || null;
  if (adTierOnly()) state.selectedBuild = "ad_tier";
  if (!adTierOnly() && state.selectedBuild === "ad_tier") state.selectedBuild = "release";
}

async function startSignIn() {
  setBusy(true, "Opening browser sign-in");
  try {
    const start = await api("/api/launcher/start", { method: "POST", body: "{}" });
    state.signIn = start;
    state.signInError = "";
    try {
      await invoke("open_url", { url: start.loginUrl });
    } catch (error) {
      state.signInError = `Could not open the browser automatically: ${error}`;
    }
    log("Waiting for browser sign-in.");
    render();
    await pollSignIn(start);
  } catch (error) {
    log(`Sign-in failed: ${error.message}`);
    if (state.signIn?.loginUrl) {
      state.signInError = error.message || "Sign-in failed. Open or copy the link below.";
      render();
    }
  } finally {
    setBusy(false);
  }
}

async function pollSignIn(start) {
  while (Date.now() / 1000 < Number(start.expiresAt || 0)) {
    await sleep(2000);
    let body;
    try {
      body = await invoke("launcher_api", {
        input: {
          method: "POST",
          path: "/api/launcher/poll",
          token: "",
          body: { code: start.code }
        }
      });
    } catch (error) {
      if (String(error).includes("HTTP 202")) {
        body = { status: "pending" };
      } else {
        throw new Error(String(error));
      }
    }
    if (body.status === "pending") {
      state.status = `Waiting ${formatDuration(Math.max(0, start.expiresAt - Math.floor(Date.now() / 1000)))}`;
      render();
      continue;
    }
    if (body.status === "ready" && body.token) {
      state.token = body.token;
      localStorage.setItem(TOKEN_KEY, state.token);
      await invoke("save_launcher_token", { token: state.token });
      applyAccount(body);
      log(`Signed in as ${accountTitle()}`);
      state.signIn = null;
      state.signInError = "";
      await refreshManifest();
      return;
    }
  }
  throw new Error("Launcher sign-in expired.");
}

async function refreshManifest() {
  const profile = profiles.find((item) => item.id === state.selectedProfile);
  if (!state.token || !profile?.client) return;
  const build = buildForAccount();
  state.manifest = await api("/api/launcher/manifest", {
    method: "POST",
    body: JSON.stringify({ build: build.id })
  });
  log(`Client available: ${displayManifest(state.manifest)}`);
}

async function installSelected() {
  setBusy(true, "Installing client");
  try {
    const build = buildForAccount();
    const result = await invoke("install_client_manifest", {
      profile: state.selectedProfile,
      build: build.id,
      token: state.token
    });
    state.manifest = result;
    log(result.message);
    await refreshFiles();
  } catch (error) {
    log(`Install failed: ${error.message || error}`);
  } finally {
    setBusy(false);
  }
}

async function startSponsor() {
  setBusy(true, "Starting sponsor break");
  try {
    const start = await api("/api/launcher/ad-reward/start", { method: "POST", body: "{}" });
    const ads = start.ads || start.adReward || {};
    const seconds = Number(ads.adSeconds || start.adSeconds || 30);
    state.sponsor = {
      remaining: seconds,
      adUrl: ads.adUrl || start.adUrl || "",
      message: start.message || ads.message || ""
    };
    log(`Sponsor break started: ${seconds}s`);
    render();
    for (let remaining = seconds; remaining > 0; remaining -= 1) {
      await sleep(1000);
      state.sponsor.remaining = remaining - 1;
      render();
    }
    const complete = await api("/api/launcher/ad-reward/complete", { method: "POST", body: "{}" });
    applyAccount(complete);
    state.sponsor = null;
    log(complete.message || "Sponsored access refreshed.");
  } catch (error) {
    state.sponsor = null;
    log(`Sponsor break failed: ${error.message}`);
  } finally {
    setBusy(false);
  }
}

async function startMicrosoftSignIn() {
  setBusy(true, "Starting Microsoft sign-in");
  try {
    const start = await invoke("microsoft_device_start", { forceAccountPicker: true });
    state.microsoftSignIn = start;
    state.microsoftError = "";
    state.microsoftPollCancelled = false;
    const deviceCode = microsoftDeviceCode(start);
    if (!deviceCode) throw new Error("Microsoft did not return a device code.");
    const url = microsoftVerificationUrl(start);
    if (url) await invoke("open_url", { url }).catch((error) => {
      state.microsoftError = `Could not open Microsoft automatically: ${error}`;
    });
    log(`Microsoft sign-in code: ${microsoftUserCode(start)}`);
    render();
    await pollMicrosoftSignIn(start, deviceCode);
  } catch (error) {
    state.microsoftError = String(error?.message || error);
    log(`Microsoft sign-in failed: ${state.microsoftError}`);
    render();
  } finally {
    setBusy(false);
  }
}

async function pollMicrosoftSignIn(start, deviceCode) {
  const interval = Math.max(2, Number(start.intervalSeconds || start.interval_seconds || 5));
  const expiresAt = Date.now() + Math.max(60, Number(start.expiresInSeconds || start.expires_in_seconds || 900)) * 1000;
  while (!state.microsoftPollCancelled && Date.now() < expiresAt) {
    await sleep(interval * 1000);
    if (state.microsoftPollCancelled) return;
    const result = await invoke("microsoft_device_poll", { deviceCode, device_code: deviceCode });
    if (result.status === "pending") {
      state.status = `Waiting for Microsoft ${formatDuration(Math.ceil((expiresAt - Date.now()) / 1000))}`;
      render();
      continue;
    }
    if (result.status === "ready" && result.account) {
      state.microsoft = result.account;
      state.username = result.account.name || state.username;
      state.microsoftSignIn = null;
      state.microsoftError = "";
      log(`Microsoft account linked: ${state.microsoft.name}`);
      render();
      return;
    }
  }
  if (!state.microsoftPollCancelled) {
    state.microsoftError = "Microsoft sign-in expired. Try again.";
    log(state.microsoftError);
    render();
  }
}

function microsoftDeviceCode(value) {
  return String(value?.deviceCode || value?.device_code || "").trim();
}

function microsoftUserCode(value) {
  return String(value?.userCode || value?.user_code || "").trim();
}

function microsoftVerificationUrl(value) {
  return String(
    value?.verificationUriComplete
      || value?.verification_uri_complete
      || value?.verificationUri
      || value?.verification_uri
      || ""
  ).trim();
}

async function refreshFiles() {
  await invoke("ensure_profile", { profile: state.selectedProfile }).catch(() => {});
  const [mods, packs] = await Promise.all([
    invoke("list_local_files", { profile: state.selectedProfile, kind: "mods" }).catch(() => []),
    invoke("list_local_files", { profile: state.selectedProfile, kind: "resourcepacks" }).catch(() => [])
  ]);
  state.mods = mods;
  state.packs = packs;
}

async function runDiagnostics() {
  const result = await invoke("diagnostics", { profile: state.selectedProfile });
  state.diagnostics = result.checks || [];
  log("Diagnostics updated.");
}

function setBusy(value, message) {
  state.busy = value;
  if (message) state.status = message;
  render();
}

app.addEventListener("click", async (event) => {
  const view = event.target.closest("[data-view]")?.dataset.view;
  if (view) {
    state.view = view;
    await refreshFiles();
    render();
    return;
  }

  const open = event.target.closest("[data-open]");
  if (open) {
    await invoke("open_url", { url: open.dataset.open });
    return;
  }

  const actionEl = event.target.closest("[data-action]");
  const action = actionEl?.dataset.action;
  if (!action) return;

  if (action === "refresh") {
    setBusy(true, "Refreshing");
    await Promise.allSettled([refreshVersion(), refreshAccount(), refreshManifest(), refreshFiles()]);
    setBusy(false, "Ready");
  } else if (action === "signin") {
    await startSignIn();
  } else if (action === "open-signin-link") {
    if (state.signIn?.loginUrl) await invoke("open_url", { url: state.signIn.loginUrl }).catch((error) => log(`Open failed: ${error}`));
  } else if (action === "copy-signin-link") {
    if (state.signIn?.loginUrl) {
      await navigator.clipboard.writeText(state.signIn.loginUrl).catch(() => {});
      log("Copied sign-in link.");
      render();
    }
  } else if (action === "cancel-signin") {
    state.signIn = null;
    state.signInError = "";
    state.busy = false;
    log("Sign-in cancelled.");
    render();
  } else if (action === "signout") {
    state.token = "";
    state.account = null;
    state.ads = null;
    localStorage.removeItem(TOKEN_KEY);
    await invoke("delete_launcher_token").catch(() => {});
    log("Signed out.");
    render();
  } else if (action === "install") {
    await installSelected();
  } else if (action === "launch") {
    if (!state.microsoft) {
      await startMicrosoftSignIn();
      return;
    }
    const hasMicrosoft = true;
    const message = await invoke("launch_game_placeholder", { hasMicrosoft });
    log(message);
    render();
  } else if (action === "microsoft" || action === "switch-microsoft") {
    await startMicrosoftSignIn();
  } else if (action === "open-microsoft-link") {
    const url = microsoftVerificationUrl(state.microsoftSignIn);
    if (url) await invoke("open_url", { url }).catch((error) => log(`Open failed: ${error}`));
  } else if (action === "copy-microsoft-code") {
    const userCode = microsoftUserCode(state.microsoftSignIn);
    if (userCode) {
      await navigator.clipboard.writeText(userCode).catch(() => {});
      log("Copied Microsoft code.");
      render();
    }
  } else if (action === "cancel-microsoft") {
    state.microsoftPollCancelled = true;
    state.microsoftSignIn = null;
    state.microsoftError = "";
    state.busy = false;
    log("Microsoft sign-in cancelled.");
    render();
  } else if (action === "sponsor") {
    await startSponsor();
  } else if (action === "toggle-file") {
    await invoke("toggle_local_file", {
      profile: state.selectedProfile,
      kind: actionEl.dataset.kind === "packs" ? "resourcepacks" : "mods",
      path: actionEl.dataset.path
    }).catch((error) => log(`Toggle failed: ${error}`));
    await refreshFiles();
    render();
  } else if (action === "add-packs") {
    const selection = await openDialog({
      multiple: true,
      directory: false,
      filters: [{ name: "Resource Packs", extensions: ["zip"] }]
    });
    const paths = Array.isArray(selection) ? selection : selection ? [selection] : [];
    if (paths.length) {
      const copied = await invoke("add_resource_packs", { profile: state.selectedProfile, paths });
      log(`Added ${copied} resource pack${copied === 1 ? "" : "s"}.`);
      await refreshFiles();
      render();
    }
  } else if (action === "open-mods") {
    const path = await invoke("open_profile_folder", { profile: state.selectedProfile, kind: "mods" });
    log(`Opened ${path}`);
    render();
  } else if (action === "open-packs") {
    const path = await invoke("open_profile_folder", { profile: state.selectedProfile, kind: "resourcepacks" });
    log(`Opened ${path}`);
    render();
  } else if (action === "open-data") {
    const path = await invoke("open_profile_folder", { profile: state.selectedProfile, kind: "data" });
    log(`Opened ${path}`);
    render();
  } else if (action === "reload-files") {
    await refreshFiles();
    log("Files refreshed.");
    render();
  } else if (action === "run-diagnostics") {
    await runDiagnostics();
    render();
  } else if (action === "clear-log") {
    state.log = [];
    render();
  }
});

app.addEventListener("change", async (event) => {
  const field = event.target.closest("[data-field]")?.dataset.field;
  if (!field) return;
  state[field] = event.target.value;
  if (field === "username") localStorage.setItem("gamble.launcher.username", state.username);
  if (field === "selectedProfile") await refreshFiles();
  if (field === "selectedBuild") state.manifest = null;
  render();
});

app.addEventListener("input", (event) => {
  const field = event.target.closest("[data-field]")?.dataset.field;
  if (!field) return;
  state[field] = event.target.value;
  if (field === "username") localStorage.setItem("gamble.launcher.username", state.username);
});

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value).replaceAll("\n", " ");
}

function formatBytes(value) {
  const bytes = Number(value || 0);
  if (bytes > 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  if (bytes > 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${bytes} B`;
}

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0));
  const minutes = Math.floor(value / 60);
  const rest = value % 60;
  return minutes ? `${minutes}m ${rest}s` : `${rest}s`;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

boot();
render();
