#!/usr/bin/env bash
# Renders store-assets/athena-icon.svg into the launcher PNGs and the TV banner. Needs rsvg-convert and magick.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
svg=store-assets/athena-icon.svg bg='#14283C' res=app/src/main/res
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
for pair in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    d=${pair%:*} s=${pair#*:} f=$((s * 9 / 4))
    rsvg-convert -w "$f" -h "$f" "$svg" -o "$res/mipmap-$d/ic_launcher_foreground.png"
    # The legacy icon: a rounded square, with the owl filling it more than the adaptive canvas does.
    rsvg-convert -w "$((s * 3 / 2))" -h "$((s * 3 / 2))" "$svg" -o "$tmp/owl.png"
    magick -size "${s}x${s}" xc:none -fill "$bg" -draw "roundrectangle 0,0,$((s-1)),$((s-1)),$((s/6)),$((s/6))" \
        "$tmp/owl.png" -gravity center -composite -strip "$res/mipmap-$d/ic_launcher.png"
done
rsvg-convert -w 765 -h 765 "$svg" -o "$tmp/web.png"
magick -size 512x512 "xc:$bg" "$tmp/web.png" -gravity center -composite -strip app/src/main/ic_launcher-web.png
rsvg-convert -w 260 -h 260 "$svg" -o "$tmp/banner.png"
magick -size 320x180 "xc:$bg" "$tmp/banner.png" -gravity west -geometry -50+0 -composite \
    -gravity west -fill '#E9C46A' -font DejaVu-Sans-Bold -pointsize 38 -annotate +150+0 Athena \
    -strip "$res/drawable-xhdpi/atv_banner.png"
