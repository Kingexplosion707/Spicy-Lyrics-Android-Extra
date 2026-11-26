#!/usr/bin/env python3
"""Writes shared-sources.txt: the :app files the desktop app compiles as they are."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main/java"
PKG = "com/tx24/spicyplayer/"
# Android-only features (services, widgets, wallpaper, always-on, haptics...) and files the desktop
# app has its own versions of (desktop/compose/src/main/kotlin, same path).
NEVER_DIRS = ["tiles", "island", "kiosk", "musicassistant"]
EXCLUDE_DIRS = ["aod", "overlay", "wallpaper", "widget", "ivlyrics"]
# Files from those that use no Android API (settings enums, line fitting...) still come along.
ANDROID_IMPORT = __import__("re").compile(r"^import (android\.|androidx\.(activity|core|media3|glance|work|compose\.ui\.platform\.LocalContext|compose\.ui\.viewinterop)|kr\.ivlis)", __import__("re").M)
EXCLUDE_FILES = [l.strip() for l in (Path(__file__).parent / "excluded-sources.txt").read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
def shim_names():
    """Fully qualified names the stand-ins declare (android.*, androidx.* shims, coil...)."""
    import re
    names = set()
    for f in (Path(__file__).parent / "src/shim/kotlin").rglob("*.kt"):
        t = f.read_text(encoding="utf-8")
        pkg = re.search(r"^package ([\w.]+)", t, re.M).group(1)
        for m in re.finditer(r"^(?:@\w+(?:\([^)]*\))? )*(?:inline |internal |open |abstract |data |enum |sealed |fun interface |annotation )*(?:class|object|interface|fun|val|var|typealias) (?:<[^>]+> )?(?:[\w.]+\.)?(\w+)", t, re.M):
            names.add(pkg + "." + m.group(1))
        for m in re.finditer(r"^(?:\s+)(?:const val|val|var|fun|class|object|enum class|data class|interface) (\w+)", t, re.M):
            pass
    return names
SHIMS = shim_names()
STUBBED = __import__("re").compile(r"^import ((?:android|androidx|coil|kr)\.[\w.]+)", __import__("re").M)
CMP_OK = ("androidx.compose.", "androidx.lifecycle.ViewModel", "androidx.lifecycle.viewModelScope", "androidx.lifecycle.compose")
def portable(text: str) -> bool:
    for m in STUBBED.finditer(text):
        name = m.group(1)
        if name.startswith(CMP_OK) and not name.startswith(("androidx.compose.ui.viewinterop", "androidx.compose.ui.platform.LocalView")):
            continue
        parts = name.split(".")
        # an import of a nested name (Outer.Inner) is fine when Outer is declared
        if not any(".".join(parts[:i]) in SHIMS for i in range(len(parts), 1, -1)):
            return False
    return True

own = {p.relative_to(Path(__file__).parent / "src/main/kotlin").as_posix() for p in (Path(__file__).parent / "src/main/kotlin").rglob("*.kt")}
out = []
for f in sorted(APP.rglob("*.kt")):
    rel = f.relative_to(APP).as_posix()
    sub = rel[len(PKG):] if rel.startswith(PKG) else rel
    if not rel.startswith(PKG) or sub in EXCLUDE_FILES or rel in own or any(sub.startswith(d + "/") for d in NEVER_DIRS):
        continue
    if any(sub.startswith(d + "/") for d in EXCLUDE_DIRS) and not portable(f.read_text(encoding="utf-8")):
        continue
    out.append(rel)
(Path(__file__).parent / "shared-sources.txt").write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
print(len(out), "shared files")
