# Gamble Client Launcher

Standalone launcher shell for Gamble Client.

Current flow:

- Opens the Gamble Client site sign-in from the top bar.
- Stores a launcher session token locally after browser sign-in succeeds.
- Requests the selected build manifest from the backend instead of asking for a URL or license key.
- Keeps the license in the shared managed `cg-mod` folder and mirrors it into the active profile for older builds.
- Ad-Tier free access is staged behind the site ad reward flow.
- Installs or updates the selected Gamble Client jar in the managed game folder, not the user's main `.minecraft`.
- Installs managed Fabric helper mods for Fabric profiles: Fabric API and Mod Menu.
- Downloads Minecraft/Fabric runtime files when missing.
- Launches Minecraft directly through Fabric Loader.
- Optionally links a Microsoft account for online Minecraft auth. Without it, the launcher falls back to the local/offline session.

Managed game folder:

- Linux: `~/.local/share/gamble-client/minecraft`
- Windows: `%APPDATA%/Gamble Client/minecraft`
- macOS: `~/Library/Application Support/Gamble Client/minecraft`

Override with `GAMBLE_CLIENT_GAME_DIR` or `-Dgamble.gameDir=/path/to/minecraft`.

Microsoft auth:

Create a Microsoft Entra app registration for the launcher and enable public-client/device-code use. Then run with one of:

```bash
GAMBLE_MICROSOFT_CLIENT_ID="application-client-id" ./gradlew run
```

```bash
java -Dgamble.microsoftClientId="application-client-id" -jar build/libs/gamble-client-launcher-0.1.0.jar
```

Build:

```bash
./gradlew clean build
```

Run:

```bash
./gradlew run
```
