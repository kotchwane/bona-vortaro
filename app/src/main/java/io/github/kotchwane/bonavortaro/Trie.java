/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012  Neil Roberts
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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/* A search index, searched in the compact form the app ships
 * (prevodb/src/pdb-compact.h): the words of the language in order, each
 * written as the bytes it shares with the one before and the rest, with
 * its articles.
 *
 * The searches still think in the tree of letters prevodb builds, the
 * trie: a node is a start of words, "cha", and its children the letters
 * that follow it, "chab…" to "chau…". In the list, a node is the run of
 * words that begin with it, as the words are in the order of the tree:
 * the words of "cha" follow each other, those of "chat" among them.
 *
 * The words are in the order of prevodb's letters (pdb-strcmp.c): by
 * code point, each Esperanto letter with a hat right after the same
 * without, "ĥ" between "h" and "i"; the keys are all in lower case. So
 * the run of a start of words is found by a binary search. To search
 * without reading the words from the first one, the key of every
 * BLOCK-th word is kept whole when the index is loaded: a word is read
 * from the key before it. */
public class Trie
{
  private static final byte[] MAGIC = { 'B', 'V', 'X', '1' };

  /* A whole key kept every BLOCK words */
  private static final int BLOCK = 16;

  /* Beyond that many words, the end of a run is found by a binary search
   * rather than read word by word */
  private static final int SCAN = 24;

  private final byte[] data;
  private final int wordCount;

  /* Where each display name of the table is in data: its length, then
   * its UTF-8 */
  private final int[] tableAt;

  /* Where the record of the first word of each block is in data, and its
   * whole key, in keys from keyAt[block] to keyAt[block + 1] */
  private final int[] blockAt;
  private final int[] keyAt;
  private final byte[] keys;

  /* The letters that begin the words, and the first word of each: in
   * Chinese and Japanese, thousands, which every search would otherwise
   * look for again */
  private final int[] firstLetterAt;
  private final int[] firstLetters;

  /* A start of words: the words from lo to hi (excluded) begin with the
   * first depth bytes of the word lo; letter is its last letter */
  private static final class Node
  {
    final int lo, hi, depth;
    final String letter;

    Node (int lo, int hi, int depth, String letter)
    {
      this.lo = lo;
      this.hi = hi;
      this.depth = depth;
      this.letter = letter;
    }

    /* Whether the words of 'other' begin with this node: in the tree,
     * whether it's this node or below it */
    boolean contains (Node other)
    {
      return lo <= other.lo && other.hi <= hi && depth <= other.depth;
    }
  }

  private static byte[] readAll (InputStream stream)
    throws IOException
  {
    ByteArrayOutputStream out = new ByteArrayOutputStream ();
    byte[] buf = new byte[65536];
    int got;
    while ((got = stream.read (buf)) != -1)
      out.write (buf, 0, got);
    return out.toByteArray ();
  }

  public Trie (InputStream dataStream)
    throws IOException
  {
    data = readAll (dataStream);

    if (data.length < 4)
      throw new IOException ("Not a compact index");
    for (int i = 0; i < 4; i++)
      if (data[i] != MAGIC[i])
        throw new IOException ("Not a compact index");

    int[] at = { 4 };
    /* The size of the trie the index was made from, and the root's
     * letter: not needed to search */
    varint (at);
    int rootLength = count (at);
    at[0] += rootLength;

    int tableSize = count (at);
    tableAt = new int[tableSize];
    for (int i = 0; i < tableSize; i++)
      {
        tableAt[i] = at[0];
        int length = count (at);
        at[0] += length;
        if (at[0] > data.length)
          throw new IOException ("Truncated compact index");
      }

    wordCount = count (at);
    int blocks = (wordCount + BLOCK - 1) / BLOCK;
    blockAt = new int[blocks];
    keyAt = new int[blocks + 1];
    ByteArrayOutputStream blockKeys = new ByteArrayOutputStream ();

    /* Read all the words once, to keep the key of each block, and to
     * check the index: the searches then trust it */
    byte[] path = new byte[256];
    int pathLength = 0;
    int[] letterAt = new int[64];
    int[] letters = new int[64];
    int letterCount = 0;
    for (int w = 0; w < wordCount; w++)
      {
        int record = at[0];
        int shared = count (at);
        int rest = count (at);
        /* matchPattern keeps the length of a key in 16 bits */
        if (shared > pathLength || at[0] + rest > data.length ||
            shared + rest >= 0x10000)
          throw new IOException ("Invalid word in compact index");
        if (shared + rest > path.length)
          path = java.util.Arrays.copyOf (path, Math.max (path.length * 2, shared + rest));

        /* The binary searches need the words in order: after the word
         * before, at the first letter they don't share */
        int letter = shared;
        while (letter > 0 && letter < pathLength && (path[letter] & 0xc0) == 0x80)
          letter--;
        int before = letter < pathLength ? orderValue (codePoint (path, letter)) : -1;

        System.arraycopy (data, at[0], path, shared, rest);
        at[0] += rest;
        pathLength = shared + rest;

        if (w > 0 &&
            (letter >= pathLength ||
             before >= orderValue (codePoint (path, letter))))
          throw new IOException ("The words of the compact index aren't in order");

        if (pathLength > 0 && (letterCount == 0 || shared < getUtf8Length (path[0])))
          {
            if (letterCount == letters.length)
              {
                letterAt = java.util.Arrays.copyOf (letterAt, letterCount * 2);
                letters = java.util.Arrays.copyOf (letters, letterCount * 2);
              }
            letterAt[letterCount] = w;
            letters[letterCount++] = codePoint (path, 0);
          }

        if (w % BLOCK == 0)
          {
            blockAt[w / BLOCK] = record;
            keyAt[w / BLOCK] = blockKeys.size ();
            blockKeys.write (path, 0, pathLength);
          }

        int articles = count (at);
        if (articles == 0)
          throw new IOException ("A word without articles in compact index");
        for (int j = 0; j < articles; j++)
          {
            varint (at);
            if (at[0] >= data.length)
              throw new IOException ("Truncated compact index");
            at[0]++;
            long code = varint (at);
            if (code == 1)
              varint (at);
            else if (code >= 2 && code - 2 >= tableSize)
              throw new IOException ("Invalid display name in compact index");
          }
      }
    keyAt[blocks] = blockKeys.size ();
    keys = blockKeys.toByteArray ();
    firstLetterAt = java.util.Arrays.copyOf (letterAt, letterCount);
    firstLetters = java.util.Arrays.copyOf (letters, letterCount);

    if (at[0] != data.length)
      throw new IOException ("Trailing data in compact index");
  }

  private long varint (int[] at)
    throws IOException
  {
    long value = 0;
    for (int shift = 0; shift < 64; shift += 7)
      {
        if (at[0] >= data.length)
          throw new IOException ("Truncated compact index");
        int b = data[at[0]++] & 0xff;
        value |= (long) (b & 0x7f) << shift;
        if ((b & 0x80) == 0)
          return value;
      }
    throw new IOException ("Invalid number in compact index");
  }

  private int count (int[] at)
    throws IOException
  {
    long value = varint (at);
    if (value < 0 || value > data.length)
      throw new IOException ("Invalid length in compact index");
    return (int) value;
  }

  /* Gets the number of bytes needed for a UTF-8 sequence which begins
   * with the given byte */
  private static int getUtf8Length (byte firstByte)
  {
    if (firstByte >= 0)
      return 1;
    if ((firstByte & 0xe0) == 0xc0)
      return 2;
    if ((firstByte & 0xf0) == 0xe0)
      return 3;
    if ((firstByte & 0xf8) == 0xf0)
      return 4;
    if ((firstByte & 0xfc) == 0xf8)
      return 5;

    return 6;
  }

  /* The letter starting at 'at' */
  private static int codePoint (byte[] bytes, int at)
  {
    int b = bytes[at] & 0xff;
    if (b < 0x80)
      return b;
    int length = getUtf8Length (bytes[at]);
    int value = b & (0x7f >> length);
    for (int i = 1; i < length; i++)
      value = (value << 6) | (bytes[at + i] & 0x3f);
    return value;
  }

  /* The place of a letter in the order of the index, as pdb_strcmp_ch:
   * ĉ ĝ ĥ ĵ ŝ ŭ right after c g h j s u. The keys are in lower case, so
   * prevodb's lower case changes nothing */
  private static int orderValue (int letter)
  {
    switch (letter)
      {
      case 0x109: return 'c' * 2 + 1;
      case 0x11d: return 'g' * 2 + 1;
      case 0x125: return 'h' * 2 + 1;
      case 0x135: return 'j' * 2 + 1;
      case 0x15d: return 's' * 2 + 1;
      case 0x16d: return 'u' * 2 + 1;
      default: return letter * 2;
      }
  }

  /* Compares the key with the start of words: 0 if the key begins with
   * it, else the order of the two */
  private static int compareStart (byte[] key, int keyOffset, int keyLength,
                                   byte[] start, int startLength)
  {
    int letter = 0;
    for (int i = 0; i < startLength; i++)
      {
        if (i >= keyLength)
          return -1;
        byte k = key[keyOffset + i];
        byte s = start[i];
        if ((s & 0xc0) != 0x80)
          letter = i;
        if (k != s)
          {
            int kv = orderValue (codePoint (key, keyOffset + letter));
            int sv = orderValue (codePoint (start, letter));
            return kv < sv ? -1 : 1;
          }
      }
    return 0;
  }

  /* Reads the words one after the other, from any of them */
  private final class Cursor
  {
    /* The word read, -1 before the first */
    int index = -1;
    /* Its key, and the number of bytes it shares with the word before */
    byte[] path = new byte[256];
    int length;
    int shared;
    /* Its number of articles, where they start, and where the next word
     * starts */
    int articles;
    int articlesAt;
    int nextAt;

    private int readCount (int[] at)
    {
      int value = 0;
      for (int shift = 0; ; shift += 7)
        {
          int b = data[at[0]++] & 0xff;
          value |= (b & 0x7f) << shift;
          if ((b & 0x80) == 0)
            return value;
        }
    }

    private long readLong (int[] at)
    {
      long value = 0;
      for (int shift = 0; ; shift += 7)
        {
          int b = data[at[0]++] & 0xff;
          value |= (long) (b & 0x7f) << shift;
          if ((b & 0x80) == 0)
            return value;
        }
    }

    /* Reads the word at 'record', the key before it in path */
    private void read (int record)
    {
      int[] at = { record };
      shared = readCount (at);
      int rest = readCount (at);
      if (shared + rest > path.length)
        path = java.util.Arrays.copyOf (path, Math.max (path.length * 2, shared + rest));
      System.arraycopy (data, at[0], path, shared, rest);
      at[0] += rest;
      length = shared + rest;
      articles = readCount (at);
      articlesAt = at[0];
      for (int j = 0; j < articles; j++)
        {
          readCount (at);
          at[0]++;
          int code = readCount (at);
          if (code == 1)
            readLong (at);
        }
      nextAt = at[0];
      index++;
    }

    void next ()
    {
      read (nextAt);
    }

    /* Goes to the word i */
    void at (int i)
    {
      if (index < 0 || i < index || i - index > BLOCK)
        {
          int block = i / BLOCK;
          int keyLength = keyAt[block + 1] - keyAt[block];
          if (keyLength > path.length)
            path = new byte[Math.max (path.length * 2, keyLength)];
          /* The bytes the first word shares with the one before are
           * already the start of its key */
          System.arraycopy (keys, keyAt[block], path, 0, keyLength);
          index = block * BLOCK - 1;
          read (blockAt[block]);
        }
      while (index < i)
        next ();
    }

    /* Whether the word is past the words that begin with 'start'
     * (exclusive), or at or past their first one */
    private boolean past (byte[] key, int offset, int keyLength,
                          byte[] start, int startLength, boolean exclusive)
    {
      int order = compareStart (key, offset, keyLength, start, startLength);
      return exclusive ? order > 0 : order >= 0;
    }

    /* The first word from lo to hi (or hi) that is past the words that
     * begin with 'start', or at or past their first one */
    int first (int lo, int hi, byte[] start, int startLength, boolean exclusive)
    {
      if (lo >= hi)
        return hi;

      /* The first block, among those that start after lo and before hi,
       * whose first word is past */
      int firstBlock = lo / BLOCK + 1;
      int lastBlock = (hi - 1) / BLOCK;
      int a = firstBlock, z = lastBlock + 1;
      while (a < z)
        {
          int m = (a + z) >>> 1;
          if (past (keys, keyAt[m], keyAt[m + 1] - keyAt[m],
                    start, startLength, exclusive))
            z = m;
          else
            a = m + 1;
        }

      /* The word is after the start of the block before, and at most
       * the first of that block */
      int from = a == firstBlock ? lo : (a - 1) * BLOCK;
      int to = a <= lastBlock ? a * BLOCK : hi;
      for (int i = from; i < to; i++)
        {
          at (i);
          if (past (path, 0, length, start, startLength, exclusive))
            return i;
        }
      return to;
    }

    /* The word after the run of those that begin as the word 'i' does in
     * its first 'depth' bytes, before hi */
    int endOfRun (int i, int hi, int depth)
    {
      at (i);
      /* Most runs are short: read on while the words share the start,
       * only the bytes they share and the length of the rest. The key
       * of i stays in path: the first word past the run shares less
       * than depth bytes with the word before, all of them in path */
      int[] at = { nextAt };
      for (int j = i + 1; j < hi && j <= i + SCAN; j++)
        {
          int record = at[0];
          int s = readCount (at);
          if (s < depth)
            {
              index = j - 1;
              read (record);
              return j;
            }
          int rest = readCount (at);
          at[0] += rest;
          int count = readCount (at);
          for (int k = 0; k < count; k++)
            {
              readCount (at);
              at[0]++;
              if (readCount (at) == 1)
                readLong (at);
            }
        }
      if (i + SCAN >= hi - 1)
        return hi;
      byte[] start = java.util.Arrays.copyOf (path, depth);
      return first (i, hi, start, depth, true);
    }

    /* The letters that follow a node, each the node of the words that
     * begin with it, in the order of the index */
    List<Node> children (Node node)
    {
      List<Node> children = new ArrayList<Node> ();
      /* The root's, found when loading */
      if (node.depth == 0)
        {
          for (int i = 0; i < firstLetterAt.length; i++)
            {
              String letter = new String (Character.toChars (firstLetters[i]));
              children.add (new Node (firstLetterAt[i],
                                      i + 1 < firstLetterAt.length ? firstLetterAt[i + 1] : wordCount,
                                      letter.getBytes (StandardCharsets.UTF_8).length,
                                      letter));
            }
          return children;
        }
      int i = node.lo;
      at (i);
      if (length == node.depth)
        i++;
      while (i < node.hi)
        {
          at (i);
          int letterLength = getUtf8Length (path[node.depth]);
          int depth = node.depth + letterLength;
          String letter = new String (path, node.depth, letterLength, StandardCharsets.UTF_8);
          int end = endOfRun (i, node.hi, depth);
          children.add (new Node (i, end, depth, letter));
          i = end;
        }
      return children;
    }

    /* The key of the word read, with the letters of the mask as capitals */
    private String withCapitals (long mask)
    {
      String key = new String (path, 0, length, StandardCharsets.UTF_8);
      StringBuilder name = new StringBuilder (key.length ());
      int letter = 0;
      for (int at = 0; at < key.length (); letter++)
        {
          int c = key.codePointAt (at);
          at += Character.charCount (c);
          if (letter < 64 && ((mask >>> letter) & 1) != 0)
            c = Character.toUpperCase (c);
          name.appendCodePoint (c);
        }
      return name.toString ();
    }

    /* Adds the articles of the word read, shown as 'word' when they have
     * no display name of their own */
    int addArticles (String word, SearchResult[] results, int numResults)
    {
      int[] at = { articlesAt };
      for (int j = 0; j < articles && numResults < results.length; j++)
        {
          int article = readCount (at);
          int mark = data[at[0]++] & 0xff;
          int code = readCount (at);

          String shown = word;
          if (code == 1)
            shown = withCapitals (readLong (at));
          else if (code >= 2)
            {
              int[] name = { tableAt[code - 2] };
              int length = readCount (name);
              shown = new String (data, name[0], length, StandardCharsets.UTF_8);
            }

          results[numResults++] = SearchResult.fromIndex (shown, article, mark);
        }
      return numResults;
    }

    String key ()
    {
      return new String (path, 0, length, StandardCharsets.UTF_8);
    }
  }

  private Node root ()
  {
    return new Node (0, wordCount, 0, "");
  }

  /* Searches the trie for words that begin with 'prefix'. The results
   * array is filled with the results. If more results are available
   * than the length of the results array then they are ignored. If
   * less are available then the remainder of the array is untouched.
   * The method returns the number of results found */
  public int search (String prefix,
                     SearchResult[] results)
  {
    byte[] start = prefix.getBytes (StandardCharsets.UTF_8);
    Cursor cursor = new Cursor ();

    /* The words that begin with it follow each other, in the order of
     * the tree */
    int lo = cursor.first (0, wordCount, start, start.length, false);
    int hi = cursor.first (lo, wordCount, start, start.length, true);

    int numResults = 0;
    for (int i = lo; i < hi && numResults < results.length; i++)
      {
        cursor.at (i);
        numResults = cursor.addArticles (cursor.key (), results, numResults);
      }
    return numResults;
  }

  /* The letters typed as two: the ligatures, the German umlauts and ß */
  private static final java.util.Map<String, String> LETTER_PAIRS = new java.util.HashMap<String, String> ();
  private static final String UMLAUTS = "\u00fc\u00e4\u00f6";
  static
  {
    LETTER_PAIRS.put ("\u0153", "oe");
    LETTER_PAIRS.put ("\u00e6", "ae");
    LETTER_PAIRS.put ("\u00fc", "ue");
    LETTER_PAIRS.put ("\u00e4", "ae");
    LETTER_PAIRS.put ("\u00f6", "oe");
    LETTER_PAIRS.put ("\u00df", "ss");
  }

  /* A node reached twice, or inside another reached ("mue" reaches "mü"
   * as "ue", and "müe" as "u" then "e"): its words once, under the
   * outer one */
  private static void keepOutermost (List<Node> nodes, List<String> paths)
  {
    for (int i = nodes.size () - 1; i >= 0; i--)
      {
        Node node = nodes.get (i);
        for (int j = 0; j < nodes.size (); j++)
          {
            Node outer = nodes.get (j);
            boolean same = outer.lo == node.lo && outer.depth == node.depth;
            if (j != i && outer.contains (node) && (!same || j < i))
              {
                nodes.remove (i);
                paths.remove (i);
                break;
              }
          }
      }
  }

  /* The letter without its accents and in lower case, eg. "e" for "É" and
   * "c" for "ĉ", to compare letters ignoring their accents */
  private static String foldLetter (String letter)
  {
    String decomposed = Normalizer.normalize (letter, Normalizer.Form.NFD);
    StringBuilder buf = new StringBuilder ();

    for (int i = 0; i < decomposed.length (); i++)
      {
        char ch = decomposed.charAt (i);

        if (Character.getType (ch) != Character.NON_SPACING_MARK)
          buf.append (ch);
      }

    /* The curly apostrophe of some keyboards, and of a few entries, is
     * the straight one: "aujourd’hui" is "aujourd'hui" */
    /* The non-breaking spaces of some entries, "brr !", are spaces; the
     * ligatures, in the order, their two letters */
    return buf.toString ().toLowerCase (Locale.ROOT).replace ('\u2019', '\'')
      .replace ('\u00a0', ' ').replace ('\u202f', ' ')
      .replace ("\u0153", "oe").replace ("\u00e6", "ae");
  }

  /* A katakana letter as its hiragana, "イ" as "い"; any other letter as
   * it is */
  static String hiragana (String letter)
  {
    if (letter.length () != 1)
      return letter;
    char ch = letter.charAt (0);
    return ch >= '\u30a1' && ch <= '\u30f6' ? String.valueOf ((char) (ch - 0x60)) : letter;
  }

  /* Whether two kana are the same in hiragana and katakana: "イヌ" typed
   * finds "いぬ", the reading of 犬, and "いぬ" finds "イヌ", as the
   * names of animals and plants are written */
  static boolean sameKana (String a, String b)
  {
    String plainA = hiragana (a);
    return !plainA.equals (a) ^ !hiragana (b).equals (b) && plainA.equals (hiragana (b));
  }

  /* Finds the nodes whose path from the root matches the rest of the
   * prefix, from prefixOffset, ignoring the accents. Only the letters
   * typed without an accent match the accented ones: "e" matches "é",
   * but "é" only matches "é", and "ĉ" (or "cx") doesn't match "c". Each
   * node is added to the nodes with its path, the string with the
   * accents of the index. */
  private void findIgnoringAccents (Cursor cursor,
                                    Node node,
                                    String prefix,
                                    int prefixOffset,
                                    StringBuilder path,
                                    List<Node> nodes,
                                    List<String> paths)
  {
    if (prefixOffset >= prefix.length ())
      {
        nodes.add (node);
        paths.add (path.toString ());
        return;
      }

    int letterEnd = prefix.offsetByCodePoints (prefixOffset, 1);
    String typed =
      prefix.substring (prefixOffset, letterEnd).toLowerCase (Locale.ROOT);
    String letter = foldLetter (typed);
    boolean plain = letter.equals (typed);

    for (Node child : cursor.children (node))
      {
        String childLetter = child.letter;

        if ((plain ?
             foldLetter (childLetter).equals (letter) :
             childLetter.toLowerCase (Locale.ROOT).equals (typed)) ||
            sameKana (childLetter, typed))
          {
            int oldLength = path.length ();
            path.append (childLetter);
            findIgnoringAccents (cursor, child, prefix, letterEnd, path, nodes, paths);
            path.setLength (oldLength);
          }
        /* "oe" typed also finds "œ", and "ae" "æ": "coeur" finds "cœur";
         * an "o" at the end of what is typed, already "œ". The German
         * letters too: "ue" finds "ü" ("Mueller" Müller), "ae" "ä", "oe"
         * "ö", and "ss" "ß" ("Strasse" Straße), with an "s" at the end
         * already "ß"; an "u" alone finds "ü" above */
        if (plain)
          {
            String ligature = childLetter.toLowerCase (Locale.ROOT);
            String pair = LETTER_PAIRS.get (ligature);
            if (pair != null && letter.equals (pair.substring (0, 1)))
              {
                boolean umlaut = UMLAUTS.indexOf (ligature) >= 0;
                int pairEnd = letterEnd;
                if (letterEnd < prefix.length ()
                    && prefix.substring (letterEnd, prefix.offsetByCodePoints (letterEnd, 1))
                         .toLowerCase (Locale.ROOT).equals (pair.substring (1)))
                  pairEnd = prefix.offsetByCodePoints (letterEnd, 1);
                else if (letterEnd < prefix.length () || umlaut)
                  continue;

                int oldLength = path.length ();
                path.append (childLetter);
                findIgnoringAccents (cursor, child, prefix, pairEnd, path, nodes, paths);
                path.setLength (oldLength);
              }
          }
      }
  }

  /* Like search, but ignoring the accents left out: "eleve" also finds
   * "élève", and "cevalo" "ĉevalo", but "ĉevalo" doesn't find "cevalo".
   * The words are in alphabetical order, accents and case aside, as in a
   * printed dictionary: "com" finds "comédie" between "combustible" and
   * "comique", where the order of the index put it after "comme", beyond
   * the results kept. A word without accent comes before the same with
   * ("cote", "coté", "côte", "côté"), so the words that match the prefix
   * exactly come first. In Esperanto, ĉ ĝ ĥ ĵ ŝ ŭ are letters of their
   * own, after c g h j s u. */
  public int searchIgnoringAccents (String prefix,
                                    SearchResult[] results,
                                    boolean esperanto)
  {
    Cursor cursor = new Cursor ();
    List<Node> nodes = new ArrayList<Node> ();
    List<String> paths = new ArrayList<String> ();

    findIgnoringAccents (cursor, root (), prefix, 0, new StringBuilder (), nodes, paths);
    keepOutermost (nodes, paths);

    /* The nodes reached, in groups that are the same in the order, eg.
     * "élève" and "eleve" */
    TreeMap<String, Group> start = new TreeMap<String, Group> ();
    int typed = foldLetter (prefix).length ();
    for (int i = 0; i < nodes.size (); i++)
      {
        String path = paths.get (i);
        StringBuilder key = new StringBuilder ();
        for (int at = 0; at < path.length (); at = path.offsetByCodePoints (at, 1))
          key.append (orderOf (path.substring (at, path.offsetByCodePoints (at, 1)),
                               esperanto));
        /* "co" reaches "cœ", whose "e" is still to come in the order */
        if (!esperanto && key.length () > typed)
          groupIn (start, key.substring (0, typed)).add (nodes.get (i), path, key.substring (typed));
        else
          groupIn (start, key.toString ()).add (nodes.get (i), path, "");
      }

    ArrayDeque<Group> stack = new ArrayDeque<Group> ();
    pushInOrder (stack, start);

    int numResults = 0;

    /* Depth first, each group of letters in order */
    while (numResults < results.length && !stack.isEmpty ())
      {
        Group group = stack.pop ();
        group.sort ();

        for (int i = 0; i < group.nodes.size (); i++)
          if (group.pending.get (i).isEmpty ())
            numResults = addWordsExactFirst (cursor,
                                             group.nodes.get (i),
                                             group.paths.get (i),
                                             results,
                                             numResults);

        TreeMap<String, Group> children = new TreeMap<String, Group> ();
        for (int i = 0; i < group.nodes.size (); i++)
          {
            Node node = group.nodes.get (i);
            String pending = group.pending.get (i);

            /* A ligature's second letter, in the order: "œ" after "o"
             * among the "e", "chœur" between "choéphore" and "chômer" */
            if (!pending.isEmpty ())
              {
                groupIn (children, pending.substring (0, 1))
                  .add (node, group.paths.get (i), pending.substring (1));
                continue;
              }

            for (Node child : cursor.children (node))
              {
                String letter = child.letter;
                String key = orderOf (letter, esperanto);
                String rest = "";
                if (!esperanto && key.length () > 1)
                  {
                    rest = key.substring (1);
                    key = key.substring (0, 1);
                  }
                groupIn (children, key).add (child, group.paths.get (i) + letter, rest);
              }
          }
        pushInOrder (stack, children);
      }

    return numResults;
  }

  /* The same, outside Esperanto */
  public int searchIgnoringAccents (String prefix,
                                    SearchResult[] results)
  {
    return searchIgnoringAccents (prefix, results, false);
  }

  /* Nodes reached by paths that are the same in the order of the results,
   * accents aside */
  private static final class Group
  {
    final List<Node> nodes = new ArrayList<Node> ();
    final List<String> paths = new ArrayList<String> ();
    /* The letters of the order a node is still ahead of: the "e" of "œ" */
    final List<String> pending = new ArrayList<String> ();

    void add (Node node, String path, String rest)
    {
      nodes.add (node);
      paths.add (path);
      pending.add (rest);
    }

    /* Without accent first: "cote", "coté", "côte", "côté" */
    void sort ()
    {
      for (int i = 1; i < paths.size (); i++)
        for (int j = i;
             j > 0 && paths.get (j - 1).compareTo (paths.get (j)) > 0;
             j--)
          {
            paths.add (j - 1, paths.remove (j));
            nodes.add (j - 1, nodes.remove (j));
            pending.add (j - 1, pending.remove (j));
          }
    }
  }

  private static Group groupIn (TreeMap<String, Group> groups, String key)
  {
    Group group = groups.get (key);

    if (group == null)
      {
        group = new Group ();
        groups.put (key, group);
      }

    return group;
  }

  /* The first group on top */
  private static void pushInOrder (ArrayDeque<Group> stack,
                                   TreeMap<String, Group> groups)
  {
    for (Group group : groups.descendingMap ().values ())
      stack.push (group);
  }

  /* The place of a letter in the order of the results: the letter without
   * its accents and case; in Esperanto, ĉ ĝ ĥ ĵ ŝ ŭ right after c g h j s u */
  private static String orderOf (String letter, boolean esperanto)
  {
    String folded = foldLetter (letter);

    if (esperanto && !folded.equals (letter.toLowerCase (Locale.ROOT)))
      return folded + '\uffff';

    return folded;
  }

  /* Like addWordsOf, but the word itself before the expressions filed
   * under it: "Amérique" before "Amérique du Nord", "cadre" before "dans
   * le cadre de"; then the word with a clarification, "livre" before
   * "livre (monnaie)", before the expressions */
  private int addWordsExactFirst (Cursor cursor,
                                  Node node,
                                  String path,
                                  SearchResult[] results,
                                  int numResults)
  {
    SearchResult[] words = new SearchResult[results.length - numResults];
    int count = addWordsOf (cursor, node, path, words, 0);
    String exact = path.toLowerCase (Locale.ROOT);

    for (int pass = 0; pass < 3; pass++)
      for (int i = 0; i < count; i++)
        {
          String word = words[i].word.toLowerCase (Locale.ROOT);
          int kind = word.equals (exact) ? 0 : word.startsWith (exact + " (") ? 1 : 2;
          if (kind == pass)
            results[numResults++] = words[i];
        }

    return numResults;
  }

  /* Adds the words that end on a node, whose path is 'path': the first
   * word of its run, if it is the node itself */
  private int addWordsOf (Cursor cursor,
                          Node node,
                          String path,
                          SearchResult[] results,
                          int numResults)
  {
    cursor.at (node.lo);
    if (cursor.length != node.depth)
      return numResults;
    return cursor.addArticles (path, results, numResults);
  }

  /* Follows the pattern from patternOffset, the node being reached by
   * 'path'. A node is visited only once for each place in the pattern,
   * so that two stars don't find a word twice. */
  private int matchPattern (Cursor cursor,
                            Node node,
                            String pattern,
                            int patternOffset,
                            StringBuilder path,
                            Set<Long> visited,
                            List<Node> children,
                            SearchResult[] results,
                            int numResults)
  {
    /* A node is its first word and its length; the length of a key
     * and the place in the pattern are below 2^16. The first word in
     * the high bits, for a hash that mixes it with the rest */
    long place = ((long) node.lo << 32) | ((long) node.depth << 16) | patternOffset;
    if (numResults >= results.length || !visited.add (place))
      return numResults;

    if (patternOffset >= pattern.length ())
      return addWordsOf (cursor, node, path.toString (), results, numResults);

    int nextOffset = pattern.offsetByCodePoints (patternOffset, 1);
    String typed =
      pattern.substring (patternOffset, nextOffset).toLowerCase (Locale.ROOT);
    boolean star = typed.equals ("*");

    /* The letters that follow, once for both ways of a star */
    if (children == null)
      children = cursor.children (node);

    /* A star can also stand for nothing */
    if (star)
      numResults = matchPattern (cursor, node, pattern, nextOffset, path,
                                 visited, children, results, numResults);

    String letter = foldLetter (typed);
    boolean plain = letter.equals (typed);

    for (Node child : children)
      {
        if (numResults >= results.length)
          break;

        String childLetter = child.letter;
        boolean matches;

        if (star || typed.equals ("?"))
          matches = true;
        else if (plain)
          matches = foldLetter (childLetter).equals (letter);
        else
          matches = childLetter.toLowerCase (Locale.ROOT).equals (typed);

        if (matches)
          {
            int oldLength = path.length ();
            path.append (childLetter);
            /* A star stays, to take more letters */
            numResults = matchPattern (cursor, child, pattern,
                                       star ? patternOffset : nextOffset,
                                       path, visited, null, results, numResults);
            path.setLength (oldLength);
          }
      }

    return numResults;
  }

  /* Searches the whole words that match a pattern, where "*" stands for
   * any letters, even none, and "?" for exactly one letter: "*ologio"
   * finds "biologio" and "geologio". The letters follow the same rules
   * as searchIgnoringAccents. The results are in the order of the
   * index. */
  public int searchPattern (String pattern,
                            SearchResult[] results)
  {
    /* Several stars in a row are the same as one */
    pattern = pattern.replaceAll ("\\*+", "*");

    /* The place in the pattern is kept in 16 bits */
    if (pattern.length () >= 0x10000)
      return 0;

    return matchPattern (new Cursor (), root (), pattern, 0, new StringBuilder (),
                         new HashSet<Long> (), null, results, 0);
  }

  /* Test program */
  public static void main (String[] args)
    throws IOException
  {
    if (args.length != 2)
      {
        System.err.println ("Usage: java Trie <index> <prefix>");
        System.exit (1);
      }

    FileInputStream inputStream = new FileInputStream (args[0]);
    Trie trie = new Trie (inputStream);

    SearchResult result[] = new SearchResult[100];

    int numResults = trie.search (args[1], result);

    for (int i = 0; i < numResults; i++)
      System.out.println (result[i].word + ": " +
                          result[i].article + "," +
                          result[i].mark);
  }
}
