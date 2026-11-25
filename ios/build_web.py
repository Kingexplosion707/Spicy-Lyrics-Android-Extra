#!/usr/bin/env python3
"""Builds the iOS app's bundled web app (ios/Web) and its icon (Assets.xcassets/AppIcon).

The web app is the desktop app (desktop/standalone, the Spicetify app with every theme and the
motion page) with host-ios.js in place of host.js. Needs npm and Pillow.
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
STANDALONE = REPO / "desktop/standalone"
WEB = HERE / "Web"
ASSETS = HERE / "SpicyLyricsPlus/Assets.xcassets"


def main():
    subprocess.run([sys.executable, str(STANDALONE / "build.py")], check=True)
    app = STANDALONE / "dist/app"
    if WEB.exists():
        shutil.rmtree(WEB)
    WEB.mkdir()
    for name in ("react.production.min.js", "react-dom.production.min.js", "spicy-lyrics-plus.js", "page.css", "icon.png"):
        shutil.copy(app / name, WEB / name)
    for name in ("host-ios.js", "page-ios.css"):
        shutil.copy(HERE / "web" / name, WEB / name)
    shutil.copy(HERE / "web/page-ios.html", WEB / "index.html")

    from PIL import Image

    src = next(p for p in [REPO / "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", *REPO.glob("app/src/main/res/mipmap-*/ic_launcher.png")] if p.exists())
    icon_dir = ASSETS / "AppIcon.appiconset"
    icon_dir.mkdir(parents=True, exist_ok=True)
    img = Image.open(src).convert("RGBA")
    # iOS masks the corners itself and wants no transparency.
    flat = Image.new("RGB", img.size, (11, 11, 16))
    flat.paste(img, mask=img.split()[3])
    flat.resize((1024, 1024), Image.LANCZOS).save(icon_dir / "icon-1024.png")
    (icon_dir / "Contents.json").write_text(json.dumps({
        "images": [{"filename": "icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
        "info": {"author": "xcode", "version": 1},
    }, indent=2))
    (ASSETS / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2))
    print(f"Built {WEB} and the app icon")


if __name__ == "__main__":
    main()
