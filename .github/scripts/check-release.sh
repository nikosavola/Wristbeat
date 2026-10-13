#!/bin/sh
# Checks that a tag is one the stores would take, before anything is published from it. Three names
# have to agree: F-Droid's UpdateCheckMode reads a tag whose name is the versionName, and Play and
# F-Droid both read the changelog named after the versionCode, so a tag whose build does not carry
# that pair is a tag no store can consume.
#
# The two names go to stdout as `key=value` lines, the form a workflow wants. Everything for a human
# goes to stderr.
#
# Usage: .github/scripts/check-release.sh v0.1.0
set -eu

GRADLE=wear/build.gradle.kts
CHANGELOGS=fastlane/metadata/android/en-US/changelogs

die() {
  echo "$1" >&2
  exit 1
}

tag=${1:-}
[ -n "$tag" ] || die "usage: check-release.sh <tag>, for example v0.1.0"
[ -f "$GRADLE" ] || die "$GRADLE is not here: run this from the root of the repository"

version_name=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' "$GRADLE")
# Underscores are how the build file writes digits apart, and not part of the number a store sees.
version_code=$(sed -n 's/^ *versionCode = \(.*\)$/\1/p' "$GRADLE" | tr -d '_')
[ -n "$version_name" ] && [ -n "$version_code" ] || die "no versionName or versionCode in $GRADLE"

[ "$tag" = "v$version_name" ] ||
  die "tag $tag does not name versionName $version_name: a store tag has to be v\$versionName"

changelog="$CHANGELOGS/$version_code.txt"
[ -s "$changelog" ] ||
  die "there is no changelog at $changelog: Play and F-Droid both read the file named after the versionCode"

# A tag points at a commit, so a version that is only in the working copy is one the tag will not
# carry, and the store would build the previous version. Nothing catches that later.
if git rev-parse --verify --quiet HEAD >/dev/null 2>&1; then
  git diff --quiet HEAD -- "$GRADLE" ||
    die "$GRADLE differs from HEAD: commit the bump and the changelog, or the tag will not carry them"
fi

# The version code has to rise, since Play refuses a code it has already seen, and a bump that moves
# the name while leaving the code alone only shows up as a rejected upload. Only tags in this clone
# can be compared, so a shallow checkout says so rather than guessing.
previous=$(git tag --list 'v*' --sort=-v:refname 2>/dev/null | grep -vx "$tag" | head -1)
if [ -n "$previous" ]; then
  previous_code=$(git show "$previous:$GRADLE" 2>/dev/null | sed -n 's/^ *versionCode = \(.*\)$/\1/p' | tr -d '_') || previous_code=
  if [ -n "${previous_code:-}" ]; then
    [ "$version_code" -gt "$previous_code" ] ||
      die "versionCode $version_code is not above the $previous_code $previous was released with"
  else
    echo "note: $previous is not in this clone, so the version code was not compared against it" >&2
  fi
else
  echo "note: this is the only tag, so there is nothing to compare the version code against" >&2
fi

echo "releasing $tag: versionName $version_name, versionCode $version_code" >&2
echo "versionName=$version_name"
echo "versionCode=$version_code"
