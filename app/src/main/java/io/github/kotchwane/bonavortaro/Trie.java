/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
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

package io.github.kotchwane.bonavortaro;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.TreeMap;

class TrieStack
{
  private int[] data;
  private int size;

  public TrieStack ()
  {
    this.data = new int[64];
    this.size = 0;
  }

  public int getTopStart ()
  {
    return data[size - 3];
  }

  public int getTopEnd ()
  {
    return data[size - 2];
  }

  public int getTopStringLength ()
  {
    return data[size - 1];
  }

  public void pop ()
  {
    size -= 3;
  }

  public boolean isEmpty ()
  {
    return size <= 0;
  }

  public void push (int start,
                    int end,
                    int stringLength)
  {
    /* If there isn't enough space in the array then we'll double its
     * size. The size of the array is initially chosen to be quite
     * large so this should probably never happen */
    if (size + 3 >= data.length)
      {
        int[] newData = new int[data.length * 2];
        System.arraycopy (data, 0, newData, 0, data.length);
        data = newData;
      }

    data[size++] = start;
    data[size++] = end;
    data[size++] = stringLength;
  }
}

public class Trie
{
  private byte data[];

  private static void readAll (InputStream stream,
                               byte[] data,
                               int offset,
                               int length)
    throws IOException
  {
    while (length > 0)
      {
        int got = stream.read (data, offset, length);

        if (got == -1)
          throw new IOException ("Unexpected end of file");
        else
          {
            offset += got;
            length -= got;
          }
      }
  }

  private static final int extractInt (byte[] data,
                                       int offset)
  {
    return (((data[offset + 0] & 0xff) << 0) |
            ((data[offset + 1] & 0xff) << 8) |
            ((data[offset + 2] & 0xff) << 16) |
            ((data[offset + 3] & 0xff) << 24));
  }

  public Trie (InputStream dataStream)
    throws IOException
  {
    byte lengthBytes[] = new byte[4];
    int totalLength;

    /* Read 4 bytes to get the length of the file */
    readAll (dataStream, lengthBytes, 0, lengthBytes.length);
    totalLength = extractInt (lengthBytes, 0) & 0x7fffffff;

    /* Create a byte array big enough to hold the entire file and copy
     * the length we just read into the beginning */
    data = new byte[totalLength];
    System.arraycopy (lengthBytes, 0, data, 0, 4);

    /* Read the rest of the data */
    readAll (dataStream, data, 4, totalLength - 4);
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

  private static boolean compareArray (byte[] a,
                                       int aOffset,
                                       byte[] b,
                                       int bOffset,
                                       int length)
  {
    while (length-- > 0)
      if (a[aOffset++] != b[bOffset++])
        return false;

    return true;
  }

  private String getCharacter (int offset)
  {
    return new String (data, offset, getUtf8Length (data[offset]));
  }

  /* Searches the trie for words that begin with 'prefix'. The results
   * array is filled with the results. If more results are available
   * than the length of the results array then they are ignored. If
   * less are available then the remainder of the array is untouched.
   * The method returns the number of results found */
  public int search (String prefix,
                     SearchResult[] results)
  {
    /* Convert the string to unicode to make it easier to compare with
     * the unicode characters in the trie. 'getBytes' with no
     * parameters converts the string to the default charset. This
     * assumes the default charset is always UTF-8 which seems to be
     * the case on Android */
    byte[] prefixBytes = prefix.getBytes ();

    int trieStart = 0;
    int prefixOffset = 0;

    while (prefixOffset < prefixBytes.length)
      {
        int characterLen = getUtf8Length (prefixBytes[prefixOffset]);
        int childStart;

        /* Get the total length of this node */
        int offset = extractInt (data, trieStart);

        /* Skip the character for this node */
        childStart = trieStart + 4;
        childStart += getUtf8Length (data[childStart]);

        /* If the high bit in the offset is set then it is followed by
         * the matching articles which we want to skip */
        if (offset < 0)
          {
            offset &= 0x7fffffff;

            boolean hasNext;

            do
              {
                hasNext = (data[childStart + 1] & 0x80) != 0;
                boolean hasDisplayName = (data[childStart + 1] & 0x40) != 0;

                childStart += 3;

                if (hasDisplayName)
                  childStart += (data[childStart] & 0xff) + 1;
              } while (hasNext);
          }

        int trieEnd = trieStart + offset;

        trieStart = childStart;

        /* trieStart is now pointing into the children of the
         * selected node. We'll scan over these until we either find a
         * matching character for the next character of the prefix or
         * we hit the end of the node */
        while (true)
          {
            /* If we've reached the end of the node then we haven't
             * found a matching character for the prefix so there are
             * no results */
            if (trieStart >= trieEnd)
              return 0;

            /* If we've found a matching character then start scanning
             * into this node */
            if (compareArray (prefixBytes, prefixOffset,
                              data, trieStart + 4,
                              characterLen))
              break;
            /* Otherwise skip past the node to the next sibling */
            else
              trieStart += extractInt (data, trieStart) & 0x7fffffff;
          }

        prefixOffset += characterLen;
      }

    StringBuilder stringBuf = new StringBuilder (prefix);

    return collect (trieStart, stringBuf, results, 0);
  }

  /* Adds to the results the words of the node at trieStart and of its
   * children, in sorted order, after the results already found. The
   * node is the last one of the string in stringBuf. Returns the new
   * number of results. */
  private int collect (int trieStart,
                       StringBuilder stringBuf,
                       SearchResult[] results,
                       int numResults)
  {
    /* trieStart is now pointing at the last node with this string.
     * Any children of that node are therefore extensions of the
     * prefix. We can now depth-first search the tree to get them all
     * in sorted order */

    TrieStack stack = new TrieStack ();

    stack.push (trieStart,
                trieStart + extractInt (data, trieStart) & 0x7fffffff,
                stringBuf.length ());

    boolean firstChar = true;

    while (numResults < results.length &&
           !stack.isEmpty ())
      {
        int searchStart = stack.getTopStart ();
        int searchEnd = stack.getTopEnd ();

        stringBuf.setLength (stack.getTopStringLength ());

        stack.pop ();

        int offset = extractInt (data, searchStart);
        int characterLen = getUtf8Length (data[searchStart + 4]);
        int childrenStart = searchStart + 4 + characterLen;
        int oldLength = stringBuf.length ();

        if (firstChar)
          firstChar = false;
        else
          stringBuf.append (new String (data,
                                        searchStart + 4,
                                        characterLen));

        /* If this is a complete word then add it to the results */
        if (offset < 0)
          {
            boolean hasNext = true;

            while (hasNext && numResults < results.length)
              {
                int article = ((data[childrenStart] & 0xff) |
                               ((data[childrenStart + 1] & 0xff) << 8));
                int mark = data[childrenStart + 2] & 0xff;
                hasNext = (article & 0x8000) != 0;
                boolean hasDisplayName = (article & 0x4000) != 0;

                childrenStart += 3;

                article &= 0x3fff;

                String word;
                if (hasDisplayName)
                  {
                    int len = data[childrenStart] & 0xff;
                    word = new String (data,
                                       childrenStart + 1,
                                       len);
                    childrenStart += len + 1;
                  }
                else
                  word = stringBuf.toString ();

                results[numResults++] = SearchResult.fromIndex (word,
                                                                article,
                                                                mark);
              }

            offset &= 0x7fffffff;
          }

        /* If there is a sibling then make sure we continue from that
         * after we've descended through the children of this node */
        if (searchStart + offset < searchEnd)
          stack.push (searchStart + offset, searchEnd, oldLength);

        /* Push a search for the children of this node */
        if (childrenStart < searchStart + offset)
            stack.push (childrenStart,
                        searchStart + offset,
                        stringBuf.length ());
      }

    return numResults;
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

  /* Finds the nodes whose path from the root matches the rest of the
   * prefix, from prefixOffset, ignoring the accents. Only the letters
   * typed without an accent match the accented ones: "e" matches "é",
   * but "é" only matches "é", and "ĉ" (or "cx") doesn't match "c". Each
   * node is added
   * to the nodes with its path, the string with the accents of the
   * index. */
  private void findIgnoringAccents (int trieStart,
                                    String prefix,
                                    int prefixOffset,
                                    StringBuilder path,
                                    List<Integer> nodes,
                                    List<String> paths)
  {
    if (prefixOffset >= prefix.length ())
      {
        nodes.add (trieStart);
        paths.add (path.toString ());
        return;
      }

    int letterEnd = prefix.offsetByCodePoints (prefixOffset, 1);
    String typed =
      prefix.substring (prefixOffset, letterEnd).toLowerCase (Locale.ROOT);
    String letter = foldLetter (typed);
    boolean plain = letter.equals (typed);

    int offset = extractInt (data, trieStart);
    int childStart = trieStart + 4;
    childStart += getUtf8Length (data[childStart]);

    /* Skip the words of the node, if it is the end of some */
    if (offset < 0)
      {
        offset &= 0x7fffffff;

        boolean hasNext;

        do
          {
            hasNext = (data[childStart + 1] & 0x80) != 0;
            boolean hasDisplayName = (data[childStart + 1] & 0x40) != 0;

            childStart += 3;

            if (hasDisplayName)
              childStart += (data[childStart] & 0xff) + 1;
          } while (hasNext);
      }

    int trieEnd = trieStart + offset;

    for (int child = childStart;
         child < trieEnd;
         child += extractInt (data, child) & 0x7fffffff)
      {
        String childLetter = getCharacter (child + 4);

        if (plain ?
            foldLetter (childLetter).equals (letter) :
            childLetter.toLowerCase (Locale.ROOT).equals (typed))
          {
            int oldLength = path.length ();
            path.append (childLetter);
            findIgnoringAccents (child, prefix, letterEnd, path, nodes, paths);
            path.setLength (oldLength);
          }
        /* "oe" typed also finds "œ", and "ae" "æ": "coeur" finds "cœur";
         * an "o" at the end of what is typed, already "œ" */
        else if (plain)
          {
            String ligature = childLetter.toLowerCase (Locale.ROOT);
            String pair = ligature.equals ("\u0153") ? "oe" : ligature.equals ("\u00e6") ? "ae" : null;
            if (pair != null && letter.equals (pair.substring (0, 1)))
              {
                int pairEnd = letterEnd;
                if (letterEnd < prefix.length ()
                    && prefix.substring (letterEnd, prefix.offsetByCodePoints (letterEnd, 1))
                         .toLowerCase (Locale.ROOT).equals ("e"))
                  pairEnd = prefix.offsetByCodePoints (letterEnd, 1);
                else if (letterEnd < prefix.length ())
                  continue;

                int oldLength = path.length ();
                path.append (childLetter);
                findIgnoringAccents (child, prefix, pairEnd, path, nodes, paths);
                path.setLength (oldLength);
              }
          }
      }
  }

  /* Like search, but ignoring the accents left out: "eleve" also finds
   * "élève", and "cevalo" "ĉevalo", but "ĉevalo" doesn't find "cevalo".
   * The words are in alphabetical order, accents and case aside, as in a
   * printed dictionary: "com" finds "comédie" between "combustible" and
   * "comique", where the order of the bytes of the index put it after
   * "comme", beyond the results kept. A word without accent comes before
   * the same with ("cote", "coté", "côte", "côté"), so the words that match
   * the prefix exactly come first. In Esperanto, ĉ ĝ ĥ ĵ ŝ ŭ are letters
   * of their own, after c g h j s u. */
  public int searchIgnoringAccents (String prefix,
                                    SearchResult[] results,
                                    boolean esperanto)
  {
    List<Integer> nodes = new ArrayList<Integer> ();
    List<String> paths = new ArrayList<String> ();

    findIgnoringAccents (0, prefix, 0, new StringBuilder (), nodes, paths);

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
            numResults = addWordsExactFirst (group.nodes.get (i),
                                             group.paths.get (i),
                                             results,
                                             numResults);

        TreeMap<String, Group> children = new TreeMap<String, Group> ();
        for (int i = 0; i < group.nodes.size (); i++)
          {
            int node = group.nodes.get (i);
            String pending = group.pending.get (i);

            /* A ligature's second letter, in the order: "œ" after "o"
             * among the "e", "chœur" between "choéphore" and "chômer" */
            if (!pending.isEmpty ())
              {
                groupIn (children, pending.substring (0, 1))
                  .add (node, group.paths.get (i), pending.substring (1));
                continue;
              }

            int end = node + (extractInt (data, node) & 0x7fffffff);

            for (int child = childrenOf (node);
                 child < end;
                 child += extractInt (data, child) & 0x7fffffff)
              {
                String letter = getCharacter (child + 4);
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
    final List<Integer> nodes = new ArrayList<Integer> ();
    final List<String> paths = new ArrayList<String> ();
    /* The letters of the order a node is still ahead of: the "e" of "œ" */
    final List<String> pending = new ArrayList<String> ();

    void add (int node, String path, String rest)
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

  /* Test program */
  /* The offset of the first child of a node, after the words that end
   * on it, if any */
  private int childrenOf (int node)
  {
    int child = node + 4;
    child += getUtf8Length (data[child]);

    if (extractInt (data, node) < 0)
      {
        boolean hasNext;

        do
          {
            hasNext = (data[child + 1] & 0x80) != 0;
            boolean hasDisplayName = (data[child + 1] & 0x40) != 0;

            child += 3;

            if (hasDisplayName)
              child += (data[child] & 0xff) + 1;
          } while (hasNext);
      }

    return child;
  }

  /* Like addWordsOf, but the word itself before the expressions filed
   * under it: "Amérique" before "Amérique du Nord", "cadre" before "dans
   * le cadre de"; then the word with a clarification, "livre" before
   * "livre (monnaie)", before the expressions */
  private int addWordsExactFirst (int node,
                                  String path,
                                  SearchResult[] results,
                                  int numResults)
  {
    SearchResult[] words = new SearchResult[results.length - numResults];
    int count = addWordsOf (node, path, words, 0);
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

  /* Adds the words that end on a node, whose path is 'path' */
  private int addWordsOf (int node,
                          String path,
                          SearchResult[] results,
                          int numResults)
  {
    if (extractInt (data, node) >= 0)
      return numResults;

    int child = node + 4;
    child += getUtf8Length (data[child]);

    boolean hasNext = true;

    while (hasNext && numResults < results.length)
      {
        int article = ((data[child] & 0xff) |
                       ((data[child + 1] & 0xff) << 8));
        int mark = data[child + 2] & 0xff;
        hasNext = (article & 0x8000) != 0;
        boolean hasDisplayName = (article & 0x4000) != 0;

        child += 3;
        article &= 0x3fff;

        String word = path;
        if (hasDisplayName)
          {
            int len = data[child] & 0xff;
            word = new String (data, child + 1, len);
            child += len + 1;
          }

        results[numResults++] = SearchResult.fromIndex (word, article, mark);
      }

    return numResults;
  }

  /* Follows the pattern from patternOffset, the node being reached by
   * 'path'. A node is visited only once for each place in the pattern,
   * so that two stars don't find a word twice. */
  private int matchPattern (int node,
                            String pattern,
                            int patternOffset,
                            StringBuilder path,
                            Set<Long> visited,
                            SearchResult[] results,
                            int numResults)
  {
    if (numResults >= results.length ||
        !visited.add (((long) node << 32) | patternOffset))
      return numResults;

    if (patternOffset >= pattern.length ())
      return addWordsOf (node, path.toString (), results, numResults);

    int nextOffset = pattern.offsetByCodePoints (patternOffset, 1);
    String typed =
      pattern.substring (patternOffset, nextOffset).toLowerCase (Locale.ROOT);
    boolean star = typed.equals ("*");

    /* A star can also stand for nothing */
    if (star)
      numResults = matchPattern (node, pattern, nextOffset, path,
                                 visited, results, numResults);

    String letter = foldLetter (typed);
    boolean plain = letter.equals (typed);
    int nodeEnd = node + (extractInt (data, node) & 0x7fffffff);

    for (int child = childrenOf (node);
         child < nodeEnd && numResults < results.length;
         child += extractInt (data, child) & 0x7fffffff)
      {
        String childLetter = getCharacter (child + 4);
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
            numResults = matchPattern (child, pattern,
                                       star ? patternOffset : nextOffset,
                                       path, visited, results, numResults);
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

    return matchPattern (0, pattern, 0, new StringBuilder (),
                         new HashSet<Long> (), results, 0);
  }

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
      System.out.println (result[i].getWord () + ": " +
                          result[i].getArticle () + "," +
                          result[i].getMark ());
  }
}
