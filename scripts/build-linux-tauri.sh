#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export PATH="$HOME/.cargo/bin:$PATH"

if [[ -d "$ROOT/.tauri-sysroot/usr/lib64/pkgconfig" ]]; then
  export PKG_CONFIG_SYSROOT_DIR="$ROOT/.tauri-sysroot"
  export PKG_CONFIG_LIBDIR="$ROOT/.tauri-sysroot/usr/lib64/pkgconfig:$ROOT/.tauri-sysroot/usr/share/pkgconfig"
  export PKG_CONFIG_PATH="$ROOT/.tauri-sysroot/usr/lib64/pkgconfig:$ROOT/.tauri-sysroot/usr/share/pkgconfig"
fi

npm run tauri -- build --bundles rpm deb

if npm run tauri -- build --bundles appimage; then
  exit 0
fi

APPDIR="$ROOT/src-tauri/target/release/bundle/appimage/Gamble Client Launcher.AppDir"
APPIMAGE="$ROOT/src-tauri/target/release/bundle/appimage/Gamble Client Launcher_0.1.59_amd64.AppImage"
ICON="$APPDIR/Gamble Client Launcher.png"
PLUGIN="$HOME/.cache/tauri/linuxdeploy-plugin-appimage.AppImage"

if [[ ! -d "$APPDIR" || ! -f "$ICON" || ! -x "$PLUGIN" ]]; then
  echo "AppImage fallback cannot run; missing AppDir, icon, or linuxdeploy plugin." >&2
  exit 1
fi

cp "$ICON" "$APPDIR/gamble-client-launcher.png"
ARCH=x86_64 \
  LINUXDEPLOY_OUTPUT_VERSION=0.1.59 \
  LDAI_OUTPUT="$APPIMAGE" \
  "$PLUGIN" --appimage-extract-and-run --appdir "$APPDIR"
