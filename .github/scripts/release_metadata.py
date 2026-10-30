#!/usr/bin/env python3
"""Validate Android version tags and prepare reviewable release metadata."""
import json
import os
from pathlib import Path
import re
import subprocess


def main():
    build = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    ref = os.environ.get("GITHUB_REF", "")
    if ref.startswith("refs/tags/") and ref.removeprefix("refs/tags/") != f"v{version}":
        raise SystemExit(f"Tag {ref} does not match Android versionName {version}")
    notes = Path("RELEASE_NOTES.md").read_text(encoding="utf-8").strip()
    if not notes or any(not line.startswith("- ") for line in notes.splitlines()):
        raise SystemExit("RELEASE_NOTES.md must contain one Markdown bullet per line")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    output = Path("build/ci")
    output.mkdir(parents=True, exist_ok=True)
    (output / "RELEASE_NOTES.md").write_text(notes + "\n", encoding="utf-8")
    (output / "ReleaseNotes.json").write_text(json.dumps({"version": version, "sourceRevision": revision}, indent=2) + "\n")
    (output / "release_body.md").write_text(
        f"## PicaX Android {version}\n\n适用于 Android 13 及以上。下载 `PicaX.apk` 安装。\n\n"
        f"{notes}\n\nCommit：`{revision}`\n构建编号：{os.environ.get('GITHUB_RUN_NUMBER', 'local')}\n", encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as target:
            target.write(f"version={version}\n")
    print(f"Android version: {version}")


if __name__ == "__main__":
    main()
