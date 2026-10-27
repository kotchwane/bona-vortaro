#!/usr/bin/env python3
# Converts "Vortoj de la bona lingvo" (labonalingvo.org) into the file the
# app reads.
#
# Usage, from the root of the repository:
#   python3 scripts/bona-lingvo.py [path of the CSV exported by labonalingvo.org]
#
# Without an argument, it reads data/bona-lingvo.csv, the copy kept in the
# repository; with one, it first copies that export there, without its
# "modified_by" column (who edited a line: not ours to publish).
#
# The list is under the CC BY 4.0 licence (granted by its authors,
# 2026-10-27). Attribution, as they wrote it: "Renato Corsetti, Noam Mohr,
# kaj la laborgrupo de la Bona Lingvo, labonalingvo.org". One line of the CSV is one
# simplification: the word ("source_word"), one word to say instead
# ("target_word"), "malrekomendo" (the word is advised against: →) or
# "alternativo" (a simpler word is proposed: ~), their contexts, the
# sources ("fun", "1oa"…, "PIV", "ReVo": the last two not yet checked by
# the group), and the order of the simplifications of a word.
#
# Only what the working group has checked is kept (the user's decision,
# 2026-10-24): a line whose word or word to say comes from PIV or Reta
# Vortaro without the group's check ("PIV", "ReVo" in its annotations) is
# left out, and a word left without any line with it.
#
# A word that is the headword of several articles of Reta Vortaro ("vato",
# the wadding and the watt) has each of its lines put with one of them, by
# hand, in data/bona-lingvo-homonyms.tsv; a line left out of it is shown in
# no article, and said here.
#
# Writes app/src/main/assets/bonalingvo/bl.tsv, one line per
# simplification, the words in alphabetical order, the simplifications of a
# word in the group's order. Columns: the word, its context, "→" or "~",
# the word to say, its context, the file of the article it is about
# ("" for any, "-" for none), and the parts of the word to say as the group
# cut it ("urin·kanal·inflam·o"), "" when it isn't one word of letters or
# the parts don't spell it.
import csv
import glob
import os
import re
import sys
import unicodedata

ROOT = os.path.join(os.path.dirname(__file__), "..")
DATA = os.path.join(ROOT, "data", "bona-lingvo.csv")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "bonalingvo", "bl.tsv")
UNCHECKED = ("PIV", "ReVo")
HOMONYMS = os.path.join(ROOT, "data", "bona-lingvo-homonyms.tsv")
REVO = os.path.join(ROOT, "extern", "revo-fonto", "revo")
LETTERS = {"Ccirc": "Ĉ", "ccirc": "ĉ", "Gcirc": "Ĝ", "gcirc": "ĝ", "Hcirc": "Ĥ", "hcirc": "ĥ",
           "Jcirc": "Ĵ", "jcirc": "ĵ", "Scirc": "Ŝ", "scirc": "ŝ", "Ubreve": "Ŭ", "ubreve": "ŭ"}


def revo_articles():
    """The files of Reta Vortaro's articles where each headword is"""
    files = {}
    for path in glob.glob(os.path.join(REVO, "*.xml")):
        with open(path, encoding="utf-8") as f:
            text = re.sub(r"&(\w+);", lambda m: LETTERS.get(m.group(1), ""), f.read())
        root = re.search(r"<rad[^>]*>([^<]*)</rad>", text)
        if not root:
            continue
        root = root.group(1)
        name = os.path.basename(path)[:-4]
        for headword in re.findall(r"<kap>(.*?)</kap>", text, re.S):
            headword = re.sub(r'<tld\s+lit="(.)"\s*/>', lambda m: m.group(1) + root[1:], headword)
            headword = re.sub(r"<(fnt|ofc)>.*?</\1>", "", headword.replace("<tld/>", root), flags=re.S)
            for word in re.sub(r"<[^>]+>", "", headword).split(","):
                word = " ".join(word.split())
                if word:
                    files.setdefault(word, set()).add(name)
    return files


def nfc(text):
    return unicodedata.normalize("NFC", text)


def homonyms():
    """The article of each line of a word of several articles, by the word and the word to say"""
    with open(HOMONYMS, encoding="utf-8") as f:
        return {(nfc(fields[0]), nfc(fields[1])): fields[2]
                for fields in (line.rstrip("\n").split("\t") for line in f if not line.startswith("#") and line.strip())}


def copy_export(source):
    with open(source, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    columns = [c for c in rows[0] if c != "modified_by"]
    with open(DATA, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({c: row[c] for c in columns})


def parts(row, target):
    """The group's cut of the word to say, if it spells it, a single word"""
    # A space slipped in beside a cut ("mjel·inflam ·o") isn't meant
    cut = re.sub(r"\s*·\s*", "·", clean(row["target_word_divided"]))
    # Letters only: not "ikso-koordinato", "fil(in)igi", which the search
    # doesn't cut either
    if not target.isalpha() or "·" not in cut or cut.replace("·", "") != target:
        return ""
    return cut


def checked(annotation):
    return not any(code in annotation.split("+") for code in UNCHECKED)


def clean(text):
    # Some letters with a hat are written as a letter and the hat apart
    # ("grafikaĵo"): as one, as Reta Vortaro does
    return " ".join(unicodedata.normalize("NFC", text).replace("\t", " ").split())


if len(sys.argv) > 1:
    copy_export(sys.argv[1])

with open(DATA, encoding="utf-8") as f:
    rows = [r for r in csv.DictReader(f) if r["is_active"] == "1"]

by_word = {}
for row in rows:
    by_word.setdefault(clean(row["source_word"]), []).append(row)

# The app keeps one context per word: say so if a word ever has several
for word, word_rows in by_word.items():
    contexts = {clean(r["source_word_context"]) for r in word_rows}
    if len(contexts) > 1:
        print(f"warning: {word} has several contexts {sorted(contexts)}; only the first is kept", file=sys.stderr)

articles = revo_articles()
chosen = homonyms()
lines = []
left_out = 0
uncut = 0
for word in sorted(by_word, key=lambda w: (w.lower().strip("-"), w)):
    seen = set()
    # The group's order; for the same number, the older line first
    for row in sorted(by_word[word], key=lambda r: (int(r["display_order"] or 0), r["created_at"], r["id"])):
        target = clean(row["target_word"])
        target_context = clean(row["target_word_context"])
        if not (checked(row["source_annotation"]) and checked(row["target_annotation"])):
            left_out += 1
            continue
        if (target, target_context) in seen or not target:
            continue
        seen.add((target, target_context))
        sign = "→" if row["recommendation_type"] == "malrekomendo" else "~"
        article = ""
        if len(articles.get(word, ())) > 1:
            article = chosen.get((nfc(word), nfc(target)), "-")
            if article == "-":
                print(f"warning: {word} → {target}: which of {sorted(articles[word])}? Shown in none", file=sys.stderr)
            elif article == "*":
                article = ""
        cut = parts(row, target)
        if not cut and target.isalpha():
            uncut += 1
        lines.append("\t".join([word, clean(row["source_word_context"]), sign, target, target_context, article, cut]))

latest = max(r["updated_at"] for r in rows)[:10]
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# Vortoj de la bona lingvo, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).\n"
            "# Atribuo: Renato Corsetti, Noam Mohr, kaj la laborgrupo de la Bona Lingvo, labonalingvo.org.\n"
            "# Adapted with scripts/bona-lingvo.py from data/bona-lingvo.csv.\n"
            f"# Version: {latest} (its last change).\n"
            "# Only what the working group has checked. Columns: the word, its context, \"→\" (advised against) or\n"
            "# \"~\" (an alternative), the word to say, its context, the file of the article it is about\n"
            "# when the word heads several (data/bona-lingvo-homonyms.tsv; \"-\": none), the parts of the word to\n"
            "# say as the group cut it (\"\" if it isn't one word of letters or they don't spell it).\n")
    f.write("\n".join(lines) + "\n")
kept = {line.split("\t")[0] for line in lines}
print(f"{len(kept)} words, {len(lines)} simplifications ({left_out} not yet checked, left out), version {latest}")
print(f"{uncut} single words to say without the group's parts (none given, or not spelling the word)")
