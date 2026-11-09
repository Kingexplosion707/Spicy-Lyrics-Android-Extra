#!/usr/bin/env bash
# Brings a newer Spicy Lyrics Mobile release into Spicy Lyrics Plus, keeping every Plus change.
#
#   tools/upstream_sync.sh [TAG]           Merge TAG into the current branch: by default the newest
#                                          vX.Y.Z release, or "main" for upstream's main branch.
#   tools/upstream_sync.sh --check [TAG]   Only say whether there is something new.
#   tools/upstream_sync.sh --continue      Finish after fixing the conflicts it stopped at
#                                          (git add the fixed files first).
#   tools/upstream_sync.sh --abort         Give up and go back to where the branch was.
#
# The merge is three-way from the release named in .github/upstream.env, as git merge would do it,
# but lands as one ordinary commit on the current branch: the history stays this repository's own,
# and upstream's commits stay out (they change workflow files, which the Actions token may not
# push). Upstream changes to .github/workflows are left out and named in the commit message.
# versionName follows upstream's, BASE_VERSION_CODE moves past both, and CHANGES.md and
# .github/upstream.env record the update.
#
# Exit status: 0 done (or nothing new), 3 stopped at conflicts, 1 error. With GITHUB_OUTPUT set it
# also writes status (current, new, merged or conflicts), target and version there, and the
# conflicting files to $GITHUB_WORKSPACE/upstream-conflicts.txt.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
CONFIG=.github/upstream.env
BUILD=app/build.gradle.kts
STATE="$(git rev-parse --git-dir)/upstream-sync"

say() { echo "upstream_sync: $*" >&2; }
die() { say "$*"; exit 1; }
output() {
  echo "$1=$2"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"; fi
}

# UPSTREAM_REPO, UPSTREAM_REF and UPSTREAM_COMMIT: the release this branch already contains.
# shellcheck source=/dev/null
. "$CONFIG"
BASE_REF=$UPSTREAM_REF
BASE_COMMIT=$UPSTREAM_COMMIT

# Temporary commits need a name; CI and most machines have one set.
if ! git config user.email > /dev/null; then
  export GIT_AUTHOR_NAME="Spicy Lyrics Plus" GIT_AUTHOR_EMAIL="sync@localhost"
  export GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME" GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"
fi

fetch() {
  git fetch --quiet --no-tags "$UPSTREAM_REPO" \
    '+refs/tags/*:refs/upstream/tags/*' '+refs/heads/main:refs/upstream/main'
  git cat-file -e "$BASE_COMMIT^{commit}" 2> /dev/null \
    || git fetch --quiet --no-tags "$UPSTREAM_REPO" "$BASE_COMMIT"
}

# Sets TARGET and TARGET_COMMIT.
pick_target() {
  TARGET=${1:-}
  if [ -z "$TARGET" ]; then
    TARGET=$(git for-each-ref --format='%(refname:lstrip=3)' refs/upstream/tags \
      | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | awk 'END { print }')
    [ -n "$TARGET" ] || die "no releases upstream"
  fi
  local ref="refs/upstream/tags/$TARGET"
  [ "$TARGET" != main ] || ref=refs/upstream/main
  TARGET_COMMIT=$(git rev-parse -q --verify "$ref^{commit}") || die "upstream has no $TARGET"
}

# The target's versionName, and its versionCode when that's a plain number.
upstream_version() {
  git show "$TARGET_COMMIT:$BUILD" | sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' | awk 'NR == 1'
}
upstream_code() {
  git show "$TARGET_COMMIT:$BUILD" | sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' | awk 'NR == 1'
}

up_to_date() { git merge-base --is-ancestor "$TARGET_COMMIT" "$BASE_COMMIT"; }

unmerged() { git diff --name-only --diff-filter=U "$@"; }

start() {
  [ ! -f "$STATE" ] || die "a sync is already under way: finish it with --continue, or --abort it"
  if ! git diff --quiet || ! git diff --cached --quiet; then die "commit or stash your changes first"; fi
  fetch
  pick_target "${1:-}"
  if up_to_date; then
    say "already has $TARGET"
    output status current
    output target "$TARGET"
    return 0
  fi
  git merge-base --is-ancestor "$BASE_COMMIT" "$TARGET_COMMIT" || die "$TARGET doesn't build on $BASE_REF"
  printf 'ORIG=%s\nTARGET=%s\nTARGET_COMMIT=%s\n' "$(git rev-parse HEAD)" "$TARGET" "$TARGET_COMMIT" > "$STATE"

  # Tell git which release this branch already contains, with a commit that's dropped again at
  # the end, so the squash merge below is three-way from that release.
  git merge --quiet -s ours --allow-unrelated-histories --no-edit \
    -m "Spicy Lyrics Mobile $BASE_REF (merge base)" "$BASE_COMMIT"
  git -c merge.conflictStyle=diff3 merge --squash "$TARGET_COMMIT" || true

  # Keep this repository's workflows.
  local file
  for file in $( { git diff --name-only HEAD -- .github/workflows; unmerged -- .github/workflows; } | sort -u); do
    if git cat-file -e "HEAD:$file" 2> /dev/null; then
      git checkout -q HEAD -- "$file"
    else
      git rm -q -f -- "$file"
    fi
  done
  # Both sides move the version lines on every release.
  if unmerged | grep -qx "$BUILD" && python3 tools/plus_version.py resolve "$BUILD"; then
    git add "$BUILD"
  fi

  if [ -n "$(unmerged)" ]; then
    say "conflicts to resolve, keeping both upstream's change and every Plus change:"
    unmerged | sed 's/^/  /' >&2
    say "then git add them and run tools/upstream_sync.sh --continue"
    if [ -n "${GITHUB_OUTPUT:-}" ]; then unmerged > "${GITHUB_WORKSPACE:-.}/upstream-conflicts.txt"; fi
    output status conflicts
    output target "$TARGET"
    output version "$(upstream_version)"
    exit 3
  fi
  finish
}

finish() {
  # shellcheck source=/dev/null
  . "$STATE" # ORIG, TARGET, TARGET_COMMIT
  [ -z "$(unmerged)" ] || die "resolve these and git add them first: $(unmerged | tr '\n' ' ')"
  local markers
  markers=$(git diff --cached --name-only --no-renames --diff-filter=AM -z \
    | xargs -0 -r grep -IlE '^(<<<<<<<|>>>>>>>)( |$)' -- 2> /dev/null || true)
  [ -z "$markers" ] || die "conflict markers are left in: $(tr '\n' ' ' <<< "$markers")"

  local version code dropped msg tree commit
  version=$(upstream_version)
  code=$(upstream_code)
  [ -n "$version" ] || die "no versionName in upstream's $BUILD"
  dropped=$(git diff --name-only "$BASE_COMMIT" "$TARGET_COMMIT" -- .github/workflows)

  python3 tools/plus_version.py name "$BUILD" "$version"
  python3 tools/plus_version.py bump "$BUILD" "${code:-0}"
  python3 tools/plus_version.py changes CHANGES.md "$version" "$(date -u +%Y-%m-%d)"
  sed -i -E "s|^UPSTREAM_REF=.*|UPSTREAM_REF=$TARGET|; s|^UPSTREAM_COMMIT=.*|UPSTREAM_COMMIT=$TARGET_COMMIT|" "$CONFIG"
  git add "$BUILD" CHANGES.md "$CONFIG"

  msg=$(mktemp)
  {
    echo "Bring in Spicy Lyrics Mobile $version"
    echo
    echo "Merged from $BASE_REF to $TARGET ($(git rev-parse --short "$TARGET_COMMIT")) of"
    echo "$UPSTREAM_REPO, keeping every Spicy Lyrics Plus change."
    if [ -n "$dropped" ]; then
      echo
      echo "Upstream also changed these workflows, which were left as they are here:"
      while IFS= read -r file; do echo "- $file"; done <<< "$dropped"
    fi
  } > "$msg"
  tree=$(git write-tree)
  commit=$(git commit-tree "$tree" -p "$ORIG" -F "$msg")
  git reset -q --soft "$commit"
  rm -f "$msg" "$STATE" "$(git rev-parse --git-dir)/SQUASH_MSG"
  git --no-pager show --stat --format='%h %s' HEAD | awk 'NR <= 40' >&2
  output status merged
  output target "$TARGET"
  output version "$version"
}

abort() {
  [ -f "$STATE" ] || die "no sync under way"
  # shellcheck source=/dev/null
  . "$STATE"
  git reset -q --hard "$ORIG"
  rm -f "$STATE" "$(git rev-parse --git-dir)/SQUASH_MSG"
  say "back at $(git rev-parse --short HEAD)"
}

case "${1:-}" in
  --check)
    fetch
    pick_target "${2:-}"
    if up_to_date; then output status current; else output status new; fi
    output target "$TARGET"
    output version "$(upstream_version)"
    ;;
  --continue) [ -f "$STATE" ] || die "no sync under way"; finish ;;
  --abort) abort ;;
  -*) sed -n '2,20p' "$0" >&2; exit 1 ;;
  *) start "${1:-}" ;;
esac
