#!/usr/bin/env python3
# Writes how often the words of Reta Vortaro are used, for the order of the
# search results: of the Esperanto words a word of another language is
# translated by, the much more frequent one first ("deviner": diveni before
# duonkompreni).
#
# Usage, from the root of the repository:
#   python3 scripts/frequency.py <words file>
# with the word list of the Esperanto web corpus of the Leipzig Corpora
# Collection (Universität Leipzig, https://wortschatz.uni-leipzig.de/en/download),
# under CC BY 4.0: epo_mixed_2012_1M-words.txt, from epo_mixed_2012_1M.tar.gz.
# Its lines: number, word, count. Not epo_newscrawl_2017_1M: despite its
# name, all its sentences are from epo.wikitrans.net, English Wikipedia
# translated by a program ("plumbo" for "lead" a choir).
#
# The forms of a word are counted together, as the Tekstaro list of
# Wiktionary does: "hundojn" as "hundo", "kuris" as "kuri", per million
# words. Only
# the words that are headwords of Reta Vortaro (extern/revo-fonto) are
# written, to app/src/main/assets/frequency/leipzig.tsv; the file is
# committed, so that the app is built the same every time.
import collections
import html
import os
import re
import sys

REVO = "extern/revo-fonto/revo"
OUT = "app/src/main/assets/frequency/leipzig.tsv"

ENTITIES = {"jcirc": "ĵ", "gcirc": "ĝ", "ccirc": "ĉ", "scirc": "ŝ", "hcirc": "ĥ", "ubreve": "ŭ",
            "Jcirc": "Ĵ", "Gcirc": "Ĝ", "Ccirc": "Ĉ", "Scirc": "Ŝ", "Hcirc": "Ĥ", "Ubreve": "Ŭ"}
ENDINGS = (("ojn", "o"), ("ajn", "a"), ("oj", "o"), ("aj", "a"), ("on", "o"), ("an", "a"), ("en", "e"),
           ("as", "i"), ("is", "i"), ("os", "i"), ("us", "i"))


def base(word):
    """A form of a word as its headword: "hundojn" as "hundo", "kuris" as "kuri" """
    word = word.lower()
    for ending, replacement in ENDINGS:
        if word.endswith(ending) and len(word) > len(ending) + 1:
            return word[:-len(ending)] + replacement
    if word.endswith("u") and len(word) > 3:
        return word[:-1] + "i"
    return word


def text(xml):
    xml = re.sub(r"&(\w+);", lambda m: ENTITIES.get(m.group(1), m.group(0)), xml)
    return html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", xml))).strip()


def headwords():
    words = set()
    for name in sorted(os.listdir(REVO)):
        if not name.endswith(".xml"):
            continue
        with open(os.path.join(REVO, name), encoding="utf-8") as f:
            article = f.read()
        root = re.search(r"<rad[^>]*>(.*?)</rad>", article, re.S)
        root = text(root.group(1)) if root else ""
        for head in re.findall(r"<kap>(.*?)</kap>", article, re.S):
            head = re.sub(r"<fnt>.*?</fnt>|<ofc>.*?</ofc>", "", head, flags=re.S)
            head = re.sub(r"<tld[^>]*/>", root, head)
            for word in re.split(r"[,/]", text(head)):
                word = word.strip().lower()
                if word and " " not in word:
                    words.add(word)
    return words


def main():
    if len(sys.argv) != 2:
        sys.exit("Usage: scripts/frequency.py <words file>")
    counts = collections.Counter()
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        for line in f:
            fields = line.rstrip("\n").split("\t")
            if len(fields) >= 3 and fields[2].isdigit() and re.fullmatch(r"[a-zĉĝĥĵŝŭA-ZĈĜĤĴŜŬ]+", fields[1]):
                counts[base(fields[1])] += int(fields[2])
    size = sum(counts.values())
    total = {word: count * 1e6 / size for word, count in counts.items()}
    words = headwords()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("# How often the headwords of Reta Vortaro are used, per million words, from the Esperanto\n")
        out.write("# web corpus of the Leipzig Corpora Collection (Universität Leipzig, wortschatz.uni-leipzig.de),\n")
        out.write("# CC BY 4.0: epo_mixed_2012_1M. With scripts/frequency.py.\n")
        written = 0
        for word in sorted(words):
            value = total.get(word, 0)
            if value >= 0.01:
                out.write(f"{word}\t{value:.3g}\n")
                written += 1
    print(f"{written} of {len(words)} headwords written to {OUT}")


if __name__ == "__main__":
    main()
