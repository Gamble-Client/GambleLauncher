use std::process::Command;

const VERSION: &str = "0.1.58";

#[tauri::command]
fn launcher_version() -> &'static str {
    VERSION
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

    #[cfg(target_os = "windows")]
    let mut command = {
        let mut command = Command::new("cmd");
        command.args(["/C", "start", "", &url]);
        command
    };

    #[cfg(target_os = "macos")]
    let mut command = {
        let mut command = Command::new("open");
        command.arg(&url);
        command
    };

    #[cfg(all(unix, not(target_os = "macos")))]
    let mut command = {
        let mut command = Command::new("xdg-open");
        command.arg(&url);
        command
    };

    command
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("Could not open URL: {error}"))
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![launcher_version, open_url])
        .run(tauri::generate_context!())
        .expect("error while running Gamble Client Launcher");
}
