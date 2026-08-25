API_RELEASE_AAR_PATH := `realpath -m api/build/outputs/aar/api-release.aar`
COMPAT_RELEASE_AAR_PATH := `realpath -m compat/build/outputs/aar/compat-release.aar`

RELEASE_DEX_PATH := `realpath -m dist/dex/release/classes.dex`
DEBUG_DEX_PATH := `realpath -m dist/dex/debug/classes.dex`

# the copy-paste loader consumers embed into their own plugin
LOADER_PY := "loader/badges_sdk.py"
DIST_DIR := "dist"
DIST_LOADER := DIST_DIR + "/badges-sdk-loader.py"

# dev-only plugin that loads the engine through that same loader
DEV_PLUGIN_PY := "dev/badges-sdk-dev.py"

# fail early if the tools a recipe needs are not installed
[private]
_require +COMMANDS:
    #!/usr/bin/env bash
    set -euo pipefail

    missing=()
    for cmd in {{ COMMANDS }}; do
        command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
    done

    if [ ${#missing[@]} -ne 0 ]; then
        echo "missing required commands: ${missing[*]}" >&2
        exit 1
    fi

# build dex in debug mode
dex: (_require "java")
    ./gradlew buildDexDebug

# build the consumer-side compat aar and copy it into a plugin that uses the SDK
compat OUTPUT: (_require "java")
    ./gradlew compat:assembleRelease
    cp {{ COMPAT_RELEASE_AAR_PATH }} '{{ OUTPUT }}'

# embed a DEX (default: release) into a distributable copy of the loader
embed DEX_PATH=RELEASE_DEX_PATH OUTPUT=DIST_LOADER SOURCE=LOADER_PY: (_require "uv")
    #!/usr/bin/env bash
    set -euo pipefail
    mkdir -p "$(dirname '{{ OUTPUT }}')"
    uv run python tools/embed_dex.py '{{ DEX_PATH }}' '{{ SOURCE }}' '{{ OUTPUT }}'

# stamp the version, build the release DEX and compat AAR, assemble the release artifacts
ci-release VERSION OUTPUT_DIR=DIST_DIR: (_require "java" "uv")
    #!/usr/bin/env bash
    set -euo pipefail

    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT

    cp '{{ LOADER_PY }}' "$tmp/{{ file_name(LOADER_PY) }}"
    cp pyproject.toml "$tmp/pyproject.toml"

    uv run python scripts/prepare_release.py \
        --version '{{ VERSION }}' \
        --loader-file "$tmp/{{ file_name(LOADER_PY) }}" \
        --pyproject-file "$tmp/pyproject.toml"

    ./gradlew buildDexRelease
    ./gradlew compat:assembleRelease

    mkdir -p '{{ OUTPUT_DIR }}'
    just embed '{{ RELEASE_DEX_PATH }}' '{{ OUTPUT_DIR }}/badges-sdk-loader.py' "$tmp/{{ file_name(LOADER_PY) }}"
    cp '{{ RELEASE_DEX_PATH }}' '{{ OUTPUT_DIR }}/badges-sdk.dex'
    cp '{{ COMPAT_RELEASE_AAR_PATH }}' '{{ OUTPUT_DIR }}/badges-sdk-compat.aar'

# watch the loader + dev plugin + debug DEX and live-reload on device via extera dev-sync
watch *ARGS: (_require "uv" "adb")
    uv run python tools/dev_watch.py '{{ DEV_PLUGIN_PY }}' '{{ DEBUG_DEX_PATH }}' \
        --loader '{{ LOADER_PY }}' {{ ARGS }}

# generate new Telegram[-compile].jar from updated extera/Ayu-Gram apk
update-apk PATH_TO_APK: (_require "dex2jar" "jbang" "git")
    #!/usr/bin/env bash
    set -veuo pipefail

    # create task temp dir
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT

    # copy provided apk into temp dir
    cp {{ PATH_TO_APK }} "$tmp/Telegram.apk"

    # convert apk to jar
    dex2jar -f -o "$tmp/Telegram.jar" "$tmp/Telegram.apk"

    # fix class inheritance and exclude unneded packages
    jbang ./tools/FixTelegramJar.java "$tmp/Telegram.jar" "$tmp/Telegram-compile.jar"

    # copy generated jars
    mkdir ./libs/
    cp "$tmp/Telegram.jar" ./libs/Telegram.jar
    cp "$tmp/Telegram-compile.jar" ./libs/Telegram-compile.jar

    # and commit them
    git add -N -- ./libs/Telegram.jar ./libs/Telegram-compile.jar
    git commit -m "chore: bump telegram version" -- ./libs/Telegram.jar ./libs/Telegram-compile.jar

# generate stubs for python
gen-stubs PATH_TO_RT_JAR PATH_TO_ANDROID_JAR: (_require "java2pyi")
    java2pyi {{ PATH_TO_RT_JAR }} {{ PATH_TO_ANDROID_JAR }} ./libs/Telegram.jar -o stubs/

# rename plugin package/id/name and move sources (e.g. just rename com.example.myplugin my-plugin "My Plugin")
init NEW_PACKAGE NEW_ID NEW_NAME: (_require "uv")
    #!/usr/bin/env bash
    set -euo pipefail

    new_package='{{ NEW_PACKAGE }}'
    new_id='{{ NEW_ID }}'
    new_name='{{ NEW_NAME }}'

    if ! [[ "$new_package" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
        echo "invalid package: $new_package (expected e.g. com.example.myplugin)" >&2
        exit 1
    fi

    if ! [[ "$new_id" =~ ^[a-z0-9][a-z0-9._-]*$ ]]; then
        echo "invalid plugin id: $new_id (expected e.g. my-plugin)" >&2
        exit 1
    fi

    # current values are read from the sources, so the recipe can be run repeatedly
    old_package=$(sed -n 's/^ *namespace = "\(.*\)"$/\1/p' build.gradle.kts)
    old_py='{{ DEV_PLUGIN_PY }}'
    # the dev plugin is the only file carrying an __id__; the SDK id is that one
    # without the -dev suffix
    old_id=$(sed -n 's/^__id__ = "\(.*\)-dev"$/\1/p' "$old_py")
    old_name=$(sed -n 's/^rootProject.name = "\(.*\)"$/\1/p' settings.gradle.kts)

    if [ -z "$old_package" ] || [ ! -f "$old_py" ] || [ -z "$old_id" ]; then
        echo "failed to detect current plugin package/id" >&2
        exit 1
    fi

    old_path="src/main/kotlin/${old_package//.//}"
    new_path="src/main/kotlin/${new_package//.//}"

    echo "package: $old_package -> $new_package"
    echo "id:      $old_id -> $new_id"
    echo "name:    $old_name -> $new_name"

    # move the sources into the new package folder
    if [ "$old_path" != "$new_path" ]; then
        mkdir -p "$(dirname "$new_path")"
        mv "$old_path" "$new_path"

        # drop the package folders left empty by the move
        old_parent=$(dirname "$old_path")
        while [ "$old_parent" != "src/main/kotlin" ] && rmdir "$old_parent" 2>/dev/null; do
            old_parent=$(dirname "$old_parent")
        done
    fi

    # package references, both dotted (kotlin) and slashed (relocation/proguard config)
    files=(build.gradle.kts proguard-rules.pro "$old_py" '{{ LOADER_PY }}')
    while IFS= read -r -d '' file; do
        files+=("$file")
    done < <(find src -name '*.kt' -print0)

    sed -i \
        -e "s|${old_package//./\\.}|${new_package}|g" \
        -e "s|${old_package//.//}|${new_package//.//}|g" \
        -e "s|${old_id}|${new_id}|g" \
        "${files[@]}"

    # plugin metadata
    sed -i "s|^__name__ = \".*\"$|__name__ = \"${new_name}\"|" "$old_py"
    sed -i "s|^rootProject.name = \".*\"$|rootProject.name = \"${new_name}\"|" settings.gradle.kts
    sed -i "s|^name = \".*\"$|name = \"${new_id}\"|" pyproject.toml

    new_py="$(dirname "$old_py")/${new_id}-dev.py"

    if [ "$old_py" != "$new_py" ]; then
        mv "$old_py" "$new_py"
    fi

    uv sync

    echo "done, run 'just dex' to rebuild"
