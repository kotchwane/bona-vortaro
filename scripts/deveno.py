#!/usr/bin/env python3
# Writes the origins of the words that Bona vortaro can show in the
# articles, when the user turns it on: two files, kept apart as their
# licences differ.
#
# Usage, from the root of the repository:
#   python3 scripts/deveno.py [dump of the Esperanto Wiktionary]
#   e.g. https://dumps.wikimedia.org/eowiktionary/latest/eowiktionary-latest-pages-articles.xml.bz2
#   (or the path of that file, downloaded)
#
# 1. András Rajki, "Etimologia Vortaro de Esperanto" (Budapest, 2006),
#    under CC0 1.0 (public domain): https://archive.org/details/EsperantoEtymologicalDictionary
#    data/rajki-etimologia-vortaro.txt is the text of its Esperanto.doc,
#    unchanged (textutil -convert txt). One root a line: "akvo = Ita. acqua,
#    Lat. aqua", the words it corresponds to in the languages it comes from;
#    or a calque in brackets: "senpeka [calque of Rus. бeзвинный]". Written
#    in Esperanto here: the names of the languages ("itala"), and Rajki's few
#    words of English ("calque of": "kalko de"). Nothing else is changed but
#    the Latin letters typed for their Cyrillic look-alikes in Russian words
#    ("paкeтa", which reads as "ракета"). A root given twice (radio, the
#    radio and the radius) has a line for each, in Rajki's order, with the
#    article of Reta Vortaro it is about, from data/rajki-homonyms.tsv.
#    Writes app/src/main/assets/deveno/rajki.tsv.
#
# 2. The Esperanto Wiktionary (eo.wiktionary.org), by its contributors, CC
#    BY-SA 4.0: the section "Deveno" or "Etimologio" of the Esperanto words,
#    its templates written out ("el {{deveno3|eo|fr|rouge}}": "el la franca
#    rouge"). A section that still has a template or markup afterwards is
#    left out, as are those that only point to a reference ("[1]").
#    Written only when a dump is given, to
#    app/src/main/assets/deveno/vikivortaro.tsv.
#
# In both files, the words of other languages are between ⟨ and ⟩, which the
# app shows in italics. The files are committed, so that the app is built
# the same every time, as F-Droid requires.
import bz2
import html
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..")
RAJKI = os.path.join(ROOT, "data", "rajki-etimologia-vortaro.txt")
RAJKI_HOMONYMS = os.path.join(ROOT, "data", "rajki-homonyms.tsv")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "deveno")

# Rajki's abbreviations ("Fra" once, for "Fre")
RAJKI_LANGUAGES = {
    "Fre": "franca", "Fra": "franca", "Eng": "angla", "Ita": "itala", "Ger": "germana", "Lat": "latina",
    "Rus": "rusa", "Lit": "litova", "Pol": "pola", "Yid": "jida", "Gre": "greka", "Spa": "hispana",
    "Dut": "nederlanda",
}
RAJKI_LANGUAGE = re.compile(r"\b(" + "|".join(RAJKI_LANGUAGES) + r")\.\s*")
# His words of English, in brackets
RAJKI_WORDS = [("back-formation from", "retroderivaĵo de"), ("calque of", "kalko de"), ("compare", "komparu")]
# Latin letters typed for Cyrillic ones
CYRILLIC = str.maketrans("aceopxyABCEHKMOPTXy", "асеорхуАВСЕНКМОРТХу")


def rajki_words(language, words):
    """"мaтeмaтикa", "(pl.) dentes": the words, in ⟨⟩"""
    plural = words.startswith("(pl.)")
    words = words.removeprefix("(pl.)").strip()
    if language == "Rus" and re.search("[а-яА-Я]", words):
        words = words.translate(CYRILLIC)
    shown = ", ".join(f"⟨{w.strip()}⟩" for w in words.split(",") if w.strip())
    return shown + (" (plurale)" if plural else "")


def rajki_text(text):
    """"Ita. acqua, Lat. aqua": "itala ⟨acqua⟩, latina ⟨aqua⟩"; in brackets,
    "[calque of Rus. …]": "kalko de la rusa ⟨…⟩" """
    text = text.strip("[] ")
    for english, esperanto in RAJKI_WORDS:
        text = text.replace(english, esperanto)
    phrases = "|".join(esperanto for _, esperanto in RAJKI_WORDS)
    parts = RAJKI_LANGUAGE.split(text)
    # parts: [before, code, words, code, words…]
    shown = parts[0]
    for code, words in zip(parts[1::2], parts[2::2]):
        # "laxatif, kalko de": the words, then the next phrase
        m = re.search(rf",?\s*\b({phrases})\s*$", words)
        phrase = m.group(1) if m else ""
        words = (words[:m.start()] if m else words).strip().rstrip(",").strip()
        name = RAJKI_LANGUAGES[code]
        # "kalko de la rusa", "komparu la jidan": after his words, with "la"
        if shown.rstrip().endswith(" de") or shown.rstrip() == "de":
            name = "la " + name
        elif shown.rstrip().endswith("komparu"):
            name = "la " + name + "n"
        shown += name + " " + rajki_words(code, words) + ", " + (phrase + " " if phrase else "")
    return re.sub(r"\s+", " ", shown).strip(" ,")


def rajki_homonyms():
    """{(root, line number): [files]}"""
    found = {}
    with open(RAJKI_HOMONYMS, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            word, number, file = line.rstrip("\n").split("\t")
            found.setdefault((word, int(number)), []).append(file)
    return found


def rajki():
    entries = []
    with open(RAJKI, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            m = re.match(r"^(\S+) = (.+)$", line) or re.match(r"^(\S+) (\[.+\])$", line)
            if m:
                entries.append((m.group(1), rajki_text(m.group(2))))
    # The roots given twice: each line with its article(s)
    homonyms = rajki_homonyms()
    counts = {}
    for word, _ in entries:
        counts[word] = counts.get(word, 0) + 1
    rows = []
    seen = {}
    for word, text in entries:
        if counts[word] == 1:
            rows.append((word, text, ""))
            continue
        # Its number among the lines of that root, in Rajki's order
        seen[word] = number = seen.get(word, 0) + 1
        files = homonyms.get((word, number))
        if not files:
            sys.exit(f"{word}: line {number} of Rajki's has no article in {RAJKI_HOMONYMS}")
        rows += [(word, text, file) for file in files]
    with open(os.path.join(OUT, "rajki.tsv"), "w", encoding="utf-8") as f:
        f.write("# András Rajki, Etimologia Vortaro de Esperanto (2006), CC0 1.0\n"
                "# (https://archive.org/details/EsperantoEtymologicalDictionary), written in Esperanto by\n"
                "# scripts/deveno.py. Columns: the root, the words it corresponds to, and for a root given twice\n"
                "# the file of the article of Reta Vortaro the line is about (\"-\" for none).\n")
        for word, text, file in rows:
            f.write(f"{word}\t{text}\t{file}\n")
    print(f"Rajki: {len(entries)} roots")


# The Esperanto Wiktionary's names of the languages, for the codes of
# {{deveno3}} that its pages use
WIKI_LANGUAGES = {
    "la": "latina", "en": "angla", "fr": "franca", "grc": "antikva greka", "de": "germana", "it": "itala",
    "ru": "rusa", "pl": "pola", "es": "hispana", "sv": "sveda", "ar": "araba", "sa": "sanskrito", "ja": "japana",
    "pt": "portugala", "he": "hebrea", "frm": "meza franca", "el": "greka", "eo": "Esperanto", "haw": "havaja",
    "yi": "jida", "enm": "meza angla", "nl": "nederlanda", "ml": "malajalama", "egy": "antikva egipta",
    "hi": "hinda", "hu": "hungara", "avk": "Kotavo", "tok": "Tokipono", "fro": "malnova franca",
    "se": "nordsamea", "is": "islanda", "qu": "keĉua", "mi": "maoria", "ms": "malaja",
    "gmh": "meza alta germana", "goh": "malnova alta germana", "ko": "korea", "lt": "litova", "eu": "eŭska",
}
# Names that aren't adjectives: "el sanskrito", not "el la sanskrito"
WIKI_NOUNS = {"sanskrito", "Esperanto", "Kotavo", "Tokipono"}

LANG = re.compile(r"^==\s*\{\{Lingvo\|([^}|]+)\}\}\s*==\s*$", re.M)
# Where the origin starts: "{{Deveno}}", "=== Etimologio ===", "'''etimologio:'''"
START = re.compile(r"^\s*(?:=+\s*)?(?:\{\{(?:Deveno|Etimologio)\}\}|Deveno|Etimologio)\s*(?:=+)?\s*$|^'''etimologio:'''", re.M | re.I)
# Where it ends: the next section
END = re.compile(r"^\s*(?:=+.*=+|\{\{[A-ZĈĜĤĴŜŬ][^|{}]*(?:\|[^{}]*)?\}\})\s*$", re.M)


def deveno3(m):
    """{{deveno3|eo|fr|trou|ts=…|sg=…}}: "la franca ⟨trou⟩ (…) „…“" """
    parts = m.group(1).split("|")
    named = dict(p.split("=", 1) for p in parts if "=" in p)
    plain = [p for p in parts if "=" not in p]
    if len(plain) < 3 or plain[1] not in WIKI_LANGUAGES:
        return "{{?}}"
    name = WIKI_LANGUAGES[plain[1]]
    shown = (name if name in WIKI_NOUNS else "la " + name) + f" ⟨{plain[2]}⟩"
    if named.get("ts"):
        shown += f" ({named['ts']})"
    if named.get("sg") and named["sg"] != "-":
        shown += f" „{named['sg']}“"
    return shown


def wiki_text(section):
    text = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", section, flags=re.S)
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = text.replace("'''etimologio:'''", "")
    text = re.sub(r"\{\{deveno3\|([^{}]*)\}\}", deveno3, text)
    # The pieces of a word, "{{radi|brazilj}}", and words of another language, "{{t|la|acidus}}"
    text = re.sub(r"\{\{(?:radi|sufi|fina|pref|mems)\|([^{}|]*)\}\}", r"\1", text)
    text = re.sub(r"\{\{t\|[^{}|]*\|(?:[^{}|]*\|)?([^{}|]*)\}\}", r"⟨\1⟩", text)
    text = re.sub(r"\{\{elpropra\|[^{}|]*\|[^{}|]*\|([^{}|]*)(?:\|[^{}]*)?\}\}", r"\1", text)
    # References and notes of layout
    text = re.sub(r"\{\{(?:ref-|lit-|aŭdo|silaboj|elparolo|nevideblafinodesekcio)[^{}]*\}\}", "", text, flags=re.I)
    text = re.sub(r"\[\[(?:Kategorio|Dosiero|Category|File):[^\]]*\]\]", "", text)
    text = re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]", r"\1", text)
    text = text.replace("'''", "")
    text = re.sub(r"''([^']+)''", r"⟨\1⟩", text)
    text = re.sub(r"<[^>]+>", "", html.unescape(text))
    lines = [re.sub(r"^[:*#]+\s*(\[\d+\]\s*)?", "", l).strip() for l in text.splitlines()]
    text = " ".join(l for l in lines if l)
    text = re.sub(r"\s+", " ", text)
    # A heading of notes with nothing after it, the end of a comment
    text = re.sub(r"\s*notoj:\s*(-->)?\s*$", "", text, flags=re.I).strip(" .")
    # "⟨abdomen⟩" twice in a row, from a word in a link in italics
    text = re.sub(r"⟨⟨([^⟩]*)⟩⟩", r"⟨\1⟩", text)
    if not text or "{{" in text or "}}" in text or "[" in text or "''" in text or "|" in text or "⟨⟩" in text:
        return None
    # "El la angla …": lower case, after the source's name in the app
    if text[:1].isupper() and text[1:2].islower() and text.split(" ")[0] in ("El", "De", "Laŭ", "Kalko", "Mallongigo"):
        text = text[0].lower() + text[1:]
    return text


def child(element, name):
    for c in element.iter():
        if c.tag.split("}")[-1] == name:
            return c
    return None


def vikivortaro(source):
    stream = bz2.open(urllib.request.urlopen(source) if source.startswith("http") else open(source, "rb"))
    found, skipped, latest = {}, 0, ""
    for _, element in ET.iterparse(stream, events=("end",)):
        tag = element.tag.split("}")[-1]
        if tag == "timestamp" and element.text and element.text > latest:
            latest = element.text
        if tag != "page":
            continue
        title, ns, text = child(element, "title"), child(element, "ns"), child(element, "text")
        if ns is not None and ns.text == "0" and title is not None and text is not None and text.text:
            page = text.text
            languages = list(LANG.finditer(page))
            for i, m in enumerate(languages):
                if m.group(1).strip() != "eo":
                    continue
                part = page[m.end(): languages[i + 1].start() if i + 1 < len(languages) else len(page)]
                start = START.search(part)
                if not start:
                    continue
                rest = part[start.end():]
                end = END.search(rest)
                origin = wiki_text(rest[:end.start()] if end else rest)
                if origin:
                    found[title.text] = origin
                else:
                    skipped += 1
        element.clear()
    with open(os.path.join(OUT, "vikivortaro.tsv"), "w", encoding="utf-8") as f:
        f.write("# From the Esperanto Wiktionary (eo.wiktionary.org), by its contributors, CC BY-SA 4.0: the origins\n"
                f"# of the words (Deveno, Etimologio). Dump of {latest[:10] or 'date unknown'} (its last edit), with\n"
                "# scripts/deveno.py. Columns: the word, its origin.\n")
        for word in sorted(found, key=str.lower):
            f.write(f"{word}\t{found[word]}\n")
    print(f"Vikivortaro: {len(found)} origins, {skipped} left out (markup, or empty)")


os.makedirs(OUT, exist_ok=True)
rajki()
if len(sys.argv) > 1:
    vikivortaro(sys.argv[1])
