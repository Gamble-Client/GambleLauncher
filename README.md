# Gamble Client Launcher

The open-source desktop launcher for Gamble Client. The launcher source is
licensed under the [MIT License](LICENSE); protected Gamble Client mod code and
server-side services are separate projects and are not included here.

Source and issue tracker: [Gamble-Client/GambleLauncher](https://github.com/Gamble-Client/GambleLauncher).

Current flow:

- Opens the Gamble Client site sign-in from the top bar.
- Stores a launcher session token locally after browser sign-in succeeds.
- Requests the selected build manifest from the backend instead of asking for a URL or license key.
- Installs a fresh authenticated standalone loader into the selected Gamble profile; the loader authorizes and loads the protected client in memory. No license file or protected client JAR is mirrored into profiles.
- Ad-Tier access to the Gamble profile requires a completed 30-second sponsor check in the Dashboard. Returning to Play refreshes access; plain Vanilla/Fabric profiles do not require a sponsor.
- First-run **Set up & play** prepares missing Minecraft, Fabric and loader files automatically. Profiles stay separate from the user's main `.minecraft`.
- Installs Fabric API for Fabric profiles; the Java distribution additionally manages Mod Menu.
- Downloads Minecraft/Fabric runtime files when missing.
- Launches Minecraft directly through Fabric Loader.
- Optionally links a Microsoft account for online Minecraft auth using the approved Gamble Client app registration. Without it, the launcher falls back to the local/offline session.

Managed game folder:

- Linux: `~/.local/share/gamble-client/minecraft`
- Windows: `%APPDATA%/Gamble Client/minecraft`
- macOS: `~/Library/Application Support/Gamble Client/minecraft`

Override with `GAMBLE_CLIENT_GAME_DIR`; the Java distribution also accepts `-Dgamble.gameDir=/path/to/minecraft`.

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
npm test
```

The isolated browser-fixture smoke is `node scripts/launcher-ui-smoke.mjs <dev-url> <output-dir>`
with Playwright installed. `LAUNCHER_PLAYWRIGHT_MODULE` and `LAUNCHER_CHROMIUM` can select existing
tooling. It exercises UI/state behavior, not production authentication or a real Minecraft launch.

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

Cross-distribution Flatpak bundle:

```bash
./gradlew test verifyHardenedLauncherJar stageFlatpakLauncher
flatpak-builder --user --force-clean --repo=flatpak-repo \
  flatpak-build flatpak/org.gambleclient.Launcher.yml
flatpak build-bundle flatpak-repo Gamble-Client-Launcher-0.1.132.flatpak \
  org.gambleclient.Launcher
flatpak install --user ./Gamble-Client-Launcher-0.1.132.flatpak
flatpak run org.gambleclient.Launcher
```

The Flatpak includes Java 21 and defaults to the Linux Swing interface, avoiding
WebKit and host-Java dependencies. Its filesystem access is limited to the shared
Gamble Client data folder and the standard `.minecraft` folder, so it can reuse
native-launcher profiles without receiving access to the rest of the home folder.

Windows installer:

```powershell
.\gradlew.bat packageWindowsExe
```

The Windows `.exe` has to be built on Windows with `jpackage` and WiX installed. Linux builds produce a portable app image under `build/native/`.

Universal JAR macOS smoke check:

```bash
./gradlew hardenedLauncherJar
./scripts/merge-macos-javafx-natives.sh build/libs/gamble-client-launcher-0.1.132.jar
```

The release workflow combines the Intel and Apple Silicon JavaFX binaries in
the universal JAR. The normal `java -jar` path does not require an executable
bit or a special fallback-GUI permission; macOS may still ask for the usual
first-run accessibility or network approval when the launcher opens Minecraft.

Run:

```bash
./gradlew run
```
