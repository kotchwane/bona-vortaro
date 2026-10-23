#!/usr/bin/env python3
# Extracts from the Esperanto Wiktionary the definitions that Bona vortaro
# shows for Esperanto words that Reta Vortaro doesn't have.
#
# Usage, from the root of the repository:
#   python3 scripts/vikivortaro-eo.py https://dumps.wikimedia.org/eowiktionary/latest/eowiktionary-latest-pages-articles.xml.bz2
#   (or the path of that file, downloaded)
#
# Only the words of data/vikivortaro-eo-words.txt are taken: chosen one by
# one among the 3,454 entries that Reta Vortaro doesn't have, in the spirit
# of Claude Piron's "La bona lingvo" (compounds that say more than their
# pieces, idioms, and new roots only for specific things; not the forms,
# variants, empty pages, nor the words that "Vortoj de la bona lingvo"
# replaces). data/vikivortaro-eo-fixes.tsv gives the few definitions
# corrected for Bona vortaro ("adaptita": a typo, a wrong word, a stray
# line), each with the text of Wiktionary it corrects, so that a change
# there is noticed. No definition is written for Bona vortaro: a word
# Wiktionary doesn't define is left out.
#
# The content is by the contributors of the Esperanto Wiktionary, under
# the licence CC BY-SA 4.0, and stays so in the file written here
# (app/src/main/assets/vikivortaro/eo-eo.tsv), kept apart from Reta
# Vortaro's data (GPL-2.0). The file is committed, so that the app is built
# the same every time, as F-Droid requires.
import bz2
import html
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

SOURCE = sys.argv[1]
ROOT = os.path.join(os.path.dirname(__file__), "..")
WORDS = os.path.join(ROOT, "data", "vikivortaro-eo-words.txt")
FIXES = os.path.join(ROOT, "data", "vikivortaro-eo-fixes.tsv")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "vikivortaro", "eo-eo.tsv")

LANG = re.compile(r"^==\s*\{\{Lingvo\|([^}|]+)\}\}\s*==\s*$", re.M)


def inner(template):
    """The readable argument of a few templates ({{l|eo|word}}), and the
    labels of style ({{k|eo|S: evitenda}}: "(evitenda)"); the others are
    left out (fields, references, layout)"""
    parts = template[2:-2].split("|")
    name = parts[0].strip().lower()
    if name in ("l", "ligilo", "t", "w") and len(parts) > 1:
        return parts[-1]
    if name == "k":
        style = [p.strip() for p in parts[2:]]
        style = [p.split(":", 1)[1].strip(" %") if p.startswith("S:") else p for p in style
                 if p.startswith("S:") or (style[0].startswith("S:") and ":" not in p)]
        if style:
            return "(" + ", ".join(style) + ")"
    return ""


def clean(text):
    text = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", text, flags=re.S)
    for _ in range(3):
        text = re.sub(r"\{\{[^{}]*\}\}", lambda m: inner(m.group(0)), text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[https?://\S+ ([^\]]*)\]", r"\1", text)
    text = re.sub(r"'''?|<[^>]+>", "", text)
    text = html.unescape(text)
    # What the labels left out leave: "()", "(, , rare)", "((evitenda))"
    text = re.sub(r",\s*(?=,)", "", text)
    text = re.sub(r"\(\s*\)", "", text)
    text = re.sub(r"\(\s*(?:,\s*)+", "(", text)
    text = re.sub(r"\(\s*\(([^()]*)\)", r"(\1", text)
    text = re.sub(r"\(([^()]*)\)\s*\)", r"(\1)", text)
    # The signs of PIV copied with a definition: its field ("Λ", "🌄") and
    # its cross-references ("☞ derivilo.")
    text = re.sub(r"\s*☞[^.;]*[.;]?", "", text)
    text = re.sub(r"^(?:[^\sA-Za-zĈĉĜĝĤĥĴĵŜŝŬŭ0-9(«„\"'\[]{1,2}\s+)+", "", text.strip())
    return " ".join(text.split()).strip(" ;,")


# A line that is only a template, maybe as a heading: "==={{Vortospeco|substantivo|eo}}===", "{{Sinonimoj}}"
TEMPLATE_LINE = re.compile(r"^\s*(=+\s*)?\{\{([^|{}]+)(\|[^{}]*)?\}\}\s*(=+)?\s*$")
HEADING = re.compile(r"^\s*=+\s*(.*?)\s*=+\s*$")
# Templates alone on a line that don't start a section
NOT_SECTIONS = {"Deklinacio-eo", "Deklinacio-0-eo", "Akuzativo-eo", "Esperanta verbo",
                "Vorto de la semajno", "Zamenhofa", "Esperantaj literoj"}
# Sections before the senses, on pages with no "Signifoj"
BEFORE_SENSES = {"Etimologio", "Deveno", "Vorterseparo", "Silabseparo"}
# Plain headings that aren't a part of speech
PLAIN_SECTIONS = {"Tradukoj", "Etimologio", "Elparolo", "Prononco", "Sinonimoj", "Antonimoj",
                  "Derivaĵoj", "Vortfaradoj", "Vidu ankaŭ", "Referencoj", "Fontoj", "Ekzemploj",
                  "Rimarkoj", "Esprimoj", "Hiponimoj", "Hiperonimoj", "Signifoj"}
SENSE_NUMBER = re.compile(r"^:\s*\[\d+[a-z]?\]")

def zones(section):
    """The lines of the section, each with the zone it is in: "signifoj", "pos"
    (right under the language or a part of speech), "before" (Etimologio…),
    or None (Sinonimoj, Tradukoj…); and ("start", "") where a part of speech
    starts"""
    zone = "pos"
    for line in section.splitlines():
        m = TEMPLATE_LINE.match(line)
        if m:
            name = m.group(2).strip()
            if name == "Signifoj":
                zone = "signifoj"
            elif name == "Vortospeco":
                zone = "pos"
                yield "start", ""
            elif name in BEFORE_SENSES:
                zone = "before"
            elif m.group(1) or (name[:1].isupper() and name not in NOT_SECTIONS):
                zone = None
            continue
        h = HEADING.match(line)
        if h:
            title = h.group(1)
            if "Vortospeco" in title:
                zone = "pos"
                yield "start", ""
            elif "Signifoj" in title:
                zone = "signifoj"
            elif "{{" not in title and title not in PLAIN_SECTIONS:
                zone = "pos"      # "=== Adjektivo ==="
                yield "start", ""
            else:
                zone = None
            continue
        yield zone, line

def sense(line, signifoj):
    if line.startswith("#") and not line.startswith(("#:", "#*", "##")):
        return "#", line[1:]
    if signifoj and SENSE_NUMBER.match(line):
        return "#", SENSE_NUMBER.sub("", line)
    if signifoj and line.startswith("*") and not line.startswith(("**", "*:")):
        return "*", line[1:]
    return None, None

def senses(section, clean):
    """The senses of an Esperanto section, part of speech by part of speech:
    in a part with a "Signifoj" block, only its lines "# …" or ":[1] …" (or
    "* …" when it has none of those); in a part without, the "# …" lines under
    the language, the part of speech, or the etymology. Sinonimoj, Hiponimoj,
    Deveno… are skipped."""
    parts = [[]]
    for zone, line in zones(section):
        if zone == "start":
            parts.append([])
        else:
            parts[-1].append((zone, line))
    texts = []
    for lines in parts:
        if any(z == "signifoj" for z, _ in lines):
            found = [sense(l, True) for z, l in lines if z == "signifoj"]
            keep = "#" if any(k == "#" for k, _ in found) else "*"
            texts += [t for k, t in found if k == keep]
        else:
            texts += [t for k, t in (sense(l, False) for z, l in lines if z in ("pos", "before")) if k]
    return [t for t in map(clean, texts) if t]


def child(element, name):
    for c in element.iter():
        if c.tag.split("}")[-1] == name:
            return c
    return None


def open_source(source):
    stream = urllib.request.urlopen(source) if source.startswith("http") else open(source, "rb")
    return bz2.open(stream)


def wanted_words():
    with open(WORDS, encoding="utf-8") as f:
        return {line.rstrip("\n") for line in f if line.strip() and not line.startswith("#")}


def fixes():
    """{word: (Wiktionary's definition, the corrected one)}"""
    found = {}
    with open(FIXES, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or line.startswith("word\t") or not line.strip():
                continue
            word, wiktionary, corrected = line.rstrip("\n").split("\t")
            found[word] = (wiktionary, corrected)
    return found


wanted = wanted_words()
fixed = fixes()
stream = open_source(SOURCE)
definitions = {}
# The date of the dump: that of its last edit
latest = ""
for _, element in ET.iterparse(stream, events=("end",)):
    tag = element.tag.split("}")[-1]
    if tag == "timestamp" and element.text and element.text > latest:
        latest = element.text
    if tag != "page":
        continue
    title, ns, text = child(element, "title"), child(element, "ns"), child(element, "text")
    if ns is not None and ns.text == "0" and title is not None and title.text in wanted and text is not None and text.text:
        page = text.text
        languages = list(LANG.finditer(page))
        for i, m in enumerate(languages):
            if m.group(1).strip() != "eo":
                continue
            section = page[m.end(): languages[i + 1].start() if i + 1 < len(languages) else len(page)]
            found = senses(section, clean)
            if found:
                definitions[title.text] = " ¶ ".join(found)
    element.clear()

adapted = set()
for word, (wiktionary, corrected) in fixed.items():
    if definitions.get(word) != wiktionary:
        sys.exit(f"{word}: Wiktionary's definition changed, check its fix:\n  now:   {definitions.get(word)}\n  fixed: {wiktionary}")
    definitions[word] = corrected
    adapted.add(word)
missing = sorted(wanted - definitions.keys())
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# From the Esperanto Wiktionary (eo.wiktionary.org), by its contributors, CC BY-SA 4.0; some definitions\n"
            "# corrected for Bona vortaro (adaptitaj, data/vikivortaro-eo-fixes.tsv).\n"
            f"# Dump of {latest[:10] or 'date unknown'} (its last edit), with scripts/vikivortaro-eo.py. Senses separated by \" ¶ \".\n"
            "# Columns: the word, its definition, and \"adaptita\" if corrected.\n")
    for word in sorted(definitions, key=str.lower):
        if word in wanted:
            f.write(f"{word}\t{definitions[word]}\t{'adaptita' if word in adapted else ''}\n")
print(f"{len(wanted & definitions.keys())} definitions ({len(fixed)} fixed), {len(missing)} without: {', '.join(missing[:20])}")
