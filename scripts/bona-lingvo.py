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
# Writes app/src/main/assets/bonalingvo/bl.tsv, one line per
# simplification, the words in alphabetical order, the simplifications of a
# word in the group's order. Columns: the word, its context, "→" or "~",
# the word to say, its context.
import csv
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..")
DATA = os.path.join(ROOT, "data", "bona-lingvo.csv")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "bonalingvo", "bl.tsv")
UNCHECKED = ("PIV", "ReVo")


def copy_export(source):
    with open(source, encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(f))
    columns = [c for c in rows[0] if c != "modified_by"]
    with open(DATA, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({c: row[c] for c in columns})


def checked(annotation):
    return not any(code in annotation.split("+") for code in UNCHECKED)


def clean(text):
    return " ".join(text.replace("\t", " ").split())


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

lines = []
left_out = 0
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
        lines.append("\t".join([word, clean(row["source_word_context"]), sign, target, target_context]))

latest = max(r["updated_at"] for r in rows)[:10]
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# Vortoj de la bona lingvo, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).\n"
            "# Atribuo: Renato Corsetti, Noam Mohr, kaj la laborgrupo de la Bona Lingvo, labonalingvo.org.\n"
            "# Adapted with scripts/bona-lingvo.py from data/bona-lingvo.csv.\n"
            f"# Version: {latest} (its last change).\n"
            "# Only what the working group has checked. Columns: the word, its context, \"→\" (advised against) or\n"
            "# \"~\" (an alternative), the word to say, its context.\n")
    f.write("\n".join(lines) + "\n")
kept = {line.split("\t")[0] for line in lines}
print(f"{len(kept)} words, {len(lines)} simplifications ({left_out} not yet checked, left out), version {latest}")
