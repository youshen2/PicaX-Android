#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/ci
unsigned_apk=app/build/outputs/apk/release/app-release-unsigned.apk
if [[ -z "${ANDROID_KEYSTORE_BASE64:-}" ]]; then
    if [[ "${GITHUB_REF:-}" == refs/tags/* ]]; then
        echo '::error::Release tags require Android signing secrets.' >&2
        exit 1
    fi
    cp "$unsigned_apk" build/ci/PicaX-unsigned.apk
    echo 'signed=false' >> "$GITHUB_OUTPUT"
    exit 0
fi
: "${ANDROID_KEYSTORE_PASSWORD:?Missing ANDROID_KEYSTORE_PASSWORD}"
: "${ANDROID_KEY_ALIAS:?Missing ANDROID_KEY_ALIAS}"
: "${ANDROID_KEY_PASSWORD:?Missing ANDROID_KEY_PASSWORD}"
keystore_file="$(mktemp "${RUNNER_TEMP:-/tmp}/picax-signing.XXXXXX")"
trap 'rm -f "$keystore_file"' EXIT
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$keystore_file"
chmod 600 "$keystore_file"
build_tools="${ANDROID_HOME}/build-tools/37.0.0"
"$build_tools/zipalign" -f -p 4 "$unsigned_apk" build/ci/PicaX-aligned.apk
"$build_tools/apksigner" sign --ks "$keystore_file" --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD \
    --out build/ci/PicaX.apk build/ci/PicaX-aligned.apk
"$build_tools/apksigner" verify build/ci/PicaX.apk
rm build/ci/PicaX-aligned.apk
echo 'signed=true' >> "$GITHUB_OUTPUT"
