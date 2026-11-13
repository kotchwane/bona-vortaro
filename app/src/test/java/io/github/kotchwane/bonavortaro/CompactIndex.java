/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
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

package io.github.kotchwane.bonavortaro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/* The compact form of a search index, as the app ships it, made into
 * the trie that Trie searches, byte for byte as prevodb wrote it before
 * compacting it (prevodb/src/pdb-compact.h describes both): the words in
 * the trie's order, each written as the bytes it shares with the one
 * before and the rest, its display name once if several words have it,
 * or as the capitals of its letters ("Hund" for "hund"). */
final class CompactIndex
{
  private static final byte[] MAGIC = { 'B', 'V', 'X', '1' };

  private static final int HAS_WORDS = 0x80000000;
  private static final int HAS_NEXT = 0x8000;
  private static final int HAS_DISPLAY = 0x4000;
  private static final int ARTICLE = 0x3fff;
  private static final int MASK_LETTERS = 64;

  private CompactIndex ()
  {
  }

  static boolean isCompact (byte[] start)
  {
    if (start.length < 4)
      return false;
    for (int i = 0; i < 4; i++)
      if (start[i] != MAGIC[i])
        return false;
    return true;
  }

  /* Where the reading is in the compact data */
  private static final class Reader
  {
    final byte[] data;
    int pos;

    Reader (byte[] data, int pos)
    {
      this.data = data;
      this.pos = pos;
    }

    long varint () throws IOException
    {
      long value = 0;
      for (int shift = 0; shift < 64; shift += 7)
        {
          if (pos >= data.length)
            throw new IOException ("Truncated compact index");
          int b = data[pos++] & 0xff;
          value |= (long) (b & 0x7f) << shift;
          if ((b & 0x80) == 0)
            return value;
        }
      throw new IOException ("Invalid number in compact index");
    }

    int count () throws IOException
    {
      long value = varint ();
      if (value < 0 || value > data.length)
        throw new IOException ("Invalid length in compact index");
      return (int) value;
    }

    byte[] bytes (int length) throws IOException
    {
      if (pos + length > data.length)
        throw new IOException ("Truncated compact index");
      byte[] out = new byte[length];
      System.arraycopy (data, pos, out, 0, length);
      pos += length;
      return out;
    }
  }

  /* The trie being written */
  private static final class Out
  {
    byte[] buf;
    int len;

    Out (int capacity)
    {
      buf = new byte[capacity];
    }

    void ensure (int more)
    {
      if (len + more > buf.length)
        {
          byte[] bigger = new byte[Math.max (buf.length * 2, len + more)];
          System.arraycopy (buf, 0, bigger, 0, len);
          buf = bigger;
        }
    }

    void add (byte b)
    {
      ensure (1);
      buf[len++] = b;
    }

    void add (byte[] bytes, int offset, int length)
    {
      ensure (length);
      System.arraycopy (bytes, offset, buf, len, length);
      len += length;
    }

    void putInt (int at, int value)
    {
      buf[at] = (byte) value;
      buf[at + 1] = (byte) (value >> 8);
      buf[at + 2] = (byte) (value >> 16);
      buf[at + 3] = (byte) (value >> 24);
    }
  }

  private static int utf8Length (byte first)
  {
    int b = first & 0xff;
    return b < 0x80 ? 1 : b < 0xe0 ? 2 : b < 0xf0 ? 3 : 4;
  }

  /* The key with the letters of the mask as capitals */
  private static byte[] withCapitals (byte[] path, int pathLen, long mask)
  {
    /* Most often ASCII letters, "Hund": their byte, a bit cleared */
    byte[] ascii = new byte[pathLen];
    long left = mask;
    int letter = 0;
    for (int at = 0; at < pathLen; at++)
      {
        byte b = path[at];
        if (b < 0)
          {
            ascii = null;
            break;
          }
        if (letter < MASK_LETTERS && ((left >>> letter) & 1) != 0)
          {
            if (b < 'a' || b > 'z')
              {
                ascii = null;
                break;
              }
            b -= 'a' - 'A';
          }
        ascii[at] = b;
        letter++;
      }
    if (ascii != null)
      return ascii;

    String key = new String (path, 0, pathLen, StandardCharsets.UTF_8);
    StringBuilder name = new StringBuilder (key.length ());
    letter = 0;
    for (int at = 0; at < key.length (); letter++)
      {
        int c = key.codePointAt (at);
        at += Character.charCount (c);
        if (letter < MASK_LETTERS && ((mask >>> letter) & 1) != 0)
          c = Character.toUpperCase (c);
        name.appendCodePoint (c);
      }
    return name.toString ().getBytes (StandardCharsets.UTF_8);
  }

  static byte[] decode (byte[] data) throws IOException
  {
    if (!isCompact (data))
      throw new IOException ("Not a compact index");

    Reader in = new Reader (data, 4);
    /* The size of the trie, written first: made at once, not grown */
    long size = in.varint ();
    if (size < 0 || size > Integer.MAX_VALUE - 8)
      throw new IOException ("Invalid size in compact index");
    Out out = new Out ((int) size);

    /* The open nodes: where each starts, the length of its path, whether
     * words end on it */
    int[] starts = new int[64];
    int[] pathLens = new int[64];
    boolean[] withWords = new boolean[64];
    int open = 0;

    /* The root */
    int rootLen = in.count ();
    starts[open] = out.len;
    pathLens[open] = 0;
    withWords[open++] = false;
    out.ensure (4);
    out.len += 4;
    out.add (in.bytes (rootLen), 0, rootLen);

    int tableSize = in.count ();
    byte[][] table = new byte[tableSize][];
    for (int i = 0; i < tableSize; i++)
      table[i] = in.bytes (in.count ());

    int words = in.count ();
    byte[] path = new byte[256];
    int pathLen = 0;

    for (int w = 0; w < words; w++)
      {
        int shared = in.count ();
        int rest = in.count ();
        if (shared > pathLen || in.pos + rest > data.length)
          throw new IOException ("Invalid word in compact index");
        if (shared + rest > path.length)
          {
            byte[] longer = new byte[Math.max (path.length * 2, shared + rest)];
            System.arraycopy (path, 0, longer, 0, pathLen);
            path = longer;
          }
        System.arraycopy (data, in.pos, path, shared, rest);
        in.pos += rest;
        pathLen = shared + rest;

        /* Close the nodes beyond the bytes shared, open those of the
         * rest */
        while (open > 1 && pathLens[open - 1] > shared)
          {
            open--;
            out.putInt (starts[open], (out.len - starts[open]) | (withWords[open] ? HAS_WORDS : 0));
          }
        for (int at = shared; at < pathLen; )
          {
            int len = utf8Length (path[at]);
            if (open == starts.length)
              {
                starts = java.util.Arrays.copyOf (starts, open * 2);
                pathLens = java.util.Arrays.copyOf (pathLens, open * 2);
                withWords = java.util.Arrays.copyOf (withWords, open * 2);
              }
            starts[open] = out.len;
            pathLens[open] = at + len;
            withWords[open++] = false;
            out.ensure (4);
            out.len += 4;
            out.add (path, at, len);
            at += len;
          }
        withWords[open - 1] = true;

        int count = in.count ();
        if (count == 0)
          throw new IOException ("A word without articles in compact index");

        for (int j = 0; j < count; j++)
          {
            long article = in.varint ();
            if (in.pos >= data.length)
              throw new IOException ("Truncated compact index");
            byte mark = data[in.pos++];
            long code = in.varint ();

            byte[] display = null;
            if (code == 1)
              display = withCapitals (path, pathLen, in.varint ());
            else if (code >= 2)
              {
                if (code - 2 >= table.length)
                  throw new IOException ("Invalid display name in compact index");
                display = table[(int) (code - 2)];
              }

            int bits = ((int) article & ARTICLE) |
              (j + 1 < count ? HAS_NEXT : 0) | (display != null ? HAS_DISPLAY : 0);
            out.add ((byte) bits);
            out.add ((byte) (bits >> 8));
            out.add (mark);
            if (display != null)
              {
                out.add ((byte) display.length);
                out.add (display, 0, display.length);
              }
          }
      }

    while (open > 0)
      {
        open--;
        out.putInt (starts[open], (out.len - starts[open]) | (withWords[open] ? HAS_WORDS : 0));
      }

    if (in.pos != data.length)
      throw new IOException ("Trailing data in compact index");

    if (out.len != size)
      throw new IOException ("The compact index doesn't make a trie of its size");
    return out.buf;
  }
}
