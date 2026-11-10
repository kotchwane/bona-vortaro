#!/usr/bin/env python3
# Extracts from the French or English Wiktionary what Bona vortaro shows next
# to Reta Vortaro: the words of that language with their Esperanto
# translations, and the short descriptions of Esperanto words in it.
#
# Usage, from the root of the repository:
#   python3 scripts/vikivortaro.py https://kaikki.org/dictionary/downloads/fr/fr-extract.jsonl.gz
#   python3 scripts/vikivortaro.py en \
#       https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz \
#       https://kaikki.org/dictionary/Esperanto/kaikki.org-dictionary-Esperanto.jsonl
#   (or the paths of those files, downloaded)
#
# The source is Kaikki's extraction (wiktextract) of the French Wiktionary,
# which has both the French words and the Esperanto ones; of the English
# Wiktionary, Kaikki's files of its English words and of its Esperanto ones.
# Its content is by the contributors of that Wiktionary, under the
# licence CC BY-SA 4.0, and stays so in the files written here, which are
# kept apart from Reta Vortaro's data (GPL-2.0). The files are committed:
# Kaikki changes its extract every week, and the app is built the same
# every time, as F-Droid requires.
"""Writes, into app/src/main/assets/vikivortaro/ (here for French, "en" for English):
  fr-eo.tsv   a French word, a tab, an Esperanto translation (one per line);
              in English, a tab and the sense of the word it translates, the
              heading of its table, when there is one
  eo-fr.tsv   an Esperanto word, a tab, its French description
Lines starting with "#" are comments: the source, its date, the licence."""
import email.utils
import gzip
import json
import os
import re
import sys
import urllib.request

LANGUAGE = sys.argv[1] if sys.argv[1] in ("fr", "en") else "fr"
SOURCES = sys.argv[2:] if sys.argv[1] in ("fr", "en") else sys.argv[1:]
NAME = {"fr": "French", "en": "English"}[LANGUAGE]
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "vikivortaro")

# The descriptions that only send to another word: "Pluriel de …", not
# the word alone, which describes "pluralo": "Pluriel."; and
# anywhere in them, "Volitif du verbe …", "Synonyme de …"
FORM = re.compile(r"^(pluriel|accusatif|forme|participe|présent|passé|futur|conditionnel|impératif|infinitif|"
                  r"orthographe|variante|singulier|nominatif|graphie)\b(?=\s*\S)(?!\s*\.?$)", re.I)
SENDS = re.compile(r"\b(volitif|pseudo-accusatif|synonyme de)\b", re.I)
# In English, the forms are tagged ("form-of"); what only sends to another
# word: "Synonym of …", "Alternative form of …"
SENDS_EN = re.compile(r"\b(synonym of|alternative (form|spelling) of|obsolete (form|spelling) of|misspelling of|"
                      r"abbreviation of|initialism of|clipping of|short for)\b", re.I)

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


# Esperanto words as Wiktionary's editors sometimes write them: in the
# x-system ("vespermangxo"), with ǔ or ù for ŭ ("neǔtrino", "laùaserte"),
# the Czech š for ŝ ("šiplifto")
X_SYSTEM = {"c": "ĉ", "g": "ĝ", "h": "ĥ", "j": "ĵ", "s": "ŝ", "u": "ŭ",
            "C": "Ĉ", "G": "Ĝ", "H": "Ĥ", "J": "Ĵ", "S": "Ŝ", "U": "Ŭ"}
TYPOS = str.maketrans({"ǔ": "ŭ", "ù": "ŭ", "Ǔ": "Ŭ", "š": "ŝ", "Š": "Ŝ"})
# A letter Esperanto doesn't have (q, w, x, y are kept, as in names)
FOREIGN_LETTER = re.compile(r"(?![a-pr-vzĉĝĥĵŝŭqwxy])[^\W\d_]", re.I)


def esperanto_spelling(word):
    """An Esperanto word of Wiktionary in Esperanto's letters; None for what
    isn't one: a word written small in other letters, "vandløs", "de día en
    día", "jam temp' está" (a name keeps its letters, "Schrödinger"), or a
    note to the reader, "but see Usono" (test round 7)"""
    word = " ".join(
        re.sub(r"([cghjsuCGHJSU])[xX]", lambda m: X_SYSTEM[m.group(1)], token) if token[:1].islower() else token
        for token in word.split(" ")).translate(TYPOS)
    word = re.sub(r"\s+[Ll]iterally$", "", word)
    if re.search(r"\b(but see|see also)\b", word):
        return None
    if any(token[:1].islower() and FOREIGN_LETTER.search(token) for token in re.split(r"[\s\-’']+", word)):
        return None
    return word


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
    if LANGUAGE == "en":
        return gloss and not SENDS_EN.search(gloss) and not MARKUP.search(gloss)
    return gloss and not FORM.match(gloss) and not SENDS.search(gloss) and not MARKUP.search(gloss)


def entries():
    """The entries of all the sources, and the date of the first one"""
    dates = []
    def read(source):
        date, source_lines = lines(source)
        dates.append(date)
        for line in source_lines:
            yield line
    def all_lines():
        for source in SOURCES:
            yield from read(source)
    return dates, all_lines()


def eo_translations(entry):
    """The Esperanto translations of an entry: in the French Wiktionary, of
    the entry; in the English one, of the entry and of each sense"""
    found = [(t, likeliest_sense(t)) for t in entry.get("translations") or []]
    if LANGUAGE == "en":
        for index, sense in enumerate(entry.get("senses") or []):
            found += [(t, index) for t in sense.get("translations") or []]
    return [(t, sense) for t, sense in found if t.get("lang_code") == "eo" and t.get("word")]


# What a heading of a table adds to its sense, or a heading that says
# nothing of it: "— see also hug", "1.1. ", "Translations"
SEE_ALSO = re.compile(r"\s+—\s+see also\b.*$")
NUMBERING = re.compile(r"^\d+(\.\d+)*\.\s+")


# What the extract leaves of the page's markup: italics, links, a power of
# ten ("10<sup>33</sup>"); and the notes of the editors to themselves
WIKI_LINK = re.compile(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]")
SUP = re.compile(r"<sup>([0-9+−-]+)</sup>")
EDITORS_NOTE = re.compile(r"\s*\((?:to be merged|these are translations)[^)]*\)|\s+-\s+translation entry$", re.I)
# A power whose exponent the extract lost: "1060" for 10⁶⁰, "260 bytes"
# for 2⁶⁰ (round 9); not 1000, 1024, nor a year
LOST_POWER = re.compile(r"\b10(?!00\b|24\b)\d{2,3}\b|\b2[1-9]0 (?:bits|bytes|or)\b")


def sense_heading(translation):
    """The sense of the word that an English translation translates, as the
    heading of its table says it: "go → fari", "to make (a specified
    sound)"; None if it says nothing, or if it lost a power of ten"""
    heading = clean(translation.get("sense") or "")
    heading = SUP.sub(lambda m: m.group(1).translate(SUPERSCRIPT_DIGITS), heading)
    if LOST_POWER.search(heading):
        return None
    heading = WIKI_LINK.sub(r"\1", heading).replace("''", "")
    heading = EDITORS_NOTE.sub("", NUMBERING.sub("", SEE_ALSO.sub("", heading))).lstrip("—– ").strip()
    if heading.lower() in ("translations", "translation", "translations to be checked"):
        return None
    return heading or None


SUPERSCRIPT_DIGITS = str.maketrans("0123456789+−-", "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁻")


def likeliest_sense(translation):
    """The sense of a table that Kaikki didn't tie to one: the one its
    scores (_dis1) make likeliest, "slut"'s first for ĉiesulino, "hand"'s
    fourth for plenmano; else after the others, in the order of the page"""
    try:
        scores = [int(score) for score in translation.get("_dis1", "").split()]
    except ValueError:
        scores = []
    return scores.index(max(scores)) if scores and max(scores) > 0 else 10_000


dates, source_lines = entries()
# Each pair with its group: the entry and the sense its table translates
translations = {}
# In English, the senses of an Esperanto word after its first, its
# description: "memkontenta", self-satisfied; complacent; smug
later_glosses = {}
# In English, each pair's sense, by the heading of its table
headings = {}
entry_number = 0
descriptions = {}
for line in source_lines:
    try:
        entry = json.loads(line)
    except ValueError:
        continue
    code = entry.get("lang_code")
    if code == LANGUAGE:
        french = clean(entry.get("word", ""))
        # French only: its words have a gender, English ones don't
        masculine = LANGUAGE == "fr" and "masculine" in (entry.get("tags") or []) and "feminine" not in (entry.get("tags") or [])
        entry_number += 1
        found = eo_translations(entry)
        words = {clean(t.get("word", "")) for t, _ in found}
        for t, group in found:
            esperanto = esperanto_spelling(clean(t.get("word", "")))
            if not esperanto or not (WORD.match(french) and WORD.match(esperanto)) or DATE_PATTERN.match(french):
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
            translations.setdefault((french, esperanto), (entry_number, group))
            if LANGUAGE == "en" and sense_heading(t):
                headings.setdefault((french, esperanto), sense_heading(t))
    elif code == "eo":
        esperanto = esperanto_spelling(clean(entry.get("word", ""))) or ""
        for sense in entry.get("senses") or []:
            # In English, a form of a word, "plural of", "past of", is tagged
            if LANGUAGE == "en" and {"form-of", "alt-of"} & set(sense.get("tags") or []):
                continue
            for gloss in sense.get("glosses") or []:
                if MARKUP.search(gloss):
                    continue
                written = NOTE.sub("", LABEL.sub("", clean(gloss))).strip(" ;,")
                gloss = clean_gloss(gloss)
                if esperanto and usable(gloss) and esperanto not in descriptions:
                    descriptions[esperanto] = gloss
                elif esperanto and usable(gloss) and LANGUAGE == "en":
                    # As written: "Cuban" stays a capital, "fellow
                    # Esperantist" doesn't take one
                    later_glosses.setdefault(esperanto, []).append(written)

# The pairs left out after a review (data/vikivortaro-<language>-rejected.tsv):
# out of the translations, and listed for the app, which leaves them out of
# the pairs it makes from the descriptions too ("painfully" → oj)
# A "*" for the foreign word leaves the Esperanto word out altogether, its
# pairs and its description: a word that isn't Esperanto as written,
# "posivisto", "chiaroscuro" (review of the new roots, 2026-11-10)
REJECTED = os.path.join(os.path.dirname(__file__), "..", "data", f"vikivortaro-{LANGUAGE}-rejected.tsv")
rejected = set()
rejected_words = set()
if os.path.exists(REJECTED):
    with open(REJECTED, encoding="utf-8") as f:
        for row in f:
            fields = row.rstrip("\n").split("\t")
            if not row.startswith("#") and len(fields) >= 2:
                if fields[0] == "*":
                    rejected_words.add(fields[1])
                else:
                    rejected.add((fields[0], fields[1]))

# Only the descriptions of the words that the foreign words lead to, counted
# before the rejections: a description stays useful when only one pair leading
# to it was wrong ("minuskla: Lowercase." gives lowercase → minuskla)
wanted = {esperanto for _, esperanto in translations}
descriptions = {k: v for k, v in descriptions.items() if k in wanted}


def gloss_words(gloss):
    """The English words a sense gives, when it is only such words, as the
    app takes them from a description (Vikivortaro.glossPairs): at most two
    words each, no small words, no signs"""
    text = gloss.rstrip(".")
    if not text or "." in text or any(c in "()[]:#«»\"/=+…!?" or c.isdigit() for c in text):
        return []
    items = [item.strip() for item in re.split("[,;]", text)]
    if any(len(item.split(" ")) not in (1, 2) or any(not w or w.lower() in GLOSS_SMALL for w in item.split(" "))
           for item in items):
        return []
    return items


GLOSS_SMALL = {"of", "the", "a", "an", "to", "for", "by", "in", "on", "at", "with", "from", "who", "which", "that",
               "or", "and"}

# The words of an Esperanto word's later senses, which the app doesn't read
# (it reads the description, the first): "smug → memkontenta", reviewed on
# 2026-11-10 (438 kept of 455), after the pairs of the tables
known_pairs = {(foreign.lower(), esperanto) for foreign, esperanto in translations}
for esperanto in sorted(later_glosses):
    if esperanto not in descriptions or esperanto in rejected_words:
        continue
    for gloss in later_glosses[esperanto]:
        # Written as Wiktionary writes them, its capitals meant: "Cuban"
        for foreign in gloss_words(gloss):
            if (foreign.lower(), esperanto) not in known_pairs:
                known_pairs.add((foreign.lower(), esperanto))
                translations[(foreign, esperanto)] = (10 ** 9, 0)
translations = {pair: group for pair, group in translations.items() if pair not in rejected and pair[1] not in rejected_words}
descriptions = {k: v for k, v in descriptions.items() if k not in rejected_words}

# How often Reta Vortaro's words are used (assets/frequency/leipzig.tsv)
FREQUENCIES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "frequency", "leipzig.tsv")
frequency = {}
with open(FREQUENCIES, encoding="utf-8") as f:
    for row in f:
        fields = row.rstrip("\n").split("\t")
        if not row.startswith("#") and len(fields) >= 2:
            try:
                frequency[fields[0]] = float(fields[1])
            except ValueError:
                pass


RARE = 1.0


def ordered(pairs):
    """The pairs by word; each word's translations by entry, as the page
    gives them (the noun "hand", mano, before the verb, enmanigi, doni);
    in an entry, by sense as Wiktionary orders its senses, except a sense
    whose words are all rare (less than once per million words), after the
    others: Kaikki ties "dog"'s "animal" (hundo, hundino) to a later sense
    than "male canine" (virhundo). Not by how used the words are, though:
    "have" would start with esti, the auxiliary, "dream" with revo, the
    hope, "slut" with hundino, the old "female dog". The app keeps this
    order."""
    # Once for words that differ only in their case: the one written as the
    # Esperanto word, "man → homo", "Bible → Biblio", not also "Man → homo",
    # "bible → Biblio"; else the first ("Irish wolfhound", "irish wolfhound",
    # test round 7)
    chosen = {}
    for foreign, esperanto in pairs:
        key = (foreign.lower(), esperanto)
        if key not in chosen or (foreign[:1].isupper() == esperanto[:1].isupper()
                                 and chosen[key][:1].isupper() != esperanto[:1].isupper()):
            chosen[key] = foreign
    by_word = {}
    for (foreign, esperanto), group in pairs.items():
        if chosen[(foreign.lower(), esperanto)] == foreign:
            by_word.setdefault(foreign, {}).setdefault(group, []).append(esperanto)
    for foreign in sorted(by_word):
        groups = sorted(by_word[foreign].items(),
                        key=lambda item: (item[0][0], max(frequency.get(w, 0.0) for w in item[1]) < RARE, item[0][1]))
        for _, words in groups:
            for esperanto in words:
                yield foreign, esperanto


os.makedirs(OUT, exist_ok=True)
date = dates[0] if dates else ""
header = (f"# From the {NAME} Wiktionary ({LANGUAGE}.wiktionary.org), by its contributors, CC BY-SA 4.0.\n"
          f"# Extracted by Kaikki (kaikki.org), {date or 'date unknown'}, with scripts/vikivortaro.py.\n")
with open(os.path.join(OUT, f"{LANGUAGE}-eo.tsv"), "w", encoding="utf-8") as f:
    f.write(header)
    for french, esperanto in ordered(translations):
        heading = headings.get((french, esperanto))
        f.write(f"{french}\t{esperanto}\t{heading}\n" if heading else f"{french}\t{esperanto}\n")
with open(os.path.join(OUT, f"eo-{LANGUAGE}.tsv"), "w", encoding="utf-8") as f:
    f.write(header)
    for esperanto, gloss in sorted(descriptions.items()):
        f.write(f"{esperanto}\t{gloss}\n")
if rejected:
    with open(os.path.join(OUT, f"{LANGUAGE}-eo-not.tsv"), "w", encoding="utf-8") as f:
        f.write(f"# Pairs left out after a review, from data/vikivortaro-{LANGUAGE}-rejected.tsv.\n")
        for foreign, esperanto in sorted(rejected):
            f.write(f"{foreign}\t{esperanto}\n")
print(f"{len(translations)} translations, {len(descriptions)} descriptions, {len(rejected)} pairs and {len(rejected_words)} words left out")
