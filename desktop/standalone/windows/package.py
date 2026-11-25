#!/usr/bin/env python3
"""Packages Spicy Lyrics Plus for Windows: SpicyLyricsPlus-Desktop-Setup-<version>.exe.

  python3 package.py --relay <Live-Lyrics bridge folder> [--version 1.0.0]

Builds the app and wallpaper (../build.py), stages them with the relay (and its ws dependency),
a portable node.exe (npm package node-win-x64) and the launcher, then makes the installer.
Needs makensis, npm and Pillow.
"""
import argparse
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
STANDALONE = HERE.parent
NODE_VERSION = "22.23.5"
RELAY_FILES = ["bridge-server.js", "nowplaying.ps1", "spicetify-lyrics-bridge.js", "package.json"]


def run(*cmd, cwd=None):
    subprocess.run(cmd, cwd=cwd, check=True)


def npm_extract(spec, member, dest, tmp):
    out = subprocess.run(["npm", "pack", spec, "--silent"], cwd=tmp, check=True, capture_output=True, text=True)
    tgz = Path(tmp) / out.stdout.strip().splitlines()[-1]
    with tarfile.open(tgz) as tar:
        if member.endswith("/"):
            for m in tar.getmembers():
                if m.name.startswith(member) and m.isfile():
                    target = dest / m.name[len(member):]
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(tar.extractfile(m).read())
        else:
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(tar.extractfile(tar.getmember(member)).read())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--relay", required=True, type=Path)
    ap.add_argument("--version", default="1.0.0")
    ap.add_argument("--out", type=Path, default=STANDALONE / "dist")
    args = ap.parse_args()

    run(sys.executable, str(STANDALONE / "build.py"))
    stage = STANDALONE / "dist" / "windows-stage"
    if stage.exists():
        shutil.rmtree(stage)
    stage.mkdir(parents=True)
    shutil.copytree(STANDALONE / "dist" / "app", stage / "app")
    shutil.copytree(STANDALONE / "dist" / "wallpaper", stage / "wallpaper")
    (stage / "relay").mkdir()
    for f in RELAY_FILES:
        shutil.copy(args.relay / f, stage / "relay" / f)
    with tempfile.TemporaryDirectory() as tmp:
        npm_extract("ws@8.18.3", "package/", stage / "relay" / "node_modules" / "ws", tmp)
        npm_extract(f"node-win-x64@{NODE_VERSION}", "package/bin/node.exe", stage / "node" / "node.exe", tmp)
    for f in ["start-relay.vbs", "setup.ps1", "stop-relay.ps1", "ensure-relay.ps1"]:
        shutil.copy(HERE / f, stage / f)
    shutil.copy(stage / "app" / "icon.ico", stage / "icon.ico")

    run("makensis", "-V2", f"-DVERSION={args.version}", f"-DOUTDIR={stage}", f"-DICON={stage / 'icon.ico'}", str(HERE / "launcher.nsi"))
    args.out.mkdir(parents=True, exist_ok=True)
    out = args.out / f"SpicyLyricsPlus-Desktop-Setup-{args.version}.exe"
    run("makensis", "-V2", f"-DVERSION={args.version}", f"-DSRC={stage}", f"-DOUT={out}", str(HERE / "setup.nsi"))
    print("Built", out)


if __name__ == "__main__":
    main()
