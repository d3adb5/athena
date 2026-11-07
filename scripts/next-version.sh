#!/usr/bin/env bash
# Prints the next release version from the Conventional Commits since the newest vX.Y.Z tag on HEAD: fix = patch,
# feat = minor, "!" or BREAKING CHANGE = major. No tag yet: 0.1.0. Nothing to release: prints nothing.
#   next-version.sh [patch|minor|major]   at least this bump (a manual release)
#   next-version.sh --last                the newest release tag ("" without one)
#   next-version.sh --describe            a version for a build that is not a release: 0.1.0-3-gabc1234
# VERSION_SUBMODULES="path ...": also count those submodules' commits between their gitlinks at the tag and at HEAD.
set -euo pipefail

# "3.7.3 v3.7.3" pairs, so bare and v-prefixed tags sort together by version.
last="$(git tag --merged HEAD | grep -E '^v?[0-9]+\.[0-9]+\.[0-9]+$' | sed -E 's/^(v?)(.*)$/\2 \1\2/' | sort -V |
  tail -1 | cut -d' ' -f2 || true)"

case "${1:-}" in
  --last) echo "$last"; exit 0 ;;
  --describe)
    if [ -n "$last" ]; then
      n="$(git rev-list --count "$last..HEAD")"
      v="${last#v}"; [ "$n" = 0 ] || v="$v-$n-g$(git rev-parse --short=7 HEAD)"
    else
      v="0.0.0-g$(git rev-parse --short=7 HEAD)"
    fi
    git diff --quiet --ignore-submodules HEAD 2>/dev/null || v="$v-dirty"
    echo "$v"; exit 0 ;;
esac

rank() { case "$1" in major) echo 3 ;; minor) echo 2 ;; patch) echo 1 ;; *) echo 0 ;; esac; }
bump=$(rank "${1:-}")

if [ -z "$last" ]; then echo 0.1.0; exit 0; fi

breaking='^[A-Za-z]+(\([^)]*\))?!:'
feat='^feat(\([^)]*\))?:'
fix='^fix(\([^)]*\))?:'
messages() {
  git log --no-merges --format='%B%x00' "$last..HEAD"
  for sm in ${VERSION_SUBMODULES:-}; do
    old="$(git rev-parse -q --verify "$last:$sm" || true)"
    new="$(git rev-parse -q --verify "HEAD:$sm" || true)"
    [ -n "$old" ] && [ -n "$new" ] && [ "$old" != "$new" ] || continue
    git -C "$sm" log --no-merges --format='%B%x00' "$old..$new" ||
      { echo "next-version.sh: $sm has no $old..$new; its commits are not counted" >&2; }
  done
}
while IFS= read -r -d '' msg; do
  msg="${msg#$'\n'}"
  subject="${msg%%$'\n'*}"
  if [[ $subject =~ $breaking ]] || grep -qE '^BREAKING[ -]CHANGE:' <<<"$msg"; then b=3
  elif [[ $subject =~ $feat ]]; then b=2
  elif [[ $subject =~ $fix ]]; then b=1
  else b=0; fi
  [ "$b" -le "$bump" ] || bump=$b
done < <(messages)

[ "$bump" != 0 ] || exit 0
IFS=. read -r major minor patch <<<"${last#v}"
case "$bump" in
  3) echo "$((major + 1)).0.0" ;;
  2) echo "$major.$((minor + 1)).0" ;;
  1) echo "$major.$minor.$((patch + 1))" ;;
esac
