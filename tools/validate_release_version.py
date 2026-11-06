"""Check that an Android release tag matches the app version and version code."""

import argparse
import pathlib
import re
import sys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    build_file = pathlib.Path(__file__).resolve().parents[1] / "app" / "build.gradle.kts"
    source = build_file.read_text(encoding="utf-8")
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', source, re.MULTILINE)
    code = re.search(r"^\s*versionCode\s*=\s*(\d+)", source, re.MULTILINE)
    if not name or not code:
        print("Could not find app versionName and versionCode", file=sys.stderr)
        return 1
    expected = f"v{name.group(1)}"
    if not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
        print("Release tag must be vMAJOR.MINOR.PATCH", file=sys.stderr)
        return 1
    if args.tag != expected:
        print(f"Tag {args.tag} does not match app version {expected}", file=sys.stderr)
        return 1
    if int(code.group(1)) < 1:
        print("versionCode must be positive", file=sys.stderr)
        return 1
    print(f"Validated {args.tag} (versionCode {code.group(1)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
