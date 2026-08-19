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

    exposed_names = []
    for name in app_entries:
        if name == "com/gambleclient/launcher/LauncherBootstrap.class":
            continue
        stem = name.removeprefix("com/gambleclient/launcher/").removesuffix(".class")
        if not stem.isalpha() or len(stem) > 2:
            exposed_names.append(name)
    if exposed_names:
        raise SystemExit(f"FAIL: launcher class name was not obfuscated: {exposed_names[0]}")

    descriptive = ("LauncherSession", "LaunchTicket", "MicrosoftToken", "VersionProfile", "LauncherManifest")
    leaked = next((name for name in app_entries if any(marker in name for marker in descriptive)), None)
    if leaked:
        raise SystemExit(f"FAIL: launcher implementation name leaked: {leaked}")

    debug_markers = (b"LineNumberTable", b"LocalVariableTable", b"SourceFile")
    leaked = next((name for name in app_entries if any(marker in archive.read(name) for marker in debug_markers)), None)
    if leaked:
        raise SystemExit(f"FAIL: launcher debug metadata leaked: {leaked}")

    application_bytes = b"".join(archive.read(name) for name in app_entries)
    forbidden_markers = (
        b"license.txt",
        b"paste-your-license-key-here",
        b"gamble.siteUrl",
        b"GAMBLE_CLIENT_SITE_URL",
        b"gamble.microsoftClientId",
        b"GAMBLE_MICROSOFT_CLIENT_ID",
        b"/home/",
        b".cargo/registry",
    )
    leaked = next((marker for marker in forbidden_markers if marker in application_bytes), None)
    if leaked:
        raise SystemExit(f"FAIL: launcher contains retired configuration or build path: {leaked!r}")

print(f"Hardened launcher verified: {len(app_entries)} protected application classes")
