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

#include "config.h"

#include <string.h>

#include "pdb-compact.h"

/* The trie, as pdb_trie_compress writes it: each node is its size (4
 * bytes, little-endian; the high bit set if words end on it), its letter
 * (UTF-8), its words if any, then its children. A word is its article (2
 * bytes: 0x8000 if another follows, 0x4000 if it has a display name),
 * its mark (1 byte), then its display name (its length, 1 byte, then its
 * UTF-8). */

#define HAS_WORDS 0x80000000u
#define HAS_NEXT 0x8000
#define HAS_DISPLAY 0x4000
#define ARTICLE 0x3fff

/* Letters whose capital the app's Java and glib agree on: Latin, Greek,
 * Cyrillic, Armenian. Any other change of case goes in the table. */
#define CASE_LIMIT 0x0530
/* The letters of a mask: the first 64 */
#define MASK_LETTERS 64

static guint32
read_u32 (const guint8 *p)
{
  return ((guint32) p[0] | ((guint32) p[1] << 8) |
          ((guint32) p[2] << 16) | ((guint32) p[3] << 24));
}

static void
write_u32 (guint8 *p, guint32 value)
{
  p[0] = value;
  p[1] = value >> 8;
  p[2] = value >> 16;
  p[3] = value >> 24;
}

static void
append_varint (GByteArray *buf, guint64 value)
{
  do
    {
      guint8 byte = value & 0x7f;
      value >>= 7;
      if (value)
        byte |= 0x80;
      g_byte_array_append (buf, &byte, 1);
    }
  while (value);
}

static gboolean
read_varint (const guint8 *data, int length, int *pos, guint64 *value)
{
  int shift = 0;

  *value = 0;

  while (*pos < length && shift < 64)
    {
      guint8 byte = data[(*pos)++];
      *value |= (guint64) (byte & 0x7f) << shift;
      if (!(byte & 0x80))
        return TRUE;
      shift += 7;
    }

  return FALSE;
}

static int
utf8_length (guint8 first)
{
  return (first < 0x80 ? 1 : first < 0xe0 ? 2 : first < 0xf0 ? 3 : 4);
}

/* The mask of the letters of 'display' that are the capitals of those
 * of 'key', if it differs from it only so */
static gboolean
case_mask (const char *key, int key_len,
           const char *display, int display_len,
           guint64 *mask)
{
  const char *k = key, *k_end = key + key_len;
  const char *d = display, *d_end = display + display_len;
  int i;

  *mask = 0;

  for (i = 0; k < k_end && d < d_end; i++)
    {
      gunichar kc = g_utf8_get_char (k);
      gunichar dc = g_utf8_get_char (d);

      if (kc != dc)
        {
          if (i >= MASK_LETTERS || kc >= CASE_LIMIT ||
              g_unichar_toupper (kc) != dc)
            return FALSE;
          *mask |= (guint64) 1 << i;
        }

      k = g_utf8_next_char (k);
      d = g_utf8_next_char (d);
    }

  return k == k_end && d == d_end;
}

typedef struct
{
  GByteArray *words;
  GByteArray *table;
  GHashTable *table_index;
  int n_table;
  int n_words;
  GString *path;
  GString *previous;
} Encoder;

static void
encode_node (Encoder *enc, const guint8 *node, gboolean root)
{
  guint32 size = read_u32 (node);
  gboolean has_words = (size & HAS_WORDS) != 0;
  const guint8 *end = node + (size & ~HAS_WORDS);
  const guint8 *p = node + 4;
  int letter_len = utf8_length (*p);
  gsize old_len = enc->path->len;

  if (!root)
    g_string_append_len (enc->path, (const char *) p, letter_len);
  p += letter_len;

  if (has_words)
    {
      GByteArray *words = enc->words;
      const guint8 *q = p;
      int shared = 0, count = 0;
      gboolean more;

      /* The bytes shared with the word before, whole letters */
      while (shared < (int) enc->path->len && shared < (int) enc->previous->len &&
             enc->path->str[shared] == enc->previous->str[shared])
        shared++;
      while (shared > 0 && (enc->path->str[shared] & 0xc0) == 0x80)
        shared--;

      append_varint (words, shared);
      append_varint (words, enc->path->len - shared);
      g_byte_array_append (words, (const guint8 *) enc->path->str + shared,
                           enc->path->len - shared);
      g_string_assign (enc->previous, enc->path->str);

      do
        {
          guint16 article = q[0] | (q[1] << 8);
          more = (article & HAS_NEXT) != 0;
          q += 3;
          if (article & HAS_DISPLAY)
            q += 1 + q[0];
          count++;
        }
      while (more);

      append_varint (words, count);

      do
        {
          guint16 article = p[0] | (p[1] << 8);
          guint8 mark = p[2];
          guint64 mask;

          more = (article & HAS_NEXT) != 0;
          append_varint (words, article & ARTICLE);
          g_byte_array_append (words, &mark, 1);
          p += 3;

          if (article & HAS_DISPLAY)
            {
              int len = p[0];
              const char *display = (const char *) p + 1;

              if (case_mask (enc->path->str, enc->path->len, display, len, &mask))
                {
                  append_varint (words, 1);
                  append_varint (words, mask);
                }
              else
                {
                  char *name = g_strndup (display, len);
                  gpointer index;

                  if (!g_hash_table_lookup_extended (enc->table_index, name, NULL, &index))
                    {
                      index = GINT_TO_POINTER (enc->n_table++);
                      g_hash_table_insert (enc->table_index, name, index);
                      append_varint (enc->table, len);
                      g_byte_array_append (enc->table, (const guint8 *) display, len);
                    }
                  else
                    g_free (name);

                  append_varint (words, 2 + GPOINTER_TO_INT (index));
                }

              p += 1 + len;
            }
          else
            append_varint (words, 0);
        }
      while (more);

      enc->n_words++;
    }

  while (p < end)
    {
      encode_node (enc, p, FALSE);
      p += read_u32 (p) & ~HAS_WORDS;
    }

  g_string_truncate (enc->path, old_len);
}

guint8 *
pdb_compact_encode (const guint8 *trie,
                    int trie_length,
                    int *length)
{
  Encoder enc;
  GByteArray *out = g_byte_array_new ();
  int root_letter_len = utf8_length (trie[4]);

  enc.words = g_byte_array_new ();
  enc.table = g_byte_array_new ();
  enc.table_index = g_hash_table_new_full (g_str_hash, g_str_equal, g_free, NULL);
  enc.n_table = 0;
  enc.n_words = 0;
  enc.path = g_string_new (NULL);
  enc.previous = g_string_new (NULL);

  encode_node (&enc, trie, TRUE);

  g_byte_array_append (out, (const guint8 *) PDB_COMPACT_MAGIC, 4);
  /* The size of the trie, for the app to make it at once */
  append_varint (out, trie_length);
  append_varint (out, root_letter_len);
  g_byte_array_append (out, trie + 4, root_letter_len);
  append_varint (out, enc.n_table);
  g_byte_array_append (out, enc.table->data, enc.table->len);
  append_varint (out, enc.n_words);
  g_byte_array_append (out, enc.words->data, enc.words->len);

  g_byte_array_free (enc.words, TRUE);
  g_byte_array_free (enc.table, TRUE);
  g_hash_table_destroy (enc.table_index);
  g_string_free (enc.path, TRUE);
  g_string_free (enc.previous, TRUE);

  *length = out->len;
  return g_byte_array_free (out, FALSE);
}

gboolean
pdb_compact_is_compact (const guint8 *data,
                        int length)
{
  return length >= 4 && !memcmp (data, PDB_COMPACT_MAGIC, 4);
}

/* The node being written: where it starts, whether words end on it */
typedef struct
{
  int start;
  gboolean has_words;
  int path_len;
} Open;

static void
close_node (GByteArray *out, Open *node)
{
  write_u32 (out->data + node->start,
             (out->len - node->start) | (node->has_words ? HAS_WORDS : 0));
}

static void
open_node (GByteArray *out, GArray *open, const guint8 *letter, int len, int path_len)
{
  Open node = { out->len, FALSE, path_len };
  guint8 size[4] = { 0 };

  g_byte_array_append (out, size, 4);
  g_byte_array_append (out, letter, len);
  g_array_append_val (open, node);
}

guint8 *
pdb_compact_decode (const guint8 *data,
                    int length,
                    int *trie_length)
{
  GByteArray *out;
  GArray *open;
  GPtrArray *table;
  GString *path;
  guint64 value, n_table, n_words, i;
  int pos = 4;
  gboolean ok = FALSE;

  if (!pdb_compact_is_compact (data, length))
    return NULL;

  out = g_byte_array_new ();
  open = g_array_new (FALSE, FALSE, sizeof (Open));
  table = g_ptr_array_new_with_free_func (g_free);
  path = g_string_new (NULL);

  /* The size of the trie, which only the app needs */
  if (!read_varint (data, length, &pos, &value))
    goto done;

  /* The root */
  if (!read_varint (data, length, &pos, &value) || pos + (int) value > length)
    goto done;
  open_node (out, open, data + pos, value, 0);
  pos += value;

  if (!read_varint (data, length, &pos, &n_table))
    goto done;
  for (i = 0; i < n_table; i++)
    {
      if (!read_varint (data, length, &pos, &value) || pos + (int) value > length)
        goto done;
      g_ptr_array_add (table, g_strndup ((const char *) data + pos, value));
      pos += value;
    }

  if (!read_varint (data, length, &pos, &n_words))
    goto done;

  for (i = 0; i < n_words; i++)
    {
      guint64 shared, rest, count, j;
      int at;

      if (!read_varint (data, length, &pos, &shared) ||
          !read_varint (data, length, &pos, &rest) ||
          shared > path->len || pos + (int) rest > length)
        goto done;

      g_string_truncate (path, shared);
      g_string_append_len (path, (const char *) data + pos, rest);
      pos += rest;

      /* Close the nodes beyond the bytes shared, open those of the rest */
      while (open->len > 1 &&
             g_array_index (open, Open, open->len - 1).path_len > (int) shared)
        {
          close_node (out, &g_array_index (open, Open, open->len - 1));
          g_array_set_size (open, open->len - 1);
        }
      for (at = shared; at < (int) path->len; )
        {
          int len = utf8_length (path->str[at]);
          open_node (out, open, (const guint8 *) path->str + at, len, at + len);
          at += len;
        }

      g_array_index (open, Open, open->len - 1).has_words = TRUE;

      if (!read_varint (data, length, &pos, &count) || count == 0)
        goto done;

      for (j = 0; j < count; j++)
        {
          guint64 article, code;
          guint8 bytes[3];
          const char *display = NULL;
          char *made = NULL;
          int display_len = 0;

          if (!read_varint (data, length, &pos, &article) || pos >= length)
            goto done;
          bytes[2] = data[pos++];
          if (!read_varint (data, length, &pos, &code))
            goto done;

          if (code == 1)
            {
              guint64 mask;
              GString *name = g_string_new (NULL);
              const char *p = path->str;
              int letter;

              if (!read_varint (data, length, &pos, &mask))
                {
                  g_string_free (name, TRUE);
                  goto done;
                }
              for (letter = 0; *p; letter++, p = g_utf8_next_char (p))
                {
                  gunichar c = g_utf8_get_char (p);
                  if (letter < MASK_LETTERS && (mask >> letter) & 1)
                    c = g_unichar_toupper (c);
                  g_string_append_unichar (name, c);
                }
              display_len = name->len;
              display = made = g_string_free (name, FALSE);
            }
          else if (code >= 2)
            {
              if (code - 2 >= table->len)
                goto done;
              display = g_ptr_array_index (table, code - 2);
              display_len = strlen (display);
            }

          article = (article & ARTICLE) |
            (j + 1 < count ? HAS_NEXT : 0) | (display ? HAS_DISPLAY : 0);
          bytes[0] = article;
          bytes[1] = article >> 8;
          g_byte_array_append (out, bytes, 3);

          if (display)
            {
              guint8 len = display_len;
              g_byte_array_append (out, &len, 1);
              g_byte_array_append (out, (const guint8 *) display, display_len);
            }
          g_free (made);
        }
    }

  while (open->len > 0)
    {
      close_node (out, &g_array_index (open, Open, open->len - 1));
      g_array_set_size (open, open->len - 1);
    }

  ok = pos == length;

 done:
  g_array_free (open, TRUE);
  g_ptr_array_free (table, TRUE);
  g_string_free (path, TRUE);

  if (!ok)
    {
      g_byte_array_free (out, TRUE);
      return NULL;
    }

  *trie_length = out->len;
  return g_byte_array_free (out, FALSE);
}
