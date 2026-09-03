#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 /path/to/gamble-client-launcher.jar" >&2
    exit 2
fi

jar_path="$1"
if [[ ! -f "$jar_path" ]]; then
    echo "Launcher JAR does not exist: $jar_path" >&2
    exit 1
fi

launcher_repo="$(cd "$(dirname "$jar_path")/../.." && pwd)"
jar_path="$(cd "$(dirname "$jar_path")" && pwd)/$(basename "$jar_path")"
javafx_version="${JAVAFX_VERSION:-$(sed -nE 's/^val javafxVersion = "([^"]+)"/\1/p' "$launcher_repo/build.gradle.kts" | head -n 1)}"
launcher_user_home="${HOME:-}"
gradle_home="${GRADLE_USER_HOME:-$launcher_user_home/.gradle}"
gradle_cache="$gradle_home/caches/modules-2/files-2.1/org.openjfx"

if [[ -z "$javafx_version" || ! -d "$gradle_cache" ]]; then
    echo "Could not locate the OpenJFX Gradle cache." >&2
    exit 1
fi

lipo_tool="${LIPO_TOOL:-}"
if [[ -z "$lipo_tool" ]]; then
    for candidate in lipo llvm-lipo /usr/bin/lipo /usr/bin/llvm-lipo /usr/lib64/rocm/llvm/bin/llvm-lipo; do
        if [[ "$candidate" == */* ]]; then
            [[ -x "$candidate" ]] || continue
            lipo_tool="$candidate"
        else
            lipo_tool="$(command -v "$candidate" 2>/dev/null || true)"
        fi
        [[ -n "$lipo_tool" ]] && break
    done
fi
if [[ -z "$lipo_tool" ]]; then
    echo "A lipo-compatible tool is required to make the macOS JAR work on Intel and Apple Silicon." >&2
    exit 1
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/gamble-macos-javafx.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
working_jar="$work_dir/launcher.jar"
cp "$jar_path" "$working_jar"

find_platform_jar() {
    local module="$1"
    local platform="$2"
    find "$gradle_cache/javafx-$module/$javafx_version" -type f \
        -name "javafx-$module-$javafx_version-$platform.jar" -print -quit 2>/dev/null
}

declare -A processed=()
for module in base graphics controls; do
    mac_jar="$(find_platform_jar "$module" mac)"
    arm_jar="$(find_platform_jar "$module" mac-aarch64)"
    if [[ -z "$mac_jar" || -z "$arm_jar" ]]; then
        echo "Missing macOS OpenJFX artifacts for module $module (version $javafx_version)." >&2
        exit 1
    fi

    while IFS= read -r entry; do
        [[ -n "$entry" ]] || continue
        name="${entry##*/}"
        [[ -n "${processed[$name]:-}" ]] && continue
        processed["$name"]=1

        unzip -p "$mac_jar" "$entry" > "$work_dir/$name.x86_64"
        unzip -p "$arm_jar" "$entry" > "$work_dir/$name.arm64"
        "$lipo_tool" -create "$work_dir/$name.x86_64" "$work_dir/$name.arm64" \
            -output "$work_dir/$name"

        info="$($lipo_tool -info "$work_dir/$name" 2>&1)"
        [[ "$info" == *x86_64* && "$info" == *arm64* ]] || {
            echo "Failed to create a fat macOS binary for $name: $info" >&2
            exit 1
        }
        zip -q -d "$working_jar" "$name" >/dev/null
        zip -q -j "$working_jar" "$work_dir/$name"
    done < <(jar tf "$mac_jar" | awk '/\.dylib$/ { print }')
done

mv "$working_jar" "$jar_path"
echo "Created macOS universal JavaFX natives in $jar_path"
