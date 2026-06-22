use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256};
use std::{
    env,
    fs::{self, File},
    io::{self, Cursor, Read, Write},
    path::{Path, PathBuf},
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};
use walkdir::WalkDir;
use zip::{write::SimpleFileOptions, CompressionMethod, ZipWriter};

const VERSION: &str = "0.1.60";
const SITE_URL: &str = "https://gamble-client.store";
const LOADER_JAR_NAME: &str = "gamble-client-loader.jar";

#[derive(Serialize)]
struct LauncherInfo {
    version: &'static str,
    managed_root: String,
    data_folder: String,
    session_file: String,
    os: String,
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
struct Diagnostics {
    checks: Vec<DiagnosticCheck>,
}

#[derive(Serialize)]
struct DiagnosticCheck {
    label: String,
    ok: bool,
    detail: String,
}

#[derive(Serialize)]
struct InstallResult {
    file_name: String,
    build: String,
    build_version: String,
    path: String,
    size: u64,
    sha256: String,
    updated: bool,
    message: String,
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
    if !path.starts_with("/api/launcher/") {
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
fn ensure_profile(profile: String) -> Result<String, String> {
    ensure_profile_folders(profile_id(&profile)).map(|path| display_path(&path))
}

#[tauri::command]
fn list_local_files(profile: String, kind: String) -> Result<Vec<LocalFile>, String> {
    let profile = profile_id(&profile);
    let folder = if kind == "resourcepacks" {
        resource_packs_folder(profile)
    } else {
        mods_folder(profile)
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
        let locked = kind != "resourcepacks" && lower == LOADER_JAR_NAME;
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
    if kind != "resourcepacks" && path.file_name().and_then(|v| v.to_str()).unwrap_or("") == LOADER_JAR_NAME {
        return Err("The Gamble Client loader is required for this profile.".to_string());
    }

    let target = toggle_target(&path)?;
    fs::rename(&path, &target).map_err(error_text)?;
    if kind == "resourcepacks" {
        set_resource_pack_enabled(profile, &target, !target.to_string_lossy().ends_with(".disabled"))?;
    }
    Ok(())
}

#[tauri::command]
fn add_resource_packs(profile: String, paths: Vec<String>) -> Result<usize, String> {
    let profile = profile_id(&profile);
    let folder = resource_packs_folder(profile);
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
        set_resource_pack_enabled(profile, &target, true)?;
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
        "mods" => mods_folder(profile),
        "resourcepacks" => resource_packs_folder(profile),
        "data" => profile_data_folder(profile),
        _ => minecraft_folder(profile),
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
    push_check(&mut checks, "Profile folder", minecraft_folder(profile).is_dir(), display_path(&minecraft_folder(profile)));
    push_check(&mut checks, "Mods folder", mods_folder(profile).is_dir(), display_path(&mods_folder(profile)));
    push_check(&mut checks, "Resource packs", resource_packs_folder(profile).is_dir(), display_path(&resource_packs_folder(profile)));
    push_check(&mut checks, "Launcher session", launcher_session_file().is_file(), display_path(&launcher_session_file()));
    push_check(&mut checks, "Microsoft account", microsoft_account_file().is_file(), display_path(&microsoft_account_file()));
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
fn install_client_manifest(profile: String, build: String, token: String) -> Result<InstallResult, String> {
    let profile = profile_id(&profile);
    if token.trim().is_empty() {
        return Err("Sign in before installing the client.".to_string());
    }
    if profile != "gamble-client" {
        return Err("Only the Gamble Client profile installs the managed client payload.".to_string());
    }
    ensure_profile_folders(profile)?;

    let client = reqwest::blocking::Client::builder()
        .user_agent(format!("GambleClientLauncher/{VERSION}"))
        .build()
        .map_err(error_text)?;
    let manifest: ManifestResponse = client
        .post(format!("{SITE_URL}/api/launcher/manifest"))
        .bearer_auth(token.trim())
        .json(&json!({ "build": build }))
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .json()
        .map_err(error_text)?;

    if manifest.file_name.is_empty() || manifest.download_url.is_empty() {
        return Err("Backend manifest did not include a jar download.".to_string());
    }

    let payload = payload_file(profile, &manifest.file_name);
    if payload.is_file() && verify_file(&payload, manifest.size, &manifest.sha256).is_ok() {
        cleanup_managed_mod_jars(profile)?;
        ensure_loader_jar(profile)?;
        write_install_marker(profile, &build, &manifest, &payload)?;
        return Ok(InstallResult {
            file_name: manifest.file_name.clone(),
            build: manifest.build.clone(),
            build_version: manifest.build_version.clone(),
            path: display_path(&payload),
            size: payload.metadata().map(|m| m.len()).unwrap_or(0),
            sha256: manifest.sha256.clone(),
            updated: false,
            message: format!("Latest managed client payload verified: {}", display_version(&manifest)),
        });
    }

    let bytes = client
        .get(&manifest.download_url)
        .send()
        .map_err(error_text)?
        .error_for_status()
        .map_err(error_text)?
        .bytes()
        .map_err(error_text)?;

    if manifest.size > 0 && bytes.len() as u64 != manifest.size {
        return Err(format!("Expected {} bytes but found {} bytes.", manifest.size, bytes.len()));
    }
    if !manifest.sha256.trim().is_empty() {
        let actual = sha256_hex(bytes.as_ref());
        if !actual.eq_ignore_ascii_case(manifest.sha256.trim()) {
            return Err(format!("Expected SHA-256 {} but found {}.", manifest.sha256, actual));
        }
    }

    fs::create_dir_all(payloads_folder(profile)).map_err(error_text)?;
    cleanup_managed_mod_jars(profile)?;
    ensure_loader_jar(profile)?;
    fs::write(&payload, bytes).map_err(error_text)?;
    write_install_marker(profile, &build, &manifest, &payload)?;

    Ok(InstallResult {
        file_name: manifest.file_name.clone(),
        build: manifest.build.clone(),
        build_version: manifest.build_version.clone(),
        path: display_path(&payload),
        size: payload.metadata().map(|m| m.len()).unwrap_or(0),
        sha256: manifest.sha256.clone(),
        updated: true,
        message: format!("Updated managed client payload to: {}", display_version(&manifest)),
    })
}

#[tauri::command]
fn launch_game_placeholder(has_microsoft: bool) -> Result<String, String> {
    if !has_microsoft {
        return Ok("Launching without a Microsoft account is not enabled in this Tauri test build yet. Add/select Microsoft in the Java launcher for real game launches while this native launch path is being ported.".to_string());
    }
    Ok("Native Minecraft process launching is still being ported. Install/update, account checks, mods, resource packs, ads, and diagnostics are available in this RPM test.".to_string())
}

#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    let allowed = [
        "https://gamble-client.store",
        "https://dash.gamble-client.store",
        "https://admin.gamble-client.store",
        "https://profile.gamble-client.store",
        "https://discord.gg",
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
            ensure_profile,
            list_local_files,
            toggle_local_file,
            add_resource_packs,
            open_path,
            open_profile_folder,
            diagnostics,
            install_client_manifest,
            launch_game_placeholder,
            open_url
        ])
        .run(tauri::generate_context!())
        .expect("error while running Gamble Client Launcher");
}

fn profile_id(value: &str) -> &'static str {
    match value {
        "vanilla" => "vanilla",
        "fabric" => "fabric",
        _ => "gamble-client",
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
    let home = env::var("HOME").unwrap_or_else(|_| ".".to_string());
    match env::consts::OS {
        "windows" => env::var("APPDATA")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from(home))
            .join("Gamble Client"),
        "macos" => PathBuf::from(home).join("Library/Application Support/Gamble Client"),
        _ => env::var("XDG_DATA_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from(home).join(".local/share"))
            .join("gamble-client"),
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

fn ensure_loader_jar(profile: &str) -> Result<(), String> {
    let mods = mods_folder(profile);
    fs::create_dir_all(&mods).map_err(error_text)?;
    let loader = mods.join(LOADER_JAR_NAME);
    if loader.is_file() {
        return Ok(());
    }

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
