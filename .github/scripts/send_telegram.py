#!/usr/bin/env python3
"""Send one Android artifact; pin only a successfully published release."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile


def request(method, fields, document=None):
    token = os.environ["TELEGRAM_BOT_TOKEN"]
    with tempfile.TemporaryDirectory() as directory:
        config = Path(directory) / "curl.conf"
        response = Path(directory) / "response.json"
        # Keep the token out of command arguments and failure diagnostics.
        url = f"https://api.telegram.org/bot{token}/{method}"
        config.write_text("url = " + json.dumps(url) + "\n", encoding="utf-8")
        config.chmod(0o600)
        command = ["curl", "--config", str(config), "--silent", "--show-error", "--connect-timeout", "15", "--max-time", "600", "--output", str(response)]
        for key, value in fields.items():
            command += ["--form-string", f"{key}={value}"]
        if document:
            command += ["--form", f"document=@{document};type=application/vnd.android.package-archive"]
        subprocess.run(command, check=True)
        payload = json.loads(response.read_text(encoding="utf-8"))
        if not payload.get("ok"):
            raise RuntimeError(f"Telegram {method} failed: {payload.get('description', 'unknown error')}")
        return payload["result"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact", required=True, type=Path)
    parser.add_argument("--caption", required=True, type=Path)
    parser.add_argument("--pin", action="store_true")
    arguments = parser.parse_args()
    for name in ("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"):
        if not os.environ.get(name):
            raise SystemExit(f"Missing Actions secret: {name}")
    caption = arguments.caption.read_text(encoding="utf-8").strip()
    if arguments.artifact.name.endswith("-unsigned.apk"):
        caption = "Android 未签名构建\n" + caption
    # Bot API captions count UTF-16 code units, including supplementary characters.
    while len(caption.encode("utf-16-le")) // 2 > 1024:
        caption = caption[:-1]
    message = request("sendDocument", {"chat_id": os.environ["TELEGRAM_CHAT_ID"], "caption": caption}, arguments.artifact)
    if arguments.pin:
        request("pinChatMessage", {"chat_id": os.environ["TELEGRAM_CHAT_ID"], "message_id": str(message["message_id"]), "disable_notification": "false"})
    print("Android artifact delivered" + (" and pinned" if arguments.pin else ""))


if __name__ == "__main__":
    main()
