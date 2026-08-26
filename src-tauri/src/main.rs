#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use base64::{
    engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD},
    Engine as _,
};
use ring::signature::{UnparsedPublicKey, ED25519};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{
    collections::{HashMap, HashSet, VecDeque},
    env,
    fs::{self, File},
    io::{self, Cursor, Read, Write},
    net::{TcpListener, TcpStream},
    path::{Path, PathBuf},
    process::{Child, Command, ExitStatus, Stdio},
    sync::{
        atomic::{AtomicU32, Ordering},
        Arc, Mutex,
    },
    time::{Duration, SystemTime, UNIX_EPOCH},
};
#[cfg(target_os = "windows")]
use tauri::Manager;
use tauri::{AppHandle, Emitter};
use walkdir::WalkDir;
use zip::ZipArchive;

#[cfg(unix)]
use std::os::unix::process::ExitStatusExt;
#[cfg(target_os = "windows")]
use std::os::windows::process::CommandExt;

const VERSION: &str = env!("CARGO_PKG_VERSION");
const SITE_URL: &str = "https://gambleclient.org";
const LOADER_JAR_NAME: &str = "gamble-client-loader.jar";
const MINECRAFT_VERSION: &str = "1.21.11";
const FABRIC_LOADER_VERSION: &str = "0.19.3";
const FABRIC_VERSIONS_URL: &str = "https://meta.fabricmc.net/v2/versions/loader/1.21.11";
const VERSION_MANIFEST_URL: &str =
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
const ASSET_BASE_URL: &str = "https://resources.download.minecraft.net/";
const MICROSOFT_DEVICE_CODE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const MICROSOFT_AUTHORIZE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const MICROSOFT_TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const MICROSOFT_SCOPE: &str = "XboxLive.signin offline_access";
const MICROSOFT_CLIENT_ID: &str = "8eea0ae2-d0a9-4af1-88b9-f66bd96c94bd";
const MICROSOFT_REDIRECT_PORT: u16 = 39062;
const MICROSOFT_REDIRECT_URI: &str = "http://localhost:39062/";
const MICROSOFT_REAUTH_REQUIRED: &str =
    "Microsoft sign-in expired or was revoked. Reconnect Microsoft to continue.";
const HTTP_CONNECT_TIMEOUT_SECONDS: u64 = 15;
const HTTP_REQUEST_TIMEOUT_SECONDS: u64 = 300;
const HTTP_API_ATTEMPTS: usize = 2;
const HTTP_RETRY_DELAY_MILLIS: u64 = 350;
const HTTP_DOWNLOAD_ATTEMPTS: usize = 3;
const MAX_DOWNLOAD_BYTES: u64 = 512 * 1024 * 1024;
const MAX_MANAGED_CLIENT_BYTES: u64 = 64 * 1024 * 1024;
const MAX_LOADER_BYTES: u64 = 16 * 1024 * 1024;
const MAX_FABRIC_METADATA_BYTES: u64 = 1024 * 1024;
const MANAGED_CLIENT_MOD_ID: &str = "cg-mod";
const STANDALONE_LOADER_MOD_ID: &str = "gamble-client-standalone-loader";
const LOADER_PROVENANCE_ENTRY: &str = "META-INF/gamble-loader-provenance.json";
const LOADER_SIGNING_KEY_ID: &str = "617acff9930c4e68";
const LOADER_SIGNING_PUBLIC_KEY: &str =
    "MCowBQYDK2VwAyEAOFpbSkB+oSSa6fr4el70SgAiOLUAsBDmb2RWhktNhyg=";
const LOADER_MUTABLE_ENTRIES: &[&str] = &[
    "fabric.mod.json",
    "assets/cg-mod/icon.png",
    "gcclient-standalone-enrollment.json",
    LOADER_PROVENANCE_ENTRY,
];
const MAX_NATIVE_FILES: usize = 2048;
const MAX_NATIVE_EXPANDED_BYTES: u64 = 512 * 1024 * 1024;
const TEMURIN_21_WINDOWS_URL: &str =
    "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";
const TRUSTED_NETWORK_HOSTS: &[&str] = &[
    "gambleclient.org",
    "dash.gambleclient.org",
    "login.microsoftonline.com",
    "user.auth.xboxlive.com",
    "xsts.auth.xboxlive.com",
    "api.minecraftservices.com",
    "api.modrinth.com",
    "cdn.modrinth.com",
    "meta.fabricmc.net",
    "maven.fabricmc.net",
    "launchermeta.mojang.com",
    "piston-meta.mojang.com",
    "piston-data.mojang.com",
    "libraries.minecraft.net",
    "resources.download.minecraft.net",
    "repo1.maven.org",
    "repo.maven.apache.org",
    // Adoptium currently redirects managed Windows Java downloads through GitHub.
    "api.adoptium.net",
    "github.com",
    "release-assets.githubusercontent.com",
    "objects.githubusercontent.com",
    "github-releases.githubusercontent.com",
];
const MAX_DOWNLOAD_REDIRECTS: usize = 4;
#[cfg(target_os = "windows")]
const CREATE_NO_WINDOW: u32 = 0x08000000;
const MINECRAFT_TOKEN_REFRESH_BUFFER_MS: u64 = 5 * 60 * 1000;
const XBOX_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MINECRAFT_LOGIN_URL: &str = "https://api.minecraftservices.com/launcher/login";
const MINECRAFT_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const ANTISCREENSHARE_CORE_ON: &[&str] = &["antiscreenshare"];
const ANTISCREENSHARE_SCOREBOARD_ON: &[&str] = &["hide-scoreboard"];
const ANTISCREENSHARE_SCOREBOARD_OFF: &[&str] = &["fake-scoreboard"];
const ANTISCREENSHARE_HUD_OFF: &[&str] = &[
    "hud",
    "jamble-hud",
    "better-tab",
    "discord-presence",
    "big-spender-net-hud",
];
const ANTISCREENSHARE_VISUAL_OFF: &[&str] = &[
    "player-esp",
    "storage-esp",
    "block-esp",
    "item-esp",
    "trident-esp",
    "invis-esp",
    "chams",
    "nametags",
    "logout-spots",
    "trail",
    "tracers",
    "light-finder",
    "hole-tunnel-stair-esp",
    "tunnel-esp",
    "base-digger",
    "base-finder",
    "block-debug-finder",
    "block-update-finder",
];
static MINECRAFT_PROCESS: Mutex<Option<Child>> = Mutex::new(None);
static LAST_MINECRAFT_EXIT: Mutex<Option<MinecraftExit>> = Mutex::new(None);
static LAST_MINECRAFT_GRAPHICS_MODE: Mutex<String> = Mutex::new(String::new());
static LAUNCH_LOCK: Mutex<()> = Mutex::new(());
static MICROSOFT_BROWSER_SIGNIN_GENERATION: AtomicU32 = AtomicU32::new(0);

const DEFAULT_GRAPHICS_MODE: &str = "automatic";
const GRAPHICS_ENV_KEYS: &[&str] = &[
    "DRI_PRIME",
    "LIBGL_ALWAYS_SOFTWARE",
    "MESA_GLTHREAD",
    "AMD_DEBUG",
    "AMD_FORCE_SHADER_USE_ACO",
    "MESA_LOADER_DRIVER_OVERRIDE",
    "VK_ICD_FILENAMES",
    "MESA_VK_DEVICE_SELECT",
    "WEBKIT_DISABLE_DMABUF_RENDERER",
];
const GPU_FAULT_MARKERS: &[&str] = &[
    "gpuvm",
    "page fault",
    "ring gfx timeout",
    "gpu reset",
    "vram is lost",
    "device wedged",
    "context is lost",
    "gl_context_lost",
    "vk_error_device_lost",
    "the cs has cancelled",
    "device lost",
];

#[derive(Clone, Serialize)]
struct MinecraftExit {
    #[serde(rename = "exitCode")]
    exit_code: Option<i32>,
    crashed: bool,
    #[serde(rename = "gpuFault")]
    gpu_fault: bool,
    message: String,
    #[serde(rename = "logPath")]
    log_path: String,
    #[serde(rename = "graphicsMode")]
    graphics_mode: String,
}

#[derive(Serialize)]
struct LauncherInfo {
    version: &'static str,
    managed_root: String,
    data_folder: String,
    session_file: String,
    os: String,
}

#[derive(Deserialize)]
struct FabricModIdentity {
    id: String,
    #[serde(default)]
    custom: HashMap<String, serde_json::Value>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LoaderProvenance {
    schema_version: u32,
    loader_version: String,
    platform: String,
    canonical_file_name: String,
    canonical_sha256: String,
    core_sha256: String,
    canonical_size: u64,
    source_commit: String,
    client_delivery: String,
    signature_algorithm: String,
    signature_key_id: String,
    signature: String,
}

#[derive(Serialize)]
struct MinecraftStatus {
    running: bool,
    pid: Option<u32>,
    #[serde(rename = "exitCode")]
    exit_code: Option<i32>,
    crashed: bool,
    #[serde(rename = "gpuFault")]
    gpu_fault: bool,
    message: String,
    #[serde(rename = "logPath")]
    log_path: String,
    #[serde(rename = "graphicsMode")]
    graphics_mode: String,
}

#[derive(Clone, Serialize)]
struct LaunchProgressEvent {
    phase: String,
    message: String,
    current: u32,
    total: u32,
    percent: u8,
}

#[derive(Serialize)]
struct LocalFile {
    name: String,
    #[serde(rename = "displayName")]
    display_name: String,
    path: String,
    enabled: bool,
    locked: bool,
    size: u64,
}

#[derive(Serialize)]
struct FabricLoaderStatus {
    profile: String,
    installed: bool,
    version: String,
    #[serde(rename = "latestVersion")]
    latest_version: String,
    #[serde(rename = "updateAvailable")]
    update_available: bool,
    #[serde(skip_serializing_if = "String::is_empty")]
    message: String,
}

#[derive(Serialize)]
struct Diagnostics {
    checks: Vec<DiagnosticCheck>,
}

#[derive(Serialize)]
struct AntiScreenshareStatus {
    enabled: bool,
    available: bool,
    #[serde(rename = "bridgeOnline")]
    bridge_online: bool,
    source: String,
    message: String,
    #[serde(rename = "modulesPath")]
    modules_path: String,
}

#[derive(Serialize)]
struct MicrosoftAccountState {
    accounts: Vec<MicrosoftAccountView>,
    #[serde(rename = "selectedUuid")]
    selected_uuid: String,
}

#[derive(Serialize)]
struct DiagnosticCheck {
    label: String,
    ok: bool,
    detail: String,
}

#[derive(Serialize)]
struct InstallResult {
    #[serde(rename = "fileName")]
    file_name: String,
    build: String,
    #[serde(rename = "buildVersion")]
    build_version: String,
    path: String,
    size: u64,
    sha256: String,
    updated: bool,
    message: String,
}

#[derive(Serialize)]
struct ClientInstallStatus {
    #[serde(rename = "fileName")]
    file_name: String,
    build: String,
    #[serde(rename = "buildVersion")]
    build_version: String,
    path: String,
    size: u64,
    sha256: String,
    installed: bool,
    #[serde(rename = "updateAvailable")]
    update_available: bool,
    message: String,
}

#[derive(Deserialize)]
struct LauncherVersionResponse {
    #[serde(default)]
    version: String,
    #[serde(default, rename = "minVersion")]
    min_version: String,
    #[serde(default, rename = "fileName")]
    file_name: String,
    #[serde(default, rename = "downloadUrl")]
    download_url: String,
    #[serde(default)]
    downloads: LauncherDownloads,
}

#[derive(Default, Deserialize)]
struct LauncherDownloads {
    #[serde(default)]
    windows: Option<LauncherDownload>,
    #[serde(default, rename = "linuxRpm")]
    linux_rpm: Option<LauncherDownload>,
    #[serde(default, rename = "linuxDeb")]
    linux_deb: Option<LauncherDownload>,
    #[serde(default)]
    jar: Option<LauncherDownload>,
}

#[derive(Clone, Default, Deserialize)]
struct LauncherDownload {
    #[serde(default, rename = "fileName")]
    file_name: String,
    #[serde(default, rename = "downloadUrl")]
    download_url: String,
    #[serde(default)]
    sha256: String,
    #[serde(default)]
    size: u64,
}

#[derive(Serialize)]
struct LauncherUpdateResult {
    version: String,
    #[serde(rename = "fileName")]
    file_name: String,
    #[serde(rename = "downloadUrl")]
    download_url: String,
    path: String,
    message: String,
}

struct ModrinthRelease {
    file_name: String,
    url: String,
}

#[derive(Clone, Deserialize, Serialize)]
struct MicrosoftAccount {
    name: String,
    uuid: String,
    xuid: String,
    #[serde(rename = "refreshToken")]
    refresh_token: String,
    #[serde(default, rename = "minecraftAccessToken")]
    minecraft_access_token: String,
    #[serde(default, rename = "minecraftExpiresAt")]
    minecraft_expires_at: u64,
}

#[derive(Clone, Serialize)]
struct MicrosoftAccountView {
    name: String,
    uuid: String,
    xuid: String,
}

impl From<&MicrosoftAccount> for MicrosoftAccountView {
    fn from(account: &MicrosoftAccount) -> Self {
        Self {
            name: account.name.clone(),
            uuid: account.uuid.clone(),
            xuid: account.xuid.clone(),
        }
    }
}

#[derive(Serialize)]
struct MicrosoftDeviceStart {
    #[serde(rename = "deviceCode")]
    device_code: String,
    #[serde(rename = "userCode")]
    user_code: String,
    #[serde(rename = "verificationUri")]
    verification_uri: String,
    #[serde(rename = "verificationUriComplete")]
    verification_uri_complete: String,
    message: String,
    #[serde(rename = "intervalSeconds")]
    interval_seconds: u64,
    #[serde(rename = "expiresInSeconds")]
    expires_in_seconds: u64,
}

#[derive(Deserialize)]
struct StandaloneLoaderVersionResponse {
    #[serde(default)]
    version: String,
}

struct MicrosoftToken {
    access_token: String,
    refresh_token: String,
}

struct XboxToken {
    token: String,
    user_hash: String,
    xuid: String,
}

struct MinecraftToken {
    access_token: String,
    expires_in_seconds: u64,
}

struct MinecraftProfile {
    uuid: String,
    name: String,
    xuid: String,
    access_token: String,
    expires_at: u64,
}

#[derive(Deserialize)]
struct ApiCommandBody {
    method: String,
    path: String,
    #[serde(default)]
    token: String,
    #[serde(default)]
    body: serde_json::Value,
}

#[derive(Deserialize)]
struct ManifestResponse {
    #[serde(default)]
    build: String,
    #[serde(default, rename = "fileName")]
    file_name: String,
    #[serde(default)]
    sha256: String,
    #[serde(default)]
    size: u64,
    #[serde(default, rename = "buildVersion")]
    build_version: String,
}

#[derive(Clone, Deserialize)]
struct LaunchRequest {
    profile: String,
    build: String,
    token: String,
    username: String,
    #[serde(default, rename = "accountUuid")]
    account_uuid: String,
    memory: u8,
    #[serde(rename = "javaArgs")]
    java_args: String,
    #[serde(rename = "antiScreenshare")]
    anti_screenshare: bool,
    #[serde(default, rename = "clientDisplayName")]
    client_display_name: String,
    #[serde(default, rename = "graphicsMode")]
    graphics_mode: String,
    #[serde(default, rename = "gpuSelector")]
    gpu_selector: String,
}

#[derive(Default)]
struct VersionProfile {
    id: String,
    main_class: String,
    asset_index_id: String,
    asset_index_url: String,
    client_version_id: String,
    client_jar_url: String,
    libraries: Vec<Library>,
    jvm_arguments: Vec<String>,
    game_arguments: Vec<String>,
}

#[derive(Clone)]
struct Library {
    name: String,
    rules: Vec<serde_json::Value>,
    artifact_path: String,
    artifact_url: String,
    natives: serde_json::Map<String, serde_json::Value>,
    classifier_paths: serde_json::Map<String, serde_json::Value>,
    classifier_urls: serde_json::Map<String, serde_json::Value>,
}

#[derive(Clone)]
struct AssetDownload {
    hash: String,
    path: PathBuf,
}

#[tauri::command]
fn launcher_info() -> Result<LauncherInfo, String> {
    let root = managed_root();
    let data = launcher_data_folder();
    Ok(LauncherInfo {
        version: VERSION,
        managed_root: display_path(&root),
        data_folder: display_path(&data),
        session_file: display_path(&launcher_session_file()),
        os: env::consts::OS.to_string(),
    })
}

#[tauri::command]
fn launcher_api(input: ApiCommandBody) -> Result<serde_json::Value, String> {
    let method = input.method.trim().to_uppercase();
    let path = input.path.trim();
    if !path.starts_with("/api/launcher/")
        && path != "/api/spotify/status"
        && !path.starts_with("/api/friends")
    {
        return Err("Launcher API path is not allowed.".to_string());
    }

    let url = format!("{SITE_URL}{path}");
    if !matches!(method.as_str(), "GET" | "POST") {
        return Err("Launcher API method is not allowed.".to_string());
    }
    let token = input.token.trim().to_string();
    let body = input.body.clone();
    let response = send_first_party_request(&url, |client, target| {
        let mut request = match method.as_str() {
            "GET" => client.get(target),
            "POST" => client.post(target),
            _ => unreachable!("method was checked above"),
        };
        if !token.is_empty() {
            request = request.bearer_auth(&token);
        }
        if method == "POST" {
            request = request.json(&body);
        }
        request
    })?;
    let status = response.status();
    let text = response.text().map_err(error_text)?;
    let body = if text.trim().is_empty() {
        serde_json::Value::Object(Default::default())
    } else {
        serde_json::from_str::<serde_json::Value>(&text)
            .unwrap_or_else(|_| json!({ "message": text }))
    };
    if !status.is_success() {
        let message = body
            .get("message")
            .and_then(|value| value.as_str())
            .filter(|value| !value.trim().is_empty())
            .unwrap_or_else(|| status.canonical_reason().unwrap_or("Launcher API failed"));
        return Err(format!("{message} (HTTP {})", status.as_u16()));
    }
    Ok(body)
}

#[tauri::command]
fn read_launcher_token() -> Result<String, String> {
    read_trimmed(&launcher_session_file()).or_else(|_| Ok(String::new()))
}

#[tauri::command]
fn save_launcher_token(token: String) -> Result<(), String> {
    let token = token.trim();
    if token.is_empty() {
        return delete_launcher_token();
    }
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    write_private_file(&launcher_session_file(), format!("{token}\n").as_bytes())
}

#[tauri::command]
fn delete_launcher_token() -> Result<(), String> {
    match fs::remove_file(launcher_session_file()) {
        Ok(_) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error_text(error)),
    }
}

#[tauri::command]
fn read_microsoft_account() -> Result<Option<MicrosoftAccountView>, String> {
    Ok(selected_microsoft_account()?
        .as_ref()
        .map(MicrosoftAccountView::from))
}

#[tauri::command]
fn list_microsoft_accounts() -> Result<MicrosoftAccountState, String> {
    let accounts = read_microsoft_account_list()?;
    Ok(MicrosoftAccountState {
        selected_uuid: selected_microsoft_uuid().unwrap_or_else(|| {
            accounts
                .first()
                .map(|account| account.uuid.clone())
                .unwrap_or_default()
        }),
        accounts: accounts.iter().map(MicrosoftAccountView::from).collect(),
    })
}

#[tauri::command]
fn select_microsoft_account(uuid: String) -> Result<Option<MicrosoftAccountView>, String> {
    let uuid = uuid.trim().replace('-', "");
    if uuid.is_empty() {
        return Err("Choose a Microsoft account first.".to_string());
    }
    let account = read_microsoft_account_list()?
        .into_iter()
        .find(|account| account.uuid.eq_ignore_ascii_case(&uuid))
        .ok_or_else(|| "That Microsoft account is not saved in this launcher.".to_string())?;
    save_selected_microsoft_uuid(&account.uuid)?;
    save_legacy_microsoft_account(&account)?;
    Ok(Some(MicrosoftAccountView::from(&account)))
}

#[tauri::command]
fn delete_microsoft_account() -> Result<(), String> {
    let _ = fs::remove_file(microsoft_accounts_file());
    let _ = fs::remove_file(selected_microsoft_account_file());
    match fs::remove_file(microsoft_account_file()) {
        Ok(_) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error_text(error)),
    }
}

#[tauri::command]
fn delete_microsoft_account_by_uuid(uuid: String) -> Result<MicrosoftAccountState, String> {
    let uuid = uuid.trim().replace('-', "");
    let mut accounts = read_microsoft_account_list()?;
    accounts.retain(|account| !account.uuid.eq_ignore_ascii_case(&uuid));
    write_microsoft_account_list(&accounts)?;

    let selected = selected_microsoft_uuid().unwrap_or_default();
    let next_selected = if selected.eq_ignore_ascii_case(&uuid) {
        accounts
            .first()
            .map(|account| account.uuid.clone())
            .unwrap_or_default()
    } else {
        selected
    };
    if next_selected.trim().is_empty() {
        let _ = fs::remove_file(selected_microsoft_account_file());
        let _ = fs::remove_file(microsoft_account_file());
    } else {
        save_selected_microsoft_uuid(&next_selected)?;
        if let Some(account) = accounts
            .iter()
            .find(|account| account.uuid.eq_ignore_ascii_case(&next_selected))
        {
            save_legacy_microsoft_account(account)?;
        }
    }

    Ok(MicrosoftAccountState {
        accounts: accounts.iter().map(MicrosoftAccountView::from).collect(),
        selected_uuid: next_selected,
    })
}

#[tauri::command]
async fn microsoft_browser_sign_in(
    force_account_picker: bool,
) -> Result<serde_json::Value, String> {
    let generation = MICROSOFT_BROWSER_SIGNIN_GENERATION
        .fetch_add(1, Ordering::SeqCst)
        .wrapping_add(1);
    run_blocking(move || microsoft_browser_sign_in_blocking(force_account_picker, generation)).await
}

#[tauri::command]
fn cancel_microsoft_browser_sign_in() -> Result<(), String> {
    MICROSOFT_BROWSER_SIGNIN_GENERATION.fetch_add(1, Ordering::SeqCst);
    Ok(())
}

fn microsoft_browser_sign_in_blocking(
    force_account_picker: bool,
    generation: u32,
) -> Result<serde_json::Value, String> {
    let state = random_base64_url(24);
    let code_verifier = random_base64_url(48);
    let code_challenge = sha256_base64_url(&code_verifier);
    let listener = TcpListener::bind(("127.0.0.1", MICROSOFT_REDIRECT_PORT))
        .map_err(|error| format!("Could not start Microsoft callback listener on port {MICROSOFT_REDIRECT_PORT}: {error}"))?;
    listener.set_nonblocking(true).map_err(error_text)?;

    let auth_url = microsoft_authorize_url(&code_challenge, &state, force_account_picker);
    open_external(&auth_url)?;

    let result = loop {
        let mut stream = wait_for_microsoft_callback(&listener, generation)?;
        let callback = read_microsoft_callback(&mut stream)?;
        if callback.get("state").map(String::as_str) != Some(state.as_str()) {
            let _ = write_microsoft_callback_response(
                &mut stream,
                "Microsoft sign-in ignored",
                "This callback was not for the active sign-in. Return to the Microsoft tab.",
            );
            continue;
        }
        if let Some(error) = callback.get("error") {
            let message = callback
                .get("error_description")
                .map(String::as_str)
                .unwrap_or(error);
            let _ =
                write_microsoft_callback_response(&mut stream, "Microsoft sign-in failed", message);
            break Err(message.to_string());
        }
        if let Some(code) = callback.get("code").filter(|code| !code.is_empty()) {
            let _ = write_microsoft_callback_response(
                &mut stream,
                "Microsoft sign-in complete",
                "You can close this tab and return to the Gamble Client launcher.",
            );
            break Ok(code.clone());
        }
        let _ = write_microsoft_callback_response(
            &mut stream,
            "Microsoft sign-in ignored",
            "This callback did not contain a sign-in code. Return to the Microsoft tab.",
        );
    };
    drop(listener);

    let code = result?;
    if microsoft_browser_sign_in_cancelled(generation) {
        return Err("Microsoft sign-in cancelled.".to_string());
    }
    let token = exchange_microsoft_authorization_code(&code, &code_verifier)?;
    let profile = exchange_microsoft_for_minecraft(&token.access_token)?;
    let account = MicrosoftAccount {
        name: profile.name,
        uuid: profile.uuid,
        xuid: profile.xuid,
        refresh_token: token.refresh_token,
        minecraft_access_token: profile.access_token,
        minecraft_expires_at: profile.expires_at,
    };
    save_microsoft_account(&account)?;
    Ok(json!({ "status": "ready", "account": MicrosoftAccountView::from(&account) }))
}

#[tauri::command]
async fn microsoft_device_start(
    force_account_picker: bool,
) -> Result<MicrosoftDeviceStart, String> {
    run_blocking(move || microsoft_device_start_blocking(force_account_picker)).await
}

fn microsoft_device_start_blocking(
    _force_account_picker: bool,
) -> Result<MicrosoftDeviceStart, String> {
    let params = vec![
        ("client_id", MICROSOFT_CLIENT_ID.to_string()),
        ("scope", MICROSOFT_SCOPE.to_string()),
    ];
    let body = http_client()?
        .post(MICROSOFT_DEVICE_CODE_URL)
        .form(&params)
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json::<serde_json::Value>()
        .map_err(error_text)?;

    let error = json_string(&body, "error");
    if !error.trim().is_empty() {
        let description = json_string(&body, "error_description");
        return Err(if description.trim().is_empty() {
            format!("Microsoft sign-in failed: {error}")
        } else {
            description
        });
    }
    let device_code = json_string(&body, "device_code");
    let user_code = json_string(&body, "user_code");
    if device_code.trim().is_empty() || user_code.trim().is_empty() {
        return Err("Microsoft did not return a usable device sign-in code.".to_string());
    }

    let verification_uri = json_string(&body, "verification_uri");
    let mut verification_uri_complete = json_string(&body, "verification_uri_complete");
    if verification_uri_complete.trim().is_empty()
        && !verification_uri.trim().is_empty()
        && !user_code.trim().is_empty()
    {
        let separator = if verification_uri.contains('?') {
            "&"
        } else {
            "?"
        };
        verification_uri_complete = format!("{verification_uri}{separator}otc={user_code}");
    }

    Ok(MicrosoftDeviceStart {
        device_code,
        user_code,
        verification_uri,
        verification_uri_complete,
        message: json_string(&body, "message"),
        interval_seconds: json_u64(&body, "interval").max(2),
        expires_in_seconds: json_u64(&body, "expires_in").max(60),
    })
}

#[tauri::command]
async fn microsoft_device_poll(device_code: String) -> Result<serde_json::Value, String> {
    run_blocking(move || microsoft_device_poll_blocking(device_code)).await
}

fn microsoft_device_poll_blocking(device_code: String) -> Result<serde_json::Value, String> {
    let device_code = device_code.trim().to_string();
    if device_code.is_empty() {
        return Err(
            "Microsoft device code was missing before polling. Start sign-in again.".to_string(),
        );
    }
    let params = [
        (
            "grant_type",
            "urn:ietf:params:oauth:grant-type:device_code".to_string(),
        ),
        ("client_id", MICROSOFT_CLIENT_ID.to_string()),
        ("device_code", device_code),
    ];
    let response = http_client()?
        .post(MICROSOFT_TOKEN_URL)
        .form(&params)
        .send()
        .map_err(error_text)?;
    let status = response.status();
    let body = response.json::<serde_json::Value>().map_err(error_text)?;

    if status.as_u16() == 400 {
        let error = json_string(&body, "error");
        if error == "authorization_pending" {
            return Ok(json!({ "status": "pending" }));
        }
        if error == "slow_down" {
            return Ok(json!({ "status": "pending", "slowDown": true }));
        }
        if error == "authorization_declined" {
            return Err("Microsoft sign-in was declined.".to_string());
        }
        if error == "expired_token" {
            return Err("Microsoft sign-in code expired.".to_string());
        }
        let description = json_string(&body, "error_description");
        return Err(if description.trim().is_empty() {
            format!("Microsoft sign-in failed: {error}")
        } else {
            description
        });
    }
    if !status.is_success() {
        return Err(format!("Microsoft returned HTTP {}", status.as_u16()));
    }

    let token = parse_microsoft_token(&body)?;
    let profile = exchange_microsoft_for_minecraft(&token.access_token)?;
    let account = MicrosoftAccount {
        name: profile.name,
        uuid: profile.uuid,
        xuid: profile.xuid,
        refresh_token: token.refresh_token,
        minecraft_access_token: profile.access_token,
        minecraft_expires_at: profile.expires_at,
    };
    save_microsoft_account(&account)?;
    Ok(json!({ "status": "ready", "account": MicrosoftAccountView::from(&account) }))
}

#[tauri::command]
fn ensure_profile(profile: String) -> Result<String, String> {
    let profile = profile_id(&profile);
    let path = ensure_profile_folders(&profile)?;
    apply_minecraft_option_defaults(&profile)?;
    Ok(display_path(&path))
}

#[tauri::command]
fn profile_loader_status(profile: String) -> Result<FabricLoaderStatus, String> {
    let profile = profile_id(&profile);
    if profile_kind(&profile) == ProfileKind::Vanilla {
        return Ok(FabricLoaderStatus {
            profile,
            installed: false,
            version: String::new(),
            latest_version: String::new(),
            update_available: false,
            message: String::new(),
        });
    }

    let folder = ensure_profile_folders(&profile)?;
    let installed = detected_fabric_loader_version(&folder);
    let latest = latest_fabric_loader_version().unwrap_or_else(|_| {
        installed
            .clone()
            .unwrap_or_else(|| FABRIC_LOADER_VERSION.to_string())
    });
    let version = installed.clone().unwrap_or_default();
    Ok(FabricLoaderStatus {
        profile,
        installed: installed.is_some(),
        update_available: !version.is_empty()
            && compare_version_strings(&version, &latest) == std::cmp::Ordering::Less,
        version,
        latest_version: latest,
        message: String::new(),
    })
}

#[tauri::command]
fn update_fabric_loader(profile: String) -> Result<FabricLoaderStatus, String> {
    let profile = profile_id(&profile);
    if profile_kind(&profile) == ProfileKind::Vanilla {
        return Err("Vanilla profiles do not use Fabric Loader.".to_string());
    }
    let folder = ensure_profile_folders(&profile)?;
    let latest = latest_fabric_loader_version()?;
    let version_id = fabric_version_id(&latest);
    let path = folder
        .join("versions")
        .join(&version_id)
        .join(format!("{version_id}.json"));
    if !path.is_file() {
        download_file(&fabric_profile_url(&latest), &path)?;
    }
    Ok(FabricLoaderStatus {
        profile,
        installed: true,
        version: latest.clone(),
        latest_version: latest,
        update_available: false,
        message: format!("Fabric Loader updated to {version_id}."),
    })
}

#[tauri::command]
fn delete_profile(profile: String) -> Result<(), String> {
    let profile = profile_id(&profile);
    if profile == "gamble-client" || profile == "vanilla" || profile == "fabric" {
        return Err("Built-in profiles cannot be deleted.".to_string());
    }
    if minecraft_status()?.running {
        return Err("Close Minecraft before deleting a profile.".to_string());
    }

    let profiles_root = managed_root().join("profiles");
    let path = profiles_root.join(&profile);
    if path.exists() {
        let metadata = fs::symlink_metadata(&path).map_err(error_text)?;
        if metadata.file_type().is_symlink() || !metadata.is_dir() {
            return Err("The managed profile path is not a normal directory.".to_string());
        }
        fs::remove_dir_all(&path)
            .map_err(|error| format!("Could not delete {}: {error}", display_path(&path)))?;
    }
    Ok(())
}

#[tauri::command]
fn list_local_files(profile: String, kind: String) -> Result<Vec<LocalFile>, String> {
    let profile = profile_id(&profile);
    let folder = if kind == "resourcepacks" {
        resource_packs_folder(&profile)
    } else {
        mods_folder(&profile)
    };
    fs::create_dir_all(&folder).map_err(error_text)?;

    let mut files = Vec::new();
    for entry in fs::read_dir(&folder).map_err(error_text)? {
        let entry = entry.map_err(error_text)?;
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().to_string();
        let lower = name.to_lowercase();
        let metadata = entry.metadata().map_err(error_text)?;
        let include = if kind == "resourcepacks" {
            (metadata.is_file() && (lower.ends_with(".zip") || lower.ends_with(".zip.disabled")))
                || (metadata.is_dir() && lower != "server-resource-packs")
        } else {
            metadata.is_file() && (lower.ends_with(".jar") || lower.ends_with(".jar.disabled"))
        };
        if !include {
            continue;
        }
        let locked = kind != "resourcepacks" && is_required_mod_for_profile(&profile, &lower);
        let enabled = if kind == "resourcepacks" {
            !lower.ends_with(".disabled")
        } else {
            lower.ends_with(".jar")
        };
        let display_name = if kind == "resourcepacks" {
            name.clone()
        } else {
            fabric_mod_display_name(&path, &name)
        };
        files.push(LocalFile {
            name,
            display_name,
            path: display_path(&path),
            enabled,
            locked,
            size: metadata.len(),
        });
    }
    files.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    Ok(files)
}

fn fabric_mod_display_name(path: &Path, fallback: &str) -> String {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(_) => return fallback.to_string(),
    };
    let mut archive = match ZipArchive::new(file) {
        Ok(archive) => archive,
        Err(_) => return fallback.to_string(),
    };
    let mut metadata = match archive.by_name("fabric.mod.json") {
        Ok(metadata) => metadata,
        Err(_) => return fallback.to_string(),
    };
    if metadata.size() > MAX_FABRIC_METADATA_BYTES {
        return fallback.to_string();
    }
    let mut contents = Vec::new();
    if (&mut metadata)
        .take(MAX_FABRIC_METADATA_BYTES + 1)
        .read_to_end(&mut contents)
        .is_err()
    {
        return fallback.to_string();
    }
    let parsed = match serde_json::from_slice::<serde_json::Value>(&contents) {
        Ok(parsed) => parsed,
        Err(_) => return fallback.to_string(),
    };
    parsed
        .get("name")
        .and_then(|value| value.as_str())
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or(fallback)
        .to_string()
}

#[tauri::command]
fn toggle_local_file(profile: String, kind: String, path: String) -> Result<(), String> {
    let profile = profile_id(&profile);
    let path = PathBuf::from(path);
    let allowed_root = if kind == "resourcepacks" {
        resource_packs_folder(&profile)
    } else {
        mods_folder(&profile)
    };
    let canonical_root = fs::canonicalize(&allowed_root).map_err(error_text)?;
    let canonical_path = fs::canonicalize(&path).map_err(error_text)?;
    if !canonical_path.starts_with(&canonical_root) {
        return Err("The selected file is outside this profile's managed folder.".to_string());
    }
    let path = canonical_path;
    if !path.exists() {
        return Err("File does not exist anymore.".to_string());
    }
    if kind != "resourcepacks" {
        let lower = path
            .file_name()
            .and_then(|v| v.to_str())
            .unwrap_or("")
            .to_lowercase();
        if is_required_mod_for_profile(&profile, &lower) {
            return Err("This required Fabric mod is managed by the launcher.".to_string());
        }
    }

    let target = toggle_target(&path)?;
    fs::rename(&path, &target).map_err(error_text)?;
    if kind == "resourcepacks" {
        set_resource_pack_enabled(
            &profile,
            &target,
            !target.to_string_lossy().ends_with(".disabled"),
        )?;
    }
    Ok(())
}

#[tauri::command]
fn add_resource_packs(profile: String, paths: Vec<String>) -> Result<usize, String> {
    let profile = profile_id(&profile);
    let folder = resource_packs_folder(&profile);
    fs::create_dir_all(&folder).map_err(error_text)?;
    let mut copied = 0;
    for source in paths {
        let source = PathBuf::from(source);
        if !source.exists() {
            continue;
        }
        let name = source
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| "Resource pack path has no filename.".to_string())?;
        let lower = name.to_lowercase();
        if source.is_file() && !lower.ends_with(".zip") {
            continue;
        }
        if source.is_dir() && lower == "server-resource-packs" {
            continue;
        }
        let target = folder.join(name.trim_end_matches(".disabled"));
        if source.is_dir() {
            copy_dir_all(&source, &target).map_err(error_text)?;
        } else {
            fs::copy(&source, &target).map_err(error_text)?;
        }
        set_resource_pack_enabled(&profile, &target, true)?;
        copied += 1;
    }
    Ok(copied)
}

#[tauri::command]
fn add_mods(profile: String, paths: Vec<String>) -> Result<usize, String> {
    let profile = profile_id(&profile);
    if profile_kind(&profile) == ProfileKind::Vanilla {
        return Err("Vanilla profiles do not have a mods folder.".to_string());
    }
    let folder = mods_folder(&profile);
    fs::create_dir_all(&folder).map_err(error_text)?;
    let mut copied = 0;
    for source in paths {
        let source = PathBuf::from(source);
        if !source.is_file() {
            continue;
        }
        let name = source
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| "Mod path has no filename.".to_string())?;
        if !name.to_lowercase().ends_with(".jar") {
            continue;
        }
        let target = folder.join(name);
        fs::copy(&source, &target).map_err(error_text)?;
        copied += 1;
    }
    Ok(copied)
}

#[tauri::command]
fn open_path(path: String) -> Result<(), String> {
    open_external(&path)
}

#[tauri::command]
fn open_profile_folder(profile: String, kind: String) -> Result<String, String> {
    let profile = profile_id(&profile);
    let (path, label) = match kind.as_str() {
        "mods" => (mods_folder(&profile), "Mods folder opened."),
        "resourcepacks" => (
            resource_packs_folder(&profile),
            "Resource packs folder opened.",
        ),
        "data" => (profile_data_folder(&profile), "Profile data folder opened."),
        _ => (minecraft_folder(&profile), "Game folder opened."),
    };
    fs::create_dir_all(&path).map_err(error_text)?;
    open_external(&display_path(&path))?;
    Ok(label.to_string())
}

fn normalize_graphics_mode(value: &str) -> Result<String, String> {
    let normalized = value.trim().to_ascii_lowercase();
    let mode = match normalized.as_str() {
        "" | "auto" | "automatic" => DEFAULT_GRAPHICS_MODE,
        "safe" | "safe-graphics" | "safe_graphics" => "safe",
        "software" | "software-rendering" | "software_rendering" => "software",
        _ => {
            return Err("Graphics mode must be automatic, safe, or software.".to_string());
        }
    };
    Ok(mode.to_string())
}

fn validate_gpu_selector(value: &str) -> Result<String, String> {
    let selector = value.trim();
    if selector.is_empty() {
        return Ok(String::new());
    }
    if selector.len() > 128
        || !selector
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._:-!".contains(&byte))
    {
        return Err(
            "GPU selector may only contain letters, numbers, '.', '_', ':', '-', or '!'."
                .to_string(),
        );
    }
    Ok(selector.to_string())
}

fn safe_environment_value(key: &str) -> String {
    env::var(key)
        .ok()
        .map(|value| {
            value
                .chars()
                .filter(|character| !character.is_control())
                .take(512)
                .collect::<String>()
        })
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| "<unset>".to_string())
}

fn first_line_from_file(path: &Path) -> String {
    fs::read_to_string(path)
        .ok()
        .and_then(|contents| contents.lines().next().map(|line| line.trim().to_string()))
        .filter(|line| !line.is_empty())
        .unwrap_or_else(|| "<unknown>".to_string())
}

fn graphics_device_report() -> String {
    #[cfg(target_os = "linux")]
    {
        let root = Path::new("/sys/class/drm");
        let mut cards = fs::read_dir(root)
            .ok()
            .into_iter()
            .flatten()
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| {
                path.file_name()
                    .and_then(|name| name.to_str())
                    .is_some_and(|name| {
                        name.strip_prefix("card").is_some_and(|suffix| {
                            !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit())
                        })
                    })
            })
            .collect::<Vec<_>>();
        cards.sort();
        if cards.is_empty() {
            return "<no DRM cards detected>".to_string();
        }
        return cards
            .into_iter()
            .map(|card| {
                let name = card
                    .file_name()
                    .and_then(|value| value.to_str())
                    .unwrap_or("card?");
                let vendor = first_line_from_file(&card.join("device/vendor"));
                let driver = fs::read_to_string(card.join("device/uevent"))
                    .ok()
                    .and_then(|contents| {
                        contents.lines().find_map(|line| {
                            line.strip_prefix("DRIVER=")
                                .map(|value| value.trim().to_string())
                                .filter(|value| !value.is_empty())
                        })
                    })
                    .unwrap_or_else(|| "<unknown>".to_string());
                format!("{name}: vendor={vendor}, driver={driver}")
            })
            .collect::<Vec<_>>()
            .join("; ");
    }

    #[cfg(not(target_os = "linux"))]
    {
        "<DRM inventory is only available on Linux>".to_string()
    }
}

fn host_has_amd_drm() -> bool {
    #[cfg(target_os = "linux")]
    {
        let root = Path::new("/sys/class/drm");
        return fs::read_dir(root)
            .ok()
            .into_iter()
            .flatten()
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| {
                path.file_name()
                    .and_then(|name| name.to_str())
                    .is_some_and(|name| {
                        name.strip_prefix("card").is_some_and(|suffix| {
                            !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit())
                        })
                    })
            })
            .any(|card| {
                let vendor = first_line_from_file(&card.join("device/vendor"));
                let driver = fs::read_to_string(card.join("device/uevent"))
                    .ok()
                    .and_then(|contents| {
                        contents.lines().find_map(|line| {
                            line.strip_prefix("DRIVER=")
                                .map(|value| value.trim().to_ascii_lowercase())
                        })
                    })
                    .unwrap_or_default();
                vendor.eq_ignore_ascii_case("0x1002") || driver == "amdgpu"
            });
    }

    #[cfg(not(target_os = "linux"))]
    {
        false
    }
}

fn host_has_non_amd_drm() -> bool {
    #[cfg(target_os = "linux")]
    {
        let root = Path::new("/sys/class/drm");
        return fs::read_dir(root)
            .ok()
            .into_iter()
            .flatten()
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| {
                path.file_name()
                    .and_then(|name| name.to_str())
                    .is_some_and(|name| {
                        name.strip_prefix("card").is_some_and(|suffix| {
                            !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit())
                        })
                    })
            })
            .any(|card| {
                let vendor = first_line_from_file(&card.join("device/vendor"));
                !vendor.is_empty()
                    && vendor != "<unknown>"
                    && !vendor.eq_ignore_ascii_case("0x1002")
            });
    }

    #[cfg(not(target_os = "linux"))]
    {
        false
    }
}

fn should_apply_amd_guard(graphics_mode: &str, has_amd_drm: bool) -> bool {
    matches!(graphics_mode, "safe" | "software")
        || (graphics_mode == DEFAULT_GRAPHICS_MODE && has_amd_drm)
}

fn launch_log_has_gpu_fault(contents: &str) -> bool {
    let lower = contents.to_ascii_lowercase();
    GPU_FAULT_MARKERS
        .iter()
        .any(|marker| lower.contains(marker))
}

fn resolve_gpu_selector(graphics_mode: &str, requested_selector: &str) -> (String, bool) {
    if !requested_selector.is_empty()
        || graphics_mode != DEFAULT_GRAPHICS_MODE
        || !host_has_amd_drm()
        || !host_has_non_amd_drm()
        || !launch_log_has_gpu_fault(&tail_of_launch_log())
    {
        return (requested_selector.to_string(), false);
    }

    // Mesa's DRI_PRIME=1 selects the first alternate GPU. Only use it after a
    // real fault and only when this machine exposes a non-AMD DRM device; on
    // AMD-only hosts the explicit software mode remains the safe fallback.
    ("1".to_string(), true)
}

fn graphics_environment_report() -> String {
    let kernel = first_line_from_file(Path::new("/proc/sys/kernel/osrelease"));
    let mut lines = vec![
        format!(
            "OS: {} / {} / kernel {}",
            env::consts::OS,
            env::consts::ARCH,
            kernel
        ),
        format!(
            "Session: XDG_SESSION_TYPE={}",
            safe_environment_value("XDG_SESSION_TYPE")
        ),
        format!("GPU devices: {}", graphics_device_report()),
    ];
    for key in GRAPHICS_ENV_KEYS {
        lines.push(format!("{key}={}", safe_environment_value(key)));
    }
    lines.push("OpenGL/Mesa/Vulkan renderer: emitted by the client after Minecraft creates its graphics context.".to_string());
    lines.join("\n")
}

#[tauri::command]
fn diagnostics(profile: String) -> Result<Diagnostics, String> {
    let profile = profile_id(&profile);
    let mut checks = Vec::new();
    let root = managed_root();
    let profile_folder = minecraft_folder(&profile);
    let mods = mods_folder(&profile);
    let packs = resource_packs_folder(&profile);
    let session = launcher_session_file();
    let accounts = microsoft_accounts_file();
    push_check(
        &mut checks,
        "Launcher files",
        root.is_dir(),
        if root.is_dir() {
            "Ready"
        } else {
            "Not created yet"
        }
        .to_string(),
    );
    push_check(
        &mut checks,
        "Profile folder",
        profile_folder.is_dir(),
        if profile_folder.is_dir() {
            "Ready"
        } else {
            "Not created yet"
        }
        .to_string(),
    );
    push_check(
        &mut checks,
        "Mods folder",
        mods.is_dir(),
        if mods.is_dir() {
            "Ready"
        } else {
            "Not created yet"
        }
        .to_string(),
    );
    push_check(
        &mut checks,
        "Resource packs",
        packs.is_dir(),
        if packs.is_dir() {
            "Ready"
        } else {
            "Not created yet"
        }
        .to_string(),
    );
    push_check(
        &mut checks,
        "Launcher session",
        session.is_file(),
        if session.is_file() {
            "Saved securely"
        } else {
            "Sign-in required"
        }
        .to_string(),
    );
    let microsoft_saved = read_microsoft_account()
        .map(|account| account.is_some())
        .unwrap_or(false);
    push_check(
        &mut checks,
        "Microsoft account",
        microsoft_saved,
        if microsoft_saved {
            "Connected"
        } else {
            "Not connected"
        }
        .to_string(),
    );
    let selected_java = java_executable();
    let java = java_version_output(Path::new(&selected_java));
    let java_ok = java
        .as_ref()
        .map(|out| out.status.success() && java_output_is_21_or_newer(out))
        .unwrap_or(false);
    let java_technical = java
        .as_ref()
        .map(|out| {
            format!(
                "{} — {}",
                selected_java,
                String::from_utf8_lossy(&out.stderr)
                    .lines()
                    .next()
                    .unwrap_or("java")
            )
        })
        .unwrap_or_else(|error| format!("{} — {}", selected_java, error));
    push_check(
        &mut checks,
        "Java runtime",
        java_ok,
        if java_ok {
            "Java 21 is ready"
        } else {
            "Java runtime needs repair"
        }
        .to_string(),
    );
    let launch_log = latest_launch_log_file();
    let launch_log_last = if launch_log.is_file() {
        let tail = fs::read_to_string(&launch_log).unwrap_or_default();
        tail.lines()
            .rev()
            .find(|line| !line.trim().is_empty())
            .unwrap_or("")
            .to_string()
    } else {
        String::new()
    };
    push_check(
        &mut checks,
        "Latest launch log",
        launch_log.is_file(),
        if launch_log.is_file() {
            "Available for support"
        } else {
            "No launch recorded yet"
        }
        .to_string(),
    );
    let process_report = LAST_MINECRAFT_EXIT
        .lock()
        .ok()
        .and_then(|exit| {
            exit.as_ref()
                .map(|value| format!("\nLast Minecraft exit: {}", value.message))
        })
        .unwrap_or_default();
    let report = format!(
        "Launcher files: {}\nProfile: {}\nMods: {}\nResource packs: {}\nLauncher session: {}\nMicrosoft accounts: {}\nJava: {}\nLatest launch log: {}{}{}\n\nGraphics diagnostics:\n{}",
        display_path(&root),
        display_path(&profile_folder),
        display_path(&mods),
        display_path(&packs),
        display_path(&session),
        display_path(&accounts),
        java_technical,
        display_path(&launch_log),
        if launch_log_last.is_empty() { String::new() } else { format!("\nLast log line: {launch_log_last}") },
        process_report,
        graphics_environment_report()
    );
    if let Some(parent) = diagnostics_report_file().parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }
    fs::write(
        diagnostics_report_file(),
        format!("Gamble Client Launcher {VERSION} technical diagnostics\n{report}\n"),
    )
    .map_err(error_text)?;
    Ok(Diagnostics { checks })
}

#[tauri::command]
fn anti_screenshare_status(profile: String) -> Result<AntiScreenshareStatus, String> {
    let profile = profile_id(&profile);
    anti_screenshare_status_for(&profile, None)
}

#[tauri::command]
fn set_anti_screenshare(profile: String, enabled: bool) -> Result<AntiScreenshareStatus, String> {
    let profile = profile_id(&profile);
    ensure_profile_folders(&profile)?;
    write_launcher_preferences(&profile, enabled)?;

    let message = match toggle_anti_screenshare_bridge_module("antiscreenshare", enabled) {
        Ok(_) => format!(
            "AntiScreenshare {} in the live client.",
            if enabled { "enabled" } else { "disabled" }
        ),
        Err(_) => update_anti_screenshare_config(
            &profile,
            &[("antiscreenshare", enabled)],
            &format!(
                "AntiScreenshare {}",
                if enabled { "enabled" } else { "disabled" }
            ),
        )?,
    };

    anti_screenshare_status_for(&profile, Some(message))
}

#[tauri::command]
fn apply_anti_screenshare_clean_view(profile: String) -> Result<AntiScreenshareStatus, String> {
    let profile = profile_id(&profile);
    ensure_profile_folders(&profile)?;
    write_launcher_preferences(&profile, true)?;

    let mut changes = Vec::new();
    add_anti_screenshare_changes(&mut changes, ANTISCREENSHARE_CORE_ON, true);
    add_anti_screenshare_changes(&mut changes, ANTISCREENSHARE_SCOREBOARD_ON, true);
    add_anti_screenshare_changes(&mut changes, ANTISCREENSHARE_SCOREBOARD_OFF, false);
    add_anti_screenshare_changes(&mut changes, ANTISCREENSHARE_HUD_OFF, false);
    add_anti_screenshare_changes(&mut changes, ANTISCREENSHARE_VISUAL_OFF, false);

    let live_count = apply_anti_screenshare_bridge_changes(&changes);
    let message = if live_count > 0 {
        format!("Clean View applied in the live client for {live_count} modules.")
    } else {
        update_anti_screenshare_config(&profile, &changes, "Clean View applied")?
    };

    anti_screenshare_status_for(&profile, Some(message))
}

#[tauri::command]
fn open_anti_screenshare_obs() -> Result<String, String> {
    match read_anti_screenshare_bridge("/health", "GET") {
        Ok(_) => {
            open_external("http://127.0.0.1:18765/public")?;
            Ok("Opened OBS Browser Source view. Use http://127.0.0.1:18765/public in OBS.".to_string())
        }
        Err(_) => Ok("Client bridge is not running. Launch Gamble Client, then add http://127.0.0.1:18765/public as an OBS Browser Source.".to_string()),
    }
}

#[tauri::command]
async fn client_install_status(
    profile: String,
    build: String,
    token: String,
) -> Result<ClientInstallStatus, String> {
    run_blocking(move || client_install_status_blocking(profile, build, token)).await
}

fn client_install_status_blocking(
    profile: String,
    build: String,
    token: String,
) -> Result<ClientInstallStatus, String> {
    let profile = profile_id(&profile);
    if !profile_installs_client(&profile) {
        return Ok(ClientInstallStatus {
            file_name: String::new(),
            build,
            build_version: String::new(),
            path: String::new(),
            size: 0,
            sha256: String::new(),
            installed: true,
            update_available: false,
            message: "This profile does not install the managed Gamble Client jar.".to_string(),
        });
    }
    if token.trim().is_empty() {
        return Err("Sign in before checking the client build.".to_string());
    }

    ensure_profile_folders(&profile)?;
    let manifest = fetch_client_manifest(&build, &token)?;
    let loader = mods_folder(&profile).join(LOADER_JAR_NAME);
    let latest_loader_version = fetch_standalone_loader_version()?;
    let installed_loader_version = memory_loader_version(&loader).unwrap_or_default();
    let current = is_memory_loader_jar(&loader)
        && !installed_loader_version.trim().is_empty()
        && compare_version_strings(&installed_loader_version, &latest_loader_version)
            != std::cmp::Ordering::Less;
    Ok(ClientInstallStatus {
        file_name: manifest.file_name.clone(),
        build: manifest.build.clone(),
        build_version: public_client_version(&manifest.build_version),
        path: display_path(&loader),
        size: manifest.size,
        sha256: manifest.sha256.clone(),
        installed: current,
        update_available: !current,
        message: if current {
            format!(
                "Memory loader {} is ready for {}",
                latest_loader_version,
                display_version(&manifest)
            )
        } else {
            format!(
                "Memory loader update available: {} (client {})",
                latest_loader_version,
                display_version(&manifest)
            )
        },
    })
}

#[tauri::command]
async fn download_launcher_update() -> Result<LauncherUpdateResult, String> {
    run_blocking(download_launcher_update_blocking).await
}

fn download_launcher_update_blocking() -> Result<LauncherUpdateResult, String> {
    let info = fetch_launcher_version_info()?;
    let download = preferred_launcher_download(&info);
    if download.download_url.trim().is_empty() || download.file_name.trim().is_empty() {
        return Err("Launcher update download is not configured for this platform.".to_string());
    }

    let update_url = trusted_launcher_update_url(&download.download_url)?;
    let safe_name = safe_file_name(&download.file_name)?;
    let target = downloads_folder().join(safe_name);
    download_file(update_url.as_str(), &target)?;
    verify_file(&target, download.size, &download.sha256)?;
    let target_text = display_path(&target);
    if let Some(parent) = target.parent() {
        let _ = open_external(&display_path(parent));
    }
    let open_message =
        "Downloaded the launcher update. Review and open the installer from your Downloads folder."
            .to_string();

    Ok(LauncherUpdateResult {
        version: if info.version.trim().is_empty() {
            info.min_version
        } else {
            info.version
        },
        file_name: download.file_name,
        download_url: download.download_url,
        path: target_text,
        message: open_message,
    })
}

#[tauri::command]
async fn install_client_manifest(
    profile: String,
    build: String,
    token: String,
) -> Result<InstallResult, String> {
    run_blocking(move || install_client_manifest_blocking(profile, build, token)).await
}

fn install_client_manifest_blocking(
    profile: String,
    build: String,
    token: String,
) -> Result<InstallResult, String> {
    let profile = profile_id(&profile);
    if token.trim().is_empty() {
        return Err("Sign in before installing the client.".to_string());
    }
    if !profile_installs_client(&profile) {
        return Err(
            "Vanilla and plain Fabric profiles do not install the managed client memory loader."
                .to_string(),
        );
    }
    ensure_profile_folders(&profile)?;

    let manifest = fetch_client_manifest(&build, &token)?;

    let loader = mods_folder(&profile).join(LOADER_JAR_NAME);
    let already_ready =
        is_memory_loader_jar(&loader) && current_memory_loader_is_current(&loader).unwrap_or(false);
    let install_result = (|| {
        ensure_loader_jar(&profile, &token)?;
        ensure_fabric_api(&profile)?;
        cleanup_managed_mod_jars(&profile)?;
        cleanup_payload_client_jars(&profile)?;
        write_install_marker(&profile, &build, &manifest, &loader)
    })();
    if let Err(error) = install_result {
        return Err(error);
    }

    Ok(InstallResult {
        file_name: manifest.file_name.clone(),
        build: manifest.build.clone(),
        build_version: public_client_version(&manifest.build_version),
        path: display_path(&loader),
        size: manifest.size,
        sha256: manifest.sha256.clone(),
        updated: !already_ready,
        message: if already_ready {
            format!("Memory loader is ready for: {}", display_version(&manifest))
        } else {
            format!(
                "Installed memory loader for: {}",
                display_version(&manifest)
            )
        },
    })
}

#[tauri::command]
async fn launch_game(app: AppHandle, input: LaunchRequest) -> Result<String, String> {
    let profile = input.profile.clone();
    let result = run_blocking(move || launch_game_blocking(app, input)).await;
    if let Err(error) = &result {
        let _ = write_launch_failure_log(&profile, error);
    }
    result
}

fn launch_game_blocking(app: AppHandle, input: LaunchRequest) -> Result<String, String> {
    let _launch_guard = LAUNCH_LOCK
        .try_lock()
        .map_err(|_| "A launch operation is already in progress.".to_string())?;
    let graphics_mode = normalize_graphics_mode(&input.graphics_mode)?;
    let requested_gpu_selector = validate_gpu_selector(&input.gpu_selector)?;
    let (gpu_selector, gpu_recovered) =
        resolve_gpu_selector(&graphics_mode, &requested_gpu_selector);
    {
        let mut running = MINECRAFT_PROCESS.lock().map_err(error_text)?;
        if let Some(child) = running.as_mut() {
            if child.try_wait().map_err(error_text)?.is_none() {
                emit_launch_progress(&app, "Stopping", "Stopping Minecraft", 1, 1);
                child.kill().map_err(error_text)?;
                child.wait().map_err(error_text)?;
                *running = None;
                if let Ok(mut exit) = LAST_MINECRAFT_EXIT.lock() {
                    *exit = None;
                }
                return Ok("Minecraft stop signal sent.".to_string());
            }
            *running = None;
        }
        if let Ok(mut exit) = LAST_MINECRAFT_EXIT.lock() {
            *exit = None;
        }
    }

    let profile = profile_id(&input.profile);
    let build = input.build.trim();
    let token = input.token.trim();
    if token.is_empty() {
        return Err("Sign in before launching Minecraft.".to_string());
    }
    ensure_profile_folders(&profile)?;
    apply_minecraft_option_defaults(&profile)?;

    emit_launch_progress(&app, "Runtime", "Checking managed Java 21 runtime", 1, 13);
    let java = ensure_java_runtime(Some(&app))?;

    emit_launch_progress(&app, "Account", "Refreshing Microsoft session", 2, 13);
    let account = microsoft_account_for_launch(&input.account_uuid)?.ok_or_else(|| {
        "Microsoft is linked on the site, but this launcher does not have a local Minecraft token yet. Connect Microsoft in the launcher first.".to_string()
    })?;
    let mut identity = refresh_minecraft_identity(account)?;
    if identity.name.trim().is_empty() && !input.username.trim().is_empty() {
        identity.name = input.username.trim().to_string();
    }
    cleanup_stale_launch_payloads(&profile)?;
    emit_launch_progress(
        &app,
        "Client",
        "Checking managed client memory loader",
        3,
        13,
    );
    if profile_installs_client(&profile) {
        install_client_manifest_blocking(
            profile.to_string(),
            build.to_string(),
            token.to_string(),
        )?;
    }

    write_launcher_preferences(&profile, input.anti_screenshare)?;

    emit_launch_progress(&app, "Profile", "Preparing Minecraft profile", 4, 13);
    let profile_dir = minecraft_folder(&profile);
    let version_id = match profile_kind(&profile) {
        ProfileKind::Vanilla => {
            ensure_vanilla_version_json_with_progress(&profile_dir, MINECRAFT_VERSION, Some(&app))?;
            MINECRAFT_VERSION.to_string()
        }
        ProfileKind::Fabric | ProfileKind::Client => {
            ensure_fabric_version_json_with_progress(&profile_dir, Some(&app))?;
            ensure_vanilla_version_json_with_progress(&profile_dir, MINECRAFT_VERSION, Some(&app))?;
            ensure_fabric_api_with_progress(&profile, Some(&app))?;
            let loader_version = detected_fabric_loader_version(&profile_dir)
                .unwrap_or_else(|| FABRIC_LOADER_VERSION.to_string());
            fabric_version_id(&loader_version)
        }
    };
    emit_launch_progress(&app, "Profile", "Reading Minecraft launch profile", 7, 13);
    let version = load_version_profile(&profile_dir, &version_id)?;
    let mut classpath = ensure_libraries_with_progress(&profile_dir, &version, Some(&app))?;
    classpath.push(ensure_client_jar_with_progress(
        &profile_dir,
        &version,
        Some(&app),
    )?);
    ensure_assets_with_progress(&profile_dir, &version, Some(&app))?;
    let natives = extract_natives_with_progress(&profile_dir, &version_id, &version, Some(&app))?;
    emit_launch_progress(
        &app,
        "Client",
        "Standalone loader will authorize the client in memory",
        11,
        13,
    );
    emit_launch_progress(
        &app,
        "Ticket",
        "Standalone loader will create the launch ticket",
        12,
        13,
    );
    emit_launch_progress(&app, "Starting", "Starting Minecraft", 13, 13);
    let command = match build_minecraft_command(
        &profile_dir,
        &profile,
        build,
        &version_id,
        &version,
        &classpath,
        &natives,
        &identity,
        input.memory.max(2).min(16),
        &input.java_args,
        input.anti_screenshare,
        &input.client_display_name,
        &graphics_mode,
        &gpu_selector,
        &java,
    ) {
        Ok(command) => command,
        Err(error) => return Err(error),
    };
    let log_file = latest_launch_log_file();
    let launch_result = (|| {
        if let Some(parent) = log_file.parent() {
            fs::create_dir_all(parent).map_err(error_text)?;
        }
        fs::write(
            &log_file,
            format!(
                "Gamble Client Launcher {VERSION}\nGraphics mode: {graphics_mode}\nGPU selector: {}\nGraphics recovery: {}\n{}\n{}\n\n{}\n\n",
                if gpu_selector.is_empty() {
                    "automatic"
                } else {
                    gpu_selector.as_str()
                },
                if gpu_recovered {
                    "automatic AMD fault recovery selected alternate GPU (DRI_PRIME=1)"
                } else {
                    "not used"
                },
                graphics_environment_report(),
                game_graphics_environment_report(&graphics_mode, &gpu_selector),
                redacted_command(&command)
            ),
        )
        .map_err(error_text)?;
        let stdout = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log_file)
            .map_err(error_text)?;
        let stderr = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log_file)
            .map_err(error_text)?;
        let mut process = Command::new(&command[0]);
        process
            .args(&command[1..])
            .current_dir(&profile_dir)
            .stdout(Stdio::from(stdout))
            .stderr(Stdio::from(stderr));
        apply_game_graphics_environment(&mut process, &graphics_mode, &gpu_selector);
        #[cfg(target_os = "windows")]
        process.creation_flags(CREATE_NO_WINDOW);
        process.spawn().map_err(|error| {
            format!(
                "Could not start Minecraft with the managed Java runtime: {error}. See {}.",
                display_path(&log_file)
            )
        })
    })();
    let mut child = launch_result?;
    let mut running = match MINECRAFT_PROCESS.lock() {
        Ok(running) => running,
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            return Err(error_text(error));
        }
    };
    *running = Some(child);
    if let Ok(mut mode) = LAST_MINECRAFT_GRAPHICS_MODE.lock() {
        *mode = graphics_mode;
    }
    Ok("Minecraft process started.".to_string())
}

#[tauri::command]
fn minecraft_status() -> Result<MinecraftStatus, String> {
    let mut running = MINECRAFT_PROCESS.lock().map_err(error_text)?;
    if let Some(child) = running.as_mut() {
        let exit_status = child.try_wait().map_err(error_text)?;
        if exit_status.is_none() {
            return Ok(MinecraftStatus {
                running: true,
                pid: Some(child.id()),
                exit_code: None,
                crashed: false,
                gpu_fault: false,
                message: String::new(),
                log_path: String::new(),
                graphics_mode: current_graphics_mode(),
            });
        }
        if let Some(status) = exit_status {
            record_minecraft_exit(&status);
        }
        *running = None;
    }
    let exit = LAST_MINECRAFT_EXIT.lock().map_err(error_text)?.clone();
    Ok(match exit {
        Some(exit) => MinecraftStatus {
            running: false,
            pid: None,
            exit_code: exit.exit_code,
            crashed: exit.crashed,
            gpu_fault: exit.gpu_fault,
            message: exit.message,
            log_path: exit.log_path,
            graphics_mode: exit.graphics_mode,
        },
        None => MinecraftStatus {
            running: false,
            pid: None,
            exit_code: None,
            crashed: false,
            gpu_fault: false,
            message: String::new(),
            log_path: String::new(),
            graphics_mode: current_graphics_mode(),
        },
    })
}

fn current_graphics_mode() -> String {
    LAST_MINECRAFT_GRAPHICS_MODE
        .lock()
        .ok()
        .map(|mode| {
            if mode.is_empty() {
                DEFAULT_GRAPHICS_MODE.to_string()
            } else {
                mode.clone()
            }
        })
        .unwrap_or_else(|| DEFAULT_GRAPHICS_MODE.to_string())
}

fn apply_game_graphics_environment(command: &mut Command, graphics_mode: &str, gpu_selector: &str) {
    // The launcher UI may need WebKit's DMABUF workaround, but Minecraft must
    // never inherit it. It is a WebKit process setting, not a Java/OpenGL
    // setting, and leaking it into the game made the two failure domains hard
    // to distinguish.
    for key in [
        "WEBKIT_DISABLE_DMABUF_RENDERER",
        "GAMBLE_WEBKIT_SAFE_MODE",
        "LIBGL_ALWAYS_SOFTWARE",
        "MESA_GLTHREAD",
        "mesa_glthread",
        "AMD_DEBUG",
        "AMD_FORCE_SHADER_USE_ACO",
        "DRI_PRIME",
        "GAMBLE_GRAPHICS_MODE",
        "GAMBLE_DRI_PRIME",
    ] {
        command.env_remove(key);
    }
    command.env("GAMBLE_GRAPHICS_MODE", graphics_mode);
    if !gpu_selector.is_empty() {
        command.env("DRI_PRIME", gpu_selector);
        command.env("GAMBLE_DRI_PRIME", gpu_selector);
    }

    // These variables must be present before Java/LWJGL/Mesa starts. The
    // client also records them, but setting them from a Java pre-launch hook
    // is too late for Mesa's process initialization and did not prevent the
    // RX 6800 GPUVM fault.
    if should_apply_amd_guard(graphics_mode, host_has_amd_drm()) {
        command.env("MESA_GLTHREAD", "0");
        command.env(
            "AMD_DEBUG",
            "usellvm,nodcc,nodpbb,nofmask,nooutoforder,nongg",
        );
        // Do not set AMD_FORCE_SHADER_USE_ACO. Mesa expects shader selectors
        // or a hash file for that debug variable; "0" is not a valid value.
    }
    if graphics_mode == "software" {
        command.env("LIBGL_ALWAYS_SOFTWARE", "1");
    }
}

fn game_graphics_environment_report(graphics_mode: &str, gpu_selector: &str) -> String {
    let amd_guard = should_apply_amd_guard(graphics_mode, host_has_amd_drm());
    format!(
        "Game environment: GAMBLE_GRAPHICS_MODE={graphics_mode}, DRI_PRIME={}, AMD guard pre-JVM={}, MESA_GLTHREAD={}, AMD_DEBUG={}, AMD_FORCE_SHADER_USE_ACO=<unset>, LIBGL_ALWAYS_SOFTWARE={}",
        if gpu_selector.is_empty() {
            "<unset>"
        } else {
            gpu_selector
        },
        if amd_guard { "enabled" } else { "disabled" },
        if amd_guard { "0" } else { "<unset>" },
        if amd_guard {
            "usellvm,nodcc,nodpbb,nofmask,nooutoforder,nongg"
        } else {
            "<unset>"
        },
        if graphics_mode == "software" {
            "1"
        } else {
            "<unset>"
        },
    )
}

fn tail_of_launch_log() -> String {
    let contents = fs::read_to_string(latest_launch_log_file()).unwrap_or_default();
    let mut tail = contents
        .chars()
        .rev()
        .take(24_000)
        .collect::<String>()
        .chars()
        .rev()
        .collect::<String>();
    if tail.is_empty() {
        tail = "<launch log is empty>".to_string();
    }
    tail
}

fn record_minecraft_exit(status: &ExitStatus) {
    let tail = tail_of_launch_log();
    let gpu_fault = launch_log_has_gpu_fault(&tail);
    #[cfg(unix)]
    let signal = status.signal();
    #[cfg(not(unix))]
    let signal: Option<i32> = None;
    let exit_code = status.code().or_else(|| signal.map(|value| 128 + value));
    let crashed = gpu_fault || signal.is_some() || exit_code.is_some_and(|code| code != 0);
    let message = if gpu_fault {
        "Minecraft exited after a graphics-driver reset or GPU context loss. The launcher kept running; attach the launch log and gpu-diagnostics.log to support.".to_string()
    } else if let Some(signal) = signal {
        format!("Minecraft terminated by signal {signal}. The launcher did not restart it.")
    } else if let Some(code) = exit_code.filter(|code| *code != 0) {
        format!("Minecraft exited with code {code}. The launcher did not restart it.")
    } else {
        "Minecraft exited normally.".to_string()
    };
    let exit = MinecraftExit {
        exit_code,
        crashed,
        gpu_fault,
        message,
        log_path: display_path(&latest_launch_log_file()),
        graphics_mode: current_graphics_mode(),
    };
    if let Ok(mut last_exit) = LAST_MINECRAFT_EXIT.lock() {
        *last_exit = Some(exit);
    }
}

fn refresh_minecraft_identity(account: MicrosoftAccount) -> Result<MinecraftProfile, String> {
    if let Some(profile) = cached_minecraft_identity(&account, MINECRAFT_TOKEN_REFRESH_BUFFER_MS) {
        return Ok(profile);
    }

    let fallback = cached_minecraft_identity(&account, 0);
    let token = match refresh_microsoft_token(&account.refresh_token) {
        Ok(token) => token,
        Err(error) if is_rate_limited_error(&error) => {
            if let Some(profile) = fallback {
                return Ok(profile);
            }
            return Err(error);
        }
        Err(error) => return Err(error),
    };
    let profile = match exchange_microsoft_for_minecraft(&token.access_token) {
        Ok(profile) => profile,
        Err(error) if is_rate_limited_error(&error) => {
            if let Some(profile) = fallback {
                return Ok(profile);
            }
            return Err(error);
        }
        Err(error) => return Err(error),
    };
    let saved = MicrosoftAccount {
        name: profile.name.clone(),
        uuid: profile.uuid.clone(),
        xuid: profile.xuid.clone(),
        refresh_token: if token.refresh_token.trim().is_empty() {
            account.refresh_token
        } else {
            token.refresh_token
        },
        minecraft_access_token: profile.access_token.clone(),
        minecraft_expires_at: profile.expires_at,
    };
    save_microsoft_account(&saved)?;
    Ok(MinecraftProfile {
        uuid: profile.uuid,
        name: profile.name,
        xuid: profile.xuid,
        access_token: profile.access_token,
        expires_at: profile.expires_at,
    })
}

fn cached_minecraft_identity(
    account: &MicrosoftAccount,
    refresh_buffer_ms: u64,
) -> Option<MinecraftProfile> {
    if account.minecraft_access_token.trim().is_empty()
        || account.uuid.trim().is_empty()
        || account.name.trim().is_empty()
    {
        return None;
    }
    if account.minecraft_expires_at <= unix_millis().saturating_add(refresh_buffer_ms) {
        return None;
    }
    Some(MinecraftProfile {
        uuid: account.uuid.replace('-', ""),
        name: account.name.clone(),
        xuid: account.xuid.clone(),
        access_token: account.minecraft_access_token.clone(),
        expires_at: account.minecraft_expires_at,
    })
}

fn is_rate_limited_error(error: &str) -> bool {
    let lower = error.to_lowercase();
    lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests")
}

fn refresh_microsoft_token(refresh_token: &str) -> Result<MicrosoftToken, String> {
    let params = [
        ("grant_type", "refresh_token".to_string()),
        ("client_id", MICROSOFT_CLIENT_ID.to_string()),
        ("refresh_token", refresh_token.to_string()),
        ("scope", MICROSOFT_SCOPE.to_string()),
    ];
    let response = http_client()?
        .post(MICROSOFT_TOKEN_URL)
        .form(&params)
        .send()
        .map_err(error_text)?;
    let status = response.status();
    let body = response.json::<serde_json::Value>().map_err(error_text)?;
    if !status.is_success() {
        return Err(microsoft_refresh_error(status.as_u16(), &body));
    }
    parse_microsoft_token(&body)
}

fn microsoft_refresh_error(status: u16, body: &serde_json::Value) -> String {
    let error = json_string(body, "error").to_ascii_lowercase();
    if matches!(
        error.as_str(),
        "invalid_grant" | "interaction_required" | "login_required" | "consent_required"
    ) {
        return MICROSOFT_REAUTH_REQUIRED.to_string();
    }
    if status == 429 {
        return "Microsoft auth is temporarily rate limited. Wait a minute, then try again."
            .to_string();
    }
    format!("Microsoft auth could not refresh this account (HTTP {status}). Reconnect Microsoft and try again.")
}

fn exchange_microsoft_authorization_code(
    code: &str,
    code_verifier: &str,
) -> Result<MicrosoftToken, String> {
    let params = [
        ("grant_type", "authorization_code".to_string()),
        ("client_id", MICROSOFT_CLIENT_ID.to_string()),
        ("code", code.to_string()),
        ("redirect_uri", MICROSOFT_REDIRECT_URI.to_string()),
        ("code_verifier", code_verifier.to_string()),
        ("scope", MICROSOFT_SCOPE.to_string()),
    ];
    let body = http_client()?
        .post(MICROSOFT_TOKEN_URL)
        .form(&params)
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json::<serde_json::Value>()
        .map_err(error_text)?;
    parse_microsoft_token(&body)
}

fn wait_for_microsoft_callback(
    listener: &TcpListener,
    generation: u32,
) -> Result<TcpStream, String> {
    let deadline = SystemTime::now() + Duration::from_secs(180);

    loop {
        if microsoft_browser_sign_in_cancelled(generation) {
            return Err("Microsoft sign-in cancelled.".to_string());
        }
        match listener.accept() {
            Ok((stream, _)) => return Ok(stream),
            Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                if SystemTime::now() >= deadline {
                    return Err("Microsoft sign-in timed out.".to_string());
                }
                std::thread::sleep(Duration::from_millis(150));
            }
            Err(error) => return Err(error_text(error)),
        }
    }
}

fn microsoft_browser_sign_in_cancelled(generation: u32) -> bool {
    MICROSOFT_BROWSER_SIGNIN_GENERATION.load(Ordering::SeqCst) != generation
}

fn microsoft_authorize_url(
    code_challenge: &str,
    state: &str,
    force_account_picker: bool,
) -> String {
    let mut params = vec![
        ("client_id", MICROSOFT_CLIENT_ID.to_string()),
        ("response_type", "code".to_string()),
        ("redirect_uri", MICROSOFT_REDIRECT_URI.to_string()),
        ("response_mode", "query".to_string()),
        ("scope", MICROSOFT_SCOPE.to_string()),
        ("code_challenge", code_challenge.to_string()),
        ("code_challenge_method", "S256".to_string()),
        ("state", state.to_string()),
    ];
    if force_account_picker {
        params.push(("prompt", "select_account".to_string()));
    }

    let query = params
        .into_iter()
        .map(|(key, value)| {
            format!(
                "{}={}",
                url_encode_component(key),
                url_encode_component(&value)
            )
        })
        .collect::<Vec<_>>()
        .join("&");
    format!("{MICROSOFT_AUTHORIZE_URL}?{query}")
}

fn read_microsoft_callback(stream: &mut TcpStream) -> Result<HashMap<String, String>, String> {
    let mut buffer = [0u8; 8192];
    let count = stream.read(&mut buffer).map_err(error_text)?;
    let request = String::from_utf8_lossy(&buffer[..count]);
    let first_line = request.lines().next().unwrap_or("");
    let target = first_line.split_whitespace().nth(1).unwrap_or("/");
    let query = target.split_once('?').map(|(_, query)| query).unwrap_or("");
    Ok(parse_url_query(query))
}

fn write_microsoft_callback_response(
    stream: &mut TcpStream,
    title: &str,
    message: &str,
) -> io::Result<()> {
    let body = format!(
        "<!doctype html><html><head><meta charset=\"utf-8\"><title>{}</title><style>body{{margin:0;min-height:100vh;display:grid;place-items:center;background:#11131a;color:#f4f6fb;font-family:system-ui,sans-serif}}main{{max-width:440px;padding:28px;border:1px solid #2b2f3a;background:#181b24}}h1{{margin:0 0 10px;font-size:24px}}p{{margin:0;color:#b8bfcc;line-height:1.5}}</style></head><body><main><h1>{}</h1><p>{}</p></main></body></html>",
        html_escape(title),
        html_escape(title),
        html_escape(message)
    );
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.as_bytes().len(),
        body
    );
    stream.write_all(response.as_bytes())
}

fn random_base64_url(len: usize) -> String {
    let mut bytes = vec![0u8; len];
    for byte in &mut bytes {
        *byte = rand::random();
    }
    URL_SAFE_NO_PAD.encode(bytes)
}

fn sha256_base64_url(value: &str) -> String {
    let digest = Sha256::digest(value.as_bytes());
    URL_SAFE_NO_PAD.encode(digest)
}

fn parse_url_query(query: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for pair in query.split('&') {
        if pair.is_empty() {
            continue;
        }
        let (key, value) = pair.split_once('=').unwrap_or((pair, ""));
        map.insert(url_decode_component(key), url_decode_component(value));
    }
    map
}

fn url_decode_component(value: &str) -> String {
    let mut bytes = Vec::with_capacity(value.len());
    let mut iter = value.as_bytes().iter().copied().peekable();
    while let Some(byte) = iter.next() {
        if byte == b'+' {
            bytes.push(b' ');
        } else if byte == b'%' {
            let hi = iter.next();
            let lo = iter.next();
            if let (Some(hi), Some(lo)) = (hi, lo) {
                if let (Some(hi), Some(lo)) = (hex_value(hi), hex_value(lo)) {
                    bytes.push((hi << 4) | lo);
                }
            }
        } else {
            bytes.push(byte);
        }
    }
    String::from_utf8_lossy(&bytes).to_string()
}

fn hex_value(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
}

fn html_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

fn write_launcher_preferences(profile: &str, anti_screenshare: bool) -> Result<(), String> {
    let folder = profile_data_folder(profile);
    fs::create_dir_all(&folder).map_err(error_text)?;
    let body = json!({
        "schema": 1,
        "antiScreenshare": anti_screenshare,
        "updatedAt": timestamp()
    });
    fs::write(
        folder.join("launcher-settings.json"),
        serde_json::to_string_pretty(&body).map_err(error_text)? + "\n",
    )
    .map_err(error_text)
}

fn read_launcher_anti_preference(profile: &str) -> Option<bool> {
    let path = profile_data_folder(profile).join("launcher-settings.json");
    let text = fs::read_to_string(path).ok()?;
    let body = serde_json::from_str::<serde_json::Value>(&text).ok()?;
    body.get("antiScreenshare")
        .and_then(|value| value.as_bool())
}

fn anti_screenshare_status_for(
    profile: &str,
    override_message: Option<String>,
) -> Result<AntiScreenshareStatus, String> {
    let modules_path = anti_screenshare_modules_file(profile);
    if let Ok(modules) = read_anti_screenshare_bridge_modules() {
        let enabled = bridge_module_active(&modules, "antiscreenshare").unwrap_or(true);
        return Ok(AntiScreenshareStatus {
            enabled,
            available: true,
            bridge_online: true,
            source: "Live client".to_string(),
            message: override_message.unwrap_or_else(|| {
                format!(
                    "Live client bridge connected. Core module is {}.",
                    if enabled { "on" } else { "off" }
                )
            }),
            modules_path: display_path(&modules_path),
        });
    }

    if modules_path.is_file() {
        let text = fs::read_to_string(&modules_path).map_err(error_text)?;
        let active = module_active_state(&text, "antiscreenshare");
        let enabled =
            active.unwrap_or_else(|| read_launcher_anti_preference(profile).unwrap_or(false));
        return Ok(AntiScreenshareStatus {
            enabled,
            available: active.is_some(),
            bridge_online: false,
            source: "Saved config".to_string(),
            message: override_message.unwrap_or_else(|| {
                if active.is_some() {
                    format!("Client is offline. Saved profile config has AntiScreenshare {}.", if enabled { "on" } else { "off" })
                } else {
                    "Client is offline. modules.txt exists, but AntiScreenshare was not found in this profile.".to_string()
                }
            }),
            modules_path: display_path(&modules_path),
        });
    }

    let enabled = read_launcher_anti_preference(profile).unwrap_or(false);
    Ok(AntiScreenshareStatus {
        enabled,
        available: false,
        bridge_online: false,
        source: "Launcher preference".to_string(),
        message: override_message.unwrap_or_else(|| {
            if enabled {
                "Saved for the next launch. Launch Gamble Client once before editing live modules."
                    .to_string()
            } else {
                "Launch Gamble Client once before AntiScreenshare can edit module config."
                    .to_string()
            }
        }),
        modules_path: display_path(&modules_path),
    })
}

fn add_anti_screenshare_changes(
    changes: &mut Vec<(&'static str, bool)>,
    modules: &'static [&'static str],
    active: bool,
) {
    for module in modules {
        changes.push((*module, active));
    }
}

fn apply_anti_screenshare_bridge_changes(changes: &[(&str, bool)]) -> usize {
    if read_anti_screenshare_bridge("/health", "GET").is_err() {
        return 0;
    }

    changes
        .iter()
        .filter(|(module, active)| toggle_anti_screenshare_bridge_module(module, *active).is_ok())
        .count()
}

fn update_anti_screenshare_config(
    profile: &str,
    changes: &[(&str, bool)],
    message: &str,
) -> Result<String, String> {
    let modules = anti_screenshare_modules_file(profile);
    if !modules.is_file() {
        return Ok("Saved for the next launch. Launch Gamble Client once before module config can be edited.".to_string());
    }

    let mut text = fs::read_to_string(&modules).map_err(error_text)?;
    let mut touched = 0usize;
    let mut missing = Vec::new();
    for (module, active) in changes {
        match set_module_active_text(&text, module, *active) {
            Some(updated) => {
                if updated != text {
                    touched += 1;
                    text = updated;
                }
            }
            None => missing.push((*module).to_string()),
        }
    }

    if touched == 0 {
        return Ok(if missing.is_empty() {
            format!("{message}; selected modules were already in that state.")
        } else {
            format!("{message}; no matching modules found in this build.")
        });
    }

    let backup = backup_anti_screenshare_modules(&modules)?;
    fs::write(&modules, text).map_err(error_text)?;
    let mut result = format!(
        "{message} for {touched} modules. Backup: {}.",
        backup
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or("modules backup")
    );
    if !missing.is_empty() {
        result.push_str(&format!(" Missing in this build: {}.", missing.join(", ")));
    }
    Ok(result)
}

fn backup_anti_screenshare_modules(modules: &Path) -> Result<PathBuf, String> {
    let backup = modules
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .join(format!(
            "modules.txt.backup-antiscreenshare-{}.txt",
            timestamp()
        ));
    fs::copy(modules, &backup).map_err(error_text)?;
    Ok(backup)
}

fn module_active_state(text: &str, module: &str) -> Option<bool> {
    let active_index = module_active_value_index(text, module)?;
    match text.as_bytes().get(active_index).copied() {
        Some(b'1') => Some(true),
        Some(b'0') => Some(false),
        _ => None,
    }
}

fn set_module_active_text(text: &str, module: &str, active: bool) -> Option<String> {
    let active_index = module_active_value_index(text, module)?;
    let current = *text.as_bytes().get(active_index)?;
    if current != b'0' && current != b'1' {
        return None;
    }
    let mut updated = text.to_string();
    updated.replace_range(
        active_index..active_index + 1,
        if active { "1" } else { "0" },
    );
    Some(updated)
}

fn module_active_value_index(text: &str, module: &str) -> Option<usize> {
    let name_index = text.find(&format!("name:\"{module}\""))?;
    let active_prefix = "{active:";
    let active_index = text[..name_index].rfind(active_prefix)? + active_prefix.len();
    Some(active_index)
}

fn read_anti_screenshare_bridge_modules() -> Result<Vec<serde_json::Value>, String> {
    let root = read_anti_screenshare_bridge("/modules", "GET")?;
    root.get("modules")
        .and_then(|value| value.as_array())
        .cloned()
        .ok_or_else(|| "AntiScreenshare bridge did not return modules.".to_string())
}

fn bridge_module_active(modules: &[serde_json::Value], name: &str) -> Option<bool> {
    modules.iter().find_map(|module| {
        let module_name = module.get("name").and_then(|value| value.as_str())?;
        if module_name.eq_ignore_ascii_case(name) {
            Some(json_bool_value(
                module
                    .get("active")
                    .unwrap_or(&serde_json::Value::Bool(false)),
            ))
        } else {
            None
        }
    })
}

fn toggle_anti_screenshare_bridge_module(module: &str, active: bool) -> Result<(), String> {
    let path = format!(
        "/toggle?name={}&state={}",
        url_encode_component(module),
        if active { "on" } else { "off" },
    );
    let root = read_anti_screenshare_bridge(&path, "POST")?;
    if json_bool_value(root.get("ok").unwrap_or(&serde_json::Value::Bool(false))) {
        Ok(())
    } else {
        Err(json_string(&root, "error"))
    }
}

fn read_anti_screenshare_bridge(path: &str, method: &str) -> Result<serde_json::Value, String> {
    let url = format!("http://127.0.0.1:18765{path}");
    let client = reqwest::blocking::Client::builder()
        .user_agent(format!("GambleClientLauncher/{VERSION}"))
        .connect_timeout(Duration::from_millis(900))
        .timeout(Duration::from_millis(1800))
        .build()
        .map_err(error_text)?;
    let request = if method == "POST" {
        client.post(url)
    } else {
        client.get(url)
    };
    let response = request.send().map_err(error_text)?;
    let status = response.status();
    let text = response.text().map_err(error_text)?;
    if !status.is_success() {
        return Err(if text.trim().is_empty() {
            format!("AntiScreenshare bridge returned HTTP {}", status.as_u16())
        } else {
            text
        });
    }
    if text.trim().is_empty() {
        return Ok(json!({}));
    }
    Ok(serde_json::from_str(&text).unwrap_or_else(|_| json!({ "ok": true, "body": text })))
}

fn ensure_fabric_version_json_with_progress(
    game_dir: &Path,
    app: Option<&AppHandle>,
) -> Result<PathBuf, String> {
    // Existing profiles may predate the launcher's current minimum (for example
    // 0.18.4).  Do not keep selecting that stale folder forever: stage the
    // supported loader on the next launch.  A newer locally installed loader is
    // still respected so custom profiles are not silently downgraded.
    let loader_version = detected_fabric_loader_version(game_dir)
        .filter(|installed| {
            compare_version_strings(installed, FABRIC_LOADER_VERSION) != std::cmp::Ordering::Less
        })
        .unwrap_or_else(|| FABRIC_LOADER_VERSION.to_string());
    let version_id = fabric_version_id(&loader_version);
    let path = game_dir
        .join("versions")
        .join(&version_id)
        .join(format!("{version_id}.json"));
    if !path.is_file() {
        if let Some(app) = app {
            emit_launch_progress(app, "Fabric", "Downloading Fabric launch profile", 4, 12);
        }
        download_file(&fabric_profile_url(&loader_version), &path)?;
    } else if let Some(app) = app {
        emit_launch_progress(app, "Fabric", "Fabric launch profile is ready", 4, 12);
    }
    Ok(path)
}

fn fabric_version_id(loader_version: &str) -> String {
    format!("fabric-loader-{loader_version}-{MINECRAFT_VERSION}")
}

fn fabric_profile_url(loader_version: &str) -> String {
    format!(
        "https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT_VERSION}/{loader_version}/profile/json"
    )
}

fn detected_fabric_loader_version(game_dir: &Path) -> Option<String> {
    let versions = game_dir.join("versions");
    let entries = fs::read_dir(versions).ok()?;
    let prefix = "fabric-loader-";
    let suffix = format!("-{MINECRAFT_VERSION}");
    entries
        .filter_map(Result::ok)
        .filter_map(|entry| entry.file_name().into_string().ok())
        .filter_map(|name| {
            let version = name.strip_prefix(prefix)?.strip_suffix(&suffix)?;
            if version.is_empty() {
                None
            } else {
                Some(version.to_string())
            }
        })
        .max_by(|left, right| compare_version_strings(left, right))
}

fn latest_fabric_loader_version() -> Result<String, String> {
    let versions = http_client()?
        .get(FABRIC_VERSIONS_URL)
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json::<Vec<serde_json::Value>>()
        .map_err(error_text)?;
    versions
        .iter()
        .find(|entry| {
            json_bool_value(
                entry
                    .get("stable")
                    .unwrap_or(&serde_json::Value::Bool(false)),
            )
        })
        .or_else(|| versions.first())
        .map(|entry| json_string(entry, "version"))
        .filter(|version| !version.trim().is_empty())
        .ok_or_else(|| "Fabric did not return a compatible loader version.".to_string())
}

fn compare_version_strings(left: &str, right: &str) -> std::cmp::Ordering {
    let parse = |value: &str| {
        value
            .split(|ch: char| !ch.is_ascii_digit())
            .filter(|part| !part.is_empty())
            .map(|part| part.parse::<u64>().unwrap_or(0))
            .collect::<Vec<_>>()
    };
    parse(left).cmp(&parse(right))
}

fn ensure_vanilla_version_json_with_progress(
    game_dir: &Path,
    version_id: &str,
    app: Option<&AppHandle>,
) -> Result<PathBuf, String> {
    let path = game_dir
        .join("versions")
        .join(version_id)
        .join(format!("{version_id}.json"));
    if path.is_file() {
        if let Some(app) = app {
            emit_launch_progress(
                app,
                "Minecraft",
                "Minecraft version profile is ready",
                5,
                12,
            );
        }
        return Ok(path);
    }
    if let Some(app) = app {
        emit_launch_progress(
            app,
            "Minecraft",
            "Downloading Minecraft version manifest",
            5,
            12,
        );
    }
    let manifest = http_client()?
        .get(VERSION_MANIFEST_URL)
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json::<serde_json::Value>()
        .map_err(error_text)?;
    let versions = manifest
        .get("versions")
        .and_then(|v| v.as_array())
        .cloned()
        .unwrap_or_default();
    let url = versions
        .iter()
        .find(|entry| json_string(entry, "id") == version_id)
        .map(|entry| json_string(entry, "url"))
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| {
            format!("Could not find Minecraft {version_id} in Mojang's version manifest.")
        })?;
    if let Some(app) = app {
        emit_launch_progress(
            app,
            "Minecraft",
            format!("Downloading Minecraft {version_id} profile"),
            5,
            12,
        );
    }
    download_file(&url, &path)?;
    Ok(path)
}

fn load_version_profile(game_dir: &Path, version_id: &str) -> Result<VersionProfile, String> {
    let path = game_dir
        .join("versions")
        .join(version_id)
        .join(format!("{version_id}.json"));
    let body =
        serde_json::from_str::<serde_json::Value>(&fs::read_to_string(&path).map_err(error_text)?)
            .map_err(error_text)?;
    let inherits = json_string(&body, "inheritsFrom");
    let mut profile = if inherits.trim().is_empty() {
        VersionProfile::default()
    } else {
        load_version_profile(game_dir, &inherits)?
    };
    profile.id = version_id.to_string();
    let main_class = json_string(&body, "mainClass");
    if !main_class.is_empty() {
        profile.main_class = main_class;
    }
    if let Some(asset) = body.get("assetIndex") {
        let id = json_string(asset, "id");
        let url = json_string(asset, "url");
        if !id.is_empty() {
            profile.asset_index_id = id;
        }
        if !url.is_empty() {
            profile.asset_index_url = url;
        }
    }
    if let Some(client) = body.pointer("/downloads/client") {
        let url = json_string(client, "url");
        if !url.is_empty() {
            profile.client_version_id = version_id.to_string();
            profile.client_jar_url = url;
        }
    }
    if let Some(libraries) = body.get("libraries").and_then(|v| v.as_array()) {
        for value in libraries {
            if let Some(library) = parse_library(value) {
                profile.libraries.push(library);
            }
        }
    }
    if let Some(arguments) = body.get("arguments") {
        if let Some(jvm) = arguments.get("jvm").and_then(|v| v.as_array()) {
            profile.jvm_arguments.extend(parse_arguments(jvm));
        }
        if let Some(game) = arguments.get("game").and_then(|v| v.as_array()) {
            let parsed = parse_arguments(game);
            if !parsed.is_empty() {
                profile.game_arguments = parsed;
            }
        }
    } else {
        let legacy = json_string(&body, "minecraftArguments");
        if !legacy.is_empty() {
            profile.game_arguments = legacy.split_whitespace().map(ToString::to_string).collect();
        }
    }
    if profile.main_class.trim().is_empty() {
        return Err(format!(
            "Minecraft version profile {version_id} did not include a main class."
        ));
    }
    Ok(profile)
}

fn parse_library(value: &serde_json::Value) -> Option<Library> {
    let name = json_string(value, "name");
    if name.is_empty() {
        return None;
    }
    let artifact = value
        .pointer("/downloads/artifact")
        .unwrap_or(&serde_json::Value::Null);
    let mut artifact_path = json_string(artifact, "path");
    let mut artifact_url = json_string(artifact, "url");
    if artifact_path.is_empty() {
        artifact_path = maven_artifact_path(&name);
        artifact_url = maven_artifact_url(&json_string(value, "url"), &artifact_path);
    }
    let classifiers = value
        .pointer("/downloads/classifiers")
        .and_then(|v| v.as_object())
        .cloned()
        .unwrap_or_default();
    let mut classifier_paths = serde_json::Map::new();
    let mut classifier_urls = serde_json::Map::new();
    for (key, item) in classifiers {
        classifier_paths.insert(
            key.clone(),
            serde_json::Value::String(json_string(&item, "path")),
        );
        classifier_urls.insert(key, serde_json::Value::String(json_string(&item, "url")));
    }
    Some(Library {
        name,
        rules: value
            .get("rules")
            .and_then(|v| v.as_array())
            .cloned()
            .unwrap_or_default(),
        artifact_path,
        artifact_url,
        natives: value
            .get("natives")
            .and_then(|v| v.as_object())
            .cloned()
            .unwrap_or_default(),
        classifier_paths,
        classifier_urls,
    })
}

fn parse_arguments(values: &[serde_json::Value]) -> Vec<String> {
    let mut out = Vec::new();
    for value in values {
        if let Some(text) = value.as_str() {
            out.push(text.to_string());
            continue;
        }
        if !rules_allow(
            value
                .get("rules")
                .and_then(|v| v.as_array())
                .cloned()
                .unwrap_or_default()
                .as_slice(),
        ) {
            continue;
        }
        let item = value.get("value").unwrap_or(&serde_json::Value::Null);
        if let Some(text) = item.as_str() {
            out.push(text.to_string());
        } else if let Some(items) = item.as_array() {
            for nested in items {
                if let Some(text) = nested.as_str() {
                    out.push(text.to_string());
                }
            }
        }
    }
    out
}

fn ensure_libraries_with_progress(
    game_dir: &Path,
    profile: &VersionProfile,
    app: Option<&AppHandle>,
) -> Result<Vec<PathBuf>, String> {
    let mut classpath = Vec::new();
    let libraries_dir = game_dir.join("libraries");
    let total = profile
        .libraries
        .iter()
        .filter(|library| rules_allow(&library.rules))
        .count()
        .max(1) as u32;
    let mut current = 0u32;
    for library in &profile.libraries {
        if !rules_allow(&library.rules) {
            continue;
        }
        current = current.saturating_add(1);
        if let Some(app) = app {
            emit_launch_progress(
                app,
                "Libraries",
                format!("Checking library {current}/{total}"),
                current,
                total,
            );
        }
        if !library.artifact_path.is_empty() {
            let path = libraries_dir.join(&library.artifact_path);
            if !path.is_file() {
                if library.artifact_url.is_empty() {
                    return Err(format!("No download URL for library {}", library.name));
                }
                if let Some(app) = app {
                    emit_launch_progress(
                        app,
                        "Libraries",
                        format!("Downloading {}", library.name),
                        current,
                        total,
                    );
                }
                download_file(&library.artifact_url, &path)?;
            }
            classpath.push(path);
        }
        if let Some((path, url)) = native_artifact(library) {
            let file = libraries_dir.join(path);
            if !file.is_file() {
                if url.is_empty() {
                    return Err(format!(
                        "No native download URL for library {}",
                        library.name
                    ));
                }
                if let Some(app) = app {
                    emit_launch_progress(
                        app,
                        "Libraries",
                        format!("Downloading native {}", library.name),
                        current,
                        total,
                    );
                }
                download_file(&url, &file)?;
            }
        }
    }
    Ok(classpath)
}

fn ensure_client_jar_with_progress(
    game_dir: &Path,
    profile: &VersionProfile,
    app: Option<&AppHandle>,
) -> Result<PathBuf, String> {
    if profile.client_version_id.is_empty() || profile.client_jar_url.is_empty() {
        return Err("Minecraft profile does not include a client jar URL.".to_string());
    }
    let path = game_dir
        .join("versions")
        .join(&profile.client_version_id)
        .join(format!("{}.jar", profile.client_version_id));
    if !path.is_file() {
        if let Some(app) = app {
            emit_launch_progress(
                app,
                "Minecraft",
                format!(
                    "Downloading Minecraft {} client jar",
                    profile.client_version_id
                ),
                8,
                12,
            );
        }
        download_file(&profile.client_jar_url, &path)?;
    } else if let Some(app) = app {
        emit_launch_progress(app, "Minecraft", "Minecraft client jar is ready", 8, 12);
    }
    Ok(path)
}

fn ensure_assets_with_progress(
    game_dir: &Path,
    profile: &VersionProfile,
    app: Option<&AppHandle>,
) -> Result<(), String> {
    if profile.asset_index_id.is_empty() || profile.asset_index_url.is_empty() {
        return Err("Minecraft profile does not include an asset index.".to_string());
    }
    let assets = game_dir.join("assets");
    let index = assets
        .join("indexes")
        .join(format!("{}.json", profile.asset_index_id));
    if !index.is_file() {
        if let Some(app) = app {
            emit_launch_progress(app, "Assets", "Downloading Minecraft asset index", 0, 1);
        }
        download_file(&profile.asset_index_url, &index)?;
    }
    let body =
        serde_json::from_str::<serde_json::Value>(&fs::read_to_string(index).map_err(error_text)?)
            .map_err(error_text)?;
    let objects = body
        .get("objects")
        .and_then(|v| v.as_object())
        .cloned()
        .unwrap_or_default();
    let total = objects.len().max(1) as u32;
    let mut current = 0u32;
    let mut missing = Vec::new();
    for item in objects.values() {
        current = current.saturating_add(1);
        let hash = json_string(item, "hash");
        if hash.len() < 2 {
            continue;
        }
        let path = assets.join("objects").join(&hash[0..2]).join(&hash);
        if !path.is_file() {
            missing.push(AssetDownload { hash, path });
        } else if let Some(app) = app {
            if current == 1 || current == total || current % 50 == 0 {
                emit_launch_progress(
                    app,
                    "Assets",
                    format!("Checking Minecraft assets {current}/{total}"),
                    current,
                    total,
                );
            }
        }
    }
    if missing.is_empty() {
        if let Some(app) = app {
            emit_launch_progress(app, "Assets", "Minecraft assets are ready", total, total);
        }
        return Ok(());
    }
    download_missing_assets(missing, app.cloned())
}

fn download_missing_assets(
    missing: Vec<AssetDownload>,
    app: Option<AppHandle>,
) -> Result<(), String> {
    let total = missing.len() as u32;
    let workers = std::thread::available_parallelism()
        .map(|count| count.get().saturating_mul(2).clamp(2, 12))
        .unwrap_or(4);
    let queue = Arc::new(Mutex::new(VecDeque::from(missing)));
    let done = Arc::new(AtomicU32::new(0));

    if let Some(app) = app.as_ref() {
        emit_launch_progress(
            app,
            "Assets",
            format!("Downloading Minecraft assets 0/{total}"),
            0,
            total,
        );
    }

    std::thread::scope(|scope| {
        let mut handles = Vec::new();
        for _ in 0..workers {
            let queue = Arc::clone(&queue);
            let done = Arc::clone(&done);
            let app = app.clone();
            handles.push(scope.spawn(move || -> Result<(), String> {
                loop {
                    let asset = {
                        let mut queue = queue.lock().map_err(error_text)?;
                        queue.pop_front()
                    };
                    let Some(asset) = asset else {
                        return Ok(());
                    };
                    if !asset.path.is_file() {
                        let url = format!("{ASSET_BASE_URL}{}/{}", &asset.hash[0..2], asset.hash);
                        download_file(&url, &asset.path)?;
                    }
                    let current = done.fetch_add(1, Ordering::SeqCst).saturating_add(1);
                    if let Some(app) = app.as_ref() {
                        if current == 1 || current == total || current % 20 == 0 {
                            emit_launch_progress(
                                app,
                                "Assets",
                                format!("Downloading Minecraft assets {current}/{total}"),
                                current,
                                total,
                            );
                        }
                    }
                }
            }));
        }

        for handle in handles {
            handle
                .join()
                .map_err(|_| "Asset worker crashed.".to_string())??;
        }
        Ok(())
    })
}

fn extract_natives_with_progress(
    game_dir: &Path,
    version_id: &str,
    profile: &VersionProfile,
    app: Option<&AppHandle>,
) -> Result<PathBuf, String> {
    let target = game_dir.join("versions").join(version_id).join("natives");
    fs::create_dir_all(&target).map_err(error_text)?;
    let libraries_dir = game_dir.join("libraries");
    let total = profile
        .libraries
        .iter()
        .filter(|library| rules_allow(&library.rules))
        .count()
        .max(1) as u32;
    let mut current = 0u32;
    for library in &profile.libraries {
        if !rules_allow(&library.rules) {
            continue;
        }
        current = current.saturating_add(1);
        if let Some((path, _)) = native_artifact(library) {
            let file = libraries_dir.join(path);
            if file.is_file() {
                if let Some(app) = app {
                    emit_launch_progress(
                        app,
                        "Natives",
                        format!("Extracting native libraries {current}/{total}"),
                        current,
                        total,
                    );
                }
                unzip_natives(&file, &target)?;
            }
        }
    }
    Ok(target)
}

fn build_minecraft_command(
    game_dir: &Path,
    profile_id: &str,
    build: &str,
    version_id: &str,
    profile: &VersionProfile,
    classpath: &[PathBuf],
    natives: &Path,
    identity: &MinecraftProfile,
    memory: u8,
    extra_java_args: &str,
    anti_screenshare: bool,
    client_display_name: &str,
    graphics_mode: &str,
    gpu_selector: &str,
    java: &str,
) -> Result<Vec<String>, String> {
    let graphics_mode = normalize_graphics_mode(graphics_mode)?;
    let gpu_selector = validate_gpu_selector(gpu_selector)?;
    let mut command = Vec::new();
    command.push(java.to_string());
    command.push(format!("-Xmx{memory}G"));
    command.push(format!("-Djava.library.path={}", display_path(natives)));
    command.push("-Dminecraft.launcher.brand=GambleClientLauncher".to_string());
    command.push(format!("-Dminecraft.launcher.version={VERSION}"));
    command.push(format!("-Dgamble.antiScreenshare={anti_screenshare}"));
    command.push(format!(
        "-Dgamble.displayName={}",
        sanitize_display_name(client_display_name, "Gamble Client")
    ));
    command.push(format!("-Dgamble.graphics.mode={graphics_mode}"));
    command.push(format!("-Dgamble.graphics.gpu={gpu_selector}"));
    if profile_installs_client(profile_id) && !build.is_empty() {
        command.push(format!("-Dgamble.launchBuild={build}"));
        command.push(format!("-Dgamble.loader.build={build}"));
    }
    if matches!(
        profile_kind(profile_id),
        ProfileKind::Fabric | ProfileKind::Client
    ) && !profile.client_version_id.is_empty()
    {
        let jar = game_dir
            .join("versions")
            .join(&profile.client_version_id)
            .join(format!("{}.jar", profile.client_version_id));
        command.push(format!("-Dfabric.gameJarPath={}", display_path(&jar)));
    }
    for arg in &profile.jvm_arguments {
        if !is_launcher_managed_jvm_arg(arg) {
            command.push(replace_jvm_placeholders(
                arg, game_dir, classpath, natives, version_id,
            ));
        }
    }
    command.extend(split_args(extra_java_args)?);
    if matches!(
        profile_kind(profile_id),
        ProfileKind::Fabric | ProfileKind::Client
    ) {
        // Fabric accepts both properties from inherited version metadata and
        // arbitrary JVM args. Set the authoritative managed profile paths last
        // so a stale launcher/profile cannot redirect discovery to another
        // .minecraft folder.
        command.push(format!(
            "-Dfabric.modsFolder={}",
            display_path(&mods_folder(profile_id))
        ));
    }
    command.push("-cp".to_string());
    command.push(join_classpath(classpath));
    command.push(profile.main_class.clone());

    for arg in &profile.game_arguments {
        command.push(
            arg.replace("${auth_player_name}", &identity.name)
                .replace(
                    "${version_name}",
                    &format!("Gamble Client {MINECRAFT_VERSION}"),
                )
                .replace("${game_directory}", &display_path(game_dir))
                .replace("${assets_root}", &display_path(&game_dir.join("assets")))
                .replace("${assets_index_name}", &profile.asset_index_id)
                .replace("${auth_uuid}", &identity.uuid)
                .replace("${auth_access_token}", &identity.access_token)
                .replace("${clientid}", MICROSOFT_CLIENT_ID)
                .replace("${auth_xuid}", &identity.xuid)
                .replace("${user_type}", "msa")
                .replace("${version_type}", "release")
                .replace("${user_properties}", "{}")
                .replace("${profile_properties}", "{}")
                .replace("${quickPlayPath}", "")
                .replace("${quickPlaySingleplayer}", "")
                .replace("${quickPlayMultiplayer}", "")
                .replace("${quickPlayRealms}", "")
                .replace("${classpath}", &join_classpath(classpath))
                .replace("${natives_directory}", &display_path(natives))
                .replace("${launcher_name}", "GambleClientLauncher")
                .replace("${launcher_version}", VERSION),
        );
    }
    Ok(command)
}

fn sanitize_display_name(value: &str, fallback: &str) -> String {
    let mut skip_format_code = false;
    let cleaned = value
        .chars()
        .filter(|character| {
            if skip_format_code {
                skip_format_code = false;
                return false;
            }
            if *character == '§' {
                skip_format_code = true;
                return false;
            }
            !character.is_control()
        })
        .take(40)
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    if cleaned.is_empty() {
        fallback.to_string()
    } else {
        cleaned
    }
}

fn download_file(url: &str, path: &Path) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }
    let mut last_error = String::new();
    for attempt in 1..=HTTP_DOWNLOAD_ATTEMPTS {
        match download_file_once(url, path) {
            Ok(()) => return Ok(()),
            Err(error) => {
                last_error = error;
                if attempt < HTTP_DOWNLOAD_ATTEMPTS {
                    std::thread::sleep(Duration::from_millis(250 * attempt as u64));
                }
            }
        }
    }
    Err(download_failure_message(url, &last_error))
}

fn download_file_once(url: &str, path: &Path) -> Result<(), String> {
    let temp = path.with_extension(format!(
        "{}.part",
        path.extension()
            .and_then(|value| value.to_str())
            .unwrap_or("download")
    ));
    let _ = fs::remove_file(&temp);
    let parsed_url = trusted_network_url(url)?;
    let request_urls = if is_first_party_backend_url(&parsed_url) {
        first_party_request_urls(url)?
    } else {
        vec![parsed_url]
    };
    let client = trusted_download_http_client()?;
    let mut last_kind = "network request failed";
    let mut response = None;
    for request_url in request_urls {
        match client.get(request_url).send() {
            Ok(value) => {
                response = Some(value);
                break;
            }
            Err(error) => last_kind = network_error_kind(&error),
        }
    }
    let response = response
        .ok_or_else(|| format!("Could not reach the download service. ({last_kind})"))?
        .error_for_status()
        .map_err(error_text)?;
    if response
        .content_length()
        .is_some_and(|size| size > MAX_DOWNLOAD_BYTES)
    {
        return Err("Download exceeds the 512 MiB safety limit.".to_string());
    }
    let result = (|| {
        let mut output = File::create(&temp).map_err(error_text)?;
        let copied = io::copy(&mut response.take(MAX_DOWNLOAD_BYTES + 1), &mut output)
            .map_err(error_text)?;
        if copied > MAX_DOWNLOAD_BYTES {
            return Err("Download exceeds the 512 MiB safety limit.".to_string());
        }
        output.flush().map_err(error_text)?;
        drop(output);
        let backup = path.with_extension("download.previous");
        replace_path_with_rollback(&temp, path, &backup)
    })();
    if result.is_err() {
        let _ = fs::remove_file(&temp);
    }
    result
}

fn download_failure_message(url: &str, error: &str) -> String {
    if url.starts_with(ASSET_BASE_URL) {
        return format!(
            "Minecraft asset download failed from Mojang's CDN after {HTTP_DOWNLOAD_ATTEMPTS} attempts: {url} ({error})"
        );
    }
    if url.contains("launchermeta.mojang.com") {
        return format!(
            "Minecraft version metadata failed from Mojang after {HTTP_DOWNLOAD_ATTEMPTS} attempts: {url} ({error})"
        );
    }
    format!("Download failed after {HTTP_DOWNLOAD_ATTEMPTS} attempts: {url} ({error})")
}

fn unzip_natives(zip_path: &Path, target: &Path) -> Result<(), String> {
    let file = File::open(zip_path).map_err(error_text)?;
    let mut archive = ZipArchive::new(file).map_err(error_text)?;
    if archive.len() > MAX_NATIVE_FILES {
        return Err("Native archive contains too many files.".to_string());
    }
    let mut expanded = 0u64;
    for i in 0..archive.len() {
        let mut item = archive.by_index(i).map_err(error_text)?;
        expanded = expanded.saturating_add(item.size());
        if expanded > MAX_NATIVE_EXPANDED_BYTES {
            return Err("Native archive expands beyond the 512 MiB safety limit.".to_string());
        }
        let name = item.name().to_string();
        if item.is_dir() || name.starts_with("META-INF/") || name.contains("..") {
            continue;
        }
        let Some(file_name) = Path::new(&name).file_name() else {
            continue;
        };
        let out = target.join(file_name);
        let mut output = File::create(out).map_err(error_text)?;
        io::copy(&mut item, &mut output).map_err(error_text)?;
    }
    Ok(())
}

fn native_artifact(library: &Library) -> Option<(String, String)> {
    if library.natives.is_empty() {
        return None;
    }
    let classifier = library
        .natives
        .get(os_name())
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .replace("${arch}", if is_64_bit() { "64" } else { "32" });
    if classifier.is_empty() {
        return None;
    }
    let path = library
        .classifier_paths
        .get(&classifier)
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();
    let url = library
        .classifier_urls
        .get(&classifier)
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();
    if path.is_empty() {
        None
    } else {
        Some((path, url))
    }
}

fn rules_allow(rules: &[serde_json::Value]) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        if !rule_applies(rule) {
            continue;
        }
        let action = json_string(rule, "action");
        if action == "allow" {
            allowed = true;
        } else if action == "disallow" {
            allowed = false;
        }
    }
    allowed
}

fn rule_applies(rule: &serde_json::Value) -> bool {
    if let Some(os) = rule.get("os") {
        let name = json_string(os, "name");
        if !name.is_empty() && name != os_name() {
            return false;
        }
    }
    if let Some(features) = rule.get("features").and_then(|v| v.as_object()) {
        for value in features.values() {
            if value.as_bool().unwrap_or(false) {
                return false;
            }
        }
    }
    true
}

fn maven_artifact_path(name: &str) -> String {
    let (coordinate, extension) = name
        .split_once('@')
        .map(|(a, b)| (a, b))
        .unwrap_or((name, "jar"));
    let parts = coordinate.split(':').collect::<Vec<_>>();
    if parts.len() < 3 {
        return String::new();
    }
    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let classifier = if parts.len() >= 4 && !parts[3].is_empty() {
        format!("-{}", parts[3])
    } else {
        String::new()
    };
    format!("{group}/{artifact}/{version}/{artifact}-{version}{classifier}.{extension}")
}

fn maven_artifact_url(base: &str, artifact_path: &str) -> String {
    if base.trim().is_empty() || artifact_path.is_empty() {
        return String::new();
    }
    format!("{}/{}", base.trim_end_matches('/'), artifact_path)
}

fn is_launcher_managed_jvm_arg(arg: &str) -> bool {
    arg.trim().is_empty()
        || arg.starts_with("-Djava.library.path=")
        || arg.starts_with("-Dminecraft.launcher.brand=")
        || arg.starts_with("-Dminecraft.launcher.version=")
        || arg.starts_with("-Dgamble.")
        || arg.starts_with("-Dfabric.modsFolder=")
        || arg == "-DFabricMcEmu="
        || arg == "-cp"
        || arg == "-classpath"
        || arg.contains("${classpath}")
        || arg.contains("${natives_directory}")
        || arg == "net.minecraft.client.main.Main"
        || arg.ends_with(".KnotClient")
}

fn replace_jvm_placeholders(
    arg: &str,
    game_dir: &Path,
    classpath: &[PathBuf],
    natives: &Path,
    version_id: &str,
) -> String {
    arg.replace("${natives_directory}", &display_path(natives))
        .replace("${launcher_name}", "GambleClientLauncher")
        .replace("${launcher_version}", VERSION)
        .replace("${classpath}", &join_classpath(classpath))
        .replace("${game_directory}", &display_path(game_dir))
        .replace("${version_name}", version_id)
}

fn split_args(value: &str) -> Result<Vec<String>, String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut single = false;
    let mut double = false;
    let mut escaping = false;
    let mut chars = value.chars().peekable();
    while let Some(ch) = chars.next() {
        if escaping {
            current.push(ch);
            escaping = false;
            continue;
        }
        if ch == '\\' && !single {
            if chars.peek().is_some_and(|next| {
                next.is_whitespace() || *next == '\\' || *next == '"' || *next == '\''
            }) {
                escaping = true;
                continue;
            }
            current.push(ch);
            continue;
        }
        if ch == '\'' && !double {
            single = !single;
            continue;
        }
        if ch == '"' && !single {
            double = !double;
            continue;
        }
        if ch.is_whitespace() && !single && !double {
            if !current.is_empty() {
                args.push(std::mem::take(&mut current));
            }
            continue;
        }
        current.push(ch);
    }
    if single || double {
        return Err("Close the quote in JVM args before launching.".to_string());
    }
    if escaping {
        current.push('\\');
    }
    if !current.is_empty() {
        args.push(current);
    }
    for arg in &args {
        if arg == "net.minecraft.client.main.Main" || arg.ends_with(".KnotClient") {
            return Err(format!(
                "JVM Args should not include the Minecraft main class: {arg}"
            ));
        }
    }
    Ok(args)
}

fn join_classpath(classpath: &[PathBuf]) -> String {
    let separator = if env::consts::OS == "windows" {
        ";"
    } else {
        ":"
    };
    classpath
        .iter()
        .map(|path| display_path(path))
        .collect::<Vec<_>>()
        .join(separator)
}

fn ensure_java_runtime(app: Option<&AppHandle>) -> Result<String, String> {
    if let Some(candidate) = preferred_java_candidate(java_candidates()) {
        return Ok(display_path(&windowless_java(&candidate)));
    }

    if env::consts::OS != "windows" {
        return Err("Java 21+ was not found. The native launcher currently installs its managed runtime automatically on Windows.".to_string());
    }

    if let Some(app) = app {
        emit_launch_progress(
            app,
            "Runtime",
            "Installing the managed Java 21 runtime (first launch only)",
            1,
            1,
        );
    }

    let install_root = managed_root().join("runtime").join("temurin-21");
    let archive = managed_root().join("runtime").join("temurin-21-jre.zip");
    if install_root.exists() {
        fs::remove_dir_all(&install_root).map_err(error_text)?;
    }
    fs::create_dir_all(&install_root).map_err(error_text)?;

    let arch = match env::consts::ARCH {
        "aarch64" => "aarch64",
        _ => "x64",
    };
    let url = if arch == "x64" {
        TEMURIN_21_WINDOWS_URL.to_string()
    } else {
        format!("https://api.adoptium.net/v3/binary/latest/21/ga/windows/{arch}/jre/hotspot/normal/eclipse")
    };
    download_file(&url, &archive)
        .map_err(|error| format!("Managed Java 21 download failed: {error}"))?;
    let extracted = extract_runtime_zip(&archive, &install_root);
    let _ = fs::remove_file(&archive);
    extracted?;

    let candidate = find_java_under(&install_root).ok_or_else(|| {
        "Managed Java archive installed, but bin/java.exe was not found.".to_string()
    })?;
    if !java_candidate_is_compatible(&candidate) {
        return Err(format!(
            "Managed Java runtime failed its Java 21 check: {}",
            display_path(&candidate)
        ));
    }
    Ok(display_path(&windowless_java(&candidate)))
}

fn java_candidate_is_compatible(candidate: &Path) -> bool {
    java_version_output(candidate)
        .map(|output| output.status.success() && java_output_is_21_or_newer(&output))
        .unwrap_or(false)
}

fn java_version_output(candidate: &Path) -> io::Result<std::process::Output> {
    let mut command = Command::new(candidate);
    command.arg("-version");
    #[cfg(target_os = "windows")]
    command.creation_flags(CREATE_NO_WINDOW);
    command.output()
}

fn windowless_java(candidate: &Path) -> PathBuf {
    if env::consts::OS != "windows" {
        return candidate.to_path_buf();
    }
    let javaw = candidate.with_file_name("javaw.exe");
    if javaw.is_file() {
        javaw
    } else {
        candidate.to_path_buf()
    }
}

fn find_java_under(root: &Path) -> Option<PathBuf> {
    WalkDir::new(root)
        .max_depth(8)
        .follow_links(false)
        .into_iter()
        .filter_map(Result::ok)
        .find(|entry| {
            entry.file_type().is_file()
                && entry
                    .file_name()
                    .to_string_lossy()
                    .eq_ignore_ascii_case("java.exe")
        })
        .map(|entry| entry.into_path())
}

fn extract_runtime_zip(zip_path: &Path, target: &Path) -> Result<(), String> {
    let file = File::open(zip_path).map_err(error_text)?;
    let mut archive = ZipArchive::new(file).map_err(error_text)?;
    if archive.len() > 4096 {
        return Err("Managed Java archive contains too many files.".to_string());
    }

    let mut expanded = 0u64;
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).map_err(error_text)?;
        let relative = entry
            .enclosed_name()
            .ok_or_else(|| "Managed Java archive contains an unsafe path.".to_string())?
            .to_path_buf();
        let output = target.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(&output).map_err(error_text)?;
            continue;
        }

        expanded = expanded.saturating_add(entry.size());
        if expanded > MAX_NATIVE_EXPANDED_BYTES {
            return Err("Managed Java archive exceeds the expanded-size safety limit.".to_string());
        }
        if let Some(parent) = output.parent() {
            fs::create_dir_all(parent).map_err(error_text)?;
        }
        let mut destination = File::create(&output).map_err(error_text)?;
        io::copy(&mut entry, &mut destination).map_err(error_text)?;
    }
    Ok(())
}

fn java_executable() -> String {
    preferred_java_candidate(java_candidates())
        .map(|candidate| display_path(&candidate))
        .unwrap_or_else(|| "java".to_string())
}

fn preferred_java_candidate(candidates: Vec<PathBuf>) -> Option<PathBuf> {
    let mut compatible = candidates
        .into_iter()
        .filter_map(|candidate| {
            let output = java_version_output(&candidate).ok()?;
            if !output.status.success() {
                return None;
            }
            let feature = java_output_feature(&output);
            (feature >= 21).then_some((candidate, feature))
        })
        .collect::<Vec<_>>();
    // Minecraft 1.21.11 targets Java 21. Prefer that exact LTS runtime to avoid
    // unsupported LWJGL/JNI combinations, then use the nearest newer runtime.
    compatible.sort_by_key(|(_, feature)| (*feature != 21, *feature));
    compatible
        .into_iter()
        .next()
        .map(|(candidate, _)| candidate)
}

fn java_candidates() -> Vec<PathBuf> {
    let executable = if env::consts::OS == "windows" {
        "java.exe"
    } else {
        "java"
    };
    let mut candidates = Vec::new();
    let mut roots = Vec::new();

    if let Ok(home) = env::var("JAVA_HOME") {
        candidates.push(PathBuf::from(home).join("bin").join(executable));
    }

    if env::consts::OS == "windows" {
        if let Ok(app_data) = env::var("APPDATA") {
            roots.push(PathBuf::from(app_data).join(".minecraft/runtime"));
        }
        if let Ok(local) = env::var("LOCALAPPDATA") {
            let local = PathBuf::from(local);
            roots.push(local.join("Programs/Eclipse Adoptium"));
            roots.push(
                local.join(
                    "Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime",
                ),
            );
            roots.push(local.join("Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Roaming/.minecraft/runtime"));
        }
        for variable in ["ProgramFiles", "ProgramFiles(x86)"] {
            if let Ok(program_files) = env::var(variable) {
                let program_files = PathBuf::from(program_files);
                roots.push(program_files.join("Java"));
                roots.push(program_files.join("Eclipse Adoptium"));
                roots.push(program_files.join("Microsoft"));
                roots.push(program_files.join("Minecraft Launcher/runtime"));
            }
        }
        roots.push(managed_root().join("runtime"));
    } else if env::consts::OS == "macos" {
        roots.push(PathBuf::from("/Library/Java/JavaVirtualMachines"));
        roots.push(managed_root().join("runtime"));
    } else {
        roots.push(PathBuf::from("/usr/lib/jvm"));
        roots.push(PathBuf::from("/usr/java"));
        roots.push(PathBuf::from("/opt/java"));
        roots.push(managed_root().join("runtime"));
    }

    for root in roots {
        if !root.is_dir() {
            continue;
        }
        for entry in WalkDir::new(root)
            .max_depth(8)
            .follow_links(false)
            .into_iter()
            .filter_map(Result::ok)
        {
            if entry.file_type().is_file()
                && entry
                    .file_name()
                    .to_string_lossy()
                    .eq_ignore_ascii_case(executable)
            {
                candidates.push(entry.into_path());
            }
        }
    }

    candidates.push(PathBuf::from(executable));
    let mut unique = Vec::new();
    for candidate in candidates {
        if !unique
            .iter()
            .any(|existing: &PathBuf| existing == &candidate)
        {
            unique.push(candidate);
        }
    }
    unique
}

fn os_name() -> &'static str {
    match env::consts::OS {
        "windows" => "windows",
        "macos" => "osx",
        _ => "linux",
    }
}

fn is_64_bit() -> bool {
    env::consts::ARCH.contains("64")
}

fn redacted_command(command: &[String]) -> String {
    command
        .iter()
        .map(|arg| {
            if arg.len() > 60 && !arg.starts_with('-') {
                "<token-or-path>".to_string()
            } else {
                arg.clone()
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}

async fn run_blocking<T, F>(task: F) -> Result<T, String>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(task)
        .await
        .map_err(|error| error.to_string())?
}

fn emit_launch_progress(
    app: &AppHandle,
    phase: &str,
    message: impl Into<String>,
    current: u32,
    total: u32,
) {
    let total = total.max(1);
    let current = current.min(total);
    let percent = (((current as f64 / total as f64) * 100.0).round() as u8).min(100);
    let _ = app.emit(
        "launch-progress",
        LaunchProgressEvent {
            phase: phase.to_string(),
            message: message.into(),
            current,
            total,
            percent,
        },
    );
}

#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    let parsed = reqwest::Url::parse(&url).map_err(|_| "URL is invalid.".to_string())?;
    let allowed = [
        "gambleclient.org",
        "dash.gambleclient.org",
        "admin.gambleclient.org",
        "profile.gambleclient.org",
        "discord.gg",
        "login.microsoftonline.com",
        "www.microsoft.com",
        "microsoft.com",
    ];
    if parsed.scheme() != "https"
        || !parsed
            .host_str()
            .is_some_and(|host| allowed.contains(&host))
        || !parsed.username().is_empty()
        || parsed.password().is_some()
    {
        return Err("URL is not allowed.".to_string());
    }
    open_external(&url)
}

fn configure_launcher_webkit_environment() {
    #[cfg(target_os = "linux")]
    {
        let safe_mode = env::var("GAMBLE_WEBKIT_SAFE_MODE")
            .ok()
            .is_some_and(|value| webkit_safe_mode_enabled(&value));
        if safe_mode {
            // This is a launcher-UI-only fallback for systems where WebKit's
            // DMA-BUF renderer itself is unstable. Keep it opt-in: setting it
            // for every Linux launch makes the normal launcher UI needlessly
            // slow and does not protect the Minecraft Java process.
            env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
        } else {
            env::remove_var("WEBKIT_DISABLE_DMABUF_RENDERER");
        }
    }
}

fn webkit_safe_mode_enabled(value: &str) -> bool {
    matches!(
        value.trim().to_ascii_lowercase().as_str(),
        "1" | "true" | "yes" | "on"
    )
}

fn write_launcher_startup_diagnostics() {
    if fs::create_dir_all(launcher_data_folder()).is_err() {
        return;
    }
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs().to_string())
        .unwrap_or_else(|_| "unknown".to_string());
    let report = format!(
        "Gamble Client Launcher {VERSION}\nStartup timestamp: {timestamp}\nWebKit DMABUF safe mode: {}\n{}\n\n",
        safe_environment_value("WEBKIT_DISABLE_DMABUF_RENDERER"),
        graphics_environment_report()
    );
    if let Ok(mut file) = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(launcher_data_folder().join("launcher-startup.log"))
    {
        let _ = file.write_all(report.as_bytes());
    }
}

fn network_self_test() -> Result<(), String> {
    for (label, path) in [
        ("Backend health", "/api/health"),
        ("Launcher metadata", "/api/launcher/version"),
        ("Standalone-loader metadata", "/api/standalone/version"),
    ] {
        let url = format!("{SITE_URL}{path}");
        let response = send_first_party_request(&url, |client, target| client.get(target))?;
        let status = response.status();
        let _ = response.text().map_err(error_text)?;
        if !status.is_success() {
            return Err(format!("{label} returned HTTP {}.", status.as_u16()));
        }
        println!("OK {label} - HTTP {}", status.as_u16());
    }
    Ok(())
}

fn main() {
    if env::args()
        .skip(1)
        .any(|argument| argument == "--network-self-test")
    {
        match network_self_test() {
            Ok(()) => std::process::exit(0),
            Err(error) => {
                eprintln!("Network self-test failed: {error}");
                std::process::exit(1);
            }
        }
    }
    configure_launcher_webkit_environment();
    write_launcher_startup_diagnostics();
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            launcher_info,
            launcher_api,
            read_launcher_token,
            save_launcher_token,
            delete_launcher_token,
            read_microsoft_account,
            list_microsoft_accounts,
            select_microsoft_account,
            delete_microsoft_account,
            delete_microsoft_account_by_uuid,
            microsoft_browser_sign_in,
            cancel_microsoft_browser_sign_in,
            microsoft_device_start,
            microsoft_device_poll,
            ensure_profile,
            profile_loader_status,
            update_fabric_loader,
            delete_profile,
            list_local_files,
            toggle_local_file,
            add_resource_packs,
            add_mods,
            open_path,
            open_profile_folder,
            diagnostics,
            anti_screenshare_status,
            set_anti_screenshare,
            apply_anti_screenshare_clean_view,
            open_anti_screenshare_obs,
            client_install_status,
            download_launcher_update,
            install_client_manifest,
            launch_game,
            minecraft_status,
            open_url
        ])
        .build(tauri::generate_context!())
        .expect("error while building Gamble Client Launcher");
    app.run(|app_handle, event| {
        #[cfg(not(target_os = "windows"))]
        let _ = (&app_handle, &event);
        #[cfg(target_os = "windows")]
        if matches!(event, tauri::RunEvent::Ready) {
            if let Some(window) = app_handle.get_webview_window("main") {
                let app_url = tauri::Url::parse("https://tauri.localhost/index.html")
                    .expect("the embedded Windows app URL is valid");
                std::thread::spawn(move || {
                    for delay_ms in [0, 250, 1_000, 2_500] {
                        std::thread::sleep(Duration::from_millis(delay_ms));
                        if window.url().is_ok_and(|url| url.as_str() != "about:blank") {
                            break;
                        }
                        if let Err(error) = window.navigate(app_url.clone()) {
                            eprintln!("Windows app navigation retry failed: {error}");
                        }
                    }
                });
            }
        }
    });
}

fn profile_id(value: &str) -> String {
    let trimmed = value.trim();
    if trimmed == "vanilla" || trimmed == "fabric" || trimmed == "gamble-client" {
        return trimmed.to_string();
    }

    let mut sanitized = String::new();
    for ch in trimmed.chars() {
        if ch.is_ascii_alphanumeric() || ch == '-' || ch == '_' {
            sanitized.push(ch.to_ascii_lowercase());
        } else if !sanitized.ends_with('-') {
            sanitized.push('-');
        }
    }
    let sanitized = sanitized.trim_matches('-').to_string();
    if sanitized.is_empty() {
        "gamble-client".to_string()
    } else {
        sanitized
    }
}

fn profile_installs_client(profile: &str) -> bool {
    matches!(profile_kind(profile), ProfileKind::Client)
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum ProfileKind {
    Client,
    Fabric,
    Vanilla,
}

fn profile_kind(profile: &str) -> ProfileKind {
    if profile == "vanilla" || profile.starts_with("vanilla-") {
        ProfileKind::Vanilla
    } else if profile == "fabric" || profile.starts_with("fabric-") {
        ProfileKind::Fabric
    } else {
        ProfileKind::Client
    }
}

fn managed_root() -> PathBuf {
    if let Ok(value) = env::var("GAMBLE_CLIENT_GAME_DIR") {
        if !value.trim().is_empty() {
            return PathBuf::from(value.trim());
        }
    }
    app_data_folder().join("minecraft")
}

fn app_data_folder() -> PathBuf {
    let home = home_folder();
    match env::consts::OS {
        "windows" => env::var("APPDATA")
            .map(PathBuf::from)
            .unwrap_or_else(|_| home.clone())
            .join("Gamble Client"),
        "macos" => home.join("Library/Application Support/Gamble Client"),
        _ => env::var("XDG_DATA_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| home.join(".local/share"))
            .join("gamble-client"),
    }
}

fn home_folder() -> PathBuf {
    env::var("HOME")
        .or_else(|_| env::var("USERPROFILE"))
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("."))
}

fn downloads_folder() -> PathBuf {
    let downloads = home_folder().join("Downloads");
    if downloads.is_dir() {
        downloads
    } else {
        home_folder()
    }
}

fn launcher_data_folder() -> PathBuf {
    managed_root().join("cg-mod")
}

fn launcher_session_file() -> PathBuf {
    launcher_data_folder().join("launcher-session.txt")
}

fn microsoft_account_file() -> PathBuf {
    launcher_data_folder().join("microsoft-account.json")
}

fn microsoft_accounts_file() -> PathBuf {
    launcher_data_folder().join("microsoft-accounts.json")
}

fn selected_microsoft_account_file() -> PathBuf {
    launcher_data_folder().join("selected-microsoft-account.txt")
}

fn latest_launch_log_file() -> PathBuf {
    launcher_data_folder().join("latest-launch.log")
}

fn diagnostics_report_file() -> PathBuf {
    launcher_data_folder().join("diagnostics.txt")
}

fn write_launch_failure_log(profile: &str, error: &str) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    let _ = diagnostics(profile.to_string());
    let report = format!(
        "Gamble Client Launcher {VERSION}\nLaunch failed before Minecraft started.\nProfile: {}\nError: {}\nDiagnostics: {}\n",
        profile_id(profile),
        error,
        display_path(&diagnostics_report_file())
    );
    fs::write(latest_launch_log_file(), report).map_err(error_text)
}

fn minecraft_folder(profile: &str) -> PathBuf {
    managed_root().join("profiles").join(profile_id(profile))
}

fn mods_folder(profile: &str) -> PathBuf {
    minecraft_folder(profile).join("mods")
}

fn resource_packs_folder(profile: &str) -> PathBuf {
    minecraft_folder(profile).join("resourcepacks")
}

fn profile_data_folder(profile: &str) -> PathBuf {
    minecraft_folder(profile).join("cg-mod")
}

fn anti_screenshare_modules_file(profile: &str) -> PathBuf {
    profile_data_folder(profile).join("modules.txt")
}

fn payloads_folder(profile: &str) -> PathBuf {
    profile_data_folder(profile).join("payloads")
}

fn safe_file_name(value: &str) -> Result<&str, String> {
    let path = Path::new(value);
    if value.trim().is_empty()
        || value != value.trim()
        || value == "."
        || value == ".."
        || value.contains('/')
        || value.contains('\\')
        || value.chars().any(char::is_control)
        || path.is_absolute()
        || path.components().count() != 1
        || path.file_name().and_then(|name| name.to_str()) != Some(value)
    {
        return Err("Server manifest filename is unsafe.".to_string());
    }
    Ok(value)
}

fn is_sha256(value: &str) -> bool {
    value.len() == 64 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn ensure_profile_folders(profile: &str) -> Result<PathBuf, String> {
    let root = minecraft_folder(profile);
    fs::create_dir_all(&root).map_err(error_text)?;
    fs::create_dir_all(resource_packs_folder(profile)).map_err(error_text)?;
    fs::create_dir_all(profile_data_folder(profile)).map_err(error_text)?;
    if profile_kind(profile) != ProfileKind::Vanilla {
        fs::create_dir_all(mods_folder(profile)).map_err(error_text)?;
    }
    Ok(root)
}

fn toggle_target(path: &Path) -> Result<PathBuf, String> {
    let value = path.to_string_lossy();
    if value.ends_with(".disabled") {
        Ok(PathBuf::from(value.trim_end_matches(".disabled")))
    } else {
        Ok(PathBuf::from(format!("{value}.disabled")))
    }
}

fn copy_dir_all(source: &Path, target: &Path) -> io::Result<()> {
    if target.exists() {
        fs::remove_dir_all(target)?;
    }
    fs::create_dir_all(target)?;
    for entry in WalkDir::new(source) {
        let entry = entry?;
        let relative = entry.path().strip_prefix(source).unwrap();
        let destination = target.join(relative);
        if entry.file_type().is_dir() {
            fs::create_dir_all(&destination)?;
        } else {
            if let Some(parent) = destination.parent() {
                fs::create_dir_all(parent)?;
            }
            fs::copy(entry.path(), destination)?;
        }
    }
    Ok(())
}

fn set_resource_pack_enabled(profile: &str, file: &Path, enabled: bool) -> Result<(), String> {
    let options = minecraft_folder(profile).join("options.txt");
    let mut lines = if options.is_file() {
        fs::read_to_string(&options)
            .map_err(error_text)?
            .lines()
            .map(ToString::to_string)
            .collect::<Vec<_>>()
    } else {
        Vec::new()
    };
    let pack_name = file
        .file_name()
        .and_then(|v| v.to_str())
        .unwrap_or("")
        .trim_end_matches(".disabled")
        .to_string();
    let entry = format!("file/{pack_name}");
    let disabled = format!("file/{pack_name}.disabled");
    let mut found = false;
    for line in &mut lines {
        if !line.starts_with("resourcePacks:") {
            continue;
        }
        let mut packs = parse_pack_list(line.trim_start_matches("resourcePacks:"));
        packs.retain(|value| value != &entry && value != &disabled);
        if enabled {
            packs.push(entry.clone());
        }
        *line = format!("resourcePacks:{}", encode_pack_list(&packs));
        found = true;
        break;
    }
    if !found && enabled {
        lines.push(format!("resourcePacks:{}", encode_pack_list(&[entry])));
    }
    if !lines
        .iter()
        .any(|line| line.starts_with("incompatibleResourcePacks:"))
    {
        lines.push("incompatibleResourcePacks:[]".to_string());
    }
    fs::write(options, format!("{}\n", lines.join("\n"))).map_err(error_text)
}

fn apply_minecraft_option_defaults(profile: &str) -> Result<(), String> {
    let options = minecraft_folder(profile).join("options.txt");
    if let Some(parent) = options.parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }

    let mut lines = if options.is_file() {
        fs::read_to_string(&options)
            .map_err(error_text)?
            .lines()
            .map(ToString::to_string)
            .collect::<Vec<_>>()
    } else {
        Vec::new()
    };

    for (key, value) in [
        ("bobView", "false"),
        ("tutorialStep", "none"),
        ("narrator", "0"),
        ("narratorHotkey", "false"),
        ("onboardAccessibility", "false"),
    ] {
        upsert_option_line(&mut lines, key, value);
    }

    fs::write(options, format!("{}\n", lines.join("\n"))).map_err(error_text)
}

fn upsert_option_line(lines: &mut Vec<String>, key: &str, value: &str) {
    let prefix = format!("{key}:");
    for line in lines.iter_mut() {
        if line.starts_with(&prefix) {
            *line = format!("{prefix}{value}");
            return;
        }
    }
    lines.push(format!("{prefix}{value}"));
}

fn parse_pack_list(value: &str) -> Vec<String> {
    serde_json::from_str::<Vec<String>>(value).unwrap_or_default()
}

fn encode_pack_list(values: &[String]) -> String {
    serde_json::to_string(values).unwrap_or_else(|_| "[]".to_string())
}

fn cleanup_managed_mod_jars(profile: &str) -> Result<(), String> {
    let mods = mods_folder(profile);
    fs::create_dir_all(&mods).map_err(error_text)?;
    let backup = mods.join(".gamble-client-backups").join(timestamp());
    for entry in fs::read_dir(&mods).map_err(error_text)? {
        let entry = entry.map_err(error_text)?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_lowercase();
        let jar_like = name.ends_with(".jar") || name.ends_with(".jar.disabled");
        if jar_like && verify_fabric_mod_id(&path, MANAGED_CLIENT_MOD_ID).is_ok() {
            fs::create_dir_all(&backup).map_err(error_text)?;
            fs::rename(&path, backup.join(entry.file_name())).map_err(error_text)?;
        }
    }
    Ok(())
}

fn cleanup_payload_client_jars(profile: &str) -> Result<(), String> {
    let payloads = payloads_folder(profile);
    if !payloads.is_dir() {
        return Ok(());
    }

    for entry in fs::read_dir(&payloads).map_err(error_text)? {
        let entry = entry.map_err(error_text)?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_lowercase();
        if name.ends_with(".jar") && verify_fabric_mod_id(&path, MANAGED_CLIENT_MOD_ID).is_ok() {
            let _ = fs::remove_file(path);
        }
    }

    Ok(())
}

fn cleanup_stale_launch_payloads(profile: &str) -> Result<(), String> {
    let folder = profile_data_folder(profile).join("launch");
    if !folder.is_dir() {
        return Ok(());
    }

    for entry in fs::read_dir(&folder).map_err(error_text)? {
        let entry = entry.map_err(error_text)?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_lowercase();
        if (name.starts_with("payload-") && name.ends_with(".jar"))
            || (name.starts_with("ticket-") && name.ends_with(".txt"))
        {
            let _ = fs::remove_file(path);
        }
    }

    Ok(())
}

fn ensure_loader_jar(profile: &str, token: &str) -> Result<(), String> {
    let mods = mods_folder(profile);
    fs::create_dir_all(&mods).map_err(error_text)?;
    let loader = mods.join(LOADER_JAR_NAME);

    if token.trim().is_empty() {
        return Err("Sign in before installing the standalone memory loader.".to_string());
    }

    let staging = loader.with_extension("jar.part");
    let result = (|| {
        let body = json!({
            "fileName": LOADER_JAR_NAME,
            "displayName": "Gamble Client Launcher",
            "platform": match env::consts::OS {
                "windows" => "windows",
                "linux" => "linux",
                _ => "universal",
            },
            "launcherManaged": true
        });
        let loader_url = format!("{SITE_URL}/api/standalone/loader");
        let launcher_token = token.trim().to_string();
        let response = send_first_party_request(&loader_url, |client, target| {
            client.post(target).bearer_auth(&launcher_token).json(&body)
        })?;
        let status = response.status();
        if !status.is_success() {
            let message = response.text().unwrap_or_default();
            let parsed =
                serde_json::from_str::<serde_json::Value>(&message).unwrap_or_else(|_| json!({}));
            let detail = json_string(&parsed, "message");
            return Err(if detail.trim().is_empty() {
                format!(
                    "Standalone loader download returned HTTP {}.",
                    status.as_u16()
                )
            } else {
                detail
            });
        }
        let bytes = response.bytes().map_err(error_text)?;
        if bytes.is_empty() || bytes.len() as u64 > MAX_LOADER_BYTES {
            return Err("Standalone loader exceeds the 16 MiB safety limit.".to_string());
        }
        fs::write(&staging, &bytes).map_err(error_text)?;
        if !is_memory_loader_jar(&staging) {
            return Err("Server returned an obsolete standalone loader artifact.".to_string());
        }
        if !has_launcher_managed_enrollment(&staging) {
            return Err("Server returned a loader without fresh managed enrollment.".to_string());
        }
        restrict_private_file(&staging)?;
        quarantine_duplicate_loader_jars(&mods, &loader)?;
        replace_file_with_rollback(&staging, &loader)?;
        if !is_memory_loader_jar(&loader) || !has_launcher_managed_enrollment(&loader) {
            return Err("Installed loader failed its post-write verification.".to_string());
        }
        Ok(())
    })();
    if result.is_err() {
        let _ = fs::remove_file(&staging);
    }
    result
}

fn replace_file_with_rollback(staging: &Path, target: &Path) -> Result<(), String> {
    let backup = target.with_extension("jar.previous");
    replace_path_with_rollback(staging, target, &backup)
}

fn replace_path_with_rollback(staging: &Path, target: &Path, backup: &Path) -> Result<(), String> {
    let _ = fs::remove_file(&backup);
    let had_target = target.is_file();
    if had_target {
        fs::rename(target, backup).map_err(|error| {
            format!("Could not prepare the existing file for replacement: {error}")
        })?;
    }

    if let Err(error) = fs::rename(staging, target) {
        if had_target && backup.is_file() {
            let _ = fs::rename(backup, target);
        }
        return Err(format!("Could not install the replacement file: {error}"));
    }
    let _ = fs::remove_file(backup);
    Ok(())
}

fn quarantine_duplicate_loader_jars(mods: &Path, canonical: &Path) -> Result<usize, String> {
    if !mods.is_dir() {
        return Ok(0);
    }
    let mut duplicates = Vec::new();
    for entry in fs::read_dir(mods).map_err(error_text)? {
        let entry = entry.map_err(error_text)?;
        let path = entry.path();
        if path == canonical || !path.is_file() {
            continue;
        }
        let lower = entry.file_name().to_string_lossy().to_lowercase();
        if !lower.ends_with(".jar") {
            continue;
        }
        if verify_fabric_mod_id(&path, STANDALONE_LOADER_MOD_ID).is_ok() {
            duplicates.push((path, entry.file_name()));
        }
    }
    if duplicates.is_empty() {
        return Ok(0);
    }

    let backup = mods.join(".gamble-client-backups").join(format!(
        "duplicate-loaders-{}-{}",
        timestamp(),
        random_base64_url(8)
    ));
    fs::create_dir_all(&backup).map_err(error_text)?;
    for (path, name) in &duplicates {
        fs::rename(path, backup.join(name)).map_err(error_text)?;
    }
    Ok(duplicates.len())
}

fn has_launcher_managed_enrollment(path: &Path) -> bool {
    let file = match File::open(path) {
        Ok(file) => file,
        Err(_) => return false,
    };
    let mut archive = match ZipArchive::new(file) {
        Ok(archive) => archive,
        Err(_) => return false,
    };
    let mut entry = match archive.by_name("gcclient-standalone-enrollment.json") {
        Ok(entry) if entry.size() <= 16 * 1024 => entry,
        _ => return false,
    };
    let mut bytes = Vec::new();
    if entry.read_to_end(&mut bytes).is_err() {
        return false;
    }
    let value = match serde_json::from_slice::<serde_json::Value>(&bytes) {
        Ok(value) => value,
        Err(_) => return false,
    };
    let code = value
        .get("code")
        .and_then(|item| item.as_str())
        .unwrap_or("");
    let expires_at = value
        .get("expiresAt")
        .and_then(|item| item.as_u64())
        .unwrap_or(0);
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();
    value
        .get("launcherManaged")
        .and_then(|item| item.as_bool())
        .unwrap_or(false)
        && (32..=256).contains(&code.len())
        && code
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_' || byte == b'-')
        && expires_at > now
}

fn is_memory_loader_jar(path: &Path) -> bool {
    let bytes = match fs::read(path) {
        Ok(bytes) => bytes,
        Err(_) => return false,
    };
    verify_memory_loader_bytes(&bytes).is_ok()
}

fn verify_memory_loader_bytes(bytes: &[u8]) -> Result<(), String> {
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .map_err(|_| "Managed loader is not a valid jar archive.".to_string())?;
    for required in [
        "fabric.mod.json",
        "gcclient/loader/StandaloneLoader.class",
        "gcclient-memory-loader.txt",
        LOADER_PROVENANCE_ENTRY,
    ] {
        let count = (0..archive.len())
            .filter_map(|index| {
                archive
                    .by_index(index)
                    .ok()
                    .map(|entry| entry.name().to_string())
            })
            .filter(|name| name == required)
            .count();
        if count != 1 {
            return Err(format!(
                "Managed loader must contain exactly one {required}."
            ));
        }
    }

    let marker_text = {
        let mut marker = archive
            .by_name("gcclient-memory-loader.txt")
            .map_err(error_text)?;
        let mut text = String::new();
        (&mut marker)
            .take(64)
            .read_to_string(&mut text)
            .map_err(error_text)?;
        text
    };
    if marker_text.trim() != "verified-memory-only-v1" {
        return Err("Managed loader memory marker is invalid.".to_string());
    }

    let metadata = {
        let mut entry = archive.by_name("fabric.mod.json").map_err(error_text)?;
        if entry.size() > MAX_FABRIC_METADATA_BYTES {
            return Err("Managed loader metadata is too large.".to_string());
        }
        let mut data = Vec::new();
        entry.read_to_end(&mut data).map_err(error_text)?;
        serde_json::from_slice::<serde_json::Value>(&data).map_err(error_text)?
    };
    if !is_expected_loader_fabric_metadata(&metadata) {
        return Err("Managed loader has invalid Fabric metadata.".to_string());
    }
    let metadata_version = metadata
        .get("version")
        .and_then(|value| value.as_str())
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| "Managed loader has no readable version.".to_string())?;

    let provenance = {
        let mut entry = archive
            .by_name(LOADER_PROVENANCE_ENTRY)
            .map_err(error_text)?;
        if entry.size() > MAX_FABRIC_METADATA_BYTES {
            return Err("Managed loader provenance is too large.".to_string());
        }
        let mut data = Vec::new();
        entry.read_to_end(&mut data).map_err(error_text)?;
        serde_json::from_slice::<LoaderProvenance>(&data).map_err(error_text)?
    };
    verify_loader_provenance(bytes, &provenance, metadata_version)
}

fn is_expected_loader_fabric_metadata(metadata: &serde_json::Value) -> bool {
    let Some(root) = metadata.as_object() else {
        return false;
    };
    let expected_root = [
        "schemaVersion",
        "id",
        "version",
        "name",
        "icon",
        "environment",
        "entrypoints",
        "depends",
    ];
    if root.len() != expected_root.len() || expected_root.iter().any(|key| !root.contains_key(*key))
    {
        return false;
    }
    let Some(entrypoints) = metadata
        .get("entrypoints")
        .and_then(|value| value.as_object())
    else {
        return false;
    };
    let Some(depends) = metadata.get("depends").and_then(|value| value.as_object()) else {
        return false;
    };
    let pre_launch = entrypoints
        .get("preLaunch")
        .and_then(|value| value.as_array());
    let minecraft = depends.get("minecraft").and_then(|value| value.as_array());
    let name = metadata
        .get("name")
        .and_then(|value| value.as_str())
        .unwrap_or("");
    let version = metadata
        .get("version")
        .and_then(|value| value.as_str())
        .unwrap_or("");
    metadata
        .get("schemaVersion")
        .and_then(|value| value.as_u64())
        == Some(1)
        && metadata.get("id").and_then(|value| value.as_str())
            == Some("gamble-client-standalone-loader")
        && !version.trim().is_empty()
        && !name.trim().is_empty()
        && name.chars().count() <= 64
        && metadata.get("icon").and_then(|value| value.as_str()) == Some("assets/cg-mod/icon.png")
        && metadata.get("environment").and_then(|value| value.as_str()) == Some("client")
        && entrypoints.len() == 1
        && pre_launch.is_some_and(|items| {
            items.len() == 1 && items[0].as_str() == Some("gcclient.loader.StandaloneLoader")
        })
        && depends.len() == 3
        && depends.get("java").and_then(|value| value.as_str()) == Some(">=21")
        && minecraft.is_some_and(|items| items.len() == 1 && items[0].as_str() == Some("1.21.11"))
        && depends.get("fabricloader").and_then(|value| value.as_str()) == Some(">=0.18.2")
}

fn verify_loader_provenance(
    bytes: &[u8],
    provenance: &LoaderProvenance,
    metadata_version: &str,
) -> Result<(), String> {
    let expected_platform = match env::consts::OS {
        "windows" => "windows",
        "linux" => "linux",
        _ => "universal",
    };
    if provenance.schema_version != 1
        || provenance.loader_version != metadata_version
        || provenance.platform != expected_platform
        || !provenance
            .canonical_file_name
            .to_ascii_lowercase()
            .ends_with(".jar")
        || !is_sha256(&provenance.canonical_sha256)
        || !is_sha256(&provenance.core_sha256)
        || provenance.canonical_size == 0
        || provenance.source_commit.len() < 7
        || !provenance
            .source_commit
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit())
        || provenance.client_delivery != "verified-memory-only"
        || provenance.signature_algorithm != "Ed25519"
        || provenance.signature_key_id != LOADER_SIGNING_KEY_ID
    {
        return Err("Managed loader provenance claims are invalid.".to_string());
    }
    let actual_core = loader_core_sha256(bytes)?;
    if actual_core != provenance.core_sha256 {
        return Err("Managed loader immutable core was modified.".to_string());
    }
    let canonical = [
        "gc-standalone-loader-provenance-v1".to_string(),
        provenance.schema_version.to_string(),
        provenance.loader_version.clone(),
        provenance.platform.clone(),
        provenance.canonical_file_name.clone(),
        provenance.canonical_sha256.clone(),
        provenance.core_sha256.clone(),
        provenance.canonical_size.to_string(),
        provenance.source_commit.clone(),
        provenance.client_delivery.clone(),
        provenance.signature_algorithm.clone(),
        provenance.signature_key_id.clone(),
    ]
    .join("\n");
    let spki = STANDARD
        .decode(LOADER_SIGNING_PUBLIC_KEY)
        .map_err(|_| "Managed loader verification key is invalid.".to_string())?;
    let public_key = spki
        .get(spki.len().saturating_sub(32)..)
        .filter(|key| key.len() == 32)
        .ok_or_else(|| "Managed loader verification key is invalid.".to_string())?;
    let signature = URL_SAFE_NO_PAD
        .decode(&provenance.signature)
        .map_err(|_| "Managed loader provenance signature is invalid.".to_string())?;
    UnparsedPublicKey::new(&ED25519, public_key)
        .verify(canonical.as_bytes(), &signature)
        .map_err(|_| "Managed loader provenance signature is invalid.".to_string())
}

#[derive(Debug)]
struct LoaderCoreEntry {
    name: String,
    method: u16,
    compressed_size: u32,
    uncompressed_size: u32,
    crc32: u32,
    payload_offset: usize,
}

fn loader_core_sha256(bytes: &[u8]) -> Result<String, String> {
    if bytes.len() < 22 {
        return Err("Managed loader ZIP directory is missing.".to_string());
    }
    let search_start = bytes.len().saturating_sub(65_557);
    let eocd = (search_start..=bytes.len() - 22)
        .rev()
        .find(|offset| zip_u32(bytes, *offset).ok() == Some(0x0605_4b50))
        .ok_or_else(|| "Managed loader ZIP directory is missing.".to_string())?;
    let count = zip_u16(bytes, eocd + 10)? as usize;
    let central_size = zip_u32(bytes, eocd + 12)? as usize;
    let central_offset = zip_u32(bytes, eocd + 16)? as usize;
    let central_end = central_offset
        .checked_add(central_size)
        .filter(|end| *end <= bytes.len())
        .ok_or_else(|| "Managed loader ZIP directory is invalid.".to_string())?;
    let mut entries = Vec::new();
    let mut offset = central_offset;
    for _ in 0..count {
        if offset + 46 > central_end || zip_u32(bytes, offset)? != 0x0201_4b50 {
            return Err("Managed loader ZIP directory is corrupt.".to_string());
        }
        let flags = zip_u16(bytes, offset + 8)?;
        if flags & 1 != 0 {
            return Err("Encrypted managed loader entries are not supported.".to_string());
        }
        let method = zip_u16(bytes, offset + 10)?;
        let crc32 = zip_u32(bytes, offset + 16)?;
        let compressed_size = zip_u32(bytes, offset + 20)?;
        let uncompressed_size = zip_u32(bytes, offset + 24)?;
        let name_length = zip_u16(bytes, offset + 28)? as usize;
        let extra_length = zip_u16(bytes, offset + 30)? as usize;
        let comment_length = zip_u16(bytes, offset + 32)? as usize;
        let local_offset = zip_u32(bytes, offset + 42)? as usize;
        let name_start = offset + 46;
        let name_end = name_start
            .checked_add(name_length)
            .filter(|end| *end <= central_end)
            .ok_or_else(|| "Managed loader entry name is invalid.".to_string())?;
        let name = std::str::from_utf8(&bytes[name_start..name_end])
            .map_err(|_| "Managed loader entry name is not UTF-8.".to_string())?
            .to_string();
        offset = name_end
            .checked_add(extra_length)
            .and_then(|value| value.checked_add(comment_length))
            .filter(|end| *end <= central_end)
            .ok_or_else(|| "Managed loader ZIP directory is corrupt.".to_string())?;
        if LOADER_MUTABLE_ENTRIES.contains(&name.as_str()) {
            continue;
        }
        if local_offset + 30 > bytes.len() || zip_u32(bytes, local_offset)? != 0x0403_4b50 {
            return Err("Managed loader local ZIP entry is corrupt.".to_string());
        }
        let local_name_length = zip_u16(bytes, local_offset + 26)? as usize;
        let local_extra_length = zip_u16(bytes, local_offset + 28)? as usize;
        let payload_offset = local_offset
            .checked_add(30)
            .and_then(|value| value.checked_add(local_name_length))
            .and_then(|value| value.checked_add(local_extra_length))
            .ok_or_else(|| "Managed loader entry offset overflowed.".to_string())?;
        payload_offset
            .checked_add(compressed_size as usize)
            .filter(|end| *end <= bytes.len())
            .ok_or_else(|| "Managed loader entry payload is truncated.".to_string())?;
        entries.push(LoaderCoreEntry {
            name,
            method,
            compressed_size,
            uncompressed_size,
            crc32,
            payload_offset,
        });
    }
    if offset != central_end || entries.is_empty() {
        return Err("Managed loader immutable core is empty or malformed.".to_string());
    }
    entries.sort_by(|left, right| left.name.cmp(&right.name));
    let mut seen = HashSet::new();
    let mut digest = Sha256::new();
    digest.update(b"gc-loader-core-v1\0");
    for entry in entries {
        if !seen.insert(entry.name.clone()) {
            return Err("Managed loader contains duplicate immutable entries.".to_string());
        }
        let name = entry.name.as_bytes();
        digest.update((name.len() as u32).to_be_bytes());
        digest.update(entry.method.to_be_bytes());
        digest.update(entry.compressed_size.to_be_bytes());
        digest.update(entry.uncompressed_size.to_be_bytes());
        digest.update(entry.crc32.to_be_bytes());
        digest.update(name);
        digest.update(
            &bytes[entry.payload_offset..entry.payload_offset + entry.compressed_size as usize],
        );
    }
    Ok(format!("{:x}", digest.finalize()))
}

fn zip_u16(bytes: &[u8], offset: usize) -> Result<u16, String> {
    let value = bytes
        .get(offset..offset + 2)
        .ok_or_else(|| "Managed loader ZIP value is truncated.".to_string())?;
    Ok(u16::from_le_bytes([value[0], value[1]]))
}

fn zip_u32(bytes: &[u8], offset: usize) -> Result<u32, String> {
    let value = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| "Managed loader ZIP value is truncated.".to_string())?;
    Ok(u32::from_le_bytes([value[0], value[1], value[2], value[3]]))
}

fn memory_loader_version(path: &Path) -> Option<String> {
    let file = File::open(path).ok()?;
    let mut archive = ZipArchive::new(file).ok()?;
    let mut metadata = archive.by_name("fabric.mod.json").ok()?;
    if metadata.size() > MAX_FABRIC_METADATA_BYTES {
        return None;
    }
    let mut bytes = Vec::new();
    metadata.read_to_end(&mut bytes).ok()?;
    serde_json::from_slice::<serde_json::Value>(&bytes)
        .ok()?
        .get("version")?
        .as_str()
        .map(str::to_string)
        .filter(|version| !version.trim().is_empty())
}

fn current_memory_loader_is_current(path: &Path) -> Result<bool, String> {
    if !is_memory_loader_jar(path) {
        return Ok(false);
    }
    let installed = memory_loader_version(path)
        .ok_or_else(|| "Installed standalone loader has no readable version.".to_string())?;
    let latest = fetch_standalone_loader_version()?;
    Ok(compare_version_strings(&installed, &latest) != std::cmp::Ordering::Less)
}

fn ensure_fabric_api(profile: &str) -> Result<(), String> {
    ensure_fabric_api_with_progress(profile, None)
}

fn ensure_fabric_api_with_progress(profile: &str, app: Option<&AppHandle>) -> Result<(), String> {
    let mods = mods_folder(profile);
    fs::create_dir_all(&mods).map_err(error_text)?;
    if find_managed_mod_jar(&mods, "fabric-api-", false)?.is_some() {
        if let Some(app) = app {
            emit_launch_progress(app, "Fabric", "Fabric API is ready", 5, 12);
        }
        return Ok(());
    }

    if let Some(disabled) = find_managed_mod_jar(&mods, "fabric-api-", true)? {
        let target = PathBuf::from(disabled.to_string_lossy().trim_end_matches(".disabled"));
        if let Some(app) = app {
            emit_launch_progress(app, "Fabric", "Enabling Fabric API", 5, 12);
        }
        fs::rename(disabled, target).map_err(error_text)?;
        return Ok(());
    }

    if let Some(app) = app {
        emit_launch_progress(app, "Fabric", "Finding Fabric API download", 5, 12);
    }
    let release = fetch_modrinth_release(&modrinth_versions_url("fabric-api"))?;
    if release.file_name.trim().is_empty() || release.url.trim().is_empty() {
        return Err(format!(
            "Could not find Fabric API for Minecraft {MINECRAFT_VERSION}."
        ));
    }

    let target = mods.join(release.file_name);
    if let Some(app) = app {
        emit_launch_progress(app, "Fabric", "Downloading Fabric API", 5, 12);
    }
    download_file(&release.url, &target)
}

fn fetch_modrinth_release(url: &str) -> Result<ModrinthRelease, String> {
    let versions = http_client()?
        .get(url)
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json::<Vec<serde_json::Value>>()
        .map_err(error_text)?;
    let version = versions.first().cloned().unwrap_or_else(|| json!({}));
    let files = version
        .get("files")
        .and_then(|value| value.as_array())
        .cloned()
        .unwrap_or_default();
    let selected = files
        .iter()
        .find(|file| {
            json_bool_value(
                file.get("primary")
                    .unwrap_or(&serde_json::Value::Bool(false)),
            )
        })
        .or_else(|| files.first())
        .cloned()
        .unwrap_or_else(|| json!({}));
    Ok(ModrinthRelease {
        file_name: json_string(&selected, "filename"),
        url: json_string(&selected, "url"),
    })
}

fn find_managed_mod_jar(
    mods: &Path,
    prefix: &str,
    include_disabled: bool,
) -> Result<Option<PathBuf>, String> {
    if !mods.is_dir() {
        return Ok(None);
    }
    for entry in fs::read_dir(mods).map_err(error_text)? {
        let entry = entry.map_err(error_text)?;
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let lower = entry.file_name().to_string_lossy().to_lowercase();
        if lower.starts_with(prefix)
            && (lower.ends_with(".jar") || (include_disabled && lower.ends_with(".jar.disabled")))
        {
            return Ok(Some(path));
        }
    }
    Ok(None)
}

fn is_required_mod_for_profile(profile: &str, lower_name: &str) -> bool {
    let base = lower_name.trim_end_matches(".disabled");
    let kind = profile_kind(profile);
    (kind == ProfileKind::Client && base == LOADER_JAR_NAME)
        || (kind != ProfileKind::Vanilla
            && (base == "fabric-api.jar" || base.starts_with("fabric-api-")))
}

fn modrinth_versions_url(slug: &str) -> String {
    format!(
        "https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{MINECRAFT_VERSION}%22%5D"
    )
}

fn write_install_marker(
    profile: &str,
    build: &str,
    manifest: &ManifestResponse,
    installed: &Path,
) -> Result<(), String> {
    let folder = profile_data_folder(profile);
    fs::create_dir_all(&folder).map_err(error_text)?;
    fs::write(
        folder.join("installed-build.txt"),
        format!("{build}\n{}\n", manifest.file_name),
    )
    .map_err(error_text)?;
    let marker = json!({
        "schema": 1,
        "build": build,
        "fileName": manifest.file_name,
        "buildVersion": manifest.build_version,
        "sha256": manifest.sha256,
        "size": manifest.size,
        "installedAt": timestamp(),
        "path": display_path(installed)
    });
    fs::write(
        folder.join("loader-manifest.json"),
        serde_json::to_string_pretty(&marker).map_err(error_text)? + "\n",
    )
    .map_err(error_text)
}

fn selected_microsoft_account() -> Result<Option<MicrosoftAccount>, String> {
    let accounts = read_microsoft_account_list()?;
    if accounts.is_empty() {
        return Ok(None);
    }
    if let Some(uuid) = selected_microsoft_uuid() {
        if let Some(account) = accounts
            .iter()
            .find(|account| account.uuid.eq_ignore_ascii_case(&uuid))
        {
            return Ok(Some(account.clone()));
        }
    }
    Ok(accounts.first().cloned())
}

fn microsoft_account_for_launch(requested_uuid: &str) -> Result<Option<MicrosoftAccount>, String> {
    let requested_uuid = requested_uuid.trim().replace('-', "");
    if requested_uuid.is_empty() {
        return selected_microsoft_account();
    }

    read_microsoft_account_list()?
        .into_iter()
        .find(|account| account.uuid.eq_ignore_ascii_case(&requested_uuid))
        .map(Some)
        .ok_or_else(|| {
            "That profile's Microsoft account is no longer saved in this launcher.".to_string()
        })
}

fn read_microsoft_account_list() -> Result<Vec<MicrosoftAccount>, String> {
    let mut accounts = Vec::new();
    let accounts_path = microsoft_accounts_file();
    if accounts_path.is_file() {
        let text = fs::read_to_string(&accounts_path).map_err(error_text)?;
        let parsed = serde_json::from_str::<serde_json::Value>(&text).map_err(error_text)?;
        if let Some(items) = parsed.as_array() {
            for item in items {
                if let Ok(account) = serde_json::from_value::<MicrosoftAccount>(item.clone()) {
                    upsert_microsoft_account(&mut accounts, account);
                }
            }
        } else if let Some(items) = parsed.get("accounts").and_then(|value| value.as_array()) {
            for item in items {
                if let Ok(account) = serde_json::from_value::<MicrosoftAccount>(item.clone()) {
                    upsert_microsoft_account(&mut accounts, account);
                }
            }
        }
    }

    let legacy_path = microsoft_account_file();
    if legacy_path.is_file() {
        let text = fs::read_to_string(&legacy_path).map_err(error_text)?;
        if let Ok(account) = serde_json::from_str::<MicrosoftAccount>(&text) {
            upsert_microsoft_account(&mut accounts, account);
        }
    }

    accounts.retain(|account| {
        !account.refresh_token.trim().is_empty() && !account.uuid.trim().is_empty()
    });
    Ok(accounts)
}

fn upsert_microsoft_account(accounts: &mut Vec<MicrosoftAccount>, mut account: MicrosoftAccount) {
    account.uuid = account.uuid.replace('-', "");
    if account.refresh_token.trim().is_empty() || account.uuid.trim().is_empty() {
        return;
    }
    if let Some(existing) = accounts
        .iter_mut()
        .find(|existing| existing.uuid.eq_ignore_ascii_case(&account.uuid))
    {
        *existing = account;
    } else {
        accounts.push(account);
    }
}

fn write_microsoft_account_list(accounts: &[MicrosoftAccount]) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    write_private_file(
        &microsoft_accounts_file(),
        (serde_json::to_string_pretty(accounts).map_err(error_text)? + "\n").as_bytes(),
    )
}

fn selected_microsoft_uuid() -> Option<String> {
    read_trimmed(&selected_microsoft_account_file())
        .ok()
        .filter(|value| !value.trim().is_empty())
}

fn save_selected_microsoft_uuid(uuid: &str) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    write_private_file(
        &selected_microsoft_account_file(),
        format!("{}\n", uuid.trim().replace('-', "")).as_bytes(),
    )
}

fn save_legacy_microsoft_account(account: &MicrosoftAccount) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    write_private_file(
        &microsoft_account_file(),
        (serde_json::to_string_pretty(account).map_err(error_text)? + "\n").as_bytes(),
    )
}

fn save_microsoft_account(account: &MicrosoftAccount) -> Result<(), String> {
    let mut accounts = read_microsoft_account_list().unwrap_or_default();
    upsert_microsoft_account(&mut accounts, account.clone());
    write_microsoft_account_list(&accounts)?;
    save_selected_microsoft_uuid(&account.uuid)?;
    save_legacy_microsoft_account(account)
}

fn parse_microsoft_token(body: &serde_json::Value) -> Result<MicrosoftToken, String> {
    let access_token = json_string(body, "access_token");
    if access_token.trim().is_empty() {
        return Err("Microsoft did not return an access token.".to_string());
    }
    Ok(MicrosoftToken {
        access_token,
        refresh_token: json_string(body, "refresh_token"),
    })
}

fn exchange_microsoft_for_minecraft(
    microsoft_access_token: &str,
) -> Result<MinecraftProfile, String> {
    let xbox = request_xbox_token(microsoft_access_token)?;
    let xsts = request_xsts_token(&xbox.token)?;
    let minecraft = request_minecraft_token(&xsts.user_hash, &xsts.token)?;
    let mut profile = request_minecraft_profile(&minecraft.access_token)?;
    profile.xuid = if xsts.xuid.trim().is_empty() {
        xbox.xuid
    } else {
        xsts.xuid
    };
    profile.access_token = minecraft.access_token;
    profile.expires_at = unix_millis() + minecraft.expires_in_seconds.max(300) * 1000;
    Ok(profile)
}

fn request_xbox_token(microsoft_access_token: &str) -> Result<XboxToken, String> {
    let body = json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={microsoft_access_token}")
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT"
    });
    parse_xbox_token(post_json(XBOX_AUTH_URL, &body, "")?, "Xbox Live")
}

fn request_xsts_token(xbox_token: &str) -> Result<XboxToken, String> {
    let body = json!({
        "Properties": {
            "SandboxId": "RETAIL",
            "UserTokens": [xbox_token]
        },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });
    parse_xbox_token(post_json(XSTS_AUTH_URL, &body, "")?, "Xbox XSTS")
}

fn parse_xbox_token(body: serde_json::Value, label: &str) -> Result<XboxToken, String> {
    let token = json_string(&body, "Token");
    let first_xui = body
        .get("DisplayClaims")
        .and_then(|claims| claims.get("xui"))
        .and_then(|xui| xui.as_array())
        .and_then(|items| items.first())
        .cloned()
        .unwrap_or_else(|| json!({}));
    let user_hash = json_string(&first_xui, "uhs");
    let xuid = json_string(&first_xui, "xid");
    if token.trim().is_empty() || user_hash.trim().is_empty() {
        return Err(format!("{label} did not return a usable token."));
    }
    Ok(XboxToken {
        token,
        user_hash,
        xuid,
    })
}

fn request_minecraft_token(user_hash: &str, xsts_token: &str) -> Result<MinecraftToken, String> {
    let body = json!({
        "xtoken": format!("XBL3.0 x={user_hash};{xsts_token}"),
        "platform": "PC_LAUNCHER"
    });
    let response = post_json(MINECRAFT_LOGIN_URL, &body, "")?;
    let access_token = json_string(&response, "access_token");
    if access_token.trim().is_empty() {
        return Err("Minecraft did not return an access token.".to_string());
    }
    Ok(MinecraftToken {
        access_token,
        expires_in_seconds: json_u64(&response, "expires_in").max(3600),
    })
}

fn request_minecraft_profile(minecraft_access_token: &str) -> Result<MinecraftProfile, String> {
    let response = http_client()?
        .get(MINECRAFT_PROFILE_URL)
        .bearer_auth(minecraft_access_token)
        .send()
        .map_err(error_text)?;
    let status = response.status();
    let body = response.json::<serde_json::Value>().map_err(error_text)?;
    if !status.is_success() {
        let message = json_string(&body, "message");
        return Err(if message.trim().is_empty() {
            format!("Minecraft profile returned HTTP {}", status.as_u16())
        } else {
            message
        });
    }
    let uuid = json_string(&body, "id").replace('-', "");
    let name = json_string(&body, "name");
    if uuid.trim().is_empty() || name.trim().is_empty() {
        return Err("This Microsoft account does not have a Minecraft Java profile.".to_string());
    }
    Ok(MinecraftProfile {
        uuid,
        name,
        xuid: String::new(),
        access_token: String::new(),
        expires_at: 0,
    })
}

fn send_first_party_request<F>(
    raw_url: &str,
    build_request: F,
) -> Result<reqwest::blocking::Response, String>
where
    F: Fn(&reqwest::blocking::Client, reqwest::Url) -> reqwest::blocking::RequestBuilder,
{
    let urls = first_party_request_urls(raw_url)?;
    let client = http_client()?;
    let mut last_kind = "network request failed";

    for (origin_index, url) in urls.iter().enumerate() {
        for attempt in 0..HTTP_API_ATTEMPTS {
            match build_request(&client, url.clone()).send() {
                Ok(response) => return Ok(response),
                Err(error) => {
                    last_kind = network_error_kind(&error);
                    if attempt + 1 < HTTP_API_ATTEMPTS {
                        let delay = HTTP_RETRY_DELAY_MILLIS.saturating_mul((attempt + 1) as u64);
                        std::thread::sleep(Duration::from_millis(delay));
                    }
                }
            }
        }

        if origin_index + 1 < urls.len() {
            std::thread::sleep(Duration::from_millis(HTTP_RETRY_DELAY_MILLIS));
        }
    }

    Err(format!(
        "Could not reach the Gamble Client backend. Check your internet connection, VPN/firewall, and system clock, then try again. ({last_kind})"
    ))
}

fn first_party_request_urls(raw_url: &str) -> Result<Vec<reqwest::Url>, String> {
    let parsed = reqwest::Url::parse(raw_url.trim())
        .map_err(|_| "Backend request URL is invalid.".to_string())?;
    if !is_first_party_backend_url(&parsed) {
        return Err("Backend request URL is not a trusted Gamble Client origin.".to_string());
    }

    let mut urls = vec![parsed.clone()];
    let alternate_host = if parsed.host_str() == Some("gambleclient.org") {
        "dash.gambleclient.org"
    } else {
        "gambleclient.org"
    };
    let mut alternate = parsed;
    alternate
        .set_host(Some(alternate_host))
        .map_err(|_| "Backend request URL is invalid.".to_string())?;
    urls.push(alternate);
    Ok(urls)
}

fn is_first_party_backend_url(url: &reqwest::Url) -> bool {
    url.scheme() == "https"
        && url.port().is_none()
        && url.username().is_empty()
        && url.password().is_none()
        && matches!(
            url.host_str(),
            Some("gambleclient.org" | "dash.gambleclient.org")
        )
}

fn network_error_kind(error: &reqwest::Error) -> &'static str {
    if error.is_timeout() {
        "request timed out"
    } else if error.is_connect() {
        "connection or TLS handshake failed"
    } else if error.is_request() {
        "request could not be created"
    } else {
        "network request failed"
    }
}

fn post_json(
    url: &str,
    body: &serde_json::Value,
    bearer_token: &str,
) -> Result<serde_json::Value, String> {
    let parsed_url = reqwest::Url::parse(url.trim())
        .map_err(|_| "Network request URL is invalid.".to_string())?;
    let bearer = bearer_token.trim().to_string();
    let response = if is_first_party_backend_url(&parsed_url) {
        send_first_party_request(url, |client, target| {
            let mut request = client.post(target).json(body);
            if !bearer.is_empty() {
                request = request.bearer_auth(&bearer);
            }
            request
        })?
    } else {
        let client = http_client()?;
        let mut request = client.post(parsed_url).json(body);
        if !bearer.is_empty() {
            request = request.bearer_auth(&bearer);
        }
        request.send().map_err(|error| {
            format!(
                "Could not reach the remote authentication service. ({})",
                network_error_kind(&error)
            )
        })?
    };
    let status = response.status();
    let text = response.text().map_err(error_text)?;
    let body = if text.trim().is_empty() {
        json!({})
    } else {
        serde_json::from_str::<serde_json::Value>(&text)
            .unwrap_or_else(|_| json!({ "message": text }))
    };
    if !status.is_success() {
        let message = json_string(&body, "message");
        return Err(post_json_error_message(url, status.as_u16(), &message));
    }
    Ok(body)
}

fn post_json_error_message(url: &str, status: u16, message: &str) -> String {
    let service = service_label_for_url(url);
    let clean = message.trim();
    if status == 429 {
        if clean.is_empty() {
            return format!(
                "{service} rate limited this request. Try again in a minute. (HTTP 429)"
            );
        }
        return format!("{clean} (HTTP 429)");
    }
    if clean.is_empty() {
        format!("{service} returned HTTP {status}")
    } else {
        clean.to_string()
    }
}

fn service_label_for_url(url: &str) -> &'static str {
    let lower = url.to_lowercase();
    if lower.contains("gambleclient.org") {
        "Backend"
    } else if lower.contains("minecraftservices.com") {
        "Minecraft auth"
    } else if lower.contains("xboxlive.com") {
        "Xbox auth"
    } else if lower.contains("login.microsoftonline.com") {
        "Microsoft auth"
    } else {
        "Service"
    }
}

fn fetch_client_manifest(build: &str, token: &str) -> Result<ManifestResponse, String> {
    let body = post_json(
        &format!("{SITE_URL}/api/launcher/manifest"),
        &json!({ "build": build }),
        token,
    )?;
    let manifest = serde_json::from_value::<ManifestResponse>(body).map_err(error_text)?;
    if canonical_build_id(&manifest.build) != canonical_build_id(build) {
        return Err("Backend manifest was issued for a different client tier.".to_string());
    }
    safe_file_name(&manifest.file_name)?;
    if manifest.size == 0 || !is_sha256(&manifest.sha256) {
        return Err(
            "Backend manifest is missing required size or SHA-256 integrity metadata.".to_string(),
        );
    }
    if manifest.size > MAX_MANAGED_CLIENT_BYTES {
        return Err("Managed client artifact exceeds the 64 MiB safety limit.".to_string());
    }
    Ok(manifest)
}

fn fetch_launcher_version_info() -> Result<LauncherVersionResponse, String> {
    let url = format!("{SITE_URL}/api/launcher/version");
    let response = send_first_party_request(&url, |client, target| client.get(target))?;
    if !response.status().is_success() {
        return Err(format!(
            "Gamble Client launcher update metadata returned HTTP {}.",
            response.status().as_u16()
        ));
    }
    response
        .json::<LauncherVersionResponse>()
        .map_err(error_text)
}

fn fetch_standalone_loader_version() -> Result<String, String> {
    let url = format!("{SITE_URL}/api/standalone/version");
    let response = send_first_party_request(&url, |client, target| client.get(target))?;
    if !response.status().is_success() {
        return Err(format!(
            "Gamble Client standalone-loader metadata returned HTTP {}.",
            response.status().as_u16()
        ));
    }
    let response = response
        .json::<StandaloneLoaderVersionResponse>()
        .map_err(error_text)?;
    if response.version.trim().is_empty() {
        return Err("Backend did not return a standalone loader version.".to_string());
    }
    Ok(response.version)
}

fn preferred_launcher_download(info: &LauncherVersionResponse) -> LauncherDownload {
    let platform = match env::consts::OS {
        "windows" => usable_launcher_download(info.downloads.windows.clone()),
        "linux" => match linux_package_preference() {
            "rpm" => usable_launcher_download(info.downloads.linux_rpm.clone()),
            "deb" => usable_launcher_download(info.downloads.linux_deb.clone()),
            _ => None,
        },
        _ => None,
    };
    platform
        .or_else(|| usable_launcher_download(info.downloads.jar.clone()))
        .unwrap_or_else(|| LauncherDownload {
            file_name: info.file_name.clone(),
            download_url: info.download_url.clone(),
            sha256: String::new(),
            size: 0,
        })
}

fn usable_launcher_download(download: Option<LauncherDownload>) -> Option<LauncherDownload> {
    download
        .filter(|item| !item.file_name.trim().is_empty() && !item.download_url.trim().is_empty())
}

fn linux_package_preference() -> &'static str {
    let text = fs::read_to_string("/etc/os-release")
        .unwrap_or_default()
        .to_lowercase();
    if text.contains("fedora")
        || text.contains("rhel")
        || text.contains("centos")
        || text.contains("suse")
    {
        "rpm"
    } else if text.contains("debian")
        || text.contains("ubuntu")
        || text.contains("linuxmint")
        || text.contains("pop")
    {
        "deb"
    } else {
        "jar"
    }
}

fn verify_file(path: &Path, expected_size: u64, expected_sha: &str) -> Result<(), String> {
    if expected_size == 0 || !is_sha256(expected_sha) {
        return Err("Required size or SHA-256 integrity metadata is missing.".to_string());
    }
    let metadata = path.metadata().map_err(error_text)?;
    if metadata.len() != expected_size {
        return Err(format!(
            "Expected {expected_size} bytes but found {} bytes.",
            metadata.len()
        ));
    }
    let mut file = File::open(path).map_err(error_text)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer).map_err(error_text)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    let actual = format!("{:x}", hasher.finalize());
    if !actual.eq_ignore_ascii_case(expected_sha.trim()) {
        return Err(format!(
            "Expected SHA-256 {} but found {}.",
            expected_sha, actual
        ));
    }
    Ok(())
}

fn verify_fabric_mod_id(path: &Path, expected_id: &str) -> Result<(), String> {
    verify_fabric_mod_identity(path, expected_id, None)
}

fn verify_fabric_mod_identity(
    path: &Path,
    expected_id: &str,
    expected_build: Option<&str>,
) -> Result<(), String> {
    let file = File::open(path).map_err(error_text)?;
    let mut archive = ZipArchive::new(file)
        .map_err(|_| "Managed client is not a valid jar archive.".to_string())?;
    let mut metadata_entries = 0;
    for index in 0..archive.len() {
        let entry = archive.by_index(index).map_err(error_text)?;
        if entry.name() == "fabric.mod.json" {
            metadata_entries += 1;
        }
    }
    if metadata_entries != 1 {
        return Err(
            "Managed client must contain exactly one top-level fabric.mod.json.".to_string(),
        );
    }
    let mut metadata = archive
        .by_name("fabric.mod.json")
        .map_err(|_| "Managed client is missing top-level fabric.mod.json.".to_string())?;
    if metadata.size() > MAX_FABRIC_METADATA_BYTES {
        return Err("Managed client fabric.mod.json exceeds the 1 MiB safety limit.".to_string());
    }
    let mut contents = Vec::new();
    (&mut metadata)
        .take(MAX_FABRIC_METADATA_BYTES + 1)
        .read_to_end(&mut contents)
        .map_err(error_text)?;
    if contents.len() as u64 > MAX_FABRIC_METADATA_BYTES {
        return Err("Managed client fabric.mod.json exceeds the 1 MiB safety limit.".to_string());
    }
    let identity: FabricModIdentity = serde_json::from_slice(&contents)
        .map_err(|error| format!("Managed client fabric.mod.json is invalid: {error}"))?;
    if identity.id != expected_id {
        return Err(format!("Managed client mod id must be {expected_id}."));
    }
    if let Some(expected_build) = expected_build.filter(|value| !value.trim().is_empty()) {
        let variant = identity
            .custom
            .get("cg-mod:build_variant")
            .and_then(|value| value.as_str())
            .unwrap_or("");
        if canonical_build_id(variant) != canonical_build_id(expected_build) {
            return Err(
                "Managed client build variant does not match the requested tier.".to_string(),
            );
        }
    }
    Ok(())
}

fn canonical_build_id(value: &str) -> String {
    match value.trim().to_lowercase().replace('-', "_").as_str() {
        "beta" | "beta_plus_plus" => "beta_plus".to_string(),
        "ad" => "ad_tier".to_string(),
        normalized => normalized.to_string(),
    }
}

fn display_version(manifest: &ManifestResponse) -> String {
    if !manifest.build_version.trim().is_empty() {
        return public_client_version(&manifest.build_version);
    }
    manifest.file_name.clone()
}

fn public_client_version(value: &str) -> String {
    let text = value.trim();
    let parts: Vec<&str> = text.split('.').collect();
    if parts.len() >= 3
        && parts[0] == "1"
        && parts[1] == "0"
        && parts[2].chars().all(|c| c.is_ascii_digit())
    {
        return format!("1.{}", parts[2]);
    }
    text.to_string()
}

fn http_client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent(format!("GambleClientLauncher/{VERSION}"))
        .connect_timeout(Duration::from_secs(HTTP_CONNECT_TIMEOUT_SECONDS))
        .timeout(Duration::from_secs(HTTP_REQUEST_TIMEOUT_SECONDS))
        // API calls include bearer tokens and must never move them to a redirect target.
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(error_text)
}

fn trusted_download_http_client() -> Result<reqwest::blocking::Client, String> {
    let redirect_policy = reqwest::redirect::Policy::custom(|attempt| {
        if attempt.previous().len() >= MAX_DOWNLOAD_REDIRECTS {
            return attempt.stop();
        }
        if is_trusted_network_url(attempt.url()) {
            attempt.follow()
        } else {
            attempt.stop()
        }
    });
    reqwest::blocking::Client::builder()
        .user_agent(format!("GambleClientLauncher/{VERSION}"))
        .connect_timeout(Duration::from_secs(HTTP_CONNECT_TIMEOUT_SECONDS))
        .timeout(Duration::from_secs(HTTP_REQUEST_TIMEOUT_SECONDS))
        .redirect(redirect_policy)
        .build()
        .map_err(error_text)
}

fn trusted_network_url(raw_url: &str) -> Result<reqwest::Url, String> {
    let raw_url = raw_url.trim();
    if raw_url_has_explicit_port(raw_url) {
        return Err("Download URL must not specify a port.".to_string());
    }
    let parsed =
        reqwest::Url::parse(raw_url).map_err(|_| "Download URL is invalid.".to_string())?;
    if !is_trusted_network_url(&parsed) {
        return Err(format!(
            "Download origin is not trusted: {}",
            parsed.host_str().unwrap_or("unknown")
        ));
    }
    Ok(parsed)
}

fn raw_url_has_explicit_port(raw_url: &str) -> bool {
    let Some((_, authority_and_path)) = raw_url.split_once("://") else {
        return false;
    };
    let authority = authority_and_path
        .split(['/', '?', '#'])
        .next()
        .unwrap_or("");
    let host_port = authority
        .rsplit_once('@')
        .map(|(_, host_port)| host_port)
        .unwrap_or(authority);

    if let Some(end) = host_port.find(']') {
        return host_port[end + 1..].starts_with(':');
    }
    host_port.contains(':')
}

fn is_trusted_network_url(url: &reqwest::Url) -> bool {
    url.scheme() == "https"
        && url.port().is_none()
        && url.username().is_empty()
        && url.password().is_none()
        && url
            .host_str()
            .is_some_and(|host| TRUSTED_NETWORK_HOSTS.contains(&host))
}

fn trusted_launcher_update_url(raw_url: &str) -> Result<reqwest::Url, String> {
    let parsed = trusted_network_url(raw_url)?;
    let first_party = matches!(
        parsed.host_str(),
        Some("gambleclient.org" | "dash.gambleclient.org")
    );
    if !first_party {
        return Err(
            "Launcher updates must be downloaded from Gamble Client infrastructure.".to_string(),
        );
    }
    Ok(parsed)
}

fn json_string(body: &serde_json::Value, key: &str) -> String {
    body.get(key)
        .and_then(|value| value.as_str())
        .unwrap_or("")
        .to_string()
}

fn json_u64(body: &serde_json::Value, key: &str) -> u64 {
    body.get(key)
        .and_then(|value| {
            value
                .as_u64()
                .or_else(|| value.as_i64().map(|v| v.max(0) as u64))
        })
        .unwrap_or(0)
}

fn json_bool_value(value: &serde_json::Value) -> bool {
    value
        .as_bool()
        .or_else(|| value.as_i64().map(|number| number != 0))
        .or_else(|| {
            value.as_str().map(|text| {
                matches!(
                    text.to_ascii_lowercase().as_str(),
                    "true" | "1" | "on" | "yes"
                )
            })
        })
        .unwrap_or(false)
}

fn url_encode_component(value: &str) -> String {
    let mut out = String::new();
    for byte in value.bytes() {
        if byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b'.' | b'~') {
            out.push(byte as char);
        } else {
            out.push_str(&format!("%{byte:02X}"));
        }
    }
    out
}

fn push_check(checks: &mut Vec<DiagnosticCheck>, label: &str, ok: bool, detail: String) {
    checks.push(DiagnosticCheck {
        label: label.to_string(),
        ok,
        detail,
    });
}

fn java_output_is_21_or_newer(output: &std::process::Output) -> bool {
    let text = format!(
        "{} {}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    java_feature_from_text(&text) >= 21
}

fn java_output_feature(output: &std::process::Output) -> u32 {
    let text = format!(
        "{} {}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    java_feature_from_text(&text)
}

fn java_feature_from_text(text: &str) -> u32 {
    let version = text.split('"').nth(1).unwrap_or(&text);
    let first = version
        .split('.')
        .next()
        .unwrap_or("")
        .trim()
        .parse::<u32>()
        .unwrap_or(0);
    if first == 1 {
        version
            .split('.')
            .nth(1)
            .and_then(|part| part.parse().ok())
            .unwrap_or(0)
    } else {
        first
    }
}

fn read_trimmed(path: &Path) -> Result<String, String> {
    fs::read_to_string(path)
        .map(|value| value.trim().to_string())
        .map_err(error_text)
}

fn write_private_file(path: &Path, data: &[u8]) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }
    fs::write(path, data).map_err(error_text)?;
    restrict_private_file(path)
}

fn restrict_private_file(path: &Path) -> Result<(), String> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(0o600)).map_err(error_text)?;
    }
    Ok(())
}

fn timestamp() -> String {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_secs().to_string())
        .unwrap_or_else(|_| "0".to_string())
}

fn unix_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_millis() as u64)
        .unwrap_or(0)
}

fn display_path(path: &Path) -> String {
    path.to_string_lossy().to_string()
}

fn error_text<E: std::fmt::Display>(error: E) -> String {
    let text = error.to_string();
    let lower = text.to_ascii_lowercase();
    if lower.contains("error sending request") {
        return "Could not reach the network service. Check your internet connection, VPN/firewall, and system clock, then try again.".to_string();
    }
    text
}

#[cfg(any(target_os = "windows", test))]
fn is_browser_url(target: &str) -> bool {
    reqwest::Url::parse(target)
        .map(|url| matches!(url.scheme(), "http" | "https"))
        .unwrap_or(false)
}

fn open_external(target: &str) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    let mut command = {
        let command = if is_browser_url(target) {
            let mut command = Command::new("rundll32.exe");
            command.args(["url.dll,FileProtocolHandler", target]);
            command
        } else {
            let mut command = Command::new("explorer.exe");
            command.arg(target);
            command
        };
        command
    };

    #[cfg(target_os = "macos")]
    let mut command = {
        let mut command = Command::new("open");
        command.arg(target);
        command
    };

    #[cfg(all(unix, not(target_os = "macos")))]
    let mut command = {
        let mut command = Command::new("xdg-open");
        command.arg(target);
        command
    };

    command
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("Could not open: {error}"))
}

#[cfg(test)]
mod tests {
    use super::{
        first_party_request_urls, has_launcher_managed_enrollment, is_browser_url,
        is_expected_loader_fabric_metadata, is_required_mod_for_profile, java_feature_from_text,
        launch_log_has_gpu_fault, loader_core_sha256, microsoft_refresh_error,
        normalize_graphics_mode, quarantine_duplicate_loader_jars, random_base64_url,
        replace_file_with_rollback, replace_path_with_rollback, safe_file_name,
        should_apply_amd_guard, trusted_launcher_update_url, trusted_network_url,
        validate_gpu_selector, verify_fabric_mod_id, verify_fabric_mod_identity,
        webkit_safe_mode_enabled, write_private_file, MANAGED_CLIENT_MOD_ID,
        MICROSOFT_REAUTH_REQUIRED,
    };
    use serde_json::json;
    use std::{env, fs, fs::File, io::Cursor, io::Write, path::PathBuf};
    use zip::{write::SimpleFileOptions, ZipWriter};

    #[test]
    fn browser_urls_are_distinguished_from_filesystem_paths() {
        assert!(is_browser_url(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=test&prompt=select_account"
        ));
        assert!(is_browser_url("http://127.0.0.1:18765/public"));
        assert!(!is_browser_url(
            r"C:\\Users\\Player\\AppData\\Roaming\\.gambleclient"
        ));
        assert!(!is_browser_url("/home/player/.gambleclient"));
        assert!(!is_browser_url("javascript:alert(1)"));
    }

    #[test]
    fn network_downloads_require_known_https_origins_without_credentials_or_ports() {
        assert!(trusted_network_url("https://meta.fabricmc.net/v2/versions/loader").is_ok());
        assert!(trusted_network_url("https://api.adoptium.net/v3/binary/latest/21").is_ok());
        assert!(
            trusted_network_url("https://release-assets.githubusercontent.com/file.zip").is_ok()
        );
        for value in [
            "http://meta.fabricmc.net/file",
            "https://evil.example/file",
            "https://user:password@meta.fabricmc.net/file",
            "https://meta.fabricmc.net:443/file",
        ] {
            assert!(
                trusted_network_url(value).is_err(),
                "{value} should be rejected"
            );
        }
    }

    #[test]
    fn launcher_updates_are_first_party_only() {
        assert!(trusted_launcher_update_url(
            "https://gambleclient.org/api/launcher/download/windows"
        )
        .is_ok());
        assert!(
            trusted_launcher_update_url("https://dash.gambleclient.org/releases/launcher.jar")
                .is_ok()
        );
        assert!(
            trusted_launcher_update_url("https://github.com/example/launcher/releases/latest")
                .is_err()
        );
    }

    #[test]
    fn backend_requests_have_a_first_party_origin_fallback_without_changing_private_query_data() {
        let urls = first_party_request_urls(
            "https://gambleclient.org/api/launcher/poll?code=private-code&state=one",
        )
        .unwrap();
        assert_eq!(urls.len(), 2);
        assert_eq!(urls[0].host_str(), Some("gambleclient.org"));
        assert_eq!(urls[1].host_str(), Some("dash.gambleclient.org"));
        assert_eq!(urls[0].path(), "/api/launcher/poll");
        assert_eq!(urls[1].path(), "/api/launcher/poll");
        assert_eq!(urls[0].query(), urls[1].query());
        assert!(first_party_request_urls("https://evil.example/api/launcher/poll").is_err());
        assert!(first_party_request_urls("http://gambleclient.org/api/launcher/poll").is_err());
    }

    #[test]
    fn raw_reqwest_transport_errors_are_not_shown_to_launcher_users() {
        let message = super::error_text(
            "error sending request for url (https://gambleclient.org/api/launcher/session): connection failed",
        );
        assert!(message.starts_with("Could not reach the network service."));
        assert!(!message.contains("gambleclient.org"));
    }

    #[test]
    fn required_mods_are_scoped_to_the_profile_that_manages_them() {
        assert!(is_required_mod_for_profile(
            "gamble-client",
            "gamble-client-loader.jar"
        ));
        assert!(is_required_mod_for_profile(
            "client-recording",
            "gamble-client-loader.jar.disabled"
        ));
        assert!(!is_required_mod_for_profile(
            "fabric",
            "gamble-client-loader.jar"
        ));
        assert!(!is_required_mod_for_profile(
            "fabric-testing",
            "gamble-client-loader.jar"
        ));
        assert!(is_required_mod_for_profile(
            "fabric",
            "fabric-api-0.140.2.jar"
        ));
        assert!(!is_required_mod_for_profile(
            "vanilla",
            "fabric-api-0.140.2.jar"
        ));
    }

    #[test]
    fn expired_microsoft_refresh_tokens_request_reauthentication_without_exposing_the_url() {
        let body = json!({
            "error": "invalid_grant",
            "error_description": "AADSTS70000: The provided grant has expired."
        });
        let message = microsoft_refresh_error(400, &body);
        assert_eq!(message, MICROSOFT_REAUTH_REQUIRED);
        assert!(!message.contains("login.microsoftonline.com"));
        assert!(!message.contains("AADSTS"));
    }

    #[test]
    fn managed_loader_metadata_keeps_the_verified_prelaunch_entrypoint() {
        let valid = json!({
            "schemaVersion": 1,
            "id": "gamble-client-standalone-loader",
            "version": "1.4.16",
            "name": "Custom Name",
            "icon": "assets/cg-mod/icon.png",
            "environment": "client",
            "entrypoints": { "preLaunch": ["gcclient.loader.StandaloneLoader"] },
            "depends": { "java": ">=21", "minecraft": ["1.21.11"], "fabricloader": ">=0.18.2" }
        });
        assert!(is_expected_loader_fabric_metadata(&valid));
        let mut altered = valid;
        altered["entrypoints"]["preLaunch"] = json!(["gcclient.loader.Disabled"]);
        assert!(!is_expected_loader_fabric_metadata(&altered));
    }

    #[test]
    fn java_feature_parser_handles_lts_and_modern_versions() {
        assert_eq!(
            java_feature_from_text("openjdk version \"21.0.8\" 2025-07-15"),
            21
        );
        assert_eq!(
            java_feature_from_text("openjdk version \"25.0.3\" 2026-04-21"),
            25
        );
        assert_eq!(java_feature_from_text("java version \"1.8.0_412\""), 8);
    }

    #[test]
    fn graphics_mode_and_gpu_selector_inputs_are_normalized_and_bounded() {
        assert_eq!(normalize_graphics_mode("auto").unwrap(), "automatic");
        assert_eq!(normalize_graphics_mode("safe-graphics").unwrap(), "safe");
        assert_eq!(
            normalize_graphics_mode("software_rendering").unwrap(),
            "software"
        );
        assert!(normalize_graphics_mode("vulkan").is_err());
        assert_eq!(validate_gpu_selector(" 1! ").unwrap(), "1!");
        assert!(validate_gpu_selector("1; rm -rf /").is_err());
        assert!(validate_gpu_selector(&"a".repeat(129)).is_err());
    }

    #[test]
    fn automatic_amd_guard_is_enabled_before_java_starts() {
        assert!(should_apply_amd_guard("automatic", true));
        assert!(!should_apply_amd_guard("automatic", false));
        assert!(should_apply_amd_guard("safe", false));
        assert!(should_apply_amd_guard("software", false));
    }

    #[test]
    fn gpu_fault_detection_ignores_normal_amd_device_inventory() {
        assert!(!launch_log_has_gpu_fault(
            "GPU devices: card2: vendor=0x1002, driver=amdgpu\n"
        ));
        assert!(launch_log_has_gpu_fault(
            "amdgpu: [gfxhub] page fault\namdgpu: ring gfx timeout\n"
        ));
        assert!(launch_log_has_gpu_fault(
            "The CS has cancelled because the context is lost."
        ));
    }

    #[test]
    fn webkit_safe_mode_is_explicitly_opt_in() {
        assert!(webkit_safe_mode_enabled("1"));
        assert!(webkit_safe_mode_enabled(" true "));
        assert!(webkit_safe_mode_enabled("ON"));
        assert!(!webkit_safe_mode_enabled("0"));
        assert!(!webkit_safe_mode_enabled("automatic"));
        assert!(!webkit_safe_mode_enabled(""));
    }

    #[cfg(unix)]
    #[test]
    fn security_tokens_are_written_owner_only() {
        use std::os::unix::fs::PermissionsExt;

        let path = env::temp_dir().join(format!("gamble-private-{}.txt", random_base64_url(24)));
        write_private_file(&path, b"one-use-token").unwrap();
        assert_eq!(
            fs::metadata(&path).unwrap().permissions().mode() & 0o777,
            0o600
        );
        let _ = fs::remove_file(path);
    }

    #[test]
    fn protected_manifest_file_names_reject_traversal() {
        assert_eq!(safe_file_name("client.jar").unwrap(), "client.jar");
        for value in [
            "",
            ".",
            "..",
            "../client.jar",
            "nested/client.jar",
            "/tmp/client.jar",
            r"..\client.jar",
        ] {
            assert!(safe_file_name(value).is_err(), "{value} should be rejected");
        }
    }

    #[test]
    fn fabric_identity_requires_one_exact_top_level_id() {
        let valid = write_test_jar(
            r#"{"id":"cg-mod","custom":{"id":"other","cg-mod:build_variant":"ad-tier"}}"#,
        );
        let nested = write_test_jar(r#"{"id":"other","custom":{"id":"cg-mod"}}"#);
        let duplicate = write_test_jar(r#"{"id":"other","id":"cg-mod"}"#);
        assert!(verify_fabric_mod_id(&valid, MANAGED_CLIENT_MOD_ID).is_ok());
        assert!(verify_fabric_mod_id(&nested, MANAGED_CLIENT_MOD_ID).is_err());
        assert!(verify_fabric_mod_id(&duplicate, MANAGED_CLIENT_MOD_ID).is_err());
        assert!(verify_fabric_mod_identity(&valid, MANAGED_CLIENT_MOD_ID, Some("ad_tier")).is_ok());
        assert!(
            verify_fabric_mod_identity(&valid, MANAGED_CLIENT_MOD_ID, Some("release")).is_err()
        );
        for path in [valid, nested, duplicate] {
            let _ = fs::remove_file(path);
        }
    }

    #[test]
    fn loader_core_hash_ignores_personalization_but_detects_executable_changes() {
        let first = write_loader_bytes(
            r#"{"id":"gamble-client-standalone-loader","name":"First"}"#,
            b"signed executable core",
        );
        let personalized = write_loader_bytes(
            r#"{"id":"gamble-client-standalone-loader","name":"Personalized"}"#,
            b"signed executable core",
        );
        let tampered = write_loader_bytes(
            r#"{"id":"gamble-client-standalone-loader","name":"Personalized"}"#,
            b"modified executable core",
        );
        assert_eq!(
            loader_core_sha256(&first).unwrap(),
            loader_core_sha256(&personalized).unwrap()
        );
        assert_ne!(
            loader_core_sha256(&first).unwrap(),
            loader_core_sha256(&tampered).unwrap()
        );
    }

    #[test]
    fn managed_enrollment_requires_a_fresh_launcher_code() {
        let valid = write_enrollment_jar(true, u64::MAX);
        let standalone = write_enrollment_jar(false, u64::MAX);
        let expired = write_enrollment_jar(true, 1);
        assert!(has_launcher_managed_enrollment(&valid));
        assert!(!has_launcher_managed_enrollment(&standalone));
        assert!(!has_launcher_managed_enrollment(&expired));
        for path in [valid, standalone, expired] {
            let _ = fs::remove_file(path);
        }
    }

    #[test]
    fn loader_replacement_overwrites_an_existing_windows_style_target() {
        let root = env::temp_dir().join(format!(
            "gamble-loader-replace-test-{}",
            random_base64_url(24)
        ));
        fs::create_dir_all(&root).unwrap();
        let target = root.join("gamble-client-loader.jar");
        let staging = root.join("gamble-client-loader.jar.part");
        fs::write(&target, b"stale").unwrap();
        fs::write(&staging, b"fresh").unwrap();
        replace_file_with_rollback(&staging, &target).unwrap();
        assert_eq!(fs::read(&target).unwrap(), b"fresh");
        assert!(!staging.exists());
        assert!(!root.join("gamble-client-loader.jar.previous").exists());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn launcher_update_replaces_an_existing_windows_style_download() {
        let root = env::temp_dir().join(format!(
            "gamble-launcher-update-test-{}",
            random_base64_url(24)
        ));
        fs::create_dir_all(&root).unwrap();
        let target = root.join("Gamble Client Launcher_0.1.130_x64-setup.exe");
        let staging = root.join("Gamble Client Launcher_0.1.130_x64-setup.exe.part");
        let backup = root.join("Gamble Client Launcher_0.1.130_x64-setup.download.previous");
        fs::write(&target, b"old installer").unwrap();
        fs::write(&staging, b"new installer").unwrap();

        replace_path_with_rollback(&staging, &target, &backup).unwrap();

        assert_eq!(fs::read(&target).unwrap(), b"new installer");
        assert!(!staging.exists());
        assert!(!backup.exists());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn duplicate_standalone_loaders_are_quarantined_before_launch() {
        let root = env::temp_dir().join(format!(
            "gamble-loader-duplicate-test-{}",
            random_base64_url(24)
        ));
        fs::create_dir_all(&root).unwrap();
        let canonical = root.join("gamble-client-loader.jar");
        let duplicate = root.join("old-personalized-loader.jar");
        let unrelated = root.join("example-mod.jar");
        write_test_jar_at(&canonical, r#"{"id":"gamble-client-standalone-loader"}"#);
        write_test_jar_at(&duplicate, r#"{"id":"gamble-client-standalone-loader"}"#);
        write_test_jar_at(&unrelated, r#"{"id":"example-mod"}"#);

        assert_eq!(
            quarantine_duplicate_loader_jars(&root, &canonical).unwrap(),
            1
        );
        assert!(canonical.is_file());
        assert!(!duplicate.exists());
        assert!(unrelated.is_file());
        let backups = root.join(".gamble-client-backups");
        assert_eq!(
            fs::read_dir(backups)
                .unwrap()
                .flat_map(|entry| fs::read_dir(entry.unwrap().path()).unwrap())
                .count(),
            1
        );
        let _ = fs::remove_dir_all(root);
    }

    fn write_loader_bytes(metadata: &str, executable: &[u8]) -> Vec<u8> {
        let mut archive = ZipWriter::new(Cursor::new(Vec::new()));
        archive
            .start_file("fabric.mod.json", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(metadata.as_bytes()).unwrap();
        archive
            .start_file(
                "gcclient/loader/StandaloneLoader.class",
                SimpleFileOptions::default(),
            )
            .unwrap();
        archive.write_all(executable).unwrap();
        archive
            .start_file("gcclient-memory-loader.txt", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(b"verified-memory-only-v1\n").unwrap();
        archive.finish().unwrap().into_inner()
    }

    fn write_test_jar(metadata: &str) -> PathBuf {
        let path = env::temp_dir().join(format!(
            "gamble-launcher-test-{}.jar",
            random_base64_url(24)
        ));
        let file = File::create(&path).unwrap();
        let mut archive = ZipWriter::new(file);
        archive
            .start_file("fabric.mod.json", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(metadata.as_bytes()).unwrap();
        archive.finish().unwrap();
        path
    }

    fn write_test_jar_at(path: &PathBuf, metadata: &str) {
        let file = File::create(path).unwrap();
        let mut archive = ZipWriter::new(file);
        archive
            .start_file("fabric.mod.json", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(metadata.as_bytes()).unwrap();
        archive.finish().unwrap();
    }

    fn write_enrollment_jar(launcher_managed: bool, expires_at: u64) -> PathBuf {
        let path = env::temp_dir().join(format!(
            "gamble-enrollment-test-{}.jar",
            random_base64_url(24)
        ));
        let file = File::create(&path).unwrap();
        let mut archive = ZipWriter::new(file);
        archive
            .start_file(
                "gcclient-standalone-enrollment.json",
                SimpleFileOptions::default(),
            )
            .unwrap();
        archive
            .write_all(
                json!({
                    "code": "abcdefghijklmnopqrstuvwxyzABCDEFGH12345678",
                    "expiresAt": expires_at,
                    "launcherManaged": launcher_managed
                })
                .to_string()
                .as_bytes(),
            )
            .unwrap();
        archive.finish().unwrap();
        path
    }
}
