/*
 * PReVo - A portable version of ReVo for Android
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

#ifndef PDB_COMPACT_H
#define PDB_COMPACT_H

#include <glib.h>

/* The compact form of a search index, as the app ships it: the same
 * words as the trie that pdb_trie_compress writes, from which the app
 * builds that trie again, byte for byte, when it loads the index.
 *
 * "BVX1", the size of the trie, the root's letter (its length then its
 * UTF-8), the display names that aren't only the word with capitals
 * (their number, then each as its length and its UTF-8, once however
 * many words have it), the number of words, then each word of the trie
 * in its order:
 *   - the number of bytes it shares with the word before, and the rest
 *     (its length then its bytes);
 *   - its number of articles, and for each: the article, the mark (one
 *     byte), and its display name: 0 none, 1 then a mask of the letters
 *     that are capitals ("Hund" for "hund": 1), 2 + n the nth display
 *     name.
 * The numbers are unsigned LEB128: seven bits a byte, the low ones first,
 * the high bit set if more follow. */

#define PDB_COMPACT_MAGIC "BVX1"

guint8 *
pdb_compact_encode (const guint8 *trie,
                    int trie_length,
                    int *length);

/* The trie again, NULL if the data isn't a compact index */
guint8 *
pdb_compact_decode (const guint8 *data,
                    int length,
                    int *trie_length);

gboolean
pdb_compact_is_compact (const guint8 *data,
                        int length);

#endif /* PDB_COMPACT_H */
