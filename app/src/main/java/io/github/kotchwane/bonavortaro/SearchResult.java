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

public class SearchResult
{
  String word;
  int article;
  int mark;
  /* Whether it is an example of the word of its section, or its
   * translation: "saluton!", "bonjour" */
  boolean example;

  public SearchResult (String word,
                       int article,
                       int mark)
  {
    this (word, article, mark, false);
  }

  public SearchResult (String word,
                       int article,
                       int mark,
                       boolean example)
  {
    this.word = word;
    this.article = article;
    this.mark = mark;
    this.example = example;
  }

  /* A result as the index has it: an example's entry is marked by a first
   * character U+0001, which prevodb adds to its display name */
  static SearchResult fromIndex (String word,
                                 int article,
                                 int mark)
  {
    if (word.length () > 0 && word.charAt (0) == '\u0001')
      return new SearchResult (word.substring (1), article, mark, true);

    return new SearchResult (word, article, mark, false);
  }

  public boolean isExample ()
  {
    return example;
  }

  public String getWord ()
  {
    return word;
  }

  public int getArticle ()
  {
    return article;
  }

  public int getMark ()
  {
    return mark;
  }

  @Override
  public String toString ()
  {
    return word;
  }
}
