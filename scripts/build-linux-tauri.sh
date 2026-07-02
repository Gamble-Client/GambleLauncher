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

npm run tauri -- build --bundles rpm,deb
