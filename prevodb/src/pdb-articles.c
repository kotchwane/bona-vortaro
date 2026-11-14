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

#include "pdb-articles.h"

static void
append_varint (GByteArray *out,
               guint64 value)
{
  do
    {
      guint8 b = value & 0x7f;
      value >>= 7;
      if (value)
        b |= 0x80;
      g_byte_array_append (out, &b, 1);
    }
  while (value);
}

static gboolean
read_varint (const guint8 *data,
             int length,
             int *pos,
             guint64 *value)
{
  int shift;

  *value = 0;

  for (shift = 0; shift < 64; shift += 7)
    {
      guint8 b;

      if (*pos >= length)
        return FALSE;

      b = data[(*pos)++];
      *value |= (guint64) (b & 0x7f) << shift;

      if ((b & 0x80) == 0)
        return TRUE;
    }

  return FALSE;
}

static guint64
zigzag (gint64 value)
{
  return value >= 0 ? (guint64) value * 2 : (guint64) (-value) * 2 - 1;
}

static gint64
unzigzag (guint64 value)
{
  return (value & 1) ? -(gint64) ((value + 1) / 2) : (gint64) (value / 2);
}

static gboolean
read_16 (const guint8 *data,
         int length,
         int *pos,
         guint16 *value)
{
  if (*pos + 2 > length)
    return FALSE;
  *value = data[*pos] | (data[*pos + 1] << 8);
  *pos += 2;
  return TRUE;
}

static void
append_16 (GByteArray *out,
           guint16 value)
{
  guint8 b[2] = { value & 0xff, value >> 8 };
  g_byte_array_append (out, b, 2);
}

/* A string of the old format, from *pos, onto out in the compact form */
static gboolean
encode_string (const guint8 *old,
               int end,
               int *pos,
               GByteArray *out)
{
  guint16 text_length;
  GByteArray *spans = g_byte_array_new ();
  int span_count = 0;
  int previous_start = 0;
  gboolean ok = FALSE;

  if (!read_16 (old, end, pos, &text_length) || *pos + text_length > end)
    goto done;

  append_varint (out, text_length);
  g_byte_array_append (out, old + *pos, text_length);
  *pos += text_length;

  while (TRUE)
    {
      guint16 span_length, start, data1, data2;
      guint8 type, flags;

      if (!read_16 (old, end, pos, &span_length))
        goto done;
      if (span_length == 0)
        break;
      if (!read_16 (old, end, pos, &start) ||
          !read_16 (old, end, pos, &data1) ||
          !read_16 (old, end, pos, &data2) ||
          *pos >= end)
        goto done;
      type = old[(*pos)++];
      /* The two high bits say which data follow */
      if (type >= 0x40)
        goto done;

      flags = type | (data1 ? 0x40 : 0) | (data2 ? 0x80 : 0);
      g_byte_array_append (spans, &flags, 1);
      append_varint (spans, zigzag ((gint64) start - previous_start));
      append_varint (spans, span_length);
      if (data1)
        append_varint (spans, data1);
      if (data2)
        append_varint (spans, data2);
      previous_start = start;
      span_count++;
    }

  append_varint (out, span_count);
  g_byte_array_append (out, spans->data, spans->len);
  ok = TRUE;

 done:
  g_byte_array_free (spans, TRUE);
  return ok;
}

guint8 *
pdb_articles_encode (const guint8 *old,
                     int old_length,
                     int *length)
{
  GByteArray *out = g_byte_array_new ();
  GByteArray *sections = g_byte_array_new ();
  int section_count = 0;
  int pos = 4, end;
  guint32 article_length;

  if (old_length < 4)
    goto fail;

  article_length = old[0] | (old[1] << 8) | (old[2] << 16) | ((guint32) old[3] << 24);
  if (article_length != (guint32) old_length - 4)
    goto fail;
  end = old_length;

  if (!encode_string (old, end, &pos, out))
    goto fail;

  while (pos < end)
    {
      if (pos + 3 > end)
        goto fail;
      g_byte_array_append (sections, old + pos, 3);
      pos += 3;
      if (!encode_string (old, end, &pos, sections) ||
          !encode_string (old, end, &pos, sections))
        goto fail;
      section_count++;
    }

  append_varint (out, section_count);
  g_byte_array_append (out, sections->data, sections->len);
  g_byte_array_free (sections, TRUE);

  *length = out->len;
  return g_byte_array_free (out, FALSE);

 fail:
  g_byte_array_free (sections, TRUE);
  g_byte_array_free (out, TRUE);
  return NULL;
}

/* A compact string, from *pos, onto out in the old format */
static gboolean
decode_string (const guint8 *data,
               int length,
               int *pos,
               GByteArray *out)
{
  guint64 text_length, span_count, i;
  gint64 start = 0;

  if (!read_varint (data, length, pos, &text_length) ||
      text_length > G_MAXUINT16 || *pos + (int) text_length > length)
    return FALSE;

  append_16 (out, text_length);
  g_byte_array_append (out, data + *pos, text_length);
  *pos += text_length;

  if (!read_varint (data, length, pos, &span_count))
    return FALSE;

  for (i = 0; i < span_count; i++)
    {
      guint64 delta, span_length, data1 = 0, data2 = 0;
      guint8 flags;

      if (*pos >= length)
        return FALSE;
      flags = data[(*pos)++];

      if (!read_varint (data, length, pos, &delta) ||
          !read_varint (data, length, pos, &span_length) ||
          ((flags & 0x40) && !read_varint (data, length, pos, &data1)) ||
          ((flags & 0x80) && !read_varint (data, length, pos, &data2)))
        return FALSE;

      start += unzigzag (delta);
      if (start < 0 || start > G_MAXUINT16 || span_length == 0 ||
          span_length > G_MAXUINT16 || data1 > G_MAXUINT16 || data2 > G_MAXUINT16)
        return FALSE;

      append_16 (out, span_length);
      append_16 (out, start);
      append_16 (out, data1);
      append_16 (out, data2);
      g_byte_array_append (out, (guint8[]) { flags & 0x3f }, 1);
    }

  append_16 (out, 0);
  return TRUE;
}

guint8 *
pdb_articles_decode (const guint8 *data,
                     int length,
                     int *old_length)
{
  GByteArray *out = g_byte_array_new ();
  guint64 section_count, i;
  int pos = 0;
  guint32 body;

  /* The length, filled in at the end */
  g_byte_array_set_size (out, 4);

  if (!decode_string (data, length, &pos, out) ||
      !read_varint (data, length, &pos, &section_count))
    goto fail;

  for (i = 0; i < section_count; i++)
    {
      if (pos + 3 > length)
        goto fail;
      g_byte_array_append (out, data + pos, 3);
      pos += 3;
      if (!decode_string (data, length, &pos, out) ||
          !decode_string (data, length, &pos, out))
        goto fail;
    }

  if (pos != length)
    goto fail;

  body = out->len - 4;
  out->data[0] = body & 0xff;
  out->data[1] = (body >> 8) & 0xff;
  out->data[2] = (body >> 16) & 0xff;
  out->data[3] = body >> 24;

  *old_length = out->len;
  return g_byte_array_free (out, FALSE);

 fail:
  g_byte_array_free (out, TRUE);
  return NULL;
}
