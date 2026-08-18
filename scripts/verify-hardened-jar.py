#!/usr/bin/env python3
from __future__ import annotations

import sys
import zipfile
from pathlib import Path


archive_path = Path(sys.argv[1])
if not archive_path.is_file():
    raise SystemExit(f"FAIL: hardened launcher is missing: {archive_path}")

with zipfile.ZipFile(archive_path) as archive:
    app_entries = [
        name for name in archive.namelist()
        if name.startswith("com/gambleclient/launcher/") and name.endswith(".class")
    ]
    if "com/gambleclient/launcher/LauncherBootstrap.class" not in app_entries:
        raise SystemExit("FAIL: hardened launcher entrypoint is missing")

    descriptive = ("LauncherSession", "LaunchTicket", "MicrosoftToken", "VersionProfile", "LauncherManifest")
    leaked = next((name for name in app_entries if any(marker in name for marker in descriptive)), None)
    if leaked:
        raise SystemExit(f"FAIL: launcher implementation name leaked: {leaked}")

    debug_markers = (b"LineNumberTable", b"LocalVariableTable", b"SourceFile")
    leaked = next((name for name in app_entries if any(marker in archive.read(name) for marker in debug_markers)), None)
    if leaked:
        raise SystemExit(f"FAIL: launcher debug metadata leaked: {leaked}")

print(f"Hardened launcher verified: {len(app_entries)} protected application classes")
