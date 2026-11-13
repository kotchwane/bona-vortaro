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

#ifndef PDB_ARTICLES_H
#define PDB_ARTICLES_H

#include <glib.h>

/* The compact form of the articles, as the app ships them: 64 to a file,
 * which the build then compresses with xz.
 *
 * A file: "BVA1", the number of articles, the length of each, then the
 * articles one after the other.
 *
 * An article: its title, its number of sections, then each section: its
 * language code (3 bytes, as before), its header and its content.
 *
 * A string: the length of its text, its UTF-8, its number of spans, then
 * each span: a byte, its type with 0x40 if data1 follows and 0x80 if
 * data2 follows; its start, as the difference with the start of the span
 * before (zigzag: 2n for n, 2n - 1 for -n); its length; then data1 and
 * data2 if they aren't 0.
 *
 * The numbers are unsigned LEB128, as in pdb-compact.h. The articles of
 * the old format (pdb_db_save_article) come back from it byte for byte:
 * prevodb checks it at every build. */

#define PDB_ARTICLES_MAGIC "BVA1"
#define PDB_ARTICLES_PER_FILE 64

/* An article of the old format, its 4 bytes of length first, in the
 * compact form; NULL if it isn't an article of the old format */
guint8 *
pdb_articles_encode (const guint8 *old,
                     int old_length,
                     int *length);

/* The article of the old format again, its length first; NULL if the
 * data isn't a compact article */
guint8 *
pdb_articles_decode (const guint8 *data,
                     int length,
                     int *old_length);

#endif /* PDB_ARTICLES_H */
