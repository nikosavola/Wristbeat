#!/bin/sh
# Renders the store graphics from design/logo.svg, so the listing icon cannot drift from the
# launcher icon, which draws the same mark. Run from the repository root:
# .github/scripts/make-store-graphics.sh
#
# Needs rsvg-convert (or Inkscape) for the vector step. The screenshots are hand-taken screencaps in
# images/wearScreenshots, which is the only honest way to make them.
set -eu

LOGO=design/logo.svg
OUT=fastlane/metadata/android/en-US/images
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

[ -f "$LOGO" ] || { echo "no $LOGO: run this from the root of the repository" >&2; exit 1; }

# icon.png: F-Droid's listing icon, and the source of the Play listing icon. 512x512, the mark
# exactly as designed, since a store icon is shown unmasked where a launcher icon is cut to a
# circle.
rsvg-convert -w 512 -h 512 "$LOGO" -o "$OUT/icon.png"

# featureGraphic.png: Play's 1024x500 listing banner. Black because that is what the app draws on.
# The mark and the wordmark are laid out as one block and the block is centred on the canvas: the
# mark is 240 square at 0.46875 of its 512 box, "Wristbeat" in bold Helvetica at 104 measures 470,
# so with a 44 gap the block is 754 wide. Those are the numbers to redo if the name or the type
# size changes.
# From the mark's opening group to just before </svg>: the group nests the tick marks' own <g>,
# so a cut at the first </g> would leave the outer group open.
MARK=$(awk '/<\/svg>/ {exit} /<g id="icon">/ {p = 1} p' "$LOGO")
cat > "$WORK/feature.svg" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
  <rect width="1024" height="500" fill="#000000"/>
  <g transform="translate(111,149.5) scale(0.46875)">
$(printf '%s' "$MARK")
  </g>
  <text x="419" y="242" font-family="Helvetica, Arial, sans-serif" font-size="104" font-weight="bold" fill="#FFFFFF">Wristbeat</text>
  <text x="423" y="308" font-family="Helvetica, Arial, sans-serif" font-size="36" fill="#C8C5CE">Heart rate over Bluetooth LE</text>
</svg>
EOF

rsvg-convert -w 1024 -h 500 "$WORK/feature.svg" -o "$OUT/featureGraphic.png"

echo "wrote $OUT"