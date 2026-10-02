/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2012  Neil Roberts
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

#ifndef PDB_SPAN_H
#define PDB_SPAN_H

#include <glib.h>

#include "pdb-list.h"

/* The values of these types are stored in the files, so new types
 * must be added at the end, before PDB_SPAN_NONE, which is never
 * stored. Readers ignore the types that they don't know. */
typedef enum
{
  PDB_SPAN_REFERENCE,
  PDB_SPAN_SUPERSCRIPT,
  PDB_SPAN_ITALIC,
  PDB_SPAN_NOTE,
  PDB_SPAN_BOLD,
  /* The kind of word, eg. "transitiva" for a verb */
  PDB_SPAN_GRAMMAR,
  /* The field of a word, eg. "matematiko" */
  PDB_SPAN_FIELD,
  /* An example. It is also in an italic span, for the applications
   * that don't know this type */
  PDB_SPAN_EXAMPLE,
  /* The symbol of the type of a link, eg. "↘". data1 is the type, as
   * its index in pdb_db_ref_types: 0 vid, 1 hom, 2 dif, 3 sin, 4 ant,
   * 5 super, 6 sub, 7 prt, 8 malprt, 9 ekz. Applications can show a
   * word instead of the symbol, which is ambiguous: "↘" is both "sub"
   * and "prt". */
  PDB_SPAN_LINK_TYPE,
  PDB_SPAN_NONE
} PdbSpanType;

typedef struct
{
  PdbList link;

  /* The span length and span start count the number of bytes. Once
   * stored to the file for an Android database, this will be
   * converted to a number of 16-bit units as if the string was stored
   * in UTF-16. */
  guint16 span_length;
  guint16 span_start;
  guint16 data1;
  guint16 data2;
  PdbSpanType type;
} PdbSpan;

void
pdb_span_free (PdbSpan *span);

void
pdb_span_free_list (PdbList *spans);

#endif /* PDB_SPAN_H */
