#!/usr/bin/env python3
"""Builds the Spicetify custom app into desktop/spicetify/dist/spicy-lyrics-plus.

The app is src/app.js with three things laid in at build time, so the desktop app always plays
what the phone plays: the motion lyrics page and its theme script (ivlyrics/src/main/assets/motion),
and the motion themes and their genre pools, read from MotionThemes.kt.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
MOTION = os.path.join(ROOT, "ivlyrics", "src", "main", "assets", "motion")
THEMES_KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "tx24", "spicyplayer", "ivlyrics", "MotionThemes.kt")
OUT = os.path.join(HERE, "dist", "spicy-lyrics-plus")


def kotlin_value(raw):
    raw = raw.strip()
    if raw.startswith("listOf("):
        return re.findall(r'"([^"]*)"', raw)
    if raw.startswith('"'):
        return raw.strip('"')
    if raw in ("true", "false"):
        return raw == "true"
    if raw == "null":
        return None
    return float(raw.rstrip("f"))


def split_args(body):
    """Top-level `name = value` pairs of a Kotlin call's arguments."""
    args, depth, current = [], 0, ""
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            args.append(current)
            current = ""
        else:
            current += ch
    if current.strip():
        args.append(current)
    out = {}
    for arg in args:
        if "=" in arg:
            key, value = arg.split("=", 1)
            out[key.strip()] = kotlin_value(value)
    return out


def themes():
    text = open(THEMES_KT, encoding="utf-8").read()
    entries = []
    for match in re.finditer(r'\n\s*([A-Z][A-Za-z]+)\("([^"]+)",\s*(null|MotionThemeSpec\()', text):
        name, label, kind = match.groups()
        spec = None
        if kind != "null":
            start = match.end()
            depth, index = 1, start
            while depth:
                if text[index] == "(":
                    depth += 1
                elif text[index] == ")":
                    depth -= 1
                index += 1
            spec = split_args(text[start:index - 1])
            spec = {"id": name, **spec}
            spec.setdefault("motifs", [])
            spec.setdefault("tilt", 1.0)
        entries.append({"id": name, "label": label, "spec": spec})
    pools = {}
    block = re.search(r"byGenre[^=]*=\s*mapOf\((.*?)\n\s*\)\n", text, re.S)
    if block:
        for genre, names in re.findall(r"SongGenre\.(\w+) to listOf\(([^)]*)\)", block.group(1)):
            pools[genre] = [n.strip() for n in names.split(",") if n.strip()]
    if len(entries) < 10 or not pools:
        sys.exit("Couldn't read the motion themes from MotionThemes.kt")
    return entries, pools


def main():
    page = open(os.path.join(MOTION, "MotionLyricsPage.html"), encoding="utf-8").read()
    theme_script = open(os.path.join(MOTION, "SpicyThemes.js"), encoding="utf-8").read()
    entries, pools = themes()
    app = open(os.path.join(HERE, "src", "app.js"), encoding="utf-8").read()
    built = (
        "// Spicy Lyrics Plus for Spicetify. Built by desktop/spicetify/build.py; don't edit by hand.\n"
        f"const SLP_MOTION_PAGE = {json.dumps(page)};\n"
        f"const SLP_THEME_SCRIPT = {json.dumps(theme_script)};\n"
        f"const SLP_THEMES = {json.dumps(entries)};\n"
        f"const SLP_GENRE_POOLS = {json.dumps(pools)};\n"
        + app
    )
    os.makedirs(OUT, exist_ok=True)
    open(os.path.join(OUT, "index.js"), "w", encoding="utf-8").write(built)
    manifest = {
        "name": "Spicy Lyrics Plus",
        "icon": open(os.path.join(HERE, "src", "icon.svg"), encoding="utf-8").read(),
        "active-icon": open(os.path.join(HERE, "src", "icon.svg"), encoding="utf-8").read(),
        "subfiles": [],
        "subfiles_extension": [],
    }
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), indent=2)
    print(f"Built {OUT}: {len(entries)} themes, {len(pools)} genre pools, {len(built) // 1024} KB")


if __name__ == "__main__":
    main()
