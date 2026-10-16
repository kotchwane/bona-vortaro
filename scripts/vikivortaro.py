#!/usr/bin/env python3
# Extracts from the French Wiktionary what Bona vortaro shows next to Reta
# Vortaro: French words with their Esperanto translations, and the short
# French descriptions of Esperanto words.
#
# Usage, from the root of the repository:
#   python3 scripts/vikivortaro.py https://kaikki.org/dictionary/downloads/fr/fr-extract.jsonl.gz
#   (or the path of that file, downloaded)
#
# The source is Kaikki's extraction (wiktextract) of the French Wiktionary.
# Its content is by the contributors of the French Wiktionary, under the
# licence CC BY-SA 4.0, and stays so in the files written here, which are
# kept apart from Reta Vortaro's data (GPL-2.0). The files are committed:
# Kaikki changes its extract every week, and the app is built the same
# every time, as F-Droid requires.
"""Writes, into app/src/main/assets/vikivortaro/:
  fr-eo.tsv   a French word, a tab, an Esperanto translation (one per line)
  eo-fr.tsv   an Esperanto word, a tab, its French description
Lines starting with "#" are comments: the source, its date, the licence."""
import email.utils
import gzip
import json
import os
import re
import sys
import urllib.request

SOURCE = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "vikivortaro")

# The descriptions that only send to another word: "Pluriel de …", not
# the word alone, which describes "pluralo": "Pluriel."; and
# anywhere in them, "Volitif du verbe …", "Synonyme de …"
FORM = re.compile(r"^(pluriel|accusatif|forme|participe|présent|passé|futur|conditionnel|impératif|infinitif|"
                  r"orthographe|variante|singulier|nominatif|graphie)\b(?=\s*\S)(?!\s*\.?$)", re.I)
SENDS = re.compile(r"\b(volitif|pseudo-accusatif|synonyme de)\b", re.I)

# What the extract sometimes gives instead of a description: the source of the
# page, its pronunciation, its placeholders ("Exemple d'utilisation manquant")
MARKUP = re.compile(r"\\|===|#\*|(^|\s)#(\s|$)|https?://|\(Ajouter\)|lire en ligne|\{\{|\[\[")

# A label before a description, "(intr.)", and a note after it, "Rem: …";
# not a word of the description, "(Faire) accoucher", "(Se) faire représenter"
LABEL = re.compile(r"^(?!\([A-ZÀ-Ý][a-zà-ÿ]*\)\s*[a-zà-ÿ])\([^)]{1,30}\)\s*")
NOTE = re.compile(r"\s*\(?\b(Rem|Note)\s*:.*$")

# The letters of Esperanto, a name written otherwise being left untranslated
ESPERANTO = re.compile(r"^[a-pr-vzĉĝĥĵŝŭ '’\-.]*$", re.I)

# The suffix -in- of a woman, "alĝerianino" next to "alĝeriano"
FEMININE = re.compile(r"^(.+)in(oj?)$")

# The suffix -iĉ- of the reform, "alĝerianiĉo" next to "alĝeriano"
REFORM = re.compile(r"^(.+)iĉ(oj?)$")


def lines(source):
    if source.startswith("http"):
        stream = urllib.request.urlopen(source)
        date = stream.headers.get("Last-Modified", "")
    else:
        stream = open(source, "rb")
        # The date of the downloaded file, as the server gives it
        date = email.utils.formatdate(os.path.getmtime(source), usegmt=True)
    if source.endswith(".gz"):
        stream = gzip.GzipFile(fileobj=stream)
    return date, (line.decode("utf-8") for line in stream)


def clean(word):
    return " ".join(word.split()).strip(" ,;")


# Words, not signs: "(((écho)))" or "#" are left out
WORD = re.compile(r"^[^\W\d_][\w '’\-.]*$")

# Nor the patterns of dates, "AAAA-MM-JJ" → "JJJJ-MM-TT"
DATE_PATTERN = re.compile(r"^(AAAA|MM|JJ)(-?(AAAA|MM|JJ))+$")


def clean_gloss(gloss):
    """A description as a reader reads it: without its label nor a note to the
    editors, with a capital and a final stop, and once only: "ex.." """
    gloss = NOTE.sub("", LABEL.sub("", clean(gloss))).strip(" ;,")
    if not gloss:
        return gloss
    gloss = gloss[0].upper() + gloss[1:]
    if gloss.endswith("..") and not gloss.endswith("..."):
        gloss = gloss[:-1]
    elif gloss[-1].isalnum() or gloss[-1] in ")»":
        gloss += "."
    return gloss


def usable(gloss):
    return gloss and not FORM.match(gloss) and not SENDS.search(gloss) and not MARKUP.search(gloss)


date, source_lines = lines(SOURCE)
translations = set()
descriptions = {}
for line in source_lines:
    try:
        entry = json.loads(line)
    except ValueError:
        continue
    code = entry.get("lang_code")
    if code == "fr":
        french = clean(entry.get("word", ""))
        masculine = "masculine" in (entry.get("tags") or []) and "feminine" not in (entry.get("tags") or [])
        found = [t for t in entry.get("translations") or [] if t.get("lang_code") == "eo"]
        words = {clean(t.get("word", "")) for t in found}
        for t in found:
            esperanto = clean(t.get("word", ""))
            if not (WORD.match(french) and WORD.match(esperanto)) or DATE_PATTERN.match(french):
                continue
            # A masculine word for a woman, "Algérien" → "alĝerianino", next to
            # the word for a man ("alĝeriano"); not "kazino", "ravino", "ĉino"
            feminine = FEMININE.match(esperanto)
            if masculine and feminine and (feminine.group(1) + "o" in words or feminine.group(1) + "oj" in words):
                continue
            # A word of the reform next to the usual one
            reform = REFORM.match(esperanto)
            if reform and (reform.group(1) + "o" in words or reform.group(1) + "oj" in words):
                continue
            if "proposition pour masculin" in (t.get("raw_tags") or []) + (t.get("tags") or []):
                continue
            # A name left as it is: in letters Esperanto doesn't have,
            # "Besançon", or without the ending of a noun, "Caen", "Cannes"
            # (not "Oslo", "Ljubljana", "Maria", "DDT")
            if esperanto == french and not (len(esperanto) > 1 and esperanto.isupper()) and (
                    not ESPERANTO.match(esperanto)
                    or (esperanto[:1].isupper() and not re.search(r"(oj?|a)$", esperanto))):
                continue
            translations.add((french, esperanto))
    elif code == "eo":
        esperanto = clean(entry.get("word", ""))
        for sense in entry.get("senses") or []:
            for gloss in sense.get("glosses") or []:
                if MARKUP.search(gloss):
                    continue
                gloss = clean_gloss(gloss)
                if esperanto and usable(gloss) and esperanto not in descriptions:
                    descriptions[esperanto] = gloss

# Only the descriptions of the words that the French words lead to
wanted = {esperanto for _, esperanto in translations}
descriptions = {k: v for k, v in descriptions.items() if k in wanted}

os.makedirs(OUT, exist_ok=True)
header = (f"# From the French Wiktionary (fr.wiktionary.org), by its contributors, CC BY-SA 4.0.\n"
          f"# Extracted by Kaikki (kaikki.org), {date or 'date unknown'}, with scripts/vikivortaro.py.\n")
with open(os.path.join(OUT, "fr-eo.tsv"), "w", encoding="utf-8") as f:
    f.write(header)
    for french, esperanto in sorted(translations):
        f.write(f"{french}\t{esperanto}\n")
with open(os.path.join(OUT, "eo-fr.tsv"), "w", encoding="utf-8") as f:
    f.write(header)
    for esperanto, gloss in sorted(descriptions.items()):
        f.write(f"{esperanto}\t{gloss}\n")
print(f"{len(translations)} translations, {len(descriptions)} descriptions")
