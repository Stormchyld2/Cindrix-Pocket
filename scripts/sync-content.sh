#!/bin/sh
# Copies the cards, the Academy, the skills list and the wild-food seasons from a Cindrix-Ops checkout into the app's assets.
# Usage: scripts/sync-content.sh /path/to/Cindrix-Ops
# Only plain text files are carried (pictures and templates are left out).
set -e
OPS="$(cd "${1:?give the path to the Cindrix-Ops folder}" && pwd)"
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"
rm -rf "$DEST/cards" "$DEST/academy"
mkdir -p "$DEST/cards" "$DEST/academy"
for f in "$OPS"/content/cards/*.md; do
  case "$(basename "$f")" in _*) continue;; esac
  cp "$f" "$DEST/cards/"
done
(cd "$OPS/content/academy" && find . -type f -name '*.md' ! -path './_template-course/*' ! -name 'HOW-TO-ADD-A-COURSE.md' | while read -r f; do
  mkdir -p "$DEST/academy/$(dirname "$f")"
  cp "$f" "$DEST/academy/$f"
done)
cp "$OPS/content/skills.csv" "$DEST/skills.csv"
cp "$OPS/content/forage-seasons.csv" "$DEST/forage-seasons.csv"
echo "cards: $(ls "$DEST/cards" | wc -l)  academy files: $(find "$DEST/academy" -type f | wc -l)  skills: $(($(wc -l < "$DEST/skills.csv") - 1))  seasons: $(($(wc -l < "$DEST/forage-seasons.csv") - 1))"
