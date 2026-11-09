"""Spicy Lyrics Plus's version lines and changelog when a Spicy Lyrics Mobile release is merged in.

    python3 tools/plus_version.py resolve app/build.gradle.kts
        Resolves merge conflicts that only touch versionCode/versionName lines: Plus keeps its
        own version-code wiring and takes upstream's version name. Exits 1, leaving the file as
        it was, when a conflict touches anything else.

    python3 tools/plus_version.py name app/build.gradle.kts VERSION
        Sets versionName.

    python3 tools/plus_version.py bump app/build.gradle.kts UPSTREAM_VERSION_CODE
        Moves BASE_VERSION_CODE past both its old value and upstream's version code.

    python3 tools/plus_version.py changes CHANGES.md VERSION DATE
        Names VERSION as the base in the opening paragraph and adds a dated line to the
        updates list.
"""

import pathlib
import re
import sys

CONFLICT = re.compile(r"<<<<<<< [^\n]*\n(.*?)(?:\|\|\|\|\|\|\| [^\n]*\n.*?)?=======\n(.*?)>>>>>>> [^\n]*\n", re.S)
VERSION_LINE = re.compile(r"\s*(versionCode|versionName)\s*=|\s*//")
VERSION_NAME = re.compile(r'(versionName\s*=\s*)"[^"]+"')
UPDATES = "## Updates from Spicy Lyrics Mobile"


def resolve(path: pathlib.Path) -> int:
    text = path.read_text(encoding="utf-8")

    def merged(match: re.Match) -> str:
        ours, theirs = match.group(1), match.group(2)
        for line in (ours + theirs).splitlines():
            if line.strip() and not VERSION_LINE.match(line):
                raise ValueError(f"not a version line: {line.strip()}")
        name = VERSION_NAME.search(theirs)
        if name and VERSION_NAME.search(ours):
            ours = VERSION_NAME.sub(lambda m: m.group(1) + name.group(0)[len(name.group(1)):], ours, count=1)
        elif name:
            ours += re.search(r"[^\n]*versionName[^\n]*\n", theirs).group(0)
        return ours

    try:
        resolved = CONFLICT.sub(merged, text)
    except ValueError as error:
        print(f"Can't resolve on its own: {error}", file=sys.stderr)
        return 1
    if "<<<<<<<" in resolved or ">>>>>>>" in resolved:
        print("Conflict markers are left", file=sys.stderr)
        return 1
    path.write_text(resolved, encoding="utf-8")
    print("Resolved the version lines")
    return 0


def name(path: pathlib.Path, version: str) -> int:
    text = path.read_text(encoding="utf-8")
    if not VERSION_NAME.search(text):
        print("No versionName", file=sys.stderr)
        return 1
    path.write_text(VERSION_NAME.sub(lambda m: f'{m.group(1)}"{version}"', text, count=1), encoding="utf-8")
    print(f"versionName {version}")
    return 0


def bump(path: pathlib.Path, upstream_code: int) -> int:
    text = path.read_text(encoding="utf-8")
    current = re.search(r"^val BASE_VERSION_CODE = (\d+)", text, re.M)
    if not current:
        print("No BASE_VERSION_CODE", file=sys.stderr)
        return 1
    code = max(int(current.group(1)) + 1, upstream_code + 1)
    path.write_text(text[:current.start(1)] + str(code) + text[current.end(1):], encoding="utf-8")
    print(f"BASE_VERSION_CODE {current.group(1)} -> {code}")
    return 0


def changes(path: pathlib.Path, version: str, date: str) -> int:
    text = path.read_text(encoding="utf-8")
    text = re.sub(
        r"(\[Spicy Lyrics Mobile\]\(https://github\.com/spicylyrics/mobile\)) \d+(?:\.\d+)+",
        lambda m: f"{m.group(1)} {version}",
        text,
        count=1,
    )
    line = f"- {date}: Spicy Lyrics Mobile {version} merged in, with every Plus change kept."
    if UPDATES + "\n" in text:
        # Newest first, right under the heading.
        head, _, tail = text.partition(UPDATES + "\n")
        text = f"{head}{UPDATES}\n\n{line}\n{tail.lstrip(chr(10))}"
    else:
        text = text.rstrip("\n") + f"\n\n{UPDATES}\n\n{line}\n"
    path.write_text(text, encoding="utf-8")
    print(f"CHANGES.md: {line}")
    return 0


def main() -> int:
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == "resolve":
        return resolve(pathlib.Path(args[1]))
    if len(args) == 3 and args[0] == "name":
        return name(pathlib.Path(args[1]), args[2])
    if len(args) == 3 and args[0] == "bump":
        return bump(pathlib.Path(args[1]), int(args[2] or 0))
    if len(args) == 4 and args[0] == "changes":
        return changes(pathlib.Path(args[1]), args[2], args[3])
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
