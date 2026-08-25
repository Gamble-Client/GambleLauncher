# Gamble Client Launcher

The open-source desktop launcher for Gamble Client. The launcher source is
licensed under the [MIT License](LICENSE); protected Gamble Client mod code and
server-side services are separate projects and are not included here.

Source and issue tracker: [Gamble-Client/GambleLauncher](https://github.com/Gamble-Client/GambleLauncher).

Current flow:

- Opens the Gamble Client site sign-in from the top bar.
- Stores a launcher session token locally after browser sign-in succeeds.
- Requests the selected build manifest from the backend instead of asking for a URL or license key.
- Keeps the license in the shared managed `cg-mod` folder and mirrors it into the active profile for older builds.
- Ad-Tier free access is launcher-only and requires a completed in-launcher sponsor break before install or launch.
- Installs or updates the selected Gamble Client jar in the managed game folder, not the user's main `.minecraft`.
- Installs managed Fabric helper mods for Fabric profiles: Fabric API and Mod Menu.
- Downloads Minecraft/Fabric runtime files when missing.
- Launches Minecraft directly through Fabric Loader.
- Optionally links a Microsoft account for online Minecraft auth using the approved Gamble Client app registration. Without it, the launcher falls back to the local/offline session.

Managed game folder:

- Linux: `~/.local/share/gamble-client/minecraft`
- Windows: `%APPDATA%/Gamble Client/minecraft`
- macOS: `~/Library/Application Support/Gamble Client/minecraft`

Override with `GAMBLE_CLIENT_GAME_DIR` or `-Dgamble.gameDir=/path/to/minecraft`.

Profile files are kept below the managed game folder in `profiles/<profile>/`.
For Fabric profiles, add extra `.jar` files to that profile's `mods/` folder;
resource packs use the matching `resourcepacks/` folder. The launcher exposes
an **Open Folder** action in both managers so the exact path is visible without
guessing which package installed it.

Microsoft auth:

The launcher uses its registered public Microsoft app ID. Use the in-launcher
Microsoft Sign In button to link an online account.

Build:

```bash
./gradlew clean build
```

The web/Tauri shell can be built with:

```bash
npm ci
npm run build
```

## Security and privacy

- No deployment credentials, artifact-signing private keys, Discord tokens, or
  payment secrets belong in this repository.
- Microsoft authentication uses a public desktop-app client ID and PKCE. The
  launcher never accepts or embeds a Microsoft client secret.
- Local launcher and Microsoft sessions are runtime data and are ignored by
  Git.
- See [SECURITY.md](SECURITY.md) before reporting a vulnerability or publishing
  diagnostic output.

Native launcher image for the current OS:

```bash
./gradlew packageNativeImage
```

Linux Tauri bundles:

```bash
./scripts/build-linux-tauri.sh
sudo dnf install "./src-tauri/target/release/bundle/rpm/Gamble Client Launcher-0.1.70-1.x86_64.rpm"
```

On Fedora 44, graphical RPM installers can fail with `Id is out of bitmap range`
from the libdnf5/PackageKit session resolver. Install the same RPM with `dnf`
from a terminal when that happens.

Windows installer:

```powershell
.\gradlew.bat packageWindowsExe
```

The Windows `.exe` has to be built on Windows with `jpackage` and WiX installed. Linux builds produce a portable app image under `build/native/`.

Universal JAR macOS smoke check:

```bash
./gradlew hardenedLauncherJar
./scripts/merge-macos-javafx-natives.sh build/libs/gamble-client-launcher-0.1.125.jar
```

The release workflow combines the Intel and Apple Silicon JavaFX binaries in
the universal JAR. The normal `java -jar` path does not require an executable
bit or a special fallback-GUI permission; macOS may still ask for the usual
first-run accessibility or network approval when the launcher opens Minecraft.

Run:

```bash
./gradlew run
```
