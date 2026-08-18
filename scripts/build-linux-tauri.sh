#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export PATH="$HOME/.cargo/bin:$PATH"

existing_rustflags="${RUSTFLAGS:-}"
release_remap_flags="--remap-path-prefix=$ROOT=/src/gamble-client-launcher --remap-path-prefix=$HOME/.cargo=/rust/cargo"
export RUSTFLAGS="${existing_rustflags:+$existing_rustflags }$release_remap_flags"

if [[ -d "$ROOT/.tauri-sysroot/usr/lib64/pkgconfig" ]]; then
  # The local pkg-config catalog supplies metadata only. Resolving its paths
  # against the real root keeps developer workstation paths out of binaries.
  export PKG_CONFIG_SYSROOT_DIR="/"
  export PKG_CONFIG_LIBDIR="$ROOT/.tauri-sysroot/usr/lib64/pkgconfig:$ROOT/.tauri-sysroot/usr/share/pkgconfig"
  export PKG_CONFIG_PATH="$ROOT/.tauri-sysroot/usr/lib64/pkgconfig:$ROOT/.tauri-sysroot/usr/share/pkgconfig"
fi

npm run tauri -- build --bundles rpm,deb
