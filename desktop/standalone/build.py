#!/usr/bin/env python3
"""Builds Spicy Lyrics Plus for outside Spotify: the desktop app and the Wallpaper Engine wallpaper.

Both are the Spicetify app (../spicetify, with every theme and the motion page) running on
host.js, which feeds it from the Live Lyrics relay. Output:

  dist/app/        served by the relay at http://127.0.0.1:8973/app/ (the desktop app window)
  dist/wallpaper/  a Wallpaper Engine web wallpaper (project.json)

Needs npm (for the React UMD files) and Pillow (icons).
"""
import json
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SPICETIFY = HERE.parent / "spicetify"
DIST = HERE / "dist"
REACT_VERSION = "17.0.2"
ICON = REPO / "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"


def run(*cmd, cwd=None):
    subprocess.run(cmd, cwd=cwd, check=True)


def react_files(into: Path):
    with tempfile.TemporaryDirectory() as tmp:
        for pkg, name in (("react", "react.production.min.js"), ("react-dom", "react-dom.production.min.js")):
            out = subprocess.run(["npm", "pack", f"{pkg}@{REACT_VERSION}", "--silent"], cwd=tmp, check=True, capture_output=True, text=True)
            tgz = Path(tmp) / out.stdout.strip().splitlines()[-1]
            with tarfile.open(tgz) as tar:
                member = tar.getmember(f"package/umd/{name}")
                data = tar.extractfile(member).read()
            (into / name).write_bytes(data)


def icons(into: Path):
    from PIL import Image

    src = ICON if ICON.exists() else next(REPO.glob("app/src/main/res/mipmap-*/ic_launcher.png"))
    img = Image.open(src).convert("RGBA")
    img.resize((256, 256), Image.LANCZOS).save(into / "icon.png")
    img.save(into / "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])


def main():
    run(sys.executable, str(SPICETIFY / "build.py"))
    bundle = SPICETIFY / "dist/spicy-lyrics-plus/index.js"

    if DIST.exists():
        shutil.rmtree(DIST)
    common = DIST / "_common"
    common.mkdir(parents=True)
    react_files(common)
    icons(common)
    shutil.copy(bundle, common / "spicy-lyrics-plus.js")
    shutil.copy(HERE / "host.js", common / "host.js")
    shutil.copy(HERE / "page.css", common / "page.css")
    page = (HERE / "page.html").read_text(encoding="utf-8")

    for kind, mode in (("app", "app"), ("wallpaper", "wallpaper")):
        out = DIST / kind
        shutil.copytree(common, out)
        html = page.replace("<!--MODE-->", f'<script>window.SLP_MODE = "{mode}";</script>')
        (out / "index.html").write_text(html, encoding="utf-8")

    wall = DIST / "wallpaper"
    project = json.loads((HERE / "wallpaper-project.json").read_text(encoding="utf-8"))
    (wall / "project.json").write_text(json.dumps(project, indent=2, ensure_ascii=False), encoding="utf-8")
    preview = HERE / "preview.jpg"
    if preview.exists():
        shutil.copy(preview, wall / "preview.jpg")
    shutil.rmtree(common)
    print(f"Built {DIST / 'app'} and {DIST / 'wallpaper'}")


if __name__ == "__main__":
    main()
