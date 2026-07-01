# Gamble Client Launcher - Open Source Version

This folder is a buildable, source-visible copy of the Gamble Client launcher. It is here so users can inspect how the launcher works without changing the existing root of this repository.

## What is included

- The Tauri launcher UI and native launcher code.
- The JavaFX fallback launcher.
- Build scripts, Gradle wrapper files, Cargo lockfile, and package lockfile.
- Public endpoints used by the launcher to fetch release metadata and client payloads.

## What is not included

- The protected Gamble Client mod payload.
- Release signing keys, R2 credentials, Discord bot tokens, Stripe keys, or any other private deployment secrets.
- Built artifacts such as `node_modules`, `dist`, Gradle output, or `src-tauri/target`.

## Build the Tauri launcher

Install Node.js, Rust, Cargo, and the native WebKit/GTK development libraries for your platform.

Fedora example:

```bash
sudo dnf install gtk3-devel webkit2gtk4.1-devel libsoup3-devel pango-devel gdk-pixbuf2-devel atk-devel cairo-devel
npm install
npm run build
npm run tauri:build:linux
```

General development commands:

```bash
npm install
npm run dev
npm run build
npm run tauri:build
```

## Build the Java fallback launcher

```bash
./gradlew jar
```

The launcher jar is written to:

```text
build/libs/gamble-client-launcher-0.1.75.jar
```

## Notes for reviewers

The launcher downloads public release metadata from Gamble Client services, verifies downloaded payload hashes, stages the client jar for the current Minecraft launch, and removes temporary launch payloads after Minecraft closes. The protected client jar is intentionally downloaded at runtime instead of being stored in this source folder.
