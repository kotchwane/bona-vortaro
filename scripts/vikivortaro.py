#!/usr/bin/env python3
# Extracts from the French, English or German Wiktionary what Bona vortaro shows next
# to Reta Vortaro: the words of that language with their Esperanto
# translations, and the short descriptions of Esperanto words in it.
#
# Usage, from the root of the repository:
#   python3 scripts/vikivortaro.py https://kaikki.org/dictionary/downloads/fr/fr-extract.jsonl.gz
#   python3 scripts/vikivortaro.py de https://kaikki.org/dictionary/downloads/de/de-extract.jsonl.gz
#   python3 scripts/vikivortaro.py en \
#       https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz \
#       https://kaikki.org/dictionary/Esperanto/kaikki.org-dictionary-Esperanto.jsonl
#   (or the paths of those files, downloaded)
#
# The source is Kaikki's extraction (wiktextract) of the French Wiktionary,
# which has both the French words and the Esperanto ones, and the same of the
# German Wiktionary; of the English
# Wiktionary, Kaikki's files of its English words and of its Esperanto ones.
# Its content is by the contributors of that Wiktionary, under the
# licence CC BY-SA 4.0, and stays so in the files written here, which are
# kept apart from Reta Vortaro's data (GPL-2.0). The files are committed:
# Kaikki changes its extract every week, and the app is built the same
# every time, as F-Droid requires.
"""Writes, into app/src/main/assets/vikivortaro/ (here for French, "en" for English, "de" for German):
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

LANGUAGE = sys.argv[1] if sys.argv[1] in ("fr", "en", "de") else "fr"
SOURCES = sys.argv[2:] if sys.argv[1] in ("fr", "en", "de") else sys.argv[1:]
NAME = {"fr": "French", "en": "English", "de": "German"}[LANGUAGE]
# Where the forms of a word are tagged ("form-of"), not told by their text
TAGGED_FORMS = LANGUAGE in ("en", "de")
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
# In German: "Nominativ Plural des Substantivs blato", when untagged;
# "Variante von …", "Synonym zu …"
SENDS_DE = re.compile(r"(^(Nominativ|Akkusativ|Indikativ|Imperativ|Konditional|Infinitiv|Partizip)\b.*\b(des|der) "
                      r"(Substantivs|Verbs|Adjektivs|Pronomens)\b|\b(Variante|Nebenform|Schreibvariante|Synonym|"
                      r"Abkürzung|Kurzform) (von|zu|für)\b)", re.I)

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
    editors, with a capital and a final stop, and once only: "ex.."; in
    German without a capital, which would make "stehlen", ŝteli, a noun"""
    gloss = NOTE.sub("", LABEL.sub("", clean(gloss))).strip(" ;,")
    if LANGUAGE == "de":
        gloss = german_gloss(gloss)
    if not gloss:
        return gloss
    if LANGUAGE != "de":
        gloss = gloss[0].upper() + gloss[1:]
    if gloss.endswith("..") and not gloss.endswith("..."):
        gloss = gloss[:-1]
    elif gloss[-1].isalnum() or gloss[-1] in ")»":
        gloss += "."
    return gloss


# The German Wiktionary's marks of a sense of the word, "satt₅", "noch₍₅₎",
# "ansetzen_([10a])", and stray spaces, "Kopf , Birne", "( machen)"
SENSE_MARK = re.compile(r"_?\(\[[^\]]*\]\)|_\(\w{1,4}\)|[₀-₉₍₎]+")


def german_gloss(gloss):
    """A German description without its sense marks nor stray spaces, each
    word once: "siegen, …, siegen" """
    gloss = SENSE_MARK.sub("", gloss)
    gloss = re.sub(r"\s+([,;)])", r"\1", gloss)
    gloss = re.sub(r"\(\s+", "(", gloss)
    gloss = re.sub(r";\s*:\s*", "; ", gloss)
    parts = re.split(r"(\s*[,;]\s*)", gloss)
    kept, seen = [], set()
    for i in range(0, len(parts), 2):
        item = parts[i].strip()
        if item and item.rstrip(".") in seen:
            continue
        seen.add(item.rstrip("."))
        if kept:
            kept.append(parts[i - 1])
        kept.append(parts[i])
    return " ".join("".join(kept).split()).strip(" ;,")


def usable(gloss):
    if LANGUAGE == "en":
        return gloss and not SENDS_EN.search(gloss) and not MARKUP.search(gloss)
    if LANGUAGE == "de":
        # Not a heading of the senses after it, "intransitiv:"
        return gloss and not SENDS_DE.search(gloss) and not MARKUP.search(gloss) and not gloss.rstrip(".").endswith(":") \
            and gloss.rstrip(".") not in ("transitiv", "intransitiv", "reflexiv")
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
    the entry; in the English one, of the entry and of each sense; in the
    German one, of the entry, with the number of their sense ("2", "1a")"""
    found = [(t, numbered_sense(t) if LANGUAGE == "de" else likeliest_sense(t)) for t in entry.get("translations") or []]
    if LANGUAGE == "en":
        for index, sense in enumerate(entry.get("senses") or []):
            found += [(t, index) for t in sense.get("translations") or []]
    return [(t, sense) for t, sense in found if t.get("lang_code") == "eo" and t.get("word")]


# How the English Wiktionary says an Esperanto word is made: its parts
# ("{{af|eo|maro|virino}}", "{{ety|eo|:af|forĵeti|-ebla}}"), or that it
# was borrowed ("{{ety|eo|:bor|pl:ocelot}}"), for the app to tell its own
# cut of a word ("mar·virin·o") from a wrong one ("oceloto", ocel·ot·o)
PARTS_TEMPLATES = {"af": 2, "affix": 2, "suffix": 2, "prefix": 2, "compound": 2, "com": 2, "confix": 2}
BORROWED_TEMPLATES = {"bor", "bor+", "der", "der+", "inh", "inh+", "calque", "cal", "univ"}
BORROWED = "<"


def made_of(entry):
    """The stems of the parts of an Esperanto word, "mar·virin" for
    marvirino, "kaf·far·ist" for kafofaristo; BORROWED if it was taken
    from another language whole; None if Wiktionary doesn't say"""
    parts = None
    borrowed = False
    for template in entry.get("etymology_templates") or []:
        name, args = template.get("name"), template.get("args") or {}
        numbered = [args[k] for k in sorted((k for k in args if k.isdigit()), key=int)]
        if name == "ety" and len(numbered) > 1:
            markers = [i for i, x in enumerate(numbered) if x.startswith(":af")]
            if markers:
                parts = parts or numbered[markers[0] + 1:]
            elif numbered[1].startswith((":bor", ":der", ":inh")):
                borrowed = True
        elif name in PARTS_TEMPLATES and args.get("1") == "eo":
            parts = parts or numbered[PARTS_TEMPLATES[name] - 1:]
        elif name in BORROWED_TEMPLATES and args.get("1") == "eo":
            borrowed = True
    if parts:
        stems = [stem for stem in (stem_of(part) for part in parts) if stem]
        if len(stems) > 1 and all(re.fullmatch(r"[a-zĉĝĥĵŝŭ]+", stem) for stem in stems):
            return "·".join(stems)
        return None
    return BORROWED if borrowed else None


def stem_of(part):
    """"maro<t:sea>" mar, "-isto" ist, "ne-" ne, "-e" nothing (an ending)"""
    part = re.sub(r"<[^>]*>", "", part).strip().lower()
    if ":" in part:
        return ""
    if part.endswith("-"):
        return part.strip("-")
    return re.sub(r"(oj|aj|on|an|o|a|e|i|j)$", "", part.strip("-"))


def numbered_sense(translation):
    """The sense of a translation by its number, from 0: "1a", "1, 2" are
    the first; none, after the others"""
    number = re.match(r"\s*(\d+)", str(translation.get("sense_index") or ""))
    return int(number.group(1)) - 1 if number else 10_000


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
# In English, how each Esperanto word is made (made_of)
made = {}
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
        # French and German: their words have a gender, English ones don't
        masculine = LANGUAGE in ("fr", "de") and "masculine" in (entry.get("tags") or []) and "feminine" not in (entry.get("tags") or [])
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
    elif code == "eo":
        esperanto = esperanto_spelling(clean(entry.get("word", ""))) or ""
        if LANGUAGE == "en" and esperanto and esperanto not in made:
            how = made_of(entry)
            if how:
                made[esperanto] = how
        for sense in entry.get("senses") or []:
            # In English and German, a form of a word, "plural of", "past of", is tagged
            if TAGGED_FORMS and {"form-of", "alt-of"} & set(sense.get("tags") or []):
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
        f.write(f"{french}\t{esperanto}\n")
with open(os.path.join(OUT, f"eo-{LANGUAGE}.tsv"), "w", encoding="utf-8") as f:
    f.write(header)
    for esperanto, gloss in sorted(descriptions.items()):
        f.write(f"{esperanto}\t{gloss}\n")
# The translations whose sense "Vortoj de la bona lingvo" advises against,
# when it does in some senses only ("glitch → cimo", pri komputiloj),
# reviewed one by one (data/vikivortaro-advised.tsv): marked so in the app
ADVISED = os.path.join(os.path.dirname(__file__), "..", "data", "vikivortaro-advised.tsv")
advised = []
if os.path.exists(ADVISED):
    with open(ADVISED, encoding="utf-8") as f:
        for row in f:
            fields = row.rstrip("\n").split("\t")
            if not row.startswith("#") and len(fields) >= 3 and fields[0] == LANGUAGE and (fields[1], fields[2]) in translations:
                advised.append((fields[1], fields[2]))
if advised:
    with open(os.path.join(OUT, f"{LANGUAGE}-eo-advised.tsv"), "w", encoding="utf-8") as f:
        f.write("# Translations whose sense Vortoj de la bona lingvo advises against, from data/vikivortaro-advised.tsv.\n")
        for foreign, esperanto in sorted(advised):
            f.write(f"{foreign}\t{esperanto}\n")
if made:
    shown = {esperanto for _, esperanto in translations} | set(descriptions)
    with open(os.path.join(OUT, "eo-parts.tsv"), "w", encoding="utf-8") as f:
        f.write(header)
        f.write(f"# An Esperanto word, a tab, the stems of its parts as Wiktionary gives them, or \"{BORROWED}\" if borrowed whole.\n")
        for esperanto in sorted(made):
            if esperanto in shown:
                f.write(f"{esperanto}\t{made[esperanto]}\n")
if rejected:
    with open(os.path.join(OUT, f"{LANGUAGE}-eo-not.tsv"), "w", encoding="utf-8") as f:
        f.write(f"# Pairs left out after a review, from data/vikivortaro-{LANGUAGE}-rejected.tsv.\n")
        for foreign, esperanto in sorted(rejected):
            f.write(f"{foreign}\t{esperanto}\n")
print(f"{len(translations)} translations, {len(descriptions)} descriptions, {len(rejected)} pairs and {len(rejected_words)} words left out")
