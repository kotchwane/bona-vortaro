#!/usr/bin/env python3
# Generates the launcher icon of the app (see the docstring below).
#
# Usage, from the root of the repository:
#   python3 -m venv /tmp/icon-venv && /tmp/icon-venv/bin/pip install fonttools
#   curl -LO https://github.com/notofonts/notofonts.github.io/raw/main/fonts/NotoSans/unhinted/ttf/NotoSans-Regular.ttf
#   /tmp/icon-venv/bin/python scripts/generate-icon.py NotoSans-Regular.ttf app/src/main/res icon.svg
#   for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
#     rsvg-convert -w ${d#*:} -h ${d#*:} icon.svg -o app/src/main/res/mipmap-${d%%:*}/ic_launcher.png
#   done
"""Generate PReVo's launcher icon as vectors: an open green book with "ĉ e".

Outputs (into the given res/ directory):
  drawable/ic_launcher_foreground.xml   adaptive icon foreground (green book, white letters)
  drawable/ic_launcher_monochrome.xml   themed icon (book with letters cut out)
  mipmap-anydpi-v26/ic_launcher.xml     adaptive icon definition
  values/ic_launcher_background.xml     background colour (white)
and an SVG of the legacy icon (book only, transparent) for rendering PNGs.

Letters: glyph outlines of Noto Sans Bold (SIL Open Font License 1.1).
The adaptive icon canvas is 108x108; the safe zone is the centred 66 circle.
"""
import os
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONT, RES, SVG_OUT = sys.argv[1], sys.argv[2], sys.argv[3]
GREEN = "#00660B"

# --- The book, in 108x108 coordinates (y down) -------------------------------
# Two pages meeting at the spine, rising to the right like the original icon.
LEFT_PAGE = "M23,49 Q38,44 53,53 L53,80 Q38,72 25,76 Z"
RIGHT_PAGE = "M55,53 Q70,44 85,49 L83,76 Q70,72 55,80 Z"
# Edges of the pages under the covers, drawn as thin white strokes
PAGE_LINES = [
    "M25,79 Q38,75 53,83",
    "M25,82 Q38,78 53,86",
    "M55,83 Q70,75 83,79",
    "M55,86 Q70,78 83,82",
]
# Covers under the page edges, so the white lines sit on green
LEFT_COVER = "M21,49 L23,49 L25,76 Q38,72 53,80 L53,89 Q38,81 23,85 Z"
RIGHT_COVER = "M55,80 Q70,72 83,76 L85,49 L87,49 L85,85 Q70,81 55,89 Z"


def glyph_path(font, char, x, y, height, skew):
    """Outline of a glyph, scaled to `height` (cap-ish), its baseline at
    (x, y), sheared vertically by `skew` (dy per dx) to follow the page."""
    glyphs = font.getGlyphSet()
    name = font.getBestCmap()[ord(char)]
    upm = font["head"].unitsPerEm
    scale = height / (upm * 0.536)  # 0.536 em ≈ x-height of Noto Sans
    width = glyphs[name].width * scale
    pen = SVGPathPen(glyphs)
    # font units: y up -> icon units: y down; shear y by skew * x
    tpen = TransformPen(pen, (scale, -skew * scale, 0, -scale, x - width / 2, y))
    glyphs[name].draw(tpen)
    return pen.getCommands()


font = TTFont(FONT)
# Letters centred on each page, following the slope of the page
LETTER_C = glyph_path(font, "ĉ", 38.5, 72, 13, 0)
LETTER_E = glyph_path(font, "e", 70, 70, 13, 0)
LETTERS = LETTER_C + " " + LETTER_E

os.makedirs(f"{RES}/drawable", exist_ok=True)
os.makedirs(f"{RES}/mipmap-anydpi-v26", exist_ok=True)
os.makedirs(f"{RES}/values", exist_ok=True)

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated: an open book with "ĉ e". Letters from Noto Sans Bold
     (SIL Open Font License 1.1). -->
"""


ROTATION = -14  # degrees, around the centre, like the tilted book of the original


def vector(body):
    # The book is centred around (54, 66). Launchers only guarantee that
    # the circle of diameter 66 at the centre is shown, so the book is
    # scaled down and moved up into it.
    body = f"""    <group android:rotation="{ROTATION}" android:pivotX="54" android:pivotY="66"
        android:scaleX="0.78" android:scaleY="0.78" android:translateY="-12">
{body}    </group>
"""
    return HEADER + f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
{body}</vector>
"""


def fill(d, color, even_odd=False):
    rule = '\n        android:fillType="evenOdd"' if even_odd else ""
    return f"""    <path
        android:fillColor="{color}"{rule}
        android:pathData="{d}" />
"""


def stroke(d, color, width):
    return f"""    <path
        android:strokeColor="{color}"
        android:strokeWidth="{width}"
        android:strokeLineCap="round"
        android:pathData="{d}" />
"""


foreground = "".join([
    fill(LEFT_COVER, GREEN), fill(RIGHT_COVER, GREEN),
    fill(LEFT_PAGE, GREEN), fill(RIGHT_PAGE, GREEN),
    *[stroke(d, "#FFFFFF", 1.1) for d in PAGE_LINES],
    fill(LETTERS, "#FFFFFF"),
])
with open(f"{RES}/drawable/ic_launcher_foreground.xml", "w") as f:
    f.write(vector(foreground))

# Monochrome: one shape, the letters being holes (even-odd)
book = " ".join([LEFT_COVER, RIGHT_COVER, LEFT_PAGE, RIGHT_PAGE])
monochrome = fill(LEFT_COVER + " " + RIGHT_COVER, "#FFFFFF") + \
    fill(LEFT_PAGE + " " + RIGHT_PAGE + " " + LETTERS, "#FFFFFF", even_odd=True)
with open(f"{RES}/drawable/ic_launcher_monochrome.xml", "w") as f:
    f.write(vector(monochrome))

with open(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", "w") as f:
    f.write("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
""")

with open(f"{RES}/values/ic_launcher_background.xml", "w") as f:
    f.write("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FFFFFF</color>
</resources>
""")

# Legacy icon (Android 6–7): the book alone on a transparent background,
# like the original, enlarged to fill the square (crop to x 20..88, y 36..94)
with open(SVG_OUT, "w") as f:
    f.write(f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="17 25 74 74">
  <g transform="rotate({ROTATION} 54 54)">
  <path fill="{GREEN}" d="{LEFT_COVER} {RIGHT_COVER} {LEFT_PAGE} {RIGHT_PAGE}"/>
  {''.join(f'<path fill="none" stroke="#FFFFFF" stroke-width="1.1" stroke-linecap="round" d="{d}"/>' for d in PAGE_LINES)}
  <path fill="#FFFFFF" d="{LETTERS}"/>
  </g>
</svg>
""")
print("ok")
