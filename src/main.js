import { invoke as tauriInvoke } from "@tauri-apps/api/core";
import { open as tauriOpenDialog } from "@tauri-apps/plugin-dialog";
import "./styles.css";
import logoUrl from "./assets/cg-mod-icon.png";

const SITE = "https://gamble-client.store";
const DASH = "https://dash.gamble-client.store";
const TOKEN_KEY = "gamble.launcher.token";
const LAUNCHER_DISMISS_KEY = "gamble.launcher.dismissedLauncherVersion";
const CLIENT_DISMISS_KEY = "gamble.launcher.dismissedClientVersion";
const CUSTOM_PROFILES_KEY = "gamble.launcher.customProfiles";
const PROFILE_ACCOUNTS_KEY = "gamble.launcher.profileAccounts";
const ADVANCED_SETTINGS_KEY = "gamble.launcher.showAdvancedSettings";
const ANIMATIONS_KEY = "gamble.launcher.animations";
const UPDATE_CHECK_TTL_MS = 5 * 60 * 1000;
const SOCIAL_CHECK_TTL_MS = 60 * 1000;
const PREVIEW = !("__TAURI_INTERNALS__" in window);

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
  microsoftAccounts: [],
  ads: null,
  social: null,
  friendUsername: "",
  selectedProfile: "gamble-client",
  selectedBuild: "release",
  customProfiles: normalizeStoredProfiles(readJsonStorage(CUSTOM_PROFILES_KEY, [])),
  profileAccountOverrides: normalizeStoredObject(readJsonStorage(PROFILE_ACCOUNTS_KEY, {})),
  newProfileName: "",
  memory: defaultMemory(),
  username: defaultUsername(),
  javaArgs: defaultJavaArgs(),
  antiScreenshare: defaultAntiScreenshare(),
  showAdvancedSettings: defaultAdvancedSettings(),
  animationsEnabled: defaultAnimationsEnabled(),
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
  antiStatus: null,
  manifest: null,
  clientStatus: null,
  minecraftRunning: false,
  minecraftPid: null,
  popup: null,
  spotify: null,
  lastVersionCheckAt: 0,
  lastManifestCheckAt: 0,
  lastSocialCheckAt: 0,
  dismissedLauncherVersion: readStorage(LAUNCHER_DISMISS_KEY),
  dismissedClientVersion: readStorage(CLIENT_DISMISS_KEY),
  log: []
};

function readStorage(key, fallback = "") {
  return (globalThis.localStorage?.getItem(key) || fallback).trim();
}

function readJsonStorage(key, fallback) {
  try {
    const value = globalThis.localStorage?.getItem(key);
    if (!value) return fallback;
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

function normalizeStoredProfiles(value) {
  return Array.isArray(value) ? value : [];
}

function normalizeStoredObject(value) {
  return value && typeof value === "object" && !Array.isArray(value) ? value : {};
}

function defaultUsername() {
  return readStorage("gamble.launcher.username", "Player") || "Player";
}

function defaultMemory() {
  return readStorage("gamble.launcher.memory", "4") || "4";
}

function defaultJavaArgs() {
  return readStorage("gamble.launcher.javaArgs");
}

function defaultAntiScreenshare() {
  return globalThis.localStorage?.getItem("gamble.launcher.antiScreenshare") === "true";
}

function defaultAdvancedSettings() {
  return globalThis.localStorage?.getItem(ADVANCED_SETTINGS_KEY) === "true";
}

function defaultAnimationsEnabled() {
  return globalThis.localStorage?.getItem(ANIMATIONS_KEY) !== "false";
}

async function invoke(command, args = {}) {
  if (!PREVIEW) return await tauriInvoke(command, args);
  return mockInvoke(command, args);
}

async function openDialog(options) {
  if (!PREVIEW) return await tauriOpenDialog(options);
  return null;
}

async function mockInvoke(command, args = {}) {
  await sleep(35);
  if (command === "launcher_info") {
    return {
      version: "0.1.75",
      managed_root: "/home/theac/.local/share/gamble-client/minecraft",
      data_folder: "/home/theac/.local/share/gamble-client/cg-mod",
      session_file: "/home/theac/.local/share/gamble-client/cg-mod/launcher-session.txt",
      os: "linux"
    };
  }
  if (command === "read_launcher_token") return "preview-token";
  if (command === "read_microsoft_account") return { name: "BaseToucher", uuid: "8667ba71b85a4004af54457a9734eed7", xuid: "preview" };
  if (command === "list_microsoft_accounts") {
    return {
      selectedUuid: "8667ba71b85a4004af54457a9734eed7",
      accounts: [
        { name: "BaseToucher", uuid: "8667ba71b85a4004af54457a9734eed7", xuid: "preview" },
        { name: "AltStacker", uuid: "df2c98f29f4a4b29b7ca5a2a43c1f333", xuid: "preview2" }
      ]
    };
  }
  if (command === "select_microsoft_account") return state.microsoftAccounts.find((account) => account.uuid === args.uuid) || state.microsoft;
  if (command === "delete_microsoft_account_by_uuid") {
    return { selectedUuid: state.microsoft?.uuid || "", accounts: state.microsoftAccounts.filter((account) => account.uuid !== args.uuid) };
  }
  if (command === "save_launcher_token" || command === "ensure_profile" || command === "open_url") return "";
  if (command === "delete_launcher_token" || command === "delete_microsoft_account") return null;
  if (command === "launcher_api") {
    const path = args.input?.path || "";
    if (path === "/api/launcher/version") {
      return {
        version: "0.1.75",
        minVersion: "0.1.75",
        downloadUrl: "/api/launcher/download",
        downloads: {
          windows: { fileName: "Gamble-Client-Launcher-0.1.75-x64-setup.exe", downloadUrl: "/api/launcher/download/windows" },
          linuxRpm: { fileName: "Gamble-Client-Launcher-0.1.75-1.x86_64.rpm", downloadUrl: "/api/launcher/download/linux-rpm" },
          linuxDeb: { fileName: "Gamble-Client-Launcher_0.1.75_amd64.deb", downloadUrl: "/api/launcher/download/linux-deb" }
        }
      };
    }
    if (path === "/api/launcher/session" || path === "/api/launcher/account") {
      return {
        user: { displayName: "BaseToucher", selectedPlan: "owner", accessStatus: "owner", ownerAccess: true },
        ads: { required: false, canWatch: false, remainingSeconds: 0 }
      };
    }
    if (path === "/api/launcher/start") {
      return { loginUrl: "https://gamble-client.store/login", expiresAt: Math.floor(Date.now() / 1000) + 120, code: "preview" };
    }
  }
  if (command === "client_install_status") {
    return {
      fileName: "cg-client-1.21.11.jar",
      build: args.build || "release",
      buildVersion: "1.21.11",
      installed: false,
      updateAvailable: true,
      message: "Client update available: 1.21.11"
    };
  }
  if (command === "download_launcher_update") {
    return {
      version: "0.1.75",
      fileName: "Gamble-Client-Launcher_0.1.75_amd64.deb",
      path: "/home/theac/Downloads/Gamble-Client-Launcher_0.1.75_amd64.deb",
      message: "Opened the downloaded launcher installer."
    };
  }
  if (command === "list_local_files") {
    if (args.kind === "resourcepacks") {
      return [
        { name: "Clean Donut UI.zip", path: "/packs/clean.zip", enabled: true, locked: false, size: 2480000 },
        { name: "Low Fire.zip.disabled", path: "/packs/lowfire.zip.disabled", enabled: false, locked: false, size: 920000 }
      ];
    }
    return [
      { name: "fabric-api-0.139.5+1.21.11.jar", path: "/mods/fabric-api.jar", enabled: true, locked: true, size: 7200000 },
      { name: "gamble-client-loader.jar", path: "/mods/gamble-client-loader.jar", enabled: true, locked: true, size: 1800 },
      { name: "modmenu.jar", path: "/mods/modmenu.jar", enabled: true, locked: false, size: 520000 }
    ];
  }
  if (command === "diagnostics") {
    return {
      checks: [
        { label: "Java", ok: true, detail: "Java 21 available" },
        { label: "Profile folder", ok: true, detail: "Writable" },
        { label: "Client jar", ok: true, detail: "Ready" }
      ]
    };
  }
  if (command === "anti_screenshare_status") {
    return {
      enabled: state.antiScreenshare,
      available: true,
      bridgeOnline: false,
      source: "Preview config",
      message: state.antiScreenshare ? "Saved config has AntiScreenshare on." : "Saved config has AntiScreenshare off.",
      modulesPath: "/preview/cg-mod/modules.txt"
    };
  }
  if (command === "set_anti_screenshare") {
    state.antiScreenshare = Boolean(args.enabled);
    return {
      enabled: state.antiScreenshare,
      available: true,
      bridgeOnline: false,
      source: "Preview config",
      message: `AntiScreenshare ${state.antiScreenshare ? "enabled" : "disabled"} in preview config.`,
      modulesPath: "/preview/cg-mod/modules.txt"
    };
  }
  if (command === "apply_anti_screenshare_clean_view") {
    state.antiScreenshare = true;
    return {
      enabled: true,
      available: true,
      bridgeOnline: false,
      source: "Preview config",
      message: "Clean View applied for preview modules.",
      modulesPath: "/preview/cg-mod/modules.txt"
    };
  }
  if (command === "open_anti_screenshare_obs") return "Opened OBS Browser Source view.";
  if (command === "install_client_manifest") {
    return {
      buildVersion: "1.21.11",
      fileName: "cg-client-1.21.11.jar",
      installed: true,
      updateAvailable: false,
      message: "Updated managed client to: 1.21.11"
    };
  }
  if (command === "launch_game") {
    state.minecraftRunning = !state.minecraftRunning;
    state.minecraftPid = state.minecraftRunning ? 4242 : null;
    if (!state.minecraftRunning) return "Minecraft stop signal sent.";
    return "Minecraft process started (pid 4242). Latest launch log: /preview/latest-launch.log";
  }
  if (command === "minecraft_status") return { running: state.minecraftRunning, pid: state.minecraftPid };
  return null;
}

let renderedView = state.view;

function captureScrollState() {
  const nodes = Array.from(app.querySelectorAll("[data-scroll-key], .content, .file-list, .diagnostics-list, pre"));
  const seen = new Set();
  const entries = [];
  for (const node of nodes) {
    const key = scrollKey(node, entries.length);
    if (seen.has(key)) continue;
    seen.add(key);
    entries.push({ key, left: node.scrollLeft, top: node.scrollTop });
  }
  return { entries, windowX: window.scrollX, windowY: window.scrollY };
}

function scrollKey(node, index) {
  if (node.dataset.scrollKey) return node.dataset.scrollKey;
  if (node.classList.contains("content")) return "content";
  if (node.classList.contains("file-list")) return `file-list:${state.view}`;
  if (node.classList.contains("diagnostics-list")) return "diagnostics-list";
  if (node.tagName === "PRE") return "log";
  return `scroll:${index}`;
}

function restoreScrollState(snapshot) {
  if (!snapshot) return;
  const restore = () => {
    for (const entry of snapshot.entries || []) {
      const node = app.querySelector(`[data-scroll-key="${cssEscape(entry.key)}"]`)
        || Array.from(app.querySelectorAll(".content, .file-list, .diagnostics-list, pre")).find((item, index) => scrollKey(item, index) === entry.key);
      if (!node) continue;
      node.scrollLeft = entry.left;
      node.scrollTop = Math.min(entry.top, Math.max(0, node.scrollHeight - node.clientHeight));
    }
    window.scrollTo(snapshot.windowX, snapshot.windowY);
  };
  requestAnimationFrame(() => {
    restore();
    requestAnimationFrame(restore);
  });
}

function cssEscape(value) {
  if (globalThis.CSS?.escape) return CSS.escape(value);
  return String(value).replaceAll('"', '\\"').replaceAll("\\", "\\\\");
}

function render() {
  const scrollState = captureScrollState();
  const preserveScroll = scrollState && renderedView === state.view;
  const profile = currentProfile();
  const signedIn = Boolean(state.account && state.token);
  const selectedBuild = buildForAccount();
  const canInstall = signedIn && profile.client;
  app.innerHTML = `
    <section class="shell ${state.animationsEnabled ? "" : "animations-off"}">
      <aside class="rail">
        <div class="brand">
          <div class="brand-mark"><img src="${escapeAttr(logoUrl)}" alt=""></div>
          <div>
            <strong>Gamble Client</strong>
            <span>Launcher ${escapeHtml(state.info?.version || "0.1.75")}</span>
          </div>
        </div>
        <nav>
          ${navButton("play", "Play")}
          ${navButton("accounts", "Accounts")}
          ${navButton("social", "Social")}
          ${navButton("updates", "Updates")}
          ${navButton("profiles", "Profiles")}
          <button class="nav-item nav-action" type="button" data-open="${DASH}/dashboard.html">Dashboard</button>
          <button class="nav-item nav-action" type="button" data-open="https://discord.gg/YPescfEt">Discord</button>
        </nav>
        <div class="rail-status">
          <div class="rail-card">
            <span>Access</span>
            <strong>${escapeHtml(accountTitle())}</strong>
            <small>${escapeHtml(accountMeta())}</small>
          </div>
        </div>
      </aside>

      <section class="content" data-scroll-key="content">
        ${topbar(signedIn)}
        ${state.view === "play" ? playView(profile, selectedBuild, canInstall, signedIn) : ""}
        ${state.view === "accounts" ? accountsView(signedIn) : ""}
        ${state.view === "social" ? socialView() : ""}
        ${state.view === "updates" ? updatesView(profile, selectedBuild, canInstall, signedIn) : ""}
        ${state.view === "profiles" ? profilesView(profile, selectedBuild) : ""}
        ${state.view === "settings" ? settingsView(profile, selectedBuild) : ""}
        ${state.signIn ? signInPanel() : ""}
        ${state.microsoftSignIn ? microsoftPanel() : ""}
      </section>
      ${updatePopup()}
    </section>
  `;

  renderedView = state.view;
  if (preserveScroll) restoreScrollState(scrollState);
}

function navButton(id, label) {
  return `<button class="nav-item ${state.view === id ? "active" : ""}" type="button" data-view="${id}">${escapeHtml(label)}</button>`;
}

function allProfiles() {
  const custom = Array.isArray(state.customProfiles) ? state.customProfiles : [];
  return [...profiles, ...custom.filter((profile) => profile?.id && profile?.label)];
}

function currentProfile() {
  return allProfiles().find((item) => item.id === state.selectedProfile) || profiles[0];
}

function profileById(id) {
  return allProfiles().find((item) => item.id === id) || null;
}

function saveCustomProfiles() {
  localStorage.setItem(CUSTOM_PROFILES_KEY, JSON.stringify(state.customProfiles || []));
}

function saveProfileAccountOverrides() {
  localStorage.setItem(PROFILE_ACCOUNTS_KEY, JSON.stringify(state.profileAccountOverrides || {}));
}

function profileAccount(profile = currentProfile()) {
  const uuid = String(state.profileAccountOverrides?.[profile.id] || "").replaceAll("-", "").toLowerCase();
  if (!uuid) return state.microsoft;
  return state.microsoftAccounts.find((account) => String(account.uuid || "").replaceAll("-", "").toLowerCase() === uuid) || state.microsoft;
}

function profileAccountLabel(profile = currentProfile()) {
  const account = profileAccount(profile);
  if (!account) return "Default";
  const hasOverride = Boolean(state.profileAccountOverrides?.[profile.id]);
  return `${account.name || "Microsoft"}${hasOverride ? "" : " (default)"}`;
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
        <button class="icon-button ${state.view === "settings" ? "active" : ""}" type="button" data-view="settings" aria-label="Settings" title="Settings"><span class="settings-glyph" aria-hidden="true">⚙</span></button>
        <button class="${signedIn ? "ghost" : "primary-small"}" type="button" data-action="${signedIn ? "signout" : "signin"}" ${state.busy ? "disabled" : ""}>${signedIn ? "Sign out" : "Sign in"}</button>
      </div>
    </header>
  `;
}

function playView(profile, selectedBuild, canInstall, signedIn) {
  const launchLabel = state.minecraftRunning ? "Stop Minecraft" : "Launch";
  const clientStatus = clientStatusLabel();
  const enabledMods = state.mods.filter((item) => item.enabled).length;
  const enabledPacks = state.packs.filter((item) => item.enabled).length;
  const activeMicrosoft = profileAccount(profile);
  return `
    <section class="play-stage">
      <section class="launch-panel">
        <div class="launch-copy">
          <span class="eyebrow">Gamble Client</span>
          <h2>${escapeHtml(selectedBuild.label)}</h2>
          <div class="version-strip">
            <span>Version</span>
            <strong>Launcher ${escapeHtml(state.info?.version || "0.1.75")} · Client ${escapeHtml(clientStatus)}</strong>
          </div>
          <div class="launch-facts">
            <div>
              <span>Profile</span>
              <strong>${escapeHtml(profile.label)}</strong>
            </div>
            <div>
              <span>Memory</span>
              <strong>${escapeHtml(state.memory)} GB</strong>
            </div>
          </div>
        </div>
        <div class="launch-stack">
          <button class="launch-button" type="button" data-action="launch" ${state.busy ? "disabled" : ""}>${escapeHtml(launchLabel)}</button>
          ${activeMicrosoft ? "" : `<p class="launch-warning">No Microsoft account linked. Launch opens Microsoft sign-in first.</p>`}
        </div>
      </section>

      <aside class="account-panel">
        <div class="identity-card">
          <div class="avatar" style="${escapeAttr(avatarStyle(activeMicrosoft))}">${avatarText(avatarStyle(activeMicrosoft), activeMicrosoft?.name)}</div>
          <div>
            <span class="eyebrow">Active identity</span>
            <strong>${escapeHtml(activeMicrosoft?.name || accountTitle())}</strong>
            <small>${escapeHtml(activeMicrosoft ? `Launching with ${profileAccountLabel(profile)}` : "Connect Microsoft before launching")}</small>
          </div>
        </div>
        ${accountRow("Launcher", accountTitle(), accountMeta(), signedIn ? "Signed in" : "Required", launcherAvatarStyle())}
        ${accountRow("Minecraft", microsoftTitle(), state.microsoft ? `${state.microsoftAccounts.length || 1} saved account${(state.microsoftAccounts.length || 1) === 1 ? "" : "s"}` : "Required for launch", state.microsoft ? "Linked" : "Required", avatarStyle(state.microsoft), avatarInitials(state.microsoft?.name))}
      </aside>
    </section>

    <section class="quick-grid main-quick-grid">
      <article class="action-tile">
        <span>Sponsor</span>
        <strong>${escapeHtml(sponsorTitle())}</strong>
        <button type="button" data-action="sponsor" ${!signedIn || !state.ads?.canWatch || state.busy ? "disabled" : ""}>Watch</button>
      </article>
      <article class="action-tile">
        <span>Mods</span>
        <strong>${profile.fabric ? `${enabledMods} enabled` : "Vanilla"}</strong>
        <button type="button" data-view="profiles">Manage</button>
      </article>
      <article class="action-tile">
        <span>Resource packs</span>
        <strong>${enabledPacks} enabled</strong>
        <button type="button" data-view="profiles">Manage</button>
      </article>
      <article class="action-tile spotify-tile">
        <span>Spotify</span>
        <strong>${escapeHtml(spotifyTitle())}</strong>
        <button type="button" data-open="${DASH}/dashboard.html#spotify">Open Dashboard</button>
      </article>
    </section>
    ${state.sponsor ? sponsorOverlay() : ""}
  `;
}

function accountsView(signedIn) {
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Identity</span>
        <h2>Accounts</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="microsoft" ${state.busy ? "disabled" : ""}>Add Microsoft</button>
        <button class="ghost" type="button" data-action="${signedIn ? "signout" : "signin"}" ${state.busy ? "disabled" : ""}>${signedIn ? "Sign out launcher" : "Sign in launcher"}</button>
      </div>
    </section>
    <section class="account-list">
      ${state.microsoftAccounts.length ? state.microsoftAccounts.map(accountCard).join("") : `<p class="empty">No Microsoft accounts saved on this device.</p>`}
    </section>
  `;
}

function socialView() {
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Social</span>
        <h2>Friends</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="refresh" ${state.busy ? "disabled" : ""}>Refresh</button>
      </div>
    </section>
    ${friendsPanel()}
  `;
}

function accountCard(account) {
  const active = state.microsoft?.uuid && account.uuid?.toLowerCase() === state.microsoft.uuid.toLowerCase();
  return `
    <article class="account-card ${active ? "active" : ""}">
      <div class="identity-card">
        <div class="avatar" style="${escapeAttr(avatarStyle(account))}">${avatarText(avatarStyle(account), account.name)}</div>
        <div>
          <span class="eyebrow">${active ? "Active" : "Saved"}</span>
          <strong>${escapeHtml(account.name || "Minecraft account")}</strong>
          <small>${escapeHtml(account.uuid || account.xuid || "")}</small>
        </div>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="select-microsoft" data-uuid="${escapeAttr(account.uuid)}" ${active || state.busy ? "disabled" : ""}>Use</button>
        <button class="ghost danger" type="button" data-action="remove-microsoft" data-uuid="${escapeAttr(account.uuid)}" ${state.busy ? "disabled" : ""}>Remove</button>
      </div>
    </article>
  `;
}

function friendsPanel() {
  const social = state.social || {};
  const friends = social.friends || [];
  const incoming = social.incomingRequests || [];
  const outgoing = social.outgoingRequests || [];
  return `
    <section class="friends-panel">
      <div class="section-head">
        <div>
          <span class="eyebrow">Social</span>
          <h3>Friends</h3>
        </div>
        <div class="top-actions">
          <input class="inline-input" data-field="friendUsername" value="${escapeAttr(state.friendUsername)}" placeholder="Username">
          <button class="primary-small" type="button" data-action="send-friend" ${!state.token || state.busy ? "disabled" : ""}>Add</button>
        </div>
      </div>
      ${social.message ? `<p class="empty">${escapeHtml(social.message)}</p>` : ""}
      ${incoming.length ? `
        <div class="friend-group">
          <span class="eyebrow">Incoming</span>
          ${incoming.map((request) => friendRequestRow(request)).join("")}
        </div>
      ` : ""}
      <div class="friend-group">
        <span class="eyebrow">Friends</span>
        ${friends.length ? friends.map(friendRow).join("") : `<p class="empty">No friends added yet.</p>`}
      </div>
      ${outgoing.length ? `
        <div class="friend-group">
          <span class="eyebrow">Outgoing</span>
          ${outgoing.map(friendPendingRow).join("")}
        </div>
      ` : ""}
    </section>
  `;
}

function friendPendingRow(request) {
  const imageStyle = friendAvatarStyle(request);
  return `
    <article class="friend-row">
      <div class="friend-identity">
        <div class="mini-avatar" style="${escapeAttr(imageStyle)}">${avatarText(imageStyle, request.username)}</div>
        <div>
          <strong>${escapeHtml(request.username)}</strong>
          <small>Pending</small>
        </div>
      </div>
    </article>
  `;
}

function friendRow(friend) {
  const imageStyle = friendAvatarStyle(friend);
  return `
    <article class="friend-row">
      <div class="friend-identity">
        <div class="mini-avatar" style="${escapeAttr(imageStyle)}">${avatarText(imageStyle, friend.username)}</div>
        <div>
          <strong>${escapeHtml(friend.username)}</strong>
          <small>${friend.access?.owner ? "Owner" : friend.access?.media ? "Media" : friend.access?.beta ? "Beta" : "Friend"}</small>
        </div>
      </div>
      <button class="ghost danger" type="button" data-action="remove-friend" data-username="${escapeAttr(friend.username)}">Remove</button>
    </article>
  `;
}

function friendRequestRow(request) {
  const imageStyle = friendAvatarStyle(request);
  return `
    <article class="friend-row">
      <div class="friend-identity">
        <div class="mini-avatar" style="${escapeAttr(imageStyle)}">${avatarText(imageStyle, request.username)}</div>
        <div>
          <strong>${escapeHtml(request.username)}</strong>
          <small>Wants to add you</small>
        </div>
      </div>
      <div class="top-actions">
        <button class="primary-small" type="button" data-action="accept-friend" data-request="${escapeAttr(request.id)}">Accept</button>
        <button class="ghost" type="button" data-action="decline-friend" data-request="${escapeAttr(request.id)}">Decline</button>
      </div>
    </article>
  `;
}

function updatesView(profile, selectedBuild, canInstall, signedIn) {
  const checkedAt = Math.max(state.lastVersionCheckAt || 0, state.lastManifestCheckAt || 0);
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Updates</span>
        <h2>Updates</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="check-updates" ${state.busy ? "disabled" : ""}>Check</button>
      </div>
    </section>
    <section class="update-grid">
      <article class="update-card ${launcherNeedsUpdate() ? "warn" : ""}">
        <span>Launcher</span>
        <strong>${escapeHtml(launcherNeedsUpdate() ? "Update required" : "Current")}</strong>
        <p>Installed ${escapeHtml(state.info?.version || "unknown")} · Latest ${escapeHtml(latestLauncherVersion() || "unknown")}</p>
        <button class="primary-small" type="button" data-action="download-launcher" ${state.busy || !latestLauncherVersion() ? "disabled" : ""}>Update Launcher</button>
      </article>
      <article class="update-card ${clientNeedsUpdate() ? "warn" : ""}">
        <span>Managed client</span>
        <strong>${escapeHtml(clientStatusLabel())}</strong>
        <p>${escapeHtml(profile.client ? `${selectedBuild.label} build` : "This profile does not install the managed client jar.")}</p>
        <button class="primary-small" type="button" data-action="install" ${!canInstall || state.busy ? "disabled" : ""}>Update Client</button>
      </article>
      <article class="update-card">
        <span>Profile</span>
        <strong>${escapeHtml(profile.label)}</strong>
        <p>${escapeHtml(profile.client ? `Using ${selectedBuild.label} access for this profile.` : "Vanilla and plain Fabric profiles skip the managed client jar.")}</p>
        <button class="ghost" type="button" data-view="profiles">Profiles</button>
      </article>
      <article class="update-card">
        <span>Last check</span>
        <strong>${escapeHtml(checkedAt ? new Date(checkedAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" }) : "Not checked")}</strong>
        <p>Cached checks keep this tab quick. Use Check when you want a fresh launcher and client status pull.</p>
        <button class="ghost" type="button" data-action="check-updates" ${state.busy ? "disabled" : ""}>Refresh now</button>
      </article>
    </section>
  `;
}

function profilesView(profile, selectedBuild) {
  const profilesList = allProfiles();
  const custom = Boolean(profile.custom);
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Launch folders</span>
        <h2>Profiles</h2>
      </div>
      <div class="top-actions">
        <input class="inline-input" data-field="newProfileName" value="${escapeAttr(state.newProfileName)}" placeholder="New profile name">
        <button class="primary-small" type="button" data-action="create-profile" ${state.busy ? "disabled" : ""}>Create</button>
      </div>
    </section>
    <section class="profiles-layout">
      <div class="profile-list">
        ${profilesList.map((item) => `
          <article class="profile-card ${item.id === profile.id ? "active" : ""}">
            <div>
              <span>${escapeHtml(item.client ? "Gamble Client" : item.fabric ? "Fabric" : "Vanilla")}</span>
              <strong>${escapeHtml(item.label)}</strong>
              <small>${escapeHtml(profileAccountLabel(item))}</small>
            </div>
            <button class="ghost" type="button" data-action="select-profile" data-profile="${escapeAttr(item.id)}" ${item.id === profile.id ? "disabled" : ""}>Use</button>
          </article>
        `).join("")}
      </div>
      <div class="profile-editor">
        <section class="settings-grid compact-grid">
          ${custom ? `
            <label>
              <span>Profile name</span>
              <input data-field="selectedProfileLabel" value="${escapeAttr(profile.label)}" placeholder="Profile name">
            </label>
          ` : `
            <div class="setting-note">
              <span>Profile name</span>
              <strong>${escapeHtml(profile.label)}</strong>
              <small>Built-in</small>
            </div>
          `}
          <label>
            <span>Microsoft account</span>
            <select data-field="profileAccount">
              <option value="" ${state.profileAccountOverrides?.[profile.id] ? "" : "selected"}>Default${state.microsoft?.name ? ` (${escapeHtml(state.microsoft.name)})` : ""}</option>
              ${state.microsoftAccounts.map((account) => {
                const uuid = String(account.uuid || "").replaceAll("-", "").toLowerCase();
                const selected = String(state.profileAccountOverrides?.[profile.id] || "").replaceAll("-", "").toLowerCase() === uuid;
                return `<option value="${escapeAttr(uuid)}" ${selected ? "selected" : ""}>${escapeHtml(account.name || "Microsoft")}</option>`;
              }).join("")}
            </select>
          </label>
          <label>
            <span>Build</span>
            <select data-field="selectedBuild" ${!profile.client || adTierOnly() ? "disabled" : ""}>
              ${builds.map((item) => `<option value="${item.id}" ${item.id === selectedBuild.id ? "selected" : ""}>${item.label}</option>`).join("")}
            </select>
          </label>
        </section>
        ${fileSection("mods", profile, state.mods)}
        ${fileSection("packs", profile, state.packs)}
      </div>
    </section>
  `;
}

function settingsView(profile, selectedBuild) {
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Launcher</span>
        <h2>Settings</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="toggle-advanced">${state.showAdvancedSettings ? "Hide Advanced" : "Show Advanced"}</button>
        <button class="ghost" type="button" data-action="open-data">Data Folder</button>
      </div>
    </section>
    <section class="settings-grid">
      ${state.microsoft ? `
        <div class="setting-note">
          <span>Offline username</span>
          <strong>${escapeHtml(state.microsoft.name)}</strong>
          <small>Hidden because a Microsoft account is linked.</small>
        </div>
      ` : `
        <label>
          <span>Offline username</span>
          <input data-field="username" value="${escapeAttr(state.username)}" placeholder="Offline name">
        </label>
      `}
      ${privacyToggle("animationsEnabled", "Launcher animations", state.animationsEnabled, "setting")}
      ${privacyToggle("allowFriendRequests", "Friend requests", state.social?.settings?.allowFriendRequests !== false)}
      ${privacyToggle("showServerToFriends", "Show server to friends", Boolean(state.social?.settings?.showServerToFriends))}
      ${privacyToggle("shareSpotifyToFriends", "Share Spotify with friends", Boolean(state.social?.settings?.shareSpotifyToFriends))}
      ${state.showAdvancedSettings ? `
        <label>
          <span>Memory</span>
          <select data-field="memory">
            ${["2", "3", "4", "5", "6", "7", "8", "10", "12", "16"].map((item) => `<option value="${item}" ${item === state.memory ? "selected" : ""}>${item} GB</option>`).join("")}
          </select>
        </label>
        <label class="wide-field">
          <span>Java Args</span>
          <input data-field="javaArgs" value="${escapeAttr(state.javaArgs)}" placeholder="-XX:+UseZGC">
        </label>
      ` : ""}
    </section>
    ${antiScreensharePanel()}
    ${diagnosticsPanel()}
  `;
}

function privacyToggle(field, label, checked, kind = "privacy") {
  const dataAttr = kind === "setting" ? "data-setting-toggle" : "data-privacy-field";
  return `
    <label class="checkbox-setting">
      <span>${escapeHtml(label)}</span>
      <input type="checkbox" ${dataAttr}="${escapeAttr(field)}" ${checked ? "checked" : ""}>
    </label>
  `;
}

function privacyView() {
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Privacy</span>
        <h2>AntiScreenshare</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="refresh-anti">Refresh</button>
      </div>
    </section>
    ${antiScreensharePanel()}
  `;
}

function antiScreensharePanel() {
  const status = state.antiStatus || {};
  const enabled = Boolean(status.enabled ?? state.antiScreenshare);
  const source = status.bridgeOnline ? "Live client" : status.source || "Launcher";
  const message = status.message || "Launch Gamble Client once before editing live modules.";
  return `
    <section class="privacy-panel ${enabled ? "on" : ""}">
      <div class="privacy-copy">
        <span class="eyebrow">Mode</span>
        <strong>AntiScreenshare ${enabled ? "on" : "off"}</strong>
        <small>${escapeHtml(source)} · ${escapeHtml(message)}</small>
      </div>
      <div class="privacy-actions">
        <button class="toggle-button ${enabled ? "on" : ""}" type="button" data-action="toggle-anti">
          <b></b>
          ${enabled ? "Turn off" : "Turn on"}
        </button>
        <button class="ghost" type="button" data-action="anti-clean">Clean View</button>
        <button class="ghost" type="button" data-action="anti-obs">OBS View</button>
      </div>
    </section>
  `;
}

function updatePopup() {
  if (state.popup) {
    return `
      <section class="modal-scrim">
        <article class="update-modal">
          <span class="eyebrow">${escapeHtml(state.popup.kind || "Launcher")}</span>
          <h2>${escapeHtml(state.popup.title || "Launcher notice")}</h2>
          <p>${escapeHtml(state.popup.message || "")}</p>
          <div class="top-actions">
            <button class="primary-small" type="button" data-action="dismiss-popup">OK</button>
          </div>
        </article>
      </section>
    `;
  }
  if (launcherNeedsUpdate() && state.dismissedLauncherVersion !== latestLauncherVersion()) {
    return `
      <section class="modal-scrim">
        <article class="update-modal">
          <span class="eyebrow">Launcher update</span>
          <h2>Update required</h2>
          <p>Installed ${escapeHtml(state.info?.version || "unknown")}. Latest is ${escapeHtml(latestLauncherVersion() || "unknown")}.</p>
          <div class="top-actions">
            <button class="primary-small" type="button" data-action="download-launcher" ${state.busy ? "disabled" : ""}>Update Launcher</button>
            <button class="ghost" type="button" data-action="dismiss-launcher-popup">Later</button>
          </div>
        </article>
      </section>
    `;
  }
  const clientKey = clientStatusKey();
  if (clientNeedsUpdate() && state.dismissedClientVersion !== clientKey) {
    return `
      <section class="modal-scrim">
        <article class="update-modal">
          <span class="eyebrow">Client update</span>
          <h2>Client update available</h2>
          <p>${escapeHtml(state.clientStatus?.message || "Install the latest managed client build.")}</p>
          <div class="top-actions">
            <button class="primary-small" type="button" data-action="install" ${state.busy ? "disabled" : ""}>Update Client</button>
            <button class="ghost" type="button" data-action="dismiss-client-popup">Later</button>
          </div>
        </article>
      </section>
    `;
  }
  return "";
}

function accountRow(label, title, meta, badge, imageStyle = "", initials = "") {
  return `
    <div class="account-row">
      <div class="mini-avatar" style="${escapeAttr(imageStyle)}">${avatarText(imageStyle, initials || title)}</div>
      <div>
        <span>${escapeHtml(label)}</span>
        <strong>${escapeHtml(title)}</strong>
        <small>${escapeHtml(meta)}</small>
      </div>
      <b>${escapeHtml(badge)}</b>
    </div>
  `;
}

function avatarStyle(account = state.microsoft) {
  const uuid = String(account?.uuid || "").replaceAll("-", "").trim();
  if (/^[a-f0-9]{32}$/i.test(uuid)) return remoteAvatarStyle(`https://mc-heads.net/avatar/${uuid}/128`);
  const name = String(account?.name || "").trim();
  if (name) return remoteAvatarStyle(`https://mc-heads.net/avatar/${encodeURIComponent(name)}/128`);
  return "";
}

function launcherAvatarStyle() {
  const avatar = state.account?.avatarUrl || state.account?.avatar || state.account?.discordAvatar || "";
  return remoteAvatarStyle(avatar);
}

function friendAvatarStyle(friend = {}) {
  const uuid = String(friend.minecraftUuid || friend.minecraftUUID || friend.uuid || "").replaceAll("-", "").trim();
  if (/^[a-f0-9]{32}$/i.test(uuid)) return remoteAvatarStyle(`https://mc-heads.net/avatar/${uuid}/128`);
  const minecraftName = String(friend.minecraftName || friend.mcName || "").trim();
  if (minecraftName) return remoteAvatarStyle(`https://mc-heads.net/avatar/${encodeURIComponent(minecraftName)}/128`);
  const username = String(friend.username || "").trim();
  if (/^[a-z0-9_]{3,16}$/i.test(username)) return remoteAvatarStyle(`https://mc-heads.net/avatar/${encodeURIComponent(username)}/128`);
  return remoteAvatarStyle(friend.avatarUrl || friend.avatar || friend.discordAvatar || "");
}

function remoteAvatarStyle(url) {
  const clean = String(url || "").trim();
  if (!/^https?:\/\//i.test(clean)) return "";
  return `background-image:linear-gradient(135deg, rgba(255, 255, 255, 0.08), rgba(90, 170, 255, 0.08)), url("${cssUrl(clean)}");`;
}

function cssUrl(value) {
  return String(value).replaceAll("\\", "\\\\").replaceAll('"', '\\"').replaceAll("\n", "");
}

function avatarText(imageStyle, source) {
  return imageStyle ? "" : escapeHtml(avatarInitials(source));
}

function avatarInitials(source = state.microsoft?.name || accountTitle() || "GC") {
  return String(source).split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase() || "GC";
}

function spotifyTitle() {
  if (!state.token) return "Not connected";
  if (!state.spotify) return "Checking";
  if (!state.spotify.configured) return "Not available";
  if (!state.spotify.connected) return "Not connected";
  return state.spotify.displayName ? `Connected: ${state.spotify.displayName}` : "Connected";
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
  const browser = Boolean(state.microsoftSignIn?.browser);
  return `
    <section class="signin-panel">
      <div>
        <span class="eyebrow">Microsoft sign-in</span>
        <h2>${browser ? "Waiting for Microsoft" : "Open Microsoft sign-in"}</h2>
        <p>${escapeHtml(state.microsoftError || (browser
          ? "Finish the browser sign-in. The launcher is listening for the local callback."
          : "Finish sign-in in your browser. This window will update automatically."))}</p>
      </div>
      <div class="top-actions">
        ${browser ? "" : `<button class="ghost" type="button" data-action="open-microsoft-link">Open Link</button>`}
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
    <section class="file-list" data-scroll-key="${kind}-file-list">
      ${files.length ? files.map((file) => fileRow(kind, file)).join("") : `<p class="empty">${isPacks ? "No resource packs yet." : "No mod jars yet."}</p>`}
    </section>
  `;
}

function fileSection(kind, profile, files) {
  const isPacks = kind === "packs";
  const disabled = kind === "mods" && !profile.fabric;
  return `
    <section class="profile-file-section">
      <div class="section-head">
        <div>
          <h3>${isPacks ? "Resource Packs" : "Mods"}</h3>
        </div>
        <div class="top-actions">
          ${isPacks ? `<button class="ghost" type="button" data-action="add-packs">Add</button>` : ""}
          <button class="ghost" type="button" data-action="open-${isPacks ? "packs" : "mods"}">Open Folder</button>
          <button class="ghost" type="button" data-action="reload-files">Refresh</button>
        </div>
      </div>
      ${disabled ? `<p class="empty">Vanilla has no mods folder. Switch to a Fabric profile to manage jar files.</p>` : ""}
      <section class="file-list" data-scroll-key="${kind}-file-list">
        ${files.length ? files.map((file) => fileRow(kind, file)).join("") : `<p class="empty">${isPacks ? "No resource packs yet." : "No mod jars yet."}</p>`}
      </section>
    </section>
  `;
}

function fileRow(kind, file) {
  return `
    <article class="file-row">
      <div>
        <strong>${escapeHtml(file.name)}</strong>
        <span>${escapeHtml(file.enabled ? "Enabled" : "Disabled")} · ${formatBytes(file.size)}${file.locked ? " · Required" : ""}</span>
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
    <section class="diagnostics-list" data-scroll-key="diagnostics-list">
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

function diagnosticsPanel() {
  return `
    <section class="screen-band">
      <div>
        <span class="eyebrow">Local checks</span>
        <h2>Diagnostics</h2>
      </div>
      <div class="top-actions">
        <button class="ghost" type="button" data-action="run-diagnostics">Run</button>
      </div>
    </section>
    <section class="diagnostics-list" data-scroll-key="diagnostics-list">
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
      <pre data-scroll-key="launcher-log">${escapeHtml(state.log.slice(-80).join("\n"))}</pre>
    </section>
  `;
}

function viewTitle() {
  if (state.view === "accounts") return "Accounts";
  if (state.view === "social") return "Social";
  if (state.view === "updates") return "Update Center";
  if (state.view === "profiles") return "Profiles";
  if (state.view === "settings") return "Launcher Settings";
  return "Launch Gamble Client";
}

function profileLabel() {
  return currentProfile().label;
}

function playCopy(profile, signedIn) {
  if (!signedIn) return "Sign in to check access, refresh Ad Tier, and install the managed client jar.";
  if (!profile.client) return `${profile.label} uses the same managed folders without installing the Gamble Client jar.`;
  return "Install and verify the managed client, keep mods/resource packs organized, and prepare the native launch path.";
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
  if (state.microsoftSignIn) return "Sign-in pending";
  return "Microsoft required";
}

function adTierOnly() {
  if (!state.account) return false;
  return preferredBuildForAccount(state.account) === "ad_tier";
}

function buildForAccount() {
  const preferred = preferredBuildForAccount();
  if (!canUseBuild(state.selectedBuild) || buildRank(state.selectedBuild) < buildRank(preferred)) state.selectedBuild = preferred;
  return builds.find((item) => item.id === state.selectedBuild) || builds[0];
}

function preferredBuildForAccount(account = state.account) {
  if (!account) return "release";
  if (account.ownerAccess || hasPlanOrStatus(account, ["owner"])) return "release";
  if (account.mediaAccess || account.testerAccess || hasPlanOrStatus(account, ["media", "tester"])) return "media";
  if (account.betaAccess || hasPlanOrStatus(account, ["beta_plus", "lifetime_beta"])) return "beta_plus";
  if (hasPlanOrStatus(account, ["weekly", "monthly", "yearly", "lifetime", "owned"])) return "release";
  return "ad_tier";
}

function canUseBuild(buildId, account = state.account) {
  if (!account) return buildId === "release";
  if (account.ownerAccess || hasPlanOrStatus(account, ["owner"])) return true;
  if (buildId === "ad_tier") return true;
  if (buildId === "release") return preferredBuildForAccount(account) !== "ad_tier";
  if (buildId === "beta_plus") return ["beta_plus", "media"].includes(preferredBuildForAccount(account));
  if (buildId === "media") return preferredBuildForAccount(account) === "media";
  return false;
}

function buildRank(buildId) {
  return { ad_tier: 0, release: 1, beta_plus: 2, media: 3 }[buildId] ?? -1;
}

function hasPlanOrStatus(account, values) {
  const plan = String(account?.selectedPlan || "").toLowerCase();
  const status = String(account?.accessStatus || "").toLowerCase();
  return values.includes(plan) || values.includes(status);
}

function sponsorTitle() {
  if (!state.account) return "Sign in";
  if (!state.ads) return "Checking";
  if (!state.ads.required) return "Ads off";
  if (state.ads.remainingSeconds > 0) return `${formatDuration(state.ads.remainingSeconds)} left`;
  return state.ads.canWatch ? "Ready" : "Capped";
}

function clientStatusLabel() {
  if (!currentProfile().client) return "No client jar";
  if (!state.clientStatus && !state.manifest) return "Not checked";
  const status = state.clientStatus || state.manifest;
  if (status.updateAvailable) return "Update available";
  return status.buildVersion || status.fileName || "Current";
}

function clientNeedsUpdate() {
  return Boolean(state.clientStatus?.updateAvailable);
}

function clientStatusKey() {
  const status = state.clientStatus || {};
  return `${state.selectedBuild}:${status.fileName || status.buildVersion || ""}`;
}

function latestLauncherVersion() {
  return String(state.version?.version || state.version?.minVersion || "").trim();
}

function launcherNeedsUpdate() {
  const current = state.info?.version || "0.0.0";
  const latest = latestLauncherVersion();
  const minimum = String(state.version?.minVersion || "").trim();
  return (latest && compareVersions(current, latest) < 0) || (minimum && compareVersions(current, minimum) < 0);
}

function compareVersions(left, right) {
  const a = String(left || "").match(/\d+/g) || [0];
  const b = String(right || "").match(/\d+/g) || [0];
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) {
    const diff = Number(a[index] || 0) - Number(b[index] || 0);
    if (diff !== 0) return diff < 0 ? -1 : 1;
  }
  return 0;
}

function log(message) {
  const time = new Date().toLocaleTimeString();
  state.log.push(`[${time}] ${message}`);
  state.status = message;
}

function showPopup(title, message, kind = "notice") {
  state.popup = { title, message, kind };
  render();
}

function knownLaunchMessage(error) {
  const text = String(error?.message || error || "Minecraft could not launch.");
  const lower = text.toLowerCase();
  if (lower.includes("update") || lower.includes("outdated")) return "Update the client or launcher, then launch again.";
  if (lower.includes("microsoft") || lower.includes("account") || lower.includes("auth")) return "Link or refresh your Microsoft account, then launch again.";
  if (lower.includes("java")) return "Java failed during launch. Run diagnostics from Settings for the exact local check.";
  return text;
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
  await loadMicrosoftAccounts();
  await Promise.allSettled([refreshVersion(), refreshFiles(), restoreSession()]);
  await invoke("ensure_profile", { profile: state.selectedProfile }).catch(() => {});
  await refreshAntiScreenshareStatus();
  await refreshManifest();
  await refreshSpotifyStatus();
  await refreshSocial();
  await refreshMinecraftStatus({ render: false });
  setInterval(() => refreshMinecraftStatus({ render: true }), 3500);
  render();
}

async function loadMicrosoftAccounts() {
  const result = await invoke("list_microsoft_accounts").catch(() => ({ accounts: [], selectedUuid: "" }));
  state.microsoftAccounts = result?.accounts || [];
  state.microsoft = await invoke("read_microsoft_account").catch(() => null);
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
    log(`Stored sign-in expired: ${error.message || error}`);
  }
}

async function refreshVersion() {
  try {
    state.version = await api("/api/launcher/version");
    state.lastVersionCheckAt = Date.now();
    log(`Launcher latest: ${latestLauncherVersion() || "unknown"}`);
  } catch (error) {
    log(`Version check failed: ${error.message || error}`);
  }
}

async function refreshAccount() {
  if (!state.token) return;
  const body = await api("/api/launcher/account");
  applyAccount(body);
  await refreshSpotifyStatus();
  await refreshSocial();
}

function applyAccount(body) {
  state.account = body.user || null;
  state.ads = body.ads || body.adReward || null;
  const preferred = preferredBuildForAccount(state.account);
  if (!canUseBuild(state.selectedBuild, state.account) || buildRank(state.selectedBuild) < buildRank(preferred)) {
    state.selectedBuild = preferred;
  }
  state.manifest = null;
  state.clientStatus = null;
  state.spotify = null;
}

function createProfile() {
  const label = String(state.newProfileName || "").trim();
  if (!label) {
    showPopup("Profile name needed", "Enter a profile name before creating it.", "profile");
    return;
  }

  const id = uniqueProfileId(label);
  const profile = { id, label, fabric: true, client: true, custom: true };
  state.customProfiles = [...(state.customProfiles || []), profile];
  state.selectedProfile = id;
  state.newProfileName = "";
  saveCustomProfiles();
  log(`Created profile: ${label}`);
}

function uniqueProfileId(label) {
  const base = `custom-${label.toLowerCase().replace(/[^a-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "") || "profile"}`;
  const used = new Set(allProfiles().map((profile) => profile.id));
  if (!used.has(base)) return base;
  for (let index = 2; index < 1000; index += 1) {
    const candidate = `${base}-${index}`;
    if (!used.has(candidate)) return candidate;
  }
  return `${base}-${Date.now()}`;
}

function updateSelectedProfileLabel(label) {
  const value = String(label || "").trim();
  if (!value) return;
  state.customProfiles = (state.customProfiles || []).map((profile) => (
    profile.id === state.selectedProfile ? { ...profile, label: value } : profile
  ));
  saveCustomProfiles();
}

async function refreshMinecraftStatus(options = {}) {
  try {
    const status = await invoke("minecraft_status");
    const wasRunning = state.minecraftRunning;
    const oldPid = state.minecraftPid;
    state.minecraftRunning = Boolean(status?.running);
    state.minecraftPid = status?.pid || null;
    if (wasRunning && !state.minecraftRunning && options.logExit !== false) {
      log("Minecraft is no longer running.");
    }
    const changed = wasRunning !== state.minecraftRunning || oldPid !== state.minecraftPid;
    if (options.render !== false && changed) render();
  } catch (error) {
    if (options.logError) log(`Process status failed: ${error.message || error}`);
  }
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
    log(`Sign-in failed: ${error.message || error}`);
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
      await refreshSpotifyStatus();
      await refreshSocial();
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
  const profile = currentProfile();
  if (!state.token || !profile?.client) {
    state.clientStatus = null;
    state.manifest = null;
    return;
  }
  const build = buildForAccount();
  state.clientStatus = await invoke("client_install_status", {
    profile: state.selectedProfile,
    build: build.id,
    token: state.token
  });
  state.manifest = state.clientStatus;
  state.lastManifestCheckAt = Date.now();
  log(state.clientStatus.message || `Client checked: ${clientStatusLabel()}`);
}

async function refreshUpdatesIfStale(force = false) {
  const now = Date.now();
  const checks = [];
  if (force || now - state.lastVersionCheckAt > UPDATE_CHECK_TTL_MS) checks.push(refreshVersion());
  if (force || now - state.lastManifestCheckAt > UPDATE_CHECK_TTL_MS) checks.push(refreshManifest());
  if (checks.length) await Promise.allSettled(checks);
}

async function refreshSpotifyStatus() {
  if (!state.token) {
    state.spotify = null;
    return;
  }
  try {
    state.spotify = await api("/api/spotify/status");
  } catch (error) {
    state.spotify = { configured: false, connected: false, message: String(error.message || error) };
  }
}

async function refreshSocial() {
  if (!state.token) {
    state.social = null;
    state.lastSocialCheckAt = Date.now();
    return;
  }
  try {
    state.social = await api("/api/friends");
    state.lastSocialCheckAt = Date.now();
  } catch (error) {
    state.social = { friends: [], incomingRequests: [], outgoingRequests: [], settings: {}, message: String(error.message || error) };
    state.lastSocialCheckAt = Date.now();
  }
}

async function refreshSocialIfStale(force = false) {
  if (force || Date.now() - state.lastSocialCheckAt > SOCIAL_CHECK_TTL_MS) {
    await refreshSocial();
  }
}

async function sendFriendRequest() {
  const username = String(state.friendUsername || "").trim();
  if (!username) {
    showPopup("Friend username needed", "Enter a Gamble Client username first.", "friends");
    return;
  }
  setBusy(true, "Sending friend request");
  try {
    await api("/api/friends/request", { method: "POST", body: JSON.stringify({ username }) });
    state.friendUsername = "";
    await refreshSocial();
    log("Friend request sent.");
  } catch (error) {
    showPopup("Friend request failed", String(error.message || error), "friends");
    log(`Friend request failed: ${error.message || error}`);
  } finally {
    setBusy(false);
  }
}

async function respondFriendRequest(requestId, action) {
  setBusy(true, "Updating friend request");
  try {
    await api("/api/friends/respond", { method: "POST", body: JSON.stringify({ requestId, action }) });
    await refreshSocial();
    log(`Friend request ${action === "accept" ? "accepted" : "declined"}.`);
  } catch (error) {
    showPopup("Friend request failed", String(error.message || error), "friends");
    log(`Friend request update failed: ${error.message || error}`);
  } finally {
    setBusy(false);
  }
}

async function removeFriend(username) {
  setBusy(true, "Removing friend");
  try {
    await api("/api/friends/remove", { method: "POST", body: JSON.stringify({ username }) });
    await refreshSocial();
    log("Friend removed.");
  } catch (error) {
    showPopup("Friend remove failed", String(error.message || error), "friends");
    log(`Friend remove failed: ${error.message || error}`);
  } finally {
    setBusy(false);
  }
}

async function updatePrivacySetting(field, value) {
  const body = { [field]: Boolean(value) };
  const previousSettings = { ...(state.social?.settings || {}) };
  state.social = {
    ...(state.social || {}),
    settings: {
      ...previousSettings,
      [field]: Boolean(value)
    }
  };
  render();
  try {
    const result = await api("/api/friends/settings", { method: "POST", body: JSON.stringify(body) });
    state.social = { ...(state.social || {}), settings: result.settings || state.social.settings || {} };
    log("Privacy settings updated.");
  } catch (error) {
    state.social = { ...(state.social || {}), settings: previousSettings };
    showPopup("Privacy update failed", String(error.message || error), "friends");
    log(`Privacy update failed: ${error.message || error}`);
  }
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
    state.clientStatus = { ...result, installed: true, updateAvailable: false };
    state.dismissedClientVersion = clientStatusKey();
    localStorage.setItem(CLIENT_DISMISS_KEY, state.dismissedClientVersion);
    log(result.message);
    await refreshFiles();
  } catch (error) {
    log(`Install failed: ${error.message || error}`);
  } finally {
    setBusy(false);
  }
}

async function downloadLauncherUpdate() {
  setBusy(true, "Downloading launcher update");
  try {
    const result = await invoke("download_launcher_update");
    state.dismissedLauncherVersion = latestLauncherVersion();
    localStorage.setItem(LAUNCHER_DISMISS_KEY, state.dismissedLauncherVersion);
    log(`${result.message} ${result.path ? `Saved: ${result.path}` : ""}`.trim());
  } catch (error) {
    log(`Launcher update failed: ${error.message || error}`);
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
    log(`Sponsor break failed: ${error.message || error}`);
  } finally {
    setBusy(false);
  }
}

async function startMicrosoftSignIn() {
  setBusy(true, "Starting Microsoft sign-in");
  try {
    state.microsoftSignIn = { browser: true };
    state.microsoftError = "";
    state.microsoftPollCancelled = false;
    log("Opening Microsoft sign-in.");
    render();
    const result = await invoke("microsoft_browser_sign_in", { forceAccountPicker: true });
    if (result?.status !== "ready" || !result.account) throw new Error("Microsoft did not return a linked Minecraft account.");
    state.microsoft = result.account;
    state.username = result.account.name || state.username;
    state.microsoftSignIn = null;
    state.microsoftError = "";
    await loadMicrosoftAccounts();
    log(`Microsoft account linked: ${state.microsoft.name}`);
    render();
  } catch (error) {
    state.microsoftError = String(error?.message || error);
    log(`Microsoft sign-in failed: ${state.microsoftError}`);
    showPopup("Microsoft sign-in failed", microsoftAuthMessage(state.microsoftError), "account");
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
      await loadMicrosoftAccounts();
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

function microsoftVerificationUrl(value) {
  return String(
    value?.verificationUriComplete
      || value?.verification_uri_complete
      || value?.verificationUri
      || value?.verification_uri
      || ""
  ).trim();
}

function microsoftAuthMessage(message) {
  const text = String(message || "Microsoft sign-in failed.");
  const lower = text.toLowerCase();
  if (lower.includes("first party application") || lower.includes("pre-authorization")) {
    return "Microsoft rejected the old device-code sign-in page. This launcher now uses browser sign-in with a local callback; update the launcher and try Add Microsoft again.";
  }
  return text;
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

async function refreshAntiScreenshareStatus() {
  try {
    const status = await invoke("anti_screenshare_status", { profile: state.selectedProfile });
    state.antiStatus = status;
    if (status?.available || status?.bridgeOnline || status?.enabled) {
      state.antiScreenshare = Boolean(status.enabled);
    } else {
      state.antiStatus.enabled = state.antiScreenshare;
    }
    localStorage.setItem("gamble.launcher.antiScreenshare", String(state.antiScreenshare));
  } catch (error) {
    state.antiStatus = {
      enabled: state.antiScreenshare,
      available: false,
      bridgeOnline: false,
      source: "Launcher",
      message: `AntiScreenshare status failed: ${error.message || error}`,
      modulesPath: ""
    };
  }
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
    if (view === "social") {
      render();
      refreshSocialIfStale().then(render).catch((error) => log(`Social refresh failed: ${error.message || error}`));
      return;
    }
    if (view === "profiles" || view === "play") await refreshFiles();
    if (view === "accounts") await loadMicrosoftAccounts();
    if (view === "updates") await refreshUpdatesIfStale();
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

  if (action === "check-updates") {
    setBusy(true, "Checking updates");
    await refreshUpdatesIfStale(true);
    setBusy(false, "Ready");
  } else if (action === "refresh") {
    setBusy(true, "Refreshing");
    await Promise.allSettled([refreshVersion(), refreshAccount(), refreshManifest(), refreshFiles(), refreshAntiScreenshareStatus(), loadMicrosoftAccounts(), refreshSpotifyStatus(), refreshSocial()]);
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
    state.spotify = null;
    state.social = null;
    state.clientStatus = null;
    state.manifest = null;
    localStorage.removeItem(TOKEN_KEY);
    await invoke("delete_launcher_token").catch(() => {});
    log("Signed out.");
    render();
  } else if (action === "download-launcher") {
    await downloadLauncherUpdate();
  } else if (action === "dismiss-launcher-popup") {
    state.dismissedLauncherVersion = latestLauncherVersion();
    localStorage.setItem(LAUNCHER_DISMISS_KEY, state.dismissedLauncherVersion);
    render();
  } else if (action === "dismiss-client-popup") {
    state.dismissedClientVersion = clientStatusKey();
    localStorage.setItem(CLIENT_DISMISS_KEY, state.dismissedClientVersion);
    render();
  } else if (action === "dismiss-popup") {
    state.popup = null;
    render();
  } else if (action === "install") {
    await installSelected();
  } else if (action === "launch") {
    await refreshMinecraftStatus({ render: false, logExit: false });
    const selectedProfile = currentProfile();
    const selectedAccount = profileAccount(selectedProfile);
    if (!state.minecraftRunning && clientNeedsUpdate()) {
      showPopup("Client update needed", state.clientStatus?.message || "Install the latest managed client build before launching.", "client");
      return;
    }
    if (!state.minecraftRunning && !selectedAccount) {
      showPopup("Microsoft account needed", "Link a Microsoft account before launching this profile.", "account");
      await startMicrosoftSignIn();
      return;
    }
    setBusy(true, "Preparing Minecraft");
    try {
      const selectedBuild = buildForAccount();
      if (selectedAccount?.uuid && state.microsoft?.uuid !== selectedAccount.uuid) {
        state.microsoft = await invoke("select_microsoft_account", { uuid: selectedAccount.uuid });
        await loadMicrosoftAccounts();
      }
      const message = await invoke("launch_game", {
        input: {
          profile: state.selectedProfile,
          build: selectedBuild.id,
          token: state.token,
          username: state.username,
          memory: Number(state.memory) || 4,
          javaArgs: state.javaArgs,
          antiScreenshare: state.antiScreenshare
        }
      });
      if (String(message).toLowerCase().includes("stop signal")) {
        state.minecraftRunning = false;
        state.minecraftPid = null;
      } else if (String(message).toLowerCase().includes("process started")) {
        state.minecraftRunning = true;
        const match = String(message).match(/pid\s+(\d+)/i);
        state.minecraftPid = match ? Number(match[1]) : state.minecraftPid;
      }
      log(message);
      await refreshMinecraftStatus({ render: false, logExit: false });
    } catch (error) {
      log(`Launch failed: ${error.message || error}`);
      showPopup("Launch failed", knownLaunchMessage(error), "launch");
      await refreshMinecraftStatus({ render: false, logExit: false });
    } finally {
      setBusy(false);
    }
  } else if (action === "microsoft") {
    await startMicrosoftSignIn();
  } else if (action === "select-microsoft") {
    setBusy(true, "Switching Microsoft account");
    try {
      state.microsoft = await invoke("select_microsoft_account", { uuid: actionEl.dataset.uuid });
      await loadMicrosoftAccounts();
      log(`Using Microsoft account: ${state.microsoft?.name || "selected"}`);
    } catch (error) {
      log(`Account switch failed: ${error.message || error}`);
    } finally {
      setBusy(false);
    }
  } else if (action === "remove-microsoft") {
    setBusy(true, "Removing Microsoft account");
    try {
      await invoke("delete_microsoft_account_by_uuid", { uuid: actionEl.dataset.uuid });
      await loadMicrosoftAccounts();
      log("Microsoft account removed.");
    } catch (error) {
      log(`Account remove failed: ${error.message || error}`);
    } finally {
      setBusy(false);
    }
  } else if (action === "refresh-anti") {
    await refreshAntiScreenshareStatus();
    log("AntiScreenshare refreshed.");
    render();
  } else if (action === "toggle-anti") {
    setBusy(true, "Updating AntiScreenshare");
    try {
      const next = !Boolean(state.antiStatus?.enabled ?? state.antiScreenshare);
      const status = await invoke("set_anti_screenshare", { profile: state.selectedProfile, enabled: next });
      state.antiStatus = status;
      state.antiScreenshare = Boolean(status.enabled);
      localStorage.setItem("gamble.launcher.antiScreenshare", String(state.antiScreenshare));
      log(status.message || `AntiScreenshare ${state.antiScreenshare ? "enabled" : "disabled"}.`);
    } catch (error) {
      log(`AntiScreenshare update failed: ${error.message || error}`);
    } finally {
      setBusy(false);
    }
  } else if (action === "anti-clean") {
    setBusy(true, "Applying Clean View");
    try {
      const status = await invoke("apply_anti_screenshare_clean_view", { profile: state.selectedProfile });
      state.antiStatus = status;
      state.antiScreenshare = Boolean(status.enabled);
      localStorage.setItem("gamble.launcher.antiScreenshare", "true");
      log(status.message || "Clean View applied.");
    } catch (error) {
      log(`Clean View failed: ${error.message || error}`);
    } finally {
      setBusy(false);
    }
  } else if (action === "anti-obs") {
    const message = await invoke("open_anti_screenshare_obs").catch((error) => `OBS view failed: ${error.message || error}`);
    log(message);
    render();
  } else if (action === "open-microsoft-link") {
    const url = microsoftVerificationUrl(state.microsoftSignIn);
    if (url) await invoke("open_url", { url }).catch((error) => log(`Open failed: ${error}`));
  } else if (action === "cancel-microsoft") {
    state.microsoftPollCancelled = true;
    state.microsoftSignIn = null;
    state.microsoftError = "";
    state.busy = false;
    log("Microsoft sign-in cancelled.");
    render();
  } else if (action === "sponsor") {
    await startSponsor();
  } else if (action === "toggle-advanced") {
    state.showAdvancedSettings = !state.showAdvancedSettings;
    localStorage.setItem(ADVANCED_SETTINGS_KEY, String(state.showAdvancedSettings));
    render();
  } else if (action === "select-profile") {
    state.selectedProfile = actionEl.dataset.profile || "gamble-client";
    state.clientStatus = null;
    state.manifest = null;
    await refreshFiles();
    await refreshAntiScreenshareStatus();
    await refreshManifest().catch(() => {});
    render();
  } else if (action === "create-profile") {
    createProfile();
    await refreshFiles();
    await refreshAntiScreenshareStatus();
    await refreshManifest().catch(() => {});
    render();
  } else if (action === "send-friend") {
    await sendFriendRequest();
  } else if (action === "accept-friend") {
    await respondFriendRequest(actionEl.dataset.request, "accept");
  } else if (action === "decline-friend") {
    await respondFriendRequest(actionEl.dataset.request, "decline");
  } else if (action === "remove-friend") {
    await removeFriend(actionEl.dataset.username);
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
  const settingToggle = event.target.closest("[data-setting-toggle]")?.dataset.settingToggle;
  if (settingToggle === "animationsEnabled") {
    state.animationsEnabled = event.target.checked;
    localStorage.setItem(ANIMATIONS_KEY, state.animationsEnabled ? "true" : "false");
    render();
    return;
  }

  const privacyField = event.target.closest("[data-privacy-field]")?.dataset.privacyField;
  if (privacyField) {
    await updatePrivacySetting(privacyField, event.target.checked);
    render();
    return;
  }

  const field = event.target.closest("[data-field]")?.dataset.field;
  if (!field) return;
  if (field === "selectedProfileLabel") {
    updateSelectedProfileLabel(event.target.value);
    render();
    return;
  }
  if (field === "profileAccount") {
    const value = String(event.target.value || "").replaceAll("-", "").toLowerCase();
    if (value) state.profileAccountOverrides[state.selectedProfile] = value;
    else delete state.profileAccountOverrides[state.selectedProfile];
    saveProfileAccountOverrides();
    render();
    return;
  }
  state[field] = event.target.value;
  if (field === "username") localStorage.setItem("gamble.launcher.username", state.username);
  if (field === "javaArgs") localStorage.setItem("gamble.launcher.javaArgs", state.javaArgs);
  if (field === "memory") localStorage.setItem("gamble.launcher.memory", state.memory);
  if (field === "selectedProfile") {
    state.clientStatus = null;
    state.manifest = null;
    await refreshFiles();
    await refreshAntiScreenshareStatus();
    await refreshManifest().catch(() => {});
  }
  if (field === "selectedBuild") {
    state.clientStatus = null;
    state.manifest = null;
    await refreshManifest().catch(() => {});
  }
  render();
});

app.addEventListener("input", (event) => {
  const field = event.target.closest("[data-field]")?.dataset.field;
  if (!field) return;
  if (field === "selectedProfileLabel") {
    updateSelectedProfileLabel(event.target.value);
    render();
    return;
  }
  state[field] = event.target.value;
  if (field === "username") localStorage.setItem("gamble.launcher.username", state.username);
  if (field === "javaArgs") localStorage.setItem("gamble.launcher.javaArgs", state.javaArgs);
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
