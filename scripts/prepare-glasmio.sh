#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
upstream_dir="$project_dir/vendor/glasmio"
output_dir="$project_dir/build/glasmio"
patch_file="$project_dir/patches/glasmio.patch"

if [[ ! -f "$upstream_dir/glasmio-glass/build.gradle.kts" ]]; then
    echo 'Glasmio submodule is missing. Run: git submodule update --init --recursive' >&2
    exit 1
fi
if ! git -C "$upstream_dir" diff --quiet || ! git -C "$upstream_dir" diff --cached --quiet; then
    echo 'Keep Glasmio changes in patches/glasmio.patch before building.' >&2
    exit 1
fi
fingerprint="$({ git -C "$upstream_dir" rev-parse HEAD; shasum -a 256 "$patch_file" "$0"; } | shasum -a 256 | awk '{print $1}')"
if [[ -f "$output_dir/.prepared" && "$(cat "$output_dir/.prepared")" == "$fingerprint" ]]; then
    printf '%s\n' "$fingerprint"
    exit 0
fi
mkdir -p "$project_dir/build"
staging_dir="$(mktemp -d "$project_dir/build/glasmio.prepare.XXXXXX")"
trap 'rm -rf "$staging_dir"' EXIT

git -C "$upstream_dir" archive HEAD -- glasmio-core glasmio-glass glasmio-icons glasmio-preference glasmio-scaffold glasmio-ui LICENSE THIRD_PARTY_NOTICES.md | tar -x -C "$staging_dir"
GIT_CEILING_DIRECTORIES="$project_dir/build" git -C "$staging_dir" apply --check "$patch_file"
GIT_CEILING_DIRECTORIES="$project_dir/build" git -C "$staging_dir" apply "$patch_file"
printf '%s\n' "$fingerprint" > "$staging_dir/.prepared"
mkdir -p "$output_dir"
rsync -a --delete --exclude=build/ --exclude=.cxx/ --exclude=.externalNativeBuild/ "$staging_dir/" "$output_dir/"
printf '%s\n' "$fingerprint"
