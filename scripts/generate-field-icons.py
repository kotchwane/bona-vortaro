#!/usr/bin/env python3
# Generates the icons of the fields (zoologio, matematiko…) from Reta
# Vortaro's drawings (see the docstring below).
#
# Usage, from the root of the repository (needs MetaPost, `mpost`, from TeX):
#   python3 scripts/generate-field-icons.py
"""Generate the icons of the fields of Reta Vortaro as Android vector
drawables, from the MetaPost sources of voko-grundo (smb/ZOO.mp…), which
Reta Vortaro's site shows next to each field. They are under the GPL 2, as
voko-grundo.

Outputs:
  app/src/main/res/drawable/fako_<code>.xml          one icon per field
  app/src/main/java/.../FieldIcons.kt                 the code → icon table

MetaPost writes SVG paths in absolute M, L, C, Z commands, filled or
stroked, sometimes in groups with an affine transform (skewed for the
shadows): the transforms are applied to the points, which keeps the curves
exact. The drawings are black, with greys made transparent; the white
shapes, a tiny reflection in MED and a line in KAL, are left out. The app tints them.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SMB = os.path.join(ROOT, "extern", "voko-grundo", "smb")
DRAWABLE = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
KOTLIN = os.path.join(ROOT, "app", "src", "main", "java", "io", "github",
                      "kotchwane", "bonavortaro", "FieldIcons.kt")

SVG = "{http://www.w3.org/2000/svg}"


def multiply(m, n):
    """The affine transform m applied after n, both as (a, b, c, d, e, f)."""
    a, b, c, d, e, f = m
    a2, b2, c2, d2, e2, f2 = n
    return (a * a2 + c * b2, b * a2 + d * b2,
            a * c2 + c * d2, b * c2 + d * d2,
            a * e2 + c * f2 + e, b * e2 + d * f2 + f)


def transform_path(d, m):
    a, b, c, dd, e, f = m
    out = []
    numbers = []
    command = None
    point = [0.0, 0.0]

    def flush():
        for i in range(0, len(numbers), 2):
            x, y = numbers[i], numbers[i + 1]
            # "l0 0", a dot drawn by a round cap, is the only relative
            # command MetaPost writes
            if command == "l":
                x, y = point[0] + x, point[1] + y
            point[:] = [x, y]
            out.append("%.2f,%.2f" % (a * x + c * y + e, b * x + dd * y + f))
        numbers.clear()

    for token in re.findall(r"[A-Za-z]|-?[0-9]*\.?[0-9]+(?:e-?[0-9]+)?", d):
        if token.isalpha():
            if token not in "MLCZl":
                sys.exit("path command %s not handled" % token)
            flush()
            command = token
            out.append("L" if token == "l" else token)
        else:
            numbers.append(float(token))
    flush()
    return " ".join(out)


def style_of(element):
    style = {}
    for part in element.get("style", "").split(";"):
        if ":" in part:
            key, value = part.split(":", 1)
            style[key.strip()] = value.strip()
    return style


def grey(colour):
    """The lightness of "rgb(35%,35%,35%)", 0 for black, 1 for white."""
    if colour == "black":
        return 0
    values = [float(v.rstrip("%")) / 100 for v in colour[4:-1].split(",")]
    return sum(values) / 3


def paths(element, m, out):
    if element.tag == SVG + "g" and element.get("transform"):
        values = re.match(r"matrix\((.*)\)", element.get("transform")).group(1)
        m = multiply(m, tuple(float(v) for v in re.split(r"[,\s]+", values)))
    if element.tag == SVG + "path":
        style = style_of(element)
        scale = abs(m[0] * m[3] - m[1] * m[2]) ** 0.5
        attributes = ['android:pathData="%s"' % transform_path(element.get("d"), m)]
        fill = style.get("fill", "none")
        if fill != "none":
            lightness = grey(fill)
            if lightness > 0.99:
                return
            attributes.append('android:fillColor="#FF000000"')
            if lightness > 0:
                attributes.append('android:fillAlpha="%.2f"' % (1 - lightness))
        stroke = style.get("stroke", "none")
        if stroke != "none" and grey(stroke) < 0.99:
            attributes.append('android:strokeColor="#FF000000"')
            attributes.append('android:strokeWidth="%.2f"'
                              % (float(style["stroke-width"]) * scale))
            if style.get("stroke-linecap") == "round":
                attributes.append('android:strokeLineCap="round"')
            if style.get("stroke-linejoin") == "round":
                attributes.append('android:strokeLineJoin="round"')
        out.append("    <path\n        " + "\n        ".join(attributes) + " />")
    for child in element:
        paths(child, m, out)


def main():
    if shutil.which("mpost") is None:
        sys.exit("mpost (MetaPost) is needed")

    # The fields in use (cfg/fakoj.xml; MAH, commented out there, has text)
    fields = ET.parse(os.path.join(ROOT, "extern", "voko-grundo", "cfg", "fakoj.xml"))
    codes = sorted(f.get("kodo") for f in fields.getroot().iter("fako")
                   if os.path.exists(os.path.join(SMB, f.get("kodo") + ".mp")))

    for old in glob.glob(os.path.join(DRAWABLE, "fako_*.xml")):
        os.remove(old)

    with tempfile.TemporaryDirectory() as work:
        shutil.copy(os.path.join(SMB, "centoblecent.mp"), work)
        for code in codes:
            shutil.copy(os.path.join(SMB, code + ".mp"), work)
            subprocess.run(["mpost", "-interaction=batchmode", code + ".mp"],
                           cwd=work, check=True, stdout=subprocess.DEVNULL)
            svg = ET.parse(os.path.join(work, code + ".svg")).getroot()
            out = []
            paths(svg, (1, 0, 0, 1, 0, 0), out)
            with open(os.path.join(DRAWABLE, "fako_%s.xml" % code.lower()), "w") as f:
                f.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        "<!-- Generated by scripts/generate-field-icons.py from "
                        "voko-grundo/smb/%s.mp (GPL 2) -->\n"
                        "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        "    android:width=\"24dp\"\n"
                        "    android:height=\"24dp\"\n"
                        "    android:viewportWidth=\"100\"\n"
                        "    android:viewportHeight=\"100\">\n"
                        "%s\n"
                        "</vector>\n" % (code, "\n".join(out)))

    with open(KOTLIN, "w") as f:
        f.write("""/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2026  kotchwane
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/* Generated by scripts/generate-field-icons.py: don't edit */

package io.github.kotchwane.bonavortaro

/** The icon of each field of Reta Vortaro, by its code ("ZOO"), drawn
 * by Reta Vortaro (voko-grundo/smb) */
val FIELD_ICONS: Map<String, Int> = mapOf(
""")
        for code in codes:
            f.write("    \"%s\" to R.drawable.fako_%s,\n" % (code, code.lower()))
        f.write(")\n")

    print("%d icons" % len(codes))


main()
