#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    env,
    fs::{self, File},
    io::{self, Cursor, Read, Write},
    net::{TcpListener, TcpStream},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::Mutex,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use walkdir::WalkDir;
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};

const VERSION: &str = "0.1.75";
const SITE_URL: &str = "https://gamble-client.store";
const LOADER_JAR_NAME: &str = "gamble-client-loader.jar";
const MINECRAFT_VERSION: &str = "1.21.11";
const FABRIC_LOADER_VERSION: &str = "0.18.4";
const FABRIC_PROFILE_URL: &str = "https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.18.4/profile/json";
const VERSION_MANIFEST_URL: &str = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
const ASSET_BASE_URL: &str = "https://resources.download.minecraft.net/";
const MICROSOFT_DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const MICROSOFT_AUTHORIZE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const MICROSOFT_TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const MICROSOFT_SCOPE: &str = "XboxLive.signin offline_access";
const MICROSOFT_CLIENT_ID: &str = "8eea0ae2-d0a9-4af1-88b9-f66bd96c94bd";
const MICROSOFT_REDIRECT_PORT: u16 = 39062;
const MICROSOFT_REDIRECT_URI: &str = "http://localhost:39062/";
const XBOX_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MINECRAFT_LOGIN_URL: &str = "https://api.minecraftservices.com/launcher/login";
const MINECRAFT_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const ANTISCREENSHARE_CORE_ON: &[&str] = &["antiscreenshare"];
const ANTISCREENSHARE_SCOREBOARD_ON: &[&str] = &["hide-scoreboard"];
const ANTISCREENSHARE_SCOREBOARD_OFF: &[&str] = &["fake-scoreboard"];
const ANTISCREENSHARE_HUD_OFF: &[&str] = &["hud", "jamble-hud", "better-tab", "discord-presence", "big-spender-net-hud"];
const ANTISCREENSHARE_VISUAL_OFF: &[&str] = &[
    "player-esp", "storage-esp", "block-esp", "item-esp", "trident-esp", "invis-esp",
    "chams", "nametags", "logout-spots", "trail", "tracers", "light-finder",
    "hole-tunnel-stair-esp", "tunnel-esp", "base-digger", "base-finder",
    "block-debug-finder", "block-update-finder",
];
static MINECRAFT_PROCESS: Mutex<Option<Child>> = Mutex::new(None);
static ACTIVE_LAUNCH_PAYLOAD: Mutex<Option<PathBuf>> = Mutex::new(None);

#[derive(Serialize)]
struct LauncherInfo {
    version: &'static str,
    managed_root: String,
    data_folder: String,
    session_file: String,
    os: String,
}

#[derive(Serialize)]
struct MinecraftStatus {
    running: bool,
    pid: Option<u32>,
}

#[derive(Serialize)]
struct LocalFile {
    name: String,
    path: String,
    enabled: bool,
    locked: bool,
    size: u64,
}

#[derive(Serialize)]
struct PurgeProfileResult {
    profile: String,
    path: String,
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
    accounts: Vec<MicrosoftAccount>,
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
    #[serde(default, rename = "linuxAppImage")]
    linux_app_image: Option<LauncherDownload>,
    #[serde(default)]
    jar: Option<LauncherDownload>,
}

#[derive(Clone, Default, Deserialize)]
struct LauncherDownload {
    #[serde(default, rename = "fileName")]
    file_name: String,
    #[serde(default, rename = "downloadUrl")]
    download_url: String,
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
    #[serde(rename = "minecraftExpiresAt")]
    minecraft_expires_at: u64,
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

struct MicrosoftToken {
    access_token: String,
    refresh_token: String,
    expires_in_seconds: u64,
}

struct XboxToken {
    token: String,
    user_hash: String,
    xuid: String,
}

struct MinecraftToken {
    access_token: String,
}

struct MinecraftProfile {
    uuid: String,
    name: String,
    xuid: String,
    access_token: String,
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
    #[serde(default, rename = "downloadUrl")]
    download_url: String,
    #[serde(default)]
    sha256: String,
    #[serde(default)]
    size: u64,
    #[serde(default, rename = "buildVersion")]
    build_version: String,
}

#[derive(Deserialize)]
struct LaunchRequest {
    profile: String,
    build: String,
    token: String,
    username: String,
    memory: u8,
    #[serde(rename = "javaArgs")]
    java_args: String,
    #[serde(rename = "antiScreenshare")]
    anti_screenshare: bool,
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
    if !path.starts_with("/api/launcher/") && path != "/api/spotify/status" && !path.starts_with("/api/friends") {
        return Err("Launcher API path is not allowed.".to_string());
    }

    let client = reqwest::blocking::Client::builder()
        .user_agent(format!("GambleClientLauncher/{VERSION}"))
        .build()
        .map_err(error_text)?;
    let url = format!("{SITE_URL}{path}");
    let mut request = match method.as_str() {
        "GET" => client.get(&url),
        "POST" => client.post(&url),
        _ => return Err("Launcher API method is not allowed.".to_string()),
    };
    if !input.token.trim().is_empty() {
        request = request.bearer_auth(input.token.trim());
    }
    if method == "POST" {
        request = request.json(&input.body);
    }

    let response = request.send().map_err(error_text)?;
    let status = response.status();
    let text = response.text().map_err(error_text)?;
    let body = if text.trim().is_empty() {
        serde_json::Value::Object(Default::default())
    } else {
        serde_json::from_str::<serde_json::Value>(&text).unwrap_or_else(|_| json!({ "message": text }))
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
    fs::write(launcher_session_file(), format!("{token}\n")).map_err(error_text)
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
fn read_microsoft_account() -> Result<Option<MicrosoftAccount>, String> {
    selected_microsoft_account()
}

#[tauri::command]
fn list_microsoft_accounts() -> Result<MicrosoftAccountState, String> {
    let accounts = read_microsoft_account_list()?;
    Ok(MicrosoftAccountState {
        selected_uuid: selected_microsoft_uuid().unwrap_or_else(|| accounts.first().map(|account| account.uuid.clone()).unwrap_or_default()),
        accounts,
    })
}

#[tauri::command]
fn select_microsoft_account(uuid: String) -> Result<Option<MicrosoftAccount>, String> {
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
    Ok(Some(account))
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
        accounts.first().map(|account| account.uuid.clone()).unwrap_or_default()
    } else {
        selected
    };
    if next_selected.trim().is_empty() {
        let _ = fs::remove_file(selected_microsoft_account_file());
        let _ = fs::remove_file(microsoft_account_file());
    } else {
        save_selected_microsoft_uuid(&next_selected)?;
        if let Some(account) = accounts.iter().find(|account| account.uuid.eq_ignore_ascii_case(&next_selected)) {
            save_legacy_microsoft_account(account)?;
        }
    }

    Ok(MicrosoftAccountState {
        accounts,
        selected_uuid: next_selected,
    })
}

#[tauri::command]
fn microsoft_browser_sign_in(force_account_picker: bool) -> Result<serde_json::Value, String> {
    let state = random_base64_url(24);
    let code_verifier = random_base64_url(48);
    let code_challenge = sha256_base64_url(&code_verifier);
    let listener = TcpListener::bind(("127.0.0.1", MICROSOFT_REDIRECT_PORT))
        .map_err(|error| format!("Could not start Microsoft callback listener on port {MICROSOFT_REDIRECT_PORT}: {error}"))?;
    listener
        .set_nonblocking(true)
        .map_err(error_text)?;

    let auth_url = microsoft_authorize_url(&code_challenge, &state, force_account_picker);
    open_external(&auth_url)?;

    let mut stream = wait_for_microsoft_callback(&listener)?;
    let callback = read_microsoft_callback(&mut stream)?;
    let title;
    let message;

    let result = if callback.get("state").map(String::as_str) != Some(state.as_str()) {
        title = "Microsoft sign-in failed";
        message = "The Microsoft sign-in state did not match. Try again.";
        Err(message.to_string())
    } else if let Some(error) = callback.get("error") {
        title = "Microsoft sign-in failed";
        message = callback.get("error_description").map(String::as_str).unwrap_or(error);
        Err(message.to_string())
    } else if let Some(code) = callback.get("code") {
        title = "Microsoft sign-in complete";
        message = "You can close this tab and return to the Gamble Client launcher.";
        Ok(code.clone())
    } else {
        title = "Microsoft sign-in failed";
        message = "Microsoft did not return an authorization code.";
        Err(message.to_string())
    };

    let _ = write_microsoft_callback_response(&mut stream, title, message);
    drop(listener);

    let code = result?;
    let token = exchange_microsoft_authorization_code(&code, &code_verifier)?;
    let profile = exchange_microsoft_for_minecraft(&token.access_token)?;
    let account = MicrosoftAccount {
        name: profile.name,
        uuid: profile.uuid,
        xuid: profile.xuid,
        refresh_token: token.refresh_token,
        minecraft_expires_at: unix_millis() + token.expires_in_seconds.max(300) * 1000,
    };
    save_microsoft_account(&account)?;
    Ok(json!({ "status": "ready", "account": account }))
}

#[tauri::command]
fn microsoft_device_start(_force_account_picker: bool) -> Result<MicrosoftDeviceStart, String> {
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
        let separator = if verification_uri.contains('?') { "&" } else { "?" };
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
fn microsoft_device_poll(device_code: String) -> Result<serde_json::Value, String> {
    let device_code = device_code.trim().to_string();
    if device_code.is_empty() {
        return Err("Microsoft device code was missing before polling. Start sign-in again.".to_string());
    }
    let params = [
        ("grant_type", "urn:ietf:params:oauth:grant-type:device_code".to_string()),
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
        minecraft_expires_at: unix_millis() + token.expires_in_seconds.max(300) * 1000,
    };
    save_microsoft_account(&account)?;
    Ok(json!({ "status": "ready", "account": account }))
}

#[tauri::command]
fn ensure_profile(profile: String) -> Result<String, String> {
    let profile = profile_id(&profile);
    ensure_profile_folders(&profile).map(|path| display_path(&path))
}

#[tauri::command]
fn purge_profile(profile: String) -> Result<PurgeProfileResult, String> {
    let profile = profile_id(&profile);
    {
        let mut guard = MINECRAFT_PROCESS.lock().map_err(|_| "Minecraft process lock failed.".to_string())?;
        if let Some(child) = guard.as_mut() {
            if child.try_wait().map_err(error_text)?.is_none() {
                return Err("Close Minecraft before purging this profile.".to_string());
            }
            *guard = None;
        }
    }

    let root = minecraft_folder(&profile);
    if root.exists() {
        fs::remove_dir_all(&root).map_err(error_text)?;
    }
    let recreated = ensure_profile_folders(&profile)?;
    Ok(PurgeProfileResult {
        profile,
        path: display_path(&recreated),
        message: "Profile folder purged. Microsoft accounts and launcher sign-in were kept.".to_string(),
    })
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
        let locked = kind != "resourcepacks" && is_required_mod_name(&lower);
        let enabled = if kind == "resourcepacks" {
            !lower.ends_with(".disabled")
        } else {
            lower.ends_with(".jar")
        };
        files.push(LocalFile {
            name,
            path: display_path(&path),
            enabled,
            locked,
            size: metadata.len(),
        });
    }
    files.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    Ok(files)
}

#[tauri::command]
fn toggle_local_file(profile: String, kind: String, path: String) -> Result<(), String> {
    let profile = profile_id(&profile);
    let path = PathBuf::from(path);
    if !path.exists() {
        return Err("File does not exist anymore.".to_string());
    }
    if kind != "resourcepacks" {
        let lower = path.file_name().and_then(|v| v.to_str()).unwrap_or("").to_lowercase();
        if is_required_mod_name(&lower) {
            return Err("This required Fabric mod is managed by the launcher.".to_string());
        }
    }

    let target = toggle_target(&path)?;
    fs::rename(&path, &target).map_err(error_text)?;
    if kind == "resourcepacks" {
        set_resource_pack_enabled(&profile, &target, !target.to_string_lossy().ends_with(".disabled"))?;
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
fn open_path(path: String) -> Result<(), String> {
    open_external(&path)
}

#[tauri::command]
fn open_profile_folder(profile: String, kind: String) -> Result<String, String> {
    let profile = profile_id(&profile);
    let path = match kind.as_str() {
        "mods" => mods_folder(&profile),
        "resourcepacks" => resource_packs_folder(&profile),
        "data" => profile_data_folder(&profile),
        _ => minecraft_folder(&profile),
    };
    fs::create_dir_all(&path).map_err(error_text)?;
    open_external(&display_path(&path))?;
    Ok(display_path(&path))
}

#[tauri::command]
fn diagnostics(profile: String) -> Result<Diagnostics, String> {
    let profile = profile_id(&profile);
    let mut checks = Vec::new();
    push_check(&mut checks, "Managed root", managed_root().is_dir(), display_path(&managed_root()));
    push_check(&mut checks, "Profile folder", minecraft_folder(&profile).is_dir(), display_path(&minecraft_folder(&profile)));
    push_check(&mut checks, "Mods folder", mods_folder(&profile).is_dir(), display_path(&mods_folder(&profile)));
    push_check(&mut checks, "Resource packs", resource_packs_folder(&profile).is_dir(), display_path(&resource_packs_folder(&profile)));
    push_check(&mut checks, "Launcher session", launcher_session_file().is_file(), display_path(&launcher_session_file()));
    let microsoft_saved = read_microsoft_account().map(|account| account.is_some()).unwrap_or(false);
    push_check(
        &mut checks,
        "Microsoft account",
        microsoft_saved,
        display_path(&microsoft_accounts_file()),
    );
    let java = Command::new("java").arg("-version").output();
    push_check(
        &mut checks,
        "Java runtime",
        java.as_ref().map(|out| out.status.success()).unwrap_or(false),
        java.map(|out| String::from_utf8_lossy(&out.stderr).lines().next().unwrap_or("java").to_string())
            .unwrap_or_else(|error| error.to_string()),
    );
    push_check(&mut checks, "Latest launch log", latest_launch_log_file().is_file(), display_path(&latest_launch_log_file()));
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
        Ok(_) => format!("AntiScreenshare {} in the live client.", if enabled { "enabled" } else { "disabled" }),
        Err(_) => update_anti_screenshare_config(
            &profile,
            &[("antiscreenshare", enabled)],
            &format!("AntiScreenshare {}", if enabled { "enabled" } else { "disabled" }),
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
fn client_install_status(profile: String, build: String, token: String) -> Result<ClientInstallStatus, String> {
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
    let installed = find_verified_client_install(&profile, &manifest);
    let expected_install = payload_file(&profile, &manifest.file_name);
    let installed_path = installed.as_deref().unwrap_or(expected_install.as_path());
    let current = installed.is_some();
    Ok(ClientInstallStatus {
        file_name: manifest.file_name.clone(),
        build: manifest.build.clone(),
        build_version: manifest.build_version.clone(),
        path: display_path(installed_path),
        size: installed_path.metadata().map(|metadata| metadata.len()).unwrap_or(0),
        sha256: manifest.sha256.clone(),
        installed: current,
        update_available: !current,
        message: if current {
            format!("Installed client is current: {}", display_version(&manifest))
        } else {
            format!("Client update available: {}", display_version(&manifest))
        },
    })
}

#[tauri::command]
fn download_launcher_update() -> Result<LauncherUpdateResult, String> {
    let info = fetch_launcher_version_info()?;
    let download = preferred_launcher_download(&info);
    if download.download_url.trim().is_empty() || download.file_name.trim().is_empty() {
        return Err("Launcher update download is not configured for this platform.".to_string());
    }

    let target = downloads_folder().join(&download.file_name);
    download_file(&download.download_url, &target)?;
    let target_text = display_path(&target);
    let open_message = match open_external(&target_text) {
        Ok(_) => "Opened the downloaded launcher installer.".to_string(),
        Err(error) => {
            if let Some(parent) = target.parent() {
                let _ = open_external(&display_path(parent));
            }
            format!("Downloaded the launcher update. Open failed: {error}")
        }
    };

    Ok(LauncherUpdateResult {
        version: if info.version.trim().is_empty() { info.min_version } else { info.version },
        file_name: download.file_name,
        download_url: download.download_url,
        path: target_text,
        message: open_message,
    })
}

#[tauri::command]
fn install_client_manifest(profile: String, build: String, token: String) -> Result<InstallResult, String> {
    let profile = profile_id(&profile);
    if token.trim().is_empty() {
        return Err("Sign in before installing the client.".to_string());
    }
    if !profile_installs_client(&profile) {
        return Err("Vanilla and plain Fabric profiles do not install the managed client jar.".to_string());
    }
    ensure_profile_folders(&profile)?;

    let client = http_client()?;
    let manifest = fetch_client_manifest(&build, &token)?;

    if manifest.file_name.is_empty() || manifest.download_url.is_empty() {
        return Err("Backend manifest did not include a jar download.".to_string());
    }

    let existing = find_verified_client_install(&profile, &manifest);
    let (bytes, downloaded) = if let Some(path) = existing {
        (fs::read(path).map_err(error_text)?, false)
    } else {
        (
            client
                .get(&manifest.download_url)
                .send()
                .map_err(error_text)?
                .error_for_status()
                .map_err(error_text)?
                .bytes()
                .map_err(error_text)?
                .to_vec(),
            true,
        )
    };

    if manifest.size > 0 && bytes.len() as u64 != manifest.size {
        return Err(format!("Expected {} bytes but found {} bytes.", manifest.size, bytes.len()));
    }
    if !manifest.sha256.trim().is_empty() {
        let actual = sha256_hex(&bytes);
        if !actual.eq_ignore_ascii_case(manifest.sha256.trim()) {
            return Err(format!("Expected SHA-256 {} but found {}.", manifest.sha256, actual));
        }
    }

    ensure_loader_jar(&profile)?;
    ensure_fabric_api(&profile)?;
    cleanup_managed_mod_jars(&profile)?;
    cleanup_payload_client_jars(&profile)?;
    let client_jar = payload_file(&profile, &manifest.file_name);
    if let Some(parent) = client_jar.parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }
    fs::write(&client_jar, bytes).map_err(error_text)?;
    write_install_marker(&profile, &build, &manifest, &client_jar)?;

    Ok(InstallResult {
        file_name: manifest.file_name.clone(),
        build: manifest.build.clone(),
        build_version: manifest.build_version.clone(),
        path: display_path(&client_jar),
        size: client_jar.metadata().map(|m| m.len()).unwrap_or(0),
        sha256: manifest.sha256.clone(),
        updated: downloaded,
        message: if downloaded {
            format!("Updated managed client to: {}", display_version(&manifest))
        } else {
            format!("Refreshed managed client in this profile: {}", display_version(&manifest))
        },
    })
}

#[tauri::command]
fn launch_game(input: LaunchRequest) -> Result<String, String> {
    {
        let mut running = MINECRAFT_PROCESS.lock().map_err(error_text)?;
        if let Some(child) = running.as_mut() {
            if child.try_wait().map_err(error_text)?.is_none() {
                child.kill().map_err(error_text)?;
                *running = None;
                cleanup_active_launch_payload()?;
                return Ok("Minecraft stop signal sent.".to_string());
            }
            *running = None;
            cleanup_active_launch_payload()?;
        }
    }

    let profile = profile_id(&input.profile);
    let build = input.build.trim();
    let token = input.token.trim();
    if token.is_empty() {
        return Err("Sign in before launching Minecraft.".to_string());
    }
    ensure_profile_folders(&profile)?;

    let account = read_microsoft_account()?.ok_or_else(|| {
        "Microsoft is linked on the site, but this launcher does not have a local Minecraft token yet. Connect Microsoft in the launcher first.".to_string()
    })?;
    let mut identity = refresh_minecraft_identity(account)?;
    if identity.name.trim().is_empty() && !input.username.trim().is_empty() {
        identity.name = input.username.trim().to_string();
    }
    cleanup_stale_launch_payloads(&profile)?;
    let managed_client_payload = if profile_installs_client(&profile) {
        let install = install_client_manifest(profile.to_string(), build.to_string(), token.to_string())?;
        Some((PathBuf::from(install.path), install.file_name))
    } else {
        None
    };

    let launch_ticket_file = if profile_installs_client(&profile) {
        Some(write_launch_ticket_file(&profile, token, build)?)
    } else {
        None
    };
    write_launcher_preferences(&profile, input.anti_screenshare)?;

    let profile_dir = minecraft_folder(&profile);
    let version_id = if profile == "vanilla" {
        ensure_vanilla_version_json(&profile_dir, MINECRAFT_VERSION)?;
        MINECRAFT_VERSION.to_string()
    } else {
        ensure_fabric_version_json(&profile_dir)?;
        ensure_vanilla_version_json(&profile_dir, MINECRAFT_VERSION)?;
        ensure_fabric_api(&profile)?;
        format!("fabric-loader-{FABRIC_LOADER_VERSION}-{MINECRAFT_VERSION}")
    };
    let version = load_version_profile(&profile_dir, &version_id)?;
    let mut classpath = ensure_libraries(&profile_dir, &version)?;
    classpath.push(ensure_client_jar(&profile_dir, &version)?);
    ensure_assets(&profile_dir, &version)?;
    let natives = extract_natives(&profile_dir, &version_id, &version)?;
    let managed_client = if let Some((payload, file_name)) = managed_client_payload.as_ref() {
        Some(prepare_launch_payload(&profile, payload, file_name)?)
    } else {
        None
    };

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
        managed_client.as_deref(),
        launch_ticket_file.as_deref(),
    ) {
        Ok(command) => command,
        Err(error) => {
            if let Some(payload) = managed_client.as_deref() {
                cleanup_launch_payload(payload);
            }
            return Err(error);
        }
    };
    let log_file = latest_launch_log_file();
    if let Some(parent) = log_file.parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }
    fs::write(&log_file, format!("Gamble Client Launcher {VERSION}\n{}\n\n", redacted_command(&command))).map_err(error_text)?;
    let stdout = fs::OpenOptions::new().create(true).append(true).open(&log_file).map_err(error_text)?;
    let stderr = fs::OpenOptions::new().create(true).append(true).open(&log_file).map_err(error_text)?;
    let mut process = Command::new(&command[0]);
    process.args(&command[1..]).current_dir(&profile_dir).stdout(Stdio::from(stdout)).stderr(Stdio::from(stderr));
    let child = process.spawn().map_err(|error| {
        if let Some(payload) = managed_client.as_deref() {
            cleanup_launch_payload(payload);
        }
        format!("Could not start Minecraft: {error}. If this mentions Java, install Java 21+ and restart the launcher.")
    })?;
    let pid = child.id();
    {
        let mut active_payload = ACTIVE_LAUNCH_PAYLOAD.lock().map_err(error_text)?;
        *active_payload = managed_client;
    }
    *MINECRAFT_PROCESS.lock().map_err(error_text)? = Some(child);
    Ok(format!("Minecraft process started (pid {pid}). Latest launch log: {}", display_path(&log_file)))
}

#[tauri::command]
fn minecraft_status() -> Result<MinecraftStatus, String> {
    let mut running = MINECRAFT_PROCESS.lock().map_err(error_text)?;
    if let Some(child) = running.as_mut() {
        if child.try_wait().map_err(error_text)?.is_none() {
            return Ok(MinecraftStatus {
                running: true,
                pid: Some(child.id()),
            });
        }
        *running = None;
        cleanup_active_launch_payload()?;
    }
    Ok(MinecraftStatus {
        running: false,
        pid: None,
    })
}

fn refresh_minecraft_identity(account: MicrosoftAccount) -> Result<MinecraftProfile, String> {
    let token = refresh_microsoft_token(&account.refresh_token)?;
    let profile = exchange_microsoft_for_minecraft(&token.access_token)?;
    let saved = MicrosoftAccount {
        name: profile.name.clone(),
        uuid: profile.uuid.clone(),
        xuid: profile.xuid.clone(),
        refresh_token: if token.refresh_token.trim().is_empty() {
            account.refresh_token
        } else {
            token.refresh_token
        },
        minecraft_expires_at: unix_millis() + token.expires_in_seconds.max(300) * 1000,
    };
    save_microsoft_account(&saved)?;
    Ok(MinecraftProfile {
        uuid: profile.uuid,
        name: profile.name,
        xuid: profile.xuid,
        access_token: profile.access_token,
    })
}

fn refresh_microsoft_token(refresh_token: &str) -> Result<MicrosoftToken, String> {
    let params = [
        ("grant_type", "refresh_token".to_string()),
        ("client_id", MICROSOFT_CLIENT_ID.to_string()),
        ("refresh_token", refresh_token.to_string()),
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

fn exchange_microsoft_authorization_code(code: &str, code_verifier: &str) -> Result<MicrosoftToken, String> {
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

fn wait_for_microsoft_callback(listener: &TcpListener) -> Result<TcpStream, String> {
    let deadline = SystemTime::now() + Duration::from_secs(180);

    loop {
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

fn microsoft_authorize_url(code_challenge: &str, state: &str, force_account_picker: bool) -> String {
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
        .map(|(key, value)| format!("{}={}", url_encode_component(key), url_encode_component(&value)))
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

fn write_microsoft_callback_response(stream: &mut TcpStream, title: &str, message: &str) -> io::Result<()> {
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
    fs::write(folder.join("launcher-settings.json"), serde_json::to_string_pretty(&body).map_err(error_text)? + "\n").map_err(error_text)
}

fn read_launcher_anti_preference(profile: &str) -> Option<bool> {
    let path = profile_data_folder(profile).join("launcher-settings.json");
    let text = fs::read_to_string(path).ok()?;
    let body = serde_json::from_str::<serde_json::Value>(&text).ok()?;
    body.get("antiScreenshare").and_then(|value| value.as_bool())
}

fn anti_screenshare_status_for(profile: &str, override_message: Option<String>) -> Result<AntiScreenshareStatus, String> {
    let modules_path = anti_screenshare_modules_file(profile);
    if let Ok(modules) = read_anti_screenshare_bridge_modules() {
        let enabled = bridge_module_active(&modules, "antiscreenshare").unwrap_or(true);
        return Ok(AntiScreenshareStatus {
            enabled,
            available: true,
            bridge_online: true,
            source: "Live client".to_string(),
            message: override_message.unwrap_or_else(|| {
                format!("Live client bridge connected. Core module is {}.", if enabled { "on" } else { "off" })
            }),
            modules_path: display_path(&modules_path),
        });
    }

    if modules_path.is_file() {
        let text = fs::read_to_string(&modules_path).map_err(error_text)?;
        let active = module_active_state(&text, "antiscreenshare");
        let enabled = active.unwrap_or_else(|| read_launcher_anti_preference(profile).unwrap_or(false));
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
                "Saved for the next launch. Launch Gamble Client once before editing live modules.".to_string()
            } else {
                "Launch Gamble Client once before AntiScreenshare can edit module config.".to_string()
            }
        }),
        modules_path: display_path(&modules_path),
    })
}

fn add_anti_screenshare_changes(changes: &mut Vec<(&'static str, bool)>, modules: &'static [&'static str], active: bool) {
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

fn update_anti_screenshare_config(profile: &str, changes: &[(&str, bool)], message: &str) -> Result<String, String> {
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
    let mut result = format!("{message} for {touched} modules. Backup: {}.", backup.file_name().and_then(|value| value.to_str()).unwrap_or("modules backup"));
    if !missing.is_empty() {
        result.push_str(&format!(" Missing in this build: {}.", missing.join(", ")));
    }
    Ok(result)
}

fn backup_anti_screenshare_modules(modules: &Path) -> Result<PathBuf, String> {
    let backup = modules
        .parent()
        .unwrap_or_else(|| Path::new("."))
        .join(format!("modules.txt.backup-antiscreenshare-{}.txt", timestamp()));
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
    updated.replace_range(active_index..active_index + 1, if active { "1" } else { "0" });
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
            Some(json_bool_value(module.get("active").unwrap_or(&serde_json::Value::Bool(false))))
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
    let request = if method == "POST" { client.post(url) } else { client.get(url) };
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

fn write_launch_ticket_file(profile: &str, token: &str, build: &str) -> Result<PathBuf, String> {
    let body = post_json(
        &format!("{SITE_URL}/api/launcher/launch-ticket"),
        &json!({ "build": build }),
        token,
    )?;
    let ticket = json_string(&body, "ticket");
    if ticket.trim().is_empty() {
        return Err("Backend did not issue a launch ticket.".to_string());
    }
    let folder = profile_data_folder(profile).join("launch");
    fs::create_dir_all(&folder).map_err(error_text)?;
    let path = folder.join(format!("ticket-{}-{}.txt", timestamp(), process_id()));
    let payload = format!(
        "ticket={}\nbuild={}\nexpiresAt={}\n",
        ticket,
        json_string(&body, "build"),
        json_u64(&body, "expiresAt")
    );
    fs::write(&path, payload).map_err(error_text)?;
    Ok(path)
}

fn ensure_fabric_version_json(game_dir: &Path) -> Result<PathBuf, String> {
    let version_id = format!("fabric-loader-{FABRIC_LOADER_VERSION}-{MINECRAFT_VERSION}");
    let path = game_dir.join("versions").join(&version_id).join(format!("{version_id}.json"));
    if !path.is_file() {
        download_file(FABRIC_PROFILE_URL, &path)?;
    }
    Ok(path)
}

fn ensure_vanilla_version_json(game_dir: &Path, version_id: &str) -> Result<PathBuf, String> {
    let path = game_dir.join("versions").join(version_id).join(format!("{version_id}.json"));
    if path.is_file() {
        return Ok(path);
    }
    let manifest = http_client()?.get(VERSION_MANIFEST_URL).send().map_err(error_text)?.error_for_status().map_err(error_text)?.json::<serde_json::Value>().map_err(error_text)?;
    let versions = manifest.get("versions").and_then(|v| v.as_array()).cloned().unwrap_or_default();
    let url = versions
        .iter()
        .find(|entry| json_string(entry, "id") == version_id)
        .map(|entry| json_string(entry, "url"))
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| format!("Could not find Minecraft {version_id} in Mojang's version manifest."))?;
    download_file(&url, &path)?;
    Ok(path)
}

fn load_version_profile(game_dir: &Path, version_id: &str) -> Result<VersionProfile, String> {
    let path = game_dir.join("versions").join(version_id).join(format!("{version_id}.json"));
    let body = serde_json::from_str::<serde_json::Value>(&fs::read_to_string(&path).map_err(error_text)?).map_err(error_text)?;
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
        return Err(format!("Minecraft version profile {version_id} did not include a main class."));
    }
    Ok(profile)
}

fn parse_library(value: &serde_json::Value) -> Option<Library> {
    let name = json_string(value, "name");
    if name.is_empty() {
        return None;
    }
    let artifact = value.pointer("/downloads/artifact").unwrap_or(&serde_json::Value::Null);
    let mut artifact_path = json_string(artifact, "path");
    let mut artifact_url = json_string(artifact, "url");
    if artifact_path.is_empty() {
        artifact_path = maven_artifact_path(&name);
        artifact_url = maven_artifact_url(&json_string(value, "url"), &artifact_path);
    }
    let classifiers = value.pointer("/downloads/classifiers").and_then(|v| v.as_object()).cloned().unwrap_or_default();
    let mut classifier_paths = serde_json::Map::new();
    let mut classifier_urls = serde_json::Map::new();
    for (key, item) in classifiers {
        classifier_paths.insert(key.clone(), serde_json::Value::String(json_string(&item, "path")));
        classifier_urls.insert(key, serde_json::Value::String(json_string(&item, "url")));
    }
    Some(Library {
        name,
        rules: value.get("rules").and_then(|v| v.as_array()).cloned().unwrap_or_default(),
        artifact_path,
        artifact_url,
        natives: value.get("natives").and_then(|v| v.as_object()).cloned().unwrap_or_default(),
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
        if !rules_allow(value.get("rules").and_then(|v| v.as_array()).cloned().unwrap_or_default().as_slice()) {
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

fn ensure_libraries(game_dir: &Path, profile: &VersionProfile) -> Result<Vec<PathBuf>, String> {
    let mut classpath = Vec::new();
    let libraries_dir = game_dir.join("libraries");
    for library in &profile.libraries {
        if !rules_allow(&library.rules) {
            continue;
        }
        if !library.artifact_path.is_empty() {
            let path = libraries_dir.join(&library.artifact_path);
            if !path.is_file() {
                if library.artifact_url.is_empty() {
                    return Err(format!("No download URL for library {}", library.name));
                }
                download_file(&library.artifact_url, &path)?;
            }
            classpath.push(path);
        }
        if let Some((path, url)) = native_artifact(library) {
            let file = libraries_dir.join(path);
            if !file.is_file() {
                if url.is_empty() {
                    return Err(format!("No native download URL for library {}", library.name));
                }
                download_file(&url, &file)?;
            }
        }
    }
    Ok(classpath)
}

fn ensure_client_jar(game_dir: &Path, profile: &VersionProfile) -> Result<PathBuf, String> {
    if profile.client_version_id.is_empty() || profile.client_jar_url.is_empty() {
        return Err("Minecraft profile does not include a client jar URL.".to_string());
    }
    let path = game_dir.join("versions").join(&profile.client_version_id).join(format!("{}.jar", profile.client_version_id));
    if !path.is_file() {
        download_file(&profile.client_jar_url, &path)?;
    }
    Ok(path)
}

fn ensure_assets(game_dir: &Path, profile: &VersionProfile) -> Result<(), String> {
    if profile.asset_index_id.is_empty() || profile.asset_index_url.is_empty() {
        return Err("Minecraft profile does not include an asset index.".to_string());
    }
    let assets = game_dir.join("assets");
    let index = assets.join("indexes").join(format!("{}.json", profile.asset_index_id));
    if !index.is_file() {
        download_file(&profile.asset_index_url, &index)?;
    }
    let body = serde_json::from_str::<serde_json::Value>(&fs::read_to_string(index).map_err(error_text)?).map_err(error_text)?;
    let objects = body.get("objects").and_then(|v| v.as_object()).cloned().unwrap_or_default();
    for item in objects.values() {
        let hash = json_string(item, "hash");
        if hash.len() < 2 {
            continue;
        }
        let path = assets.join("objects").join(&hash[0..2]).join(&hash);
        if !path.is_file() {
            download_file(&format!("{ASSET_BASE_URL}{}/{}", &hash[0..2], hash), &path)?;
        }
    }
    Ok(())
}

fn extract_natives(game_dir: &Path, version_id: &str, profile: &VersionProfile) -> Result<PathBuf, String> {
    let target = game_dir.join("versions").join(version_id).join("natives");
    fs::create_dir_all(&target).map_err(error_text)?;
    let libraries_dir = game_dir.join("libraries");
    for library in &profile.libraries {
        if !rules_allow(&library.rules) {
            continue;
        }
        if let Some((path, _)) = native_artifact(library) {
            let file = libraries_dir.join(path);
            if file.is_file() {
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
    payload: Option<&Path>,
    launch_ticket_file: Option<&Path>,
) -> Result<Vec<String>, String> {
    let mut command = Vec::new();
    command.push(java_executable());
    command.push(format!("-Xmx{memory}G"));
    command.push(format!("-Djava.library.path={}", display_path(natives)));
    command.push("-Dminecraft.launcher.brand=GambleClientLauncher".to_string());
    command.push(format!("-Dminecraft.launcher.version={VERSION}"));
    command.push(format!("-Dgamble.antiScreenshare={anti_screenshare}"));
    if let Some(ticket) = launch_ticket_file {
        command.push(format!("-Dgamble.launchTicketFile={}", display_path(ticket)));
    }
    if profile_id == "gamble-client" && !build.is_empty() {
        command.push(format!("-Dgamble.launchBuild={build}"));
    }
    if let Some(payload) = payload.filter(|path| path.parent() != Some(&game_dir.join("mods"))) {
        command.push(format!("-Dfabric.addMods={}", display_path(payload)));
    }
    if profile_id != "vanilla" && !profile.client_version_id.is_empty() {
        let jar = game_dir.join("versions").join(&profile.client_version_id).join(format!("{}.jar", profile.client_version_id));
        command.push(format!("-Dfabric.gameJarPath={}", display_path(&jar)));
    }
    for arg in &profile.jvm_arguments {
        if !is_launcher_managed_jvm_arg(arg) {
            command.push(replace_jvm_placeholders(arg, game_dir, classpath, natives, version_id));
        }
    }
    command.extend(split_args(extra_java_args)?);
    command.push("-cp".to_string());
    command.push(join_classpath(classpath));
    command.push(profile.main_class.clone());

    for arg in &profile.game_arguments {
        command.push(
            arg.replace("${auth_player_name}", &identity.name)
                .replace("${version_name}", &format!("Gamble Client {MINECRAFT_VERSION}"))
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

fn download_file(url: &str, path: &Path) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(error_text)?;
    }
    let bytes = http_client()?.get(url).send().map_err(error_text)?.error_for_status().map_err(error_text)?.bytes().map_err(error_text)?;
    fs::write(path, bytes).map_err(error_text)
}

fn unzip_natives(zip_path: &Path, target: &Path) -> Result<(), String> {
    let file = File::open(zip_path).map_err(error_text)?;
    let mut archive = ZipArchive::new(file).map_err(error_text)?;
    for i in 0..archive.len() {
        let mut item = archive.by_index(i).map_err(error_text)?;
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
    let path = library.classifier_paths.get(&classifier).and_then(|v| v.as_str()).unwrap_or("").to_string();
    let url = library.classifier_urls.get(&classifier).and_then(|v| v.as_str()).unwrap_or("").to_string();
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
    let (coordinate, extension) = name.split_once('@').map(|(a, b)| (a, b)).unwrap_or((name, "jar"));
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
        || arg == "-DFabricMcEmu="
        || arg == "-cp"
        || arg == "-classpath"
        || arg.contains("${classpath}")
        || arg.contains("${natives_directory}")
        || arg == "net.minecraft.client.main.Main"
        || arg.ends_with(".KnotClient")
}

fn replace_jvm_placeholders(arg: &str, game_dir: &Path, classpath: &[PathBuf], natives: &Path, version_id: &str) -> String {
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
    for ch in value.chars() {
        if escaping {
            current.push(ch);
            escaping = false;
            continue;
        }
        if ch == '\\' && !single {
            escaping = true;
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
            return Err(format!("JVM Args should not include the Minecraft main class: {arg}"));
        }
    }
    Ok(args)
}

fn join_classpath(classpath: &[PathBuf]) -> String {
    let separator = if env::consts::OS == "windows" { ";" } else { ":" };
    classpath.iter().map(|path| display_path(path)).collect::<Vec<_>>().join(separator)
}

fn java_executable() -> String {
    if let Ok(home) = env::var("JAVA_HOME") {
        let candidate = PathBuf::from(home).join("bin").join(if env::consts::OS == "windows" { "java.exe" } else { "java" });
        if candidate.is_file() {
            return display_path(&candidate);
        }
    }
    "java".to_string()
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

fn process_id() -> u32 {
    std::process::id()
}

fn redacted_command(command: &[String]) -> String {
    command
        .iter()
        .map(|arg| {
            if arg.starts_with("-Dgamble.launchTicket") {
                "-Dgamble.launchTicketFile=<redacted>".to_string()
            } else if arg.len() > 60 && !arg.starts_with('-') {
                "<token-or-path>".to_string()
            } else {
                arg.clone()
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}


#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    let allowed = [
        "https://gamble-client.store",
        "https://dash.gamble-client.store",
        "https://admin.gamble-client.store",
        "https://profile.gamble-client.store",
        "https://discord.gg",
        "https://login.microsoftonline.com",
        "https://www.microsoft.com",
        "https://microsoft.com",
    ];

    if !allowed.iter().any(|prefix| url.starts_with(prefix)) {
        return Err("URL is not allowed.".to_string());
    }
    open_external(&url)
}

fn main() {
    tauri::Builder::default()
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
            microsoft_device_start,
            microsoft_device_poll,
            ensure_profile,
            purge_profile,
            list_local_files,
            toggle_local_file,
            add_resource_packs,
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
        .run(tauri::generate_context!())
        .expect("error while running Gamble Client Launcher");
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
    profile != "vanilla" && profile != "fabric"
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

fn payload_file(profile: &str, file_name: &str) -> PathBuf {
    payloads_folder(profile).join(file_name)
}

fn ensure_profile_folders(profile: &str) -> Result<PathBuf, String> {
    let root = minecraft_folder(profile);
    fs::create_dir_all(&root).map_err(error_text)?;
    fs::create_dir_all(resource_packs_folder(profile)).map_err(error_text)?;
    fs::create_dir_all(profile_data_folder(profile)).map_err(error_text)?;
    if profile != "vanilla" {
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
        fs::read_to_string(&options).map_err(error_text)?.lines().map(ToString::to_string).collect::<Vec<_>>()
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
    if !lines.iter().any(|line| line.starts_with("incompatibleResourcePacks:")) {
        lines.push("incompatibleResourcePacks:[]".to_string());
    }
    fs::write(options, format!("{}\n", lines.join("\n"))).map_err(error_text)
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
        let managed = (name.starts_with("cg-client") || name.starts_with("cg-mod"))
            && (name.ends_with(".jar") || name.ends_with(".jar.disabled"));
        if managed {
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
        if name.starts_with("cg-client") && name.ends_with(".jar") {
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
        if name.starts_with("payload-") && name.ends_with(".jar") {
            let _ = fs::remove_file(path);
        }
    }

    Ok(())
}

fn prepare_launch_payload(profile: &str, source: &Path, file_name: &str) -> Result<PathBuf, String> {
    if !source.is_file() {
        return Err(format!("Managed client payload is missing: {}", display_path(source)));
    }
    let folder = profile_data_folder(profile).join("launch");
    fs::create_dir_all(&folder).map_err(error_text)?;
    let safe_name = Path::new(file_name)
        .file_name()
        .and_then(|value| value.to_str())
        .filter(|value| !value.trim().is_empty())
        .unwrap_or("cg-client.jar");
    let target = folder.join(format!("payload-{}-{}-{safe_name}", timestamp(), process_id()));
    fs::copy(source, &target).map_err(error_text)?;
    Ok(target)
}

fn cleanup_launch_payload(path: &Path) {
    let _ = fs::remove_file(path);
}

fn cleanup_active_launch_payload() -> Result<(), String> {
    let payload = {
        let mut active = ACTIVE_LAUNCH_PAYLOAD.lock().map_err(error_text)?;
        active.take()
    };
    if let Some(path) = payload {
        cleanup_launch_payload(&path);
    }
    Ok(())
}

fn ensure_loader_jar(profile: &str) -> Result<(), String> {
    let mods = mods_folder(profile);
    fs::create_dir_all(&mods).map_err(error_text)?;
    let loader = mods.join(LOADER_JAR_NAME);

    let mut buffer = Cursor::new(Vec::new());
    {
        let mut zip = ZipWriter::new(&mut buffer);
        let options = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);
        zip.start_file("fabric.mod.json", options).map_err(error_text)?;
        let json = format!(
            "{{\"schemaVersion\":1,\"id\":\"gamble-client-loader\",\"version\":\"{}\",\"name\":\"Gamble Client Loader\",\"description\":\"Launcher-managed bootstrap marker for Gamble Client.\",\"authors\":[\"Gamble Client\"],\"environment\":\"client\",\"depends\":{{\"fabricloader\":\">=0.18.0\"}}}}",
            VERSION
        );
        zip.write_all(json.as_bytes()).map_err(error_text)?;
        zip.finish().map_err(error_text)?;
    }
    fs::write(loader, buffer.into_inner()).map_err(error_text)
}

fn find_verified_client_install(profile: &str, manifest: &ManifestResponse) -> Option<PathBuf> {
    client_install_candidates(profile, manifest)
        .into_iter()
        .find(|path| path.is_file() && verify_file(path, manifest.size, &manifest.sha256).is_ok())
}

fn client_install_candidates(profile: &str, manifest: &ManifestResponse) -> Vec<PathBuf> {
    vec![payload_file(profile, &manifest.file_name)]
}

fn ensure_fabric_api(profile: &str) -> Result<(), String> {
    let mods = mods_folder(profile);
    fs::create_dir_all(&mods).map_err(error_text)?;
    if find_managed_mod_jar(&mods, "fabric-api-", false)?.is_some() {
        return Ok(());
    }

    if let Some(disabled) = find_managed_mod_jar(&mods, "fabric-api-", true)? {
        let target = PathBuf::from(disabled.to_string_lossy().trim_end_matches(".disabled"));
        fs::rename(disabled, target).map_err(error_text)?;
        return Ok(());
    }

    let release = fetch_modrinth_release(&modrinth_versions_url("fabric-api"))?;
    if release.file_name.trim().is_empty() || release.url.trim().is_empty() {
        return Err(format!("Could not find Fabric API for Minecraft {MINECRAFT_VERSION}."));
    }

    let target = mods.join(release.file_name);
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
    let files = version.get("files").and_then(|value| value.as_array()).cloned().unwrap_or_default();
    let selected = files
        .iter()
        .find(|file| json_bool_value(file.get("primary").unwrap_or(&serde_json::Value::Bool(false))))
        .or_else(|| files.first())
        .cloned()
        .unwrap_or_else(|| json!({}));
    Ok(ModrinthRelease {
        file_name: json_string(&selected, "filename"),
        url: json_string(&selected, "url"),
    })
}

fn find_managed_mod_jar(mods: &Path, prefix: &str, include_disabled: bool) -> Result<Option<PathBuf>, String> {
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
        if lower.starts_with(prefix) && (lower.ends_with(".jar") || (include_disabled && lower.ends_with(".jar.disabled"))) {
            return Ok(Some(path));
        }
    }
    Ok(None)
}

fn is_required_mod_name(lower_name: &str) -> bool {
    let base = lower_name.trim_end_matches(".disabled");
    base == LOADER_JAR_NAME || base == "fabric-api.jar" || base.starts_with("fabric-api-")
}

fn modrinth_versions_url(slug: &str) -> String {
    format!(
        "https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{MINECRAFT_VERSION}%22%5D"
    )
}

fn write_install_marker(profile: &str, build: &str, manifest: &ManifestResponse, installed: &Path) -> Result<(), String> {
    let folder = profile_data_folder(profile);
    fs::create_dir_all(&folder).map_err(error_text)?;
    fs::write(folder.join("installed-build.txt"), format!("{build}\n{}\n", manifest.file_name)).map_err(error_text)?;
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
        if let Some(account) = accounts.iter().find(|account| account.uuid.eq_ignore_ascii_case(&uuid)) {
            return Ok(Some(account.clone()));
        }
    }
    Ok(accounts.first().cloned())
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

    accounts.retain(|account| !account.refresh_token.trim().is_empty() && !account.uuid.trim().is_empty());
    Ok(accounts)
}

fn upsert_microsoft_account(accounts: &mut Vec<MicrosoftAccount>, mut account: MicrosoftAccount) {
    account.uuid = account.uuid.replace('-', "");
    if account.refresh_token.trim().is_empty() || account.uuid.trim().is_empty() {
        return;
    }
    if let Some(existing) = accounts.iter_mut().find(|existing| existing.uuid.eq_ignore_ascii_case(&account.uuid)) {
        *existing = account;
    } else {
        accounts.push(account);
    }
}

fn write_microsoft_account_list(accounts: &[MicrosoftAccount]) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    fs::write(
        microsoft_accounts_file(),
        serde_json::to_string_pretty(accounts).map_err(error_text)? + "\n",
    )
    .map_err(error_text)
}

fn selected_microsoft_uuid() -> Option<String> {
    read_trimmed(&selected_microsoft_account_file()).ok().filter(|value| !value.trim().is_empty())
}

fn save_selected_microsoft_uuid(uuid: &str) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    fs::write(selected_microsoft_account_file(), format!("{}\n", uuid.trim().replace('-', ""))).map_err(error_text)
}

fn save_legacy_microsoft_account(account: &MicrosoftAccount) -> Result<(), String> {
    fs::create_dir_all(launcher_data_folder()).map_err(error_text)?;
    fs::write(
        microsoft_account_file(),
        serde_json::to_string_pretty(account).map_err(error_text)? + "\n",
    )
    .map_err(error_text)
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
        expires_in_seconds: json_u64(body, "expires_in"),
    })
}

fn exchange_microsoft_for_minecraft(microsoft_access_token: &str) -> Result<MinecraftProfile, String> {
    let xbox = request_xbox_token(microsoft_access_token)?;
    let xsts = request_xsts_token(&xbox.token)?;
    let minecraft = request_minecraft_token(&xsts.user_hash, &xsts.token)?;
    let mut profile = request_minecraft_profile(&minecraft.access_token)?;
    profile.xuid = if xsts.xuid.trim().is_empty() { xbox.xuid } else { xsts.xuid };
    profile.access_token = minecraft.access_token;
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
    Ok(XboxToken { token, user_hash, xuid })
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
    Ok(MinecraftToken { access_token })
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
    })
}

fn post_json(url: &str, body: &serde_json::Value, bearer_token: &str) -> Result<serde_json::Value, String> {
    let mut request = http_client()?.post(url).json(body);
    if !bearer_token.trim().is_empty() {
        request = request.bearer_auth(bearer_token.trim());
    }
    let response = request.send().map_err(error_text)?;
    let status = response.status();
    let text = response.text().map_err(error_text)?;
    let body = if text.trim().is_empty() {
        json!({})
    } else {
        serde_json::from_str::<serde_json::Value>(&text).unwrap_or_else(|_| json!({ "message": text }))
    };
    if !status.is_success() {
        let message = json_string(&body, "message");
        return Err(if message.trim().is_empty() {
            format!("Backend returned HTTP {}", status.as_u16())
        } else {
            message
        });
    }
    Ok(body)
}

fn fetch_client_manifest(build: &str, token: &str) -> Result<ManifestResponse, String> {
    let body = post_json(
        &format!("{SITE_URL}/api/launcher/manifest"),
        &json!({ "build": build }),
        token,
    )?;
    serde_json::from_value::<ManifestResponse>(body).map_err(error_text)
}

fn fetch_launcher_version_info() -> Result<LauncherVersionResponse, String> {
    http_client()?
        .get(format!("{SITE_URL}/api/launcher/version"))
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json::<LauncherVersionResponse>()
        .map_err(error_text)
}

fn preferred_launcher_download(info: &LauncherVersionResponse) -> LauncherDownload {
    let platform = match env::consts::OS {
        "windows" => usable_launcher_download(info.downloads.windows.clone()),
        "linux" => match linux_package_preference() {
            "rpm" => usable_launcher_download(info.downloads.linux_rpm.clone()).or_else(|| usable_launcher_download(info.downloads.linux_app_image.clone())),
            "deb" => usable_launcher_download(info.downloads.linux_deb.clone()).or_else(|| usable_launcher_download(info.downloads.linux_app_image.clone())),
            _ => usable_launcher_download(info.downloads.linux_app_image.clone())
                .or_else(|| usable_launcher_download(info.downloads.linux_rpm.clone()))
                .or_else(|| usable_launcher_download(info.downloads.linux_deb.clone())),
        },
        _ => None,
    };
    platform
        .or_else(|| usable_launcher_download(info.downloads.jar.clone()))
        .unwrap_or_else(|| LauncherDownload {
            file_name: info.file_name.clone(),
            download_url: info.download_url.clone(),
        })
}

fn usable_launcher_download(download: Option<LauncherDownload>) -> Option<LauncherDownload> {
    download.filter(|item| !item.file_name.trim().is_empty() && !item.download_url.trim().is_empty())
}

fn linux_package_preference() -> &'static str {
    let text = fs::read_to_string("/etc/os-release").unwrap_or_default().to_lowercase();
    if text.contains("fedora") || text.contains("rhel") || text.contains("centos") || text.contains("suse") {
        "rpm"
    } else if text.contains("debian") || text.contains("ubuntu") || text.contains("linuxmint") || text.contains("pop") {
        "deb"
    } else {
        "appimage"
    }
}

fn verify_file(path: &Path, expected_size: u64, expected_sha: &str) -> Result<(), String> {
    let metadata = path.metadata().map_err(error_text)?;
    if expected_size > 0 && metadata.len() != expected_size {
        return Err(format!("Expected {expected_size} bytes but found {} bytes.", metadata.len()));
    }
    if !expected_sha.trim().is_empty() {
        let mut file = File::open(path).map_err(error_text)?;
        let mut data = Vec::new();
        file.read_to_end(&mut data).map_err(error_text)?;
        let actual = sha256_hex(&data);
        if !actual.eq_ignore_ascii_case(expected_sha.trim()) {
            return Err(format!("Expected SHA-256 {} but found {}.", expected_sha, actual));
        }
    }
    Ok(())
}

fn display_version(manifest: &ManifestResponse) -> String {
    if !manifest.build_version.trim().is_empty() {
        return manifest.build_version.clone();
    }
    manifest.file_name.clone()
}

fn sha256_hex(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    format!("{:x}", hasher.finalize())
}

fn http_client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent(format!("GambleClientLauncher/{VERSION}"))
        .build()
        .map_err(error_text)
}

fn json_string(body: &serde_json::Value, key: &str) -> String {
    body.get(key)
        .and_then(|value| value.as_str())
        .unwrap_or("")
        .to_string()
}

fn json_u64(body: &serde_json::Value, key: &str) -> u64 {
    body.get(key)
        .and_then(|value| value.as_u64().or_else(|| value.as_i64().map(|v| v.max(0) as u64)))
        .unwrap_or(0)
}

fn json_bool_value(value: &serde_json::Value) -> bool {
    value
        .as_bool()
        .or_else(|| value.as_i64().map(|number| number != 0))
        .or_else(|| value.as_str().map(|text| matches!(text.to_ascii_lowercase().as_str(), "true" | "1" | "on" | "yes")))
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

fn read_trimmed(path: &Path) -> Result<String, String> {
    fs::read_to_string(path).map(|value| value.trim().to_string()).map_err(error_text)
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
    error.to_string()
}

fn open_external(target: &str) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    let mut command = {
        let mut command = Command::new("cmd");
        command.args(["/C", "start", "", target]);
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

    command.spawn().map(|_| ()).map_err(|error| format!("Could not open: {error}"))
}
