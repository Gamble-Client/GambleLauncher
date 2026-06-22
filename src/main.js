import { invoke } from "@tauri-apps/api/core";
import "./styles.css";

const app = document.querySelector("#app");

const state = {
  account: "Not signed in",
  version: "Checking...",
  status: "Ready",
  build: "Ad Tier"
};

function render() {
  app.innerHTML = `
    <section class="shell">
      <aside class="rail">
        <div class="brand">
          <div class="brand-mark">GC</div>
          <div>
            <strong>Gamble Client</strong>
            <span>Launcher</span>
          </div>
        </div>
        <nav>
          <button class="nav-item active" type="button">Play</button>
          <button class="nav-item" type="button" data-open="https://dash.gamble-client.store/dashboard.html">Dashboard</button>
          <button class="nav-item" type="button" data-open="https://gamble-client.store/download">Downloads</button>
          <button class="nav-item" type="button" data-open="https://discord.gg/YPescfEt">Discord</button>
        </nav>
        <div class="rail-card">
          <span>Launcher</span>
          <strong>${escapeHtml(state.version)}</strong>
        </div>
      </aside>

      <section class="content">
        <header class="topbar">
          <div>
            <span class="eyebrow">Managed install</span>
            <h1>Launch Gamble Client</h1>
          </div>
          <button class="ghost" type="button" data-action="refresh">Refresh</button>
        </header>

        <section class="launch-panel">
          <div>
            <span class="eyebrow">Selected build</span>
            <h2>${escapeHtml(state.build)}</h2>
            <p>Install, update, and launch from one clean native shell. The Java launcher stays available while this Tauri UI takes over.</p>
          </div>
          <button class="launch-button" type="button" data-action="launch">Launch</button>
        </section>

        <section class="grid">
          <article>
            <span>Account</span>
            <strong>${escapeHtml(state.account)}</strong>
            <button type="button" data-open="https://dash.gamble-client.store/login.html">Sign in</button>
          </article>
          <article>
            <span>Status</span>
            <strong>${escapeHtml(state.status)}</strong>
            <button type="button" data-action="diagnostics">Diagnostics</button>
          </article>
          <article>
            <span>Resource packs</span>
            <strong>Ready</strong>
            <button type="button" data-action="packs">Manage</button>
          </article>
          <article>
            <span>Mods</span>
            <strong>Managed</strong>
            <button type="button" data-action="mods">Open</button>
          </article>
        </section>
      </section>
    </section>
  `;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function loadVersion() {
  try {
    state.version = await invoke("launcher_version");
  } catch (error) {
    state.version = "Local build";
  }
  render();
}

app.addEventListener("click", async (event) => {
  const open = event.target.closest("[data-open]");
  if (open) {
    await invoke("open_url", { url: open.dataset.open });
    return;
  }

  const action = event.target.closest("[data-action]")?.dataset.action;
  if (!action) return;

  if (action === "refresh") {
    state.status = "Refreshing account and manifests";
    render();
    await loadVersion();
    state.status = "Ready";
    render();
    return;
  }

  const messages = {
    launch: "Native launch flow is being ported from the Java launcher.",
    diagnostics: "Diagnostics will surface logs, Java, account, and install checks here.",
    packs: "Resource pack management will move into this Tauri screen.",
    mods: "Mod management will move into this Tauri screen."
  };
  state.status = messages[action] || "Ready";
  render();
});

render();
loadVersion();
