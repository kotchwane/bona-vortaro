/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2012, 2014, 2016, 2017  Neil Roberts
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
#include <stdio.h>
#include <errno.h>
#include <glib/gstdio.h>
#include <glib/gi18n.h>

#include "pdb-db.h"
#include "pdb-lang.h"
#include "pdb-error.h"
#include "pdb-doc.h"
#include "pdb-mkdir.h"
#include "pdb-error.h"
#include "pdb-strcmp.h"
#include "pdb-roman.h"
#include "pdb-list.h"
#include "pdb-file.h"
#include "pdb-span.h"
#include "pdb-trim.h"

/* An article file comprises of up to 16 articles. The articles are
 * bundled together to reduce the number of files because the package
 * installer in older versions of Android seems to have a bug with
 * large numbers of files. We don't want to put them all in one file
 * however because it is not possible to seek within an asset file. */

/* Each article in the file has the following format:
 * • A 4-byte little endian number representing the size of the
 *   article data in bytes
 * • A spanned string representing the title of the article.
 * • A list of sections for the article.
 *
 * Each section consists of the following:
 *
 * • A three-byte string representing the language code for this
 *   section. If the code is shorter than three bytes then the
 *   remaining bytes will be set to zero. The code will usually be
 *   ‘eo’ for esperanto but it can but other codes for the translated
 *   sections.
 * • A spanned string for the section title.
 * • A spanned string with the section contents.
 *
 * A spanned string comprises of:
 * • A two byte little endian number for the length of the string data
 * • The string data in UTF-8
 * • A list of string spans. These comprise of:
 *   • A two-byte length to mark the end of the span
 *   • A two-byte string offset to mark the start of the span
 *   • Two 16-bit numbers of extra data whose meaning depends on the
 *     span type number above.
 *   • One byte as a number representing the intended formatting of the span.
 *   The list of spans is terminated by two zero bytes (which would otherwise
 *   appear as the start of a zero-length span).
 *
 * The length and string offset are counted in 16-bit units as if the
 * string was encoded in UTF-16.
 */

/* If a single output file is selected, the format is as follows:
 * • Three bytes for the magic string ‘PRD’
 * • A one byte file format version number. The current version is 67
 * • Four bytes in little-endian format representing the number of articles.
 * • Four bytes in little-endian for each article representing the
 *   offset in the file to the data for that article.
 * • Four bytes in little-endian representing the number of languages.
 * • A table of offsets to the indices for each language consisting of:
 *   • Four bytes reserved for a null terminated string representing the
 *     language code
 *   • Four bytes in little-endian for the offset to the index for that
 *     language.
 *   The entry for each language is a fixed length which means the table can
 *   be binary chopped.
 * • The data for each language. This consists of:
 *   • A null-terminated string in UTF-8 representing the full language name.
 *   • The data for the index as described in pdb-trie.c
 * • The data for each article in the format described above except for the
 *   following:
 *   • The span offsets and lengths are stored as byte counts rather than
 *     as counts of UTF-16 code points.
 */

/* The first version of the database is 66 because the magic string
 * used to be the four letter code ‘PRDB’. The B has been hijacked as
 * a version number.
 *
 * Subsequent changes to the format are:
 *
 * Version 67:
 * • The language codes for each section were added.
 */

static const char pdb_db_magic[4] = "PRDC";

typedef struct
{
  int length;
  char *text;

  /* A list of PdbSpans */
  PdbList spans;
} PdbDbSpannableString;

typedef struct
{
  int section_num;
  char lang_code[4];

  PdbDbSpannableString title;
  PdbDbSpannableString text;
} PdbDbSection;

typedef struct
{
  int article_num;

  PdbDbSpannableString title;

  /* A list of PdbDbSections */
  GList *sections;
} PdbDbArticle;

/* A reference represents a position in an article. The reference can
 * either directly contain a pointer to the section and article or it
 * can refer to it indirectly via a mark name. These are used in a
 * PdbDbLink to store the location the link refers to and also as the
 * value of the index entries */
typedef enum
{
  PDB_DB_REFERENCE_TYPE_MARK,
  PDB_DB_REFERENCE_TYPE_DIRECT
} PdbDbReferenceType;

typedef struct
{
  PdbDbReferenceType type;

  union
  {
    struct
    {
      PdbDbArticle *article;
      PdbDbSection *section;
    } direct;

    char *mark;
  } d;
} PdbDbReference;

/* A link stores a delayed reference to a section from a reference
 * span. These are all collected in the 'references' list so that they
 * can be resolved later once all of the articles are loaded */
typedef struct
{
  PdbDbSpannableString *string;
  PdbSpan *span;
  PdbDbReference *reference;
  /* If not NULL, the span is empty and the title of the target
   * section, followed by this number of the target sence in
   * superscript, is inserted in it once the link is resolved */
  char *sence_label;
  /* For a <sncref>, the number of the target sence as it is shown,
   * eg. “1a” for a subsence, if the target is in the same article */
  char *snc_label;
  /* Whether that number stands on its own, after “senco” or “kaj”, and
   * is written as the sence is labelled, “a)”, rather than in
   * superscript after a word, “konfuzi²” */
  gboolean snc_plain;
  /* …and between brackets, where a letter has no bracket of its own:
   * “teni (1a, 1b, 2)” */
  gboolean snc_in_brackets;
  gboolean is_sncref;
} PdbDbLink;

/* PdbDbMark is only used as the value of the 'marks' hash table. This
 * points to a particular secion in an article and is used to convert
 * the mark name to its actual section */
typedef struct
{
  PdbDbArticle *article;
  PdbDbSection *section;
  /* If the mark is in a particular sence then this will be the sence
   * number. Otherwise it will be -1. */
  int sence;
} PdbDbMark;

struct _PdbDb
{
  PdbLang *lang;

  GPtrArray *articles;

  /* The name of the file of each article, without its directory and
   * ".xml", eg. "korvet", as Reta Vortaro's site names its pages */
  GPtrArray *article_files;

  /* Temporary storage location for the word root. This is only valid
   * while parsing an article */
  char *word_root;
  /* Hash table of alternative roots. This will only contain anything
   * while parsing an article. */
  GHashTable *root_variants;

  /* Temporary hash table of the translations indexed by language
   * code. This only contains anything while parsing an article */
  GHashTable *translations;

  /* While the translations of an example are indexed: without the mark
   * that ends them, "bonjour" for "bonjour!" */
  gboolean in_example;

  GHashTable *marks;

  /* The names of the fields, indexed by their code, eg. "MAT" for
   * "matematiko", from cfg/fakoj.xml */
  GHashTable *fields;

  /* The titles of the works of the bibliography, indexed by their
   * abbreviation, eg. "BL", from cfg/bibliogr.xml */
  GHashTable *works;

  /* What the abbreviations of cfg/mallongigoj.xml stand for, indexed
   * by the abbreviation, eg. "kaj similaj" for "ks" */
  GHashTable *abbreviations;

  /* The codes of the fields, sorted. The span of a field has the
   * number of its code in this list, from 1, written one a line in
   * assets/fields.txt, so that applications can show its icon */
  GPtrArray *field_codes;

  /* The details of the works (PdbDbWork), indexed by their
   * abbreviation, for the sources of the examples */
  GHashTable *work_details;

  /* The addresses of the sources of the examples, written one a line
   * in assets/sources.txt. The span of a source has the number of its
   * address, from 1, or 0 */
  GPtrArray *source_urls;
  GHashTable *source_url_numbers;

  /* This is a list of links. Each link contains a reference to a
   * section (either directly a pointer or a mark name) and and a
   * pointer to the span. The data in the span will be replaced by an
   * article and mark number as a post-processing step once all of the
   * articles have been read so that the references can be resolved.
   * The links are sorted by the offset */
  GList *links;
};

typedef struct
{
  const char *name;
  const char *replacement;
} PdbDbReplacement;

/* The index of each type of link is written in the files with
 * PDB_SPAN_LINK_TYPE, so new types must be added at the end */
static const PdbDbReplacement
pdb_db_ref_types[] =
  {
    { "vid", "→" },
    { "hom", "→" },
    { "dif", "=" },
    { "sin", "⇒" },
    { "ant", "⇝" },
    { "super", "↗" },
    { "sub", "↘" },
    { "prt", "↘" },
    { "malprt", "↗" },
    /* { "lst", "??" }, */
    { "ekz", "●" }
  };

/* The kinds of verbs, from cfg/mallongigoj.xml of voko-grundo. The
 * other kinds of words, such as "prefikso", are already written in
 * full. */
static const PdbDbReplacement
pdb_db_verb_types[] =
  {
    { "tr", "transitiva" },
    { "ntr", "netransitiva" },
    { "x", "transitiva kaj netransitiva" }
  };

static const PdbDbReplacement
pdb_db_styles[] =
  {
    { "KOMUNE", "(komune) " },
    { "FIG", "(figure) " },
    { "ARK", "(arkaismo) " },
    { "EVI", "(evitinde) " },
    { "FRAZ", "(frazaĵo) " },
    { "VULG", "(vulgare) " },
    { "RAR", "(malofte) " },
    { "POE", "(poezie) " },
    { "NEO", "(neologismo) " }
  };

/* The abbreviations of cfg/mallongigoj.xml that are spelled out in
 * the text of Reta Vortaro, outside the examples: those a reader of
 * Esperanto may not know, unlike "ktp", "t.e." or "ekz." */
typedef struct
{
  const char *abbreviation;
  /* Whether a full stop after it is its own, not the end of the
   * sentence: "Kp. ĉe la komenco" */
  gboolean takes_stop;
} PdbDbSpelledOut;

static const PdbDbSpelledOut
pdb_db_spelled_out[] =
  {
    { "ks", FALSE },
    { "kc", FALSE },
    { "jc", FALSE },
    { "pp", TRUE },
    { "sk", TRUE },
    { "Vd", TRUE },
    { "Kp", TRUE },
    { "resp.", FALSE },
    /* Not "i.a." while cfg/mallongigoj.xml gives "interalia" for the
     * adverb "interalie" */
    { "simb.", FALSE }
  };

static void
pdb_db_reference_free (PdbDbReference *entry)
{
  switch (entry->type)
    {
    case PDB_DB_REFERENCE_TYPE_MARK:
      g_free (entry->d.mark);
      break;

    case PDB_DB_REFERENCE_TYPE_DIRECT:
      break;
    }

  g_slice_free (PdbDbReference, entry);
}

static PdbDbReference *
pdb_db_reference_copy (const PdbDbReference *entry_in)
{
  PdbDbReference *entry_out = g_slice_dup (PdbDbReference, entry_in);

  switch (entry_in->type)
    {
    case PDB_DB_REFERENCE_TYPE_MARK:
      entry_out->d.mark = g_strdup (entry_out->d.mark);
      break;

    case PDB_DB_REFERENCE_TYPE_DIRECT:
      break;
    }

  return entry_out;
}

static const char *
pdb_db_reference_get_name (const PdbDbReference *ref)
{
  switch (ref->type)
    {
    case PDB_DB_REFERENCE_TYPE_MARK:
      return ref->d.mark;

    case PDB_DB_REFERENCE_TYPE_DIRECT:
      return ref->d.direct.section->title.text ? ref->d.direct.section->title.text : "";
    }

  g_assert_not_reached ();
}

static void
pdb_db_link_free (PdbDbLink *link)
{
  pdb_db_reference_free (link->reference);
  g_free (link->sence_label);
  g_free (link->snc_label);
  g_slice_free (PdbDbLink, link);
}

static void
pdb_db_add_index_entry (PdbDb *db,
                        const char *lang,
                        const char *name,
                        const char *display_name,
                        const PdbDbReference *entry_in)
{
  PdbTrie *trie;

  if (*name == '\0')
    {
      fprintf (stderr,
               _("empty name found for index entry \"%s\" "
                 "in language \"%s\"\n"),
               pdb_db_reference_get_name (entry_in),
               lang);
      return;
    }

  if (display_name && *display_name == '\0')
    {
      fprintf (stderr,
               _("empty display name found for index entry "
                 "\"%s\" in language \"%s\"\n"),
               pdb_db_reference_get_name (entry_in),
               lang);
      return;
    }

  trie = pdb_lang_get_trie (db->lang, lang);

  /* An entry of an example is marked at the start of its display name, a
   * character readers know to leave out: the word itself, "piedo", isn't
   * its example "piedo" */
  char *marked = NULL;

  if (db->in_example)
    display_name = marked = g_strdup_printf ("\x01%s", display_name ? display_name : name);

  if (trie)
    {
      PdbDbReference *entry =
        pdb_db_reference_copy (entry_in);
      const char *p;

      /* Check if any of the characters in the name are upper case */
      for (p = name; *p; p = g_utf8_next_char (p))
        {
          gunichar ch = g_utf8_get_char (p);

          if (g_unichar_isupper (ch))
            break;
        }

      /* If we found an uppercase character then we'll additionally
       * add a lower case representation of the name so that the
       * search can be case insensitive */
      if (*p || display_name)
        {
          GString *buf = g_string_new (NULL);

          for (p = name; *p; p = g_utf8_next_char (p))
            {
              gunichar ch = g_unichar_tolower (g_utf8_get_char (p));
              g_string_append_unichar (buf, ch);
            }

          (db->in_example ? pdb_trie_add_word_last : pdb_trie_add_word)
            (trie, buf->str, display_name ? display_name : name, entry);

          g_string_free (buf, TRUE);
        }
      else
        {
          (db->in_example ? pdb_trie_add_word_last : pdb_trie_add_word)
            (trie, name, NULL, entry);
        }
    }

  g_free (marked);
}

static void
pdb_db_append_tld (PdbDb *db,
                   GString *buf,
                   char **atts)
{
  const char *root = db->word_root;
  const char *lit = NULL;
  const char *var;
  char **att;

  for (att = atts; att[0]; att += 2)
    {
      if (!strcmp (att[0], "lit"))
        {
          lit = att[1];
        }
      else if (!strcmp (att[0], "var"))
        {
          var = g_hash_table_lookup (db->root_variants, att[1]);

          if (var == NULL)
            {
              fprintf (stderr,
                       _("missing variant \"%s\" for root \"%s\"\n"),
                       att[1],
                       db->word_root);
            }
          else
            {
              root = var;
            }
        }
    }

  if (lit)
    {
      g_string_append (buf, lit);

      if (*root)
        root = g_utf8_next_char (root);
    }

  g_string_append (buf, root);
}

static void
pdb_db_mark_free (PdbDbMark *mark)
{
  g_slice_free (PdbDbMark, mark);
}

static void
pdb_db_destroy_spannable_string (PdbDbSpannableString *string)
{
  g_free (string->text);
  pdb_span_free_list (&string->spans);
}

static void
pdb_db_free_section_cb (void *ptr,
                        void *user_data)
{
  PdbDbSection *section = ptr;
  pdb_db_destroy_spannable_string (&section->title);
  pdb_db_destroy_spannable_string (&section->text);
  g_slice_free (PdbDbSection, section);
}

static void
pdb_db_free_section_list (GList *sections)
{
  g_list_foreach (sections, pdb_db_free_section_cb, NULL);
  g_list_free (sections);
}

static int
pdb_db_get_element_num (PdbDocElementNode *element)
{
  PdbDocNode *n;
  int num = 0;

  /* Count the matching elements before this one */
  for (n = element->node.prev; n; n = n->prev)
    if (n->type == PDB_DOC_NODE_TYPE_ELEMENT &&
        !strcmp (((PdbDocElementNode *) n)->name, element->name))
      num++;

  /* If this is the first matching element then check if it's also the
   * only one */
  if (num == 0)
    {
      for (n = element->node.next; n; n = n->next)
        if (n->type == PDB_DOC_NODE_TYPE_ELEMENT &&
            !strcmp (((PdbDocElementNode *) n)->name, element->name))
          /* It's not the only one so return a real number */
          return 0;

      /* It is the only one so return -1 */
      return -1;
    }
  else
    return num;
}

static PdbDbMark *
pdb_db_add_mark (PdbDb *db,
                 PdbDbArticle *article,
                 PdbDbSection *section,
                 const char *mark_name)
{
  PdbDbMark *mark = g_slice_new (PdbDbMark);

  mark->article = article;
  mark->section = section;
  mark->sence = -1;

  g_hash_table_insert (db->marks,
                       g_strdup (mark_name),
                       mark);

  return mark;
}

static int
pdb_db_get_sence_number (const PdbDocElementNode *elem)
{
  /* Look for a containing “snc” element */
  while (elem && elem->node.type == PDB_DOC_NODE_TYPE_ELEMENT)
    {
      if (!strcmp (elem->name, "snc"))
        {
          int sence_num = 1;

          /* Count the previous sences */
          for (PdbDocNode *node = elem->node.prev; node; node = node->prev)
            {
              if (node->type == PDB_DOC_NODE_TYPE_ELEMENT &&
                  !strcmp (((PdbDocElementNode *) node)->name, "snc"))
                sence_num++;
            }

          return sence_num;
        }

      elem = (PdbDocElementNode *) elem->node.parent;
    }

  return -1;
}

static void
pdb_db_add_mark_from_element_to_reference (PdbDb *db,
                                           const PdbDbReference *reference,
                                           PdbDocElementNode *elem)
{
  /* Only add marks to direct references */
  if (reference->type != PDB_DB_REFERENCE_TYPE_DIRECT)
    return;

  const char *mrk = pdb_doc_get_attribute (elem, "mrk");

  if (mrk == NULL)
    return;

  PdbDbMark *mark = pdb_db_add_mark (db,
                                     reference->d.direct.article,
                                     reference->d.direct.section,
                                     mrk);

  mark->sence = pdb_db_get_sence_number (elem);
}

typedef struct
{
  GString *buf;
  PdbList spans;
} PdbDbTranslationData;

static void
pdb_db_free_translation_data_cb (void *user_data)
{
  PdbDbTranslationData *data = user_data;

  if (data->buf)
    g_string_free (data->buf, TRUE);

  pdb_span_free_list (&data->spans);

  g_slice_free (PdbDbTranslationData, data);
}

static int
pdb_db_compare_language_code (const void *a,
                              const void *b,
                              void *user_data)
{
  PdbDb *db = user_data;
  const char *code_a = a;
  const char *code_b = b;
  const char *name_a, *name_b;

  name_a = pdb_lang_get_name (db->lang, code_a);
  if (name_a == NULL)
    name_a = code_a;

  name_b = pdb_lang_get_name (db->lang, code_b);
  if (name_b == NULL)
    name_b = code_b;

  return pdb_strcmp (name_a, name_b);
}

/* Removes the spaces and commas at the end of the buffer, but not before
 * min_len, so that the text before the part being written is left as it is */
static void
trim_trailing_commas (GString *buf,
                      gsize min_len)
{
  gsize len = buf->len;

  while (len > min_len &&
         (g_ascii_isspace (buf->str[len - 1]) ||
          buf->str[len - 1] == ','))
    len--;

  g_string_truncate (buf, len);
}

/* Appends the text of a kap element, its roots and tildes as "~", and
 * leaving out its variants */
static void
pdb_db_append_kap_text (GString *buf,
                        PdbDocElementNode *kap)
{
  PdbDocNode *n;

  for (n = kap->node.first_child; n; n = n->next)
    {
      switch (n->type)
        {
        case PDB_DOC_NODE_TYPE_TEXT:
          {
            PdbDocTextNode *text = (PdbDocTextNode *) n;
            const char *p, *end;

            for (p = text->data, end = text->data + text->len;
                 p < end;
                 p++)
              if (g_ascii_isspace (*p))
                {
                  if (buf->len > 0 &&
                      !g_ascii_isspace (buf->str[buf->len - 1]))
                    g_string_append_c (buf, ' ');
                }
              else
                g_string_append_c (buf, *p);
          }
          break;

        case PDB_DOC_NODE_TYPE_ELEMENT:
          {
            PdbDocElementNode *elem = (PdbDocElementNode *) n;

            if (!strcmp (elem->name, "tld") ||
                !strcmp (elem->name, "rad"))
              g_string_append_c (buf, '~');
          }
          break;
        }
    }
}

static gboolean
pdb_db_get_trd_link (PdbDb *db,
                     PdbDocElementNode *trd_elem,
                     const PdbDbReference *reference,
                     GString *buf,
                     PdbList *spans,
                     GError **error)
{
  PdbDocElementNode *parent, *kap;
  int sence_num = -1;
  int subsence_num = -1;
  PdbSpan *span;
  PdbDbLink *link;
  int span_start, span_end;

  /* Check that the parent is either <snc> or <subsnc> */
  parent = (PdbDocElementNode *) trd_elem->node.parent;

  /* For the translation of an example, the sence or the word it is an
   * example of, maybe in a remark */
  if (!strcmp (trd_elem->name, "ekz"))
    while (parent->node.parent &&
           strcmp (parent->name, "dif") &&
           strcmp (parent->name, "subsnc") &&
           strcmp (parent->name, "snc") &&
           strcmp (parent->name, "subdrv") &&
           strcmp (parent->name, "subart") &&
           strcmp (parent->name, "drv") &&
           strcmp (parent->name, "art"))
      parent = (PdbDocElementNode *) parent->node.parent;

  if (!strcmp (parent->name, "dif"))
    parent = (PdbDocElementNode *) parent->node.parent;

  if (!strcmp (parent->name, "subsnc"))
    {
      subsence_num = pdb_db_get_element_num (parent);
      parent = (PdbDocElementNode *) parent->node.parent;
    }

  if (!strcmp (parent->name, "snc"))
    {
      sence_num = pdb_db_get_element_num (parent);
      parent = (PdbDocElementNode *) parent->node.parent;
    }

  if (!strcmp (parent->name, "subdrv") ||
      !strcmp (parent->name, "subart"))
    parent = (PdbDocElementNode *) parent->node.parent;

  if (strcmp (parent->name, "drv") &&
      strcmp (parent->name, "art"))
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("%s tag found with unknown parent %s"),
                   trd_elem->name,
                   parent->name);
      return FALSE;
    }

  kap = pdb_doc_get_child_element (&parent->node, "kap");

  if (kap == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("drv node found without a kap"));
      return FALSE;
    }

  span_start = buf->len;

  pdb_db_append_kap_text (buf, kap);

  /* A headword made only of a variant, eg. "<kap><var><kap>~eco</kap></var></kap>"
   * in korupt.xml: use the variant, instead of an empty label */
  if (buf->len == span_start)
    {
      PdbDocElementNode *var = pdb_doc_get_child_element (&kap->node, "var");
      PdbDocElementNode *var_kap =
        var ? pdb_doc_get_child_element (&var->node, "kap") : NULL;

      if (var_kap)
        pdb_db_append_kap_text (buf, var_kap);
    }

  /* If the root has variants then the text of the kap element will
   * have trailing commas. Only the label is trimmed: an empty label
   * would otherwise eat into the text before it, and give the span a
   * negative length */
  trim_trailing_commas (buf, span_start);

  if (sence_num != -1)
    {
      g_string_append_printf (buf, " %i", sence_num + 1);

      if (subsence_num != -1)
        g_string_append_printf (buf, ".%c", subsence_num + 'a');
    }

  span_end = buf->len;

  span = g_slice_new0 (PdbSpan);
  span->span_length = span_end - span_start;
  span->span_start = span_start;
  span->type = PDB_SPAN_REFERENCE;
  /* Add this span to the end of the list */
  pdb_list_insert (spans->prev, &span->link);

  link = g_slice_new0 (PdbDbLink);
  link->span = span;
  link->reference = pdb_db_reference_copy (reference);
  link->string = NULL;

  db->links = g_list_prepend (db->links, link);

  return TRUE;
}

static gboolean
pdb_db_is_empty_translation (PdbDocElementNode *element)
{
  PdbDocNode *node;

  /* Some of the documents (eg, 'missupozi' for French) have empty
   * translations. We want to totally skip these so that they don't
   * mess up the index */

  for (node = element->node.first_child; node; node = node->next)
    switch (node->type)
      {
      case PDB_DOC_NODE_TYPE_TEXT:
        {
          PdbDocTextNode *text = (PdbDocTextNode *) node;
          const char *end = text->data + text->len;
          const char *p;

          for (p = text->data; p < end; p++)
            if (!g_ascii_isspace (*p))
              return FALSE;
        }
        break;

      case PDB_DOC_NODE_TYPE_ELEMENT:
        if (!pdb_db_is_empty_translation ((PdbDocElementNode *) node))
          return FALSE;
      }

  return TRUE;
}

/* Without the exclamation or question mark that ends a phrase, and the
 * spaces before it */
static void
pdb_db_trim_final_mark (GString *buf)
{
  for (;;)
    {
      if (buf->len > 0 && strchr ("!? ", buf->str[buf->len - 1]))
        g_string_truncate (buf, buf->len - 1);
      /* A narrow or a no-break space before it: "merde !" */
      else if (g_str_has_suffix (buf->str, "\xe2\x80\xaf"))
        g_string_truncate (buf, buf->len - 3);
      else if (g_str_has_suffix (buf->str, "\xc2\xa0"))
        g_string_truncate (buf, buf->len - 2);
      else
        break;
    }

  /* And the mark that opens it in Spanish: "¡hola!", "¿qué?" */
  if (g_str_has_prefix (buf->str, "\xc2\xa1") || g_str_has_prefix (buf->str, "\xc2\xbf"))
    g_string_erase (buf, 0, 2);
}

static gboolean
pdb_db_add_trd_index (PdbDb *db,
                      PdbDocElementNode *element,
                      const char *lang_code,
                      const PdbDbReference *reference,
                      GError **error)
{
  PdbDocElementNode *ind;
  GString *display_name;

  display_name = g_string_new (NULL);

  pdb_doc_append_element_text_with_ignore (element,
                                           display_name,
                                           "ofc",
                                           "baz",
                                           NULL);
  pdb_trim_buf (display_name);
  if (db->in_example)
    pdb_db_trim_final_mark (display_name);

  if ((ind = pdb_doc_get_child_element (&element->node, "ind")))
    {
      GString *real_name = g_string_new (NULL);

      pdb_doc_append_element_text (ind, real_name);
      pdb_trim_buf (real_name);
      if (db->in_example)
        pdb_db_trim_final_mark (real_name);

      pdb_db_add_index_entry (db,
                              lang_code,
                              real_name->str,
                              display_name->str,
                              reference);

      /* Also as it is written, from its start: "se confier à qn", filed
       * under "confier", is found by "se confier" too */
      GString *whole = g_string_new (NULL);

      pdb_doc_append_element_text_with_ignore (element,
                                               whole,
                                               "ofc",
                                               "klr",
                                               "baz",
                                               NULL);
      pdb_trim_buf (whole);
      if (db->in_example)
        pdb_db_trim_final_mark (whole);

      if (whole->len > 0 && strcmp (whole->str, real_name->str) &&
          g_ascii_strncasecmp (whole->str, real_name->str, real_name->len))
        pdb_db_add_index_entry (db,
                                lang_code,
                                whole->str,
                                display_name->str,
                                reference);

      g_string_free (whole, TRUE);
      g_string_free (real_name, TRUE);
    }
  else if (pdb_doc_element_has_child_element (element))
    {
      GString *real_name = g_string_new (NULL);

      /* We don't want <klr> tags in the index name */
      pdb_doc_append_element_text_with_ignore (element,
                                               real_name,
                                               "ofc",
                                               "klr",
                                               "baz",
                                               NULL);
      pdb_trim_buf (real_name);
      if (db->in_example)
        pdb_db_trim_final_mark (real_name);

      pdb_db_add_index_entry (db,
                              lang_code,
                              real_name->str,
                              display_name->str,
                              reference);

      g_string_free (real_name, TRUE);
    }
  else
    {
      pdb_db_add_index_entry (db,
                              lang_code,
                              display_name->str,
                              NULL,
                              reference);
    }

  g_string_free (display_name, TRUE);

  return TRUE;
}

static gboolean
pdb_db_add_translation_index (PdbDb *db,
                              PdbDocElementNode *element,
                              const PdbDbReference *reference,
                              GError **error)
{
  const char *lang_code;

  lang_code = pdb_doc_get_attribute (element, "lng");

  if (lang_code == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("%s element with no lng attribute"),
                   element->name);
      return FALSE;
    }

  if (!strcmp (element->name, "trdgrp"))
    {
      PdbDocNode *node;

      for (node = element->node.first_child; node; node = node->next)
        if (node->type == PDB_DOC_NODE_TYPE_ELEMENT &&
            !pdb_db_add_trd_index (db,
                                   (PdbDocElementNode *) node,
                                   lang_code,
                                   reference,
                                   error))
          return FALSE;

      return TRUE;
    }
  else
    return pdb_db_add_trd_index (db, element, lang_code, reference, error);
}

/* Without the space that the end of a <trd> leaves before the comma of
 * its group, “<trd>…\n</trd>,”: “абандонировать, отказаться” */
static void
pdb_db_remove_space_before_punctuation (GString *buf)
{
  int dst = 0;

  for (int src = 0; src < buf->len; src++)
    {
      if (buf->str[src] == ' ' && src + 1 < buf->len &&
          strchr (",)", buf->str[src + 1]))
        continue;
      buf->str[dst++] = buf->str[src];
    }

  g_string_set_size (buf, dst);
}

static gboolean
pdb_db_handle_translation (PdbDb *db,
                           PdbDocElementNode *element,
                           const PdbDbReference *reference,
                           GError **error)
{
  const char *lang_code;
  PdbDbTranslationData *data;
  GString *content;

  /* Silently ignore empty translations */
  if (pdb_db_is_empty_translation (element))
    return TRUE;

  lang_code = pdb_doc_get_attribute (element, "lng");

  if (lang_code == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("%s element with no lng attribute"),
                   element->name);
      return FALSE;
    }
  else if (strlen (lang_code) > 3)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("The language code “%s” is too long"),
                   lang_code);
      return FALSE;
    }

  if ((data = g_hash_table_lookup (db->translations,
                                   lang_code)) == NULL)
    {
      data = g_slice_new (PdbDbTranslationData);
      data->buf = g_string_new (NULL);
      pdb_list_init (&data->spans);
      g_hash_table_insert (db->translations,
                           g_strdup (lang_code),
                           data);
    }
  else if (data->buf->len > 0)
    g_string_append (data->buf, "; ");

  if (!pdb_db_get_trd_link (db,
                            element,
                            reference,
                            data->buf,
                            &data->spans,
                            error))
    return FALSE;

  g_string_append (data->buf, ": ");

  content = g_string_new (NULL);
  /* Without the base form of an Indonesian word, which is only for
   * its place in ReVo's index: "<baz>sayur</baz>sayuran" is "sayuran" */
  pdb_doc_append_element_text_with_ignore (element, content, "baz", NULL);
  pdb_trim_buf (content);
  pdb_db_remove_space_before_punctuation (content);
  g_string_append_len (data->buf, content->str, content->len);
  g_string_free (content, TRUE);

  return pdb_db_add_translation_index (db,
                                       element,
                                       reference,
                                       error);
}

/* The text of an example, its root in place of <tld/>, without its
 * translations and sources */
static void
pdb_db_append_example_text (PdbDb *db,
                            PdbDocElementNode *element,
                            GString *buf)
{
  for (PdbDocNode *node = element->node.first_child; node; node = node->next)
    {
      if (node->type == PDB_DOC_NODE_TYPE_TEXT)
        {
          PdbDocTextNode *text = (PdbDocTextNode *) node;
          g_string_append_len (buf, text->data, text->len);
        }
      else
        {
          PdbDocElementNode *child = (PdbDocElementNode *) node;

          if (!strcmp (child->name, "tld"))
            pdb_db_append_tld (db, buf, child->atts);
          else if (strcmp (child->name, "trd") &&
                   strcmp (child->name, "trdgrp") &&
                   strcmp (child->name, "fnt") &&
                   strcmp (child->name, "adm") &&
                   strcmp (child->name, "bld"))
            pdb_db_append_example_text (db, child, buf);
        }
    }
}

/* A translation of an example, after those of its word: “~o: <the
 * example> bonjour!, salut!”, the example in an example span, which the
 * readers that don't know it show as text */
static gboolean
pdb_db_handle_example_translation (PdbDb *db,
                                   PdbDocElementNode *ekz,
                                   const char *phrase,
                                   PdbDocElementNode *element,
                                   const PdbDbReference *reference,
                                   GError **error)
{
  const char *lang_code;
  PdbDbTranslationData *data;
  GString *content;

  if (pdb_db_is_empty_translation (element))
    return TRUE;

  lang_code = pdb_doc_get_attribute (element, "lng");

  if (lang_code == NULL || strlen (lang_code) > 3)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("%s element in an example with no valid lng attribute"),
                   element->name);
      return FALSE;
    }

  if ((data = g_hash_table_lookup (db->translations,
                                   lang_code)) == NULL)
    {
      data = g_slice_new (PdbDbTranslationData);
      data->buf = g_string_new (NULL);
      pdb_list_init (&data->spans);
      g_hash_table_insert (db->translations,
                           g_strdup (lang_code),
                           data);
    }
  else if (data->buf->len > 0)
    g_string_append (data->buf, "; ");

  if (!pdb_db_get_trd_link (db,
                            ekz,
                            reference,
                            data->buf,
                            &data->spans,
                            error))
    return FALSE;

  g_string_append (data->buf, ": ");

  PdbSpan *span = g_slice_new0 (PdbSpan);
  span->type = PDB_SPAN_EXAMPLE;
  span->span_start = data->buf->len;
  g_string_append (data->buf, phrase);
  span->span_length = data->buf->len - span->span_start;
  pdb_list_insert (data->spans.prev, &span->link);

  g_string_append (data->buf, " ");

  content = g_string_new (NULL);
  /* Without the base form of an Indonesian word, which is only for
   * its place in ReVo's index: "<baz>sayur</baz>sayuran" is "sayuran" */
  pdb_doc_append_element_text_with_ignore (element, content, "baz", NULL);
  pdb_trim_buf (content);
  pdb_db_remove_space_before_punctuation (content);
  g_string_append_len (data->buf, content->str, content->len);
  g_string_free (content, TRUE);

  db->in_example = TRUE;
  gboolean ret = pdb_db_add_translation_index (db,
                                               element,
                                               reference,
                                               error);
  db->in_example = FALSE;

  return ret;
}

/* The translations of an example, and the phrase it marks for the
 * index, “<ind>~on!</ind>”: “saluton!” is found in Esperanto */
static gboolean
pdb_db_handle_example (PdbDb *db,
                       PdbDocElementNode *ekz,
                       const PdbDbReference *reference,
                       GError **error)
{
  PdbDocElementNode *ind = pdb_doc_get_child_element (&ekz->node, "ind");
  GString *phrase = g_string_new (NULL);
  gboolean ret = TRUE;

  pdb_db_append_example_text (db, ind ? ind : ekz, phrase);
  pdb_trim_buf (phrase);
  pdb_db_remove_space_before_punctuation (phrase);

  /* Without the stop or the comma after it */
  while (phrase->len > 0 && strchr (".,;:", phrase->str[phrase->len - 1]))
    g_string_truncate (phrase, phrase->len - 1);
  pdb_trim_buf (phrase);

  if (phrase->len > 0)
    {
      /* After the word itself, if it is the same: "piedo"; found
       * without what it starts with in brackets, "(elsalti) el la
       * fritilo…" by "el la fritilo"; not without a letter, "…" */
      const char *key = phrase->str;

      if (*key == '(' && strchr (key, ')'))
        {
          key = strchr (key, ')') + 1;
          while (*key == ' ')
            key++;
        }

      gboolean has_letter = FALSE;

      for (const char *c = key; *c && !has_letter; c = g_utf8_next_char (c))
        has_letter = g_unichar_isalpha (g_utf8_get_char (c));

      if (ind && has_letter)
        {
          db->in_example = TRUE;
          pdb_db_add_index_entry (db, "eo", key, key == phrase->str ? NULL : phrase->str, reference);
          db->in_example = FALSE;
        }

      for (PdbDocNode *node = ekz->node.first_child; node; node = node->next)
        {
          if (node->type != PDB_DOC_NODE_TYPE_ELEMENT)
            continue;

          PdbDocElementNode *element = (PdbDocElementNode *) node;

          if ((!strcmp (element->name, "trd") ||
               !strcmp (element->name, "trdgrp")) &&
              !pdb_db_handle_example_translation (db,
                                                  ekz,
                                                  phrase->str,
                                                  element,
                                                  reference,
                                                  error))
            {
              ret = FALSE;
              break;
            }
        }
    }

  g_string_free (phrase, TRUE);

  return ret;
}

static gboolean
pdb_db_find_translations_recursive_skip (PdbDb *db,
                                         PdbDocElementNode *root_node,
                                         const char *skip,
                                         const PdbDbReference *reference,
                                         GError **error)
{
  GPtrArray *stack;
  gboolean ret = TRUE;

  pdb_db_add_mark_from_element_to_reference (db, reference, root_node);

  stack = g_ptr_array_new ();

  if (root_node->node.first_child)
    g_ptr_array_add (stack, root_node->node.first_child);

  while (stack->len > 0)
    {
      PdbDocNode *node = g_ptr_array_index (stack, stack->len - 1);
      g_ptr_array_set_size (stack, stack->len - 1);

      if (node->next)
        g_ptr_array_add (stack, node->next);

      if (node->type == PDB_DOC_NODE_TYPE_ELEMENT)
        {
          PdbDocElementNode *element = (PdbDocElementNode *) node;

          if (!strcmp (element->name, "trdgrp") ||
              !strcmp (element->name, "trd"))
            {
              if (!pdb_db_handle_translation (db,
                                              element,
                                              reference,
                                              error))
                {
                  ret = FALSE;
                  break;
                }
            }
          else if (!strcmp (element->name, "ekz"))
            {
              if (!pdb_db_handle_example (db, element, reference, error))
                {
                  ret = FALSE;
                  break;
                }
            }
          else if (node->first_child &&
                   strcmp (element->name, "bld") &&
                   strcmp (element->name, "adm") &&
                   strcmp (element->name, "fnt") &&
                   (skip == NULL || strcmp (element->name, skip)))
            g_ptr_array_add (stack, node->first_child);

          pdb_db_add_mark_from_element_to_reference (db, reference, element);
        }
    }

  g_ptr_array_free (stack, TRUE);

  return ret;
}

static gboolean
pdb_db_find_translations_recursive (PdbDb *db,
                                    PdbDocElementNode *root_node,
                                    const PdbDbReference *reference,
                                    GError **error)
{
  return pdb_db_find_translations_recursive_skip (db,
                                                  root_node,
                                                  NULL, /* skip */
                                                  reference,
                                                  error);
}

static gboolean
pdb_db_find_translations (PdbDb *db,
                          PdbDocElementNode *root_node,
                          const PdbDbReference *reference,
                          GError **error)
{
  PdbDocNode *node;
  gboolean ret = TRUE;

  pdb_db_add_mark_from_element_to_reference (db, reference, root_node);

  for (node = root_node->node.first_child; node; node = node->next)
    if (node->type == PDB_DOC_NODE_TYPE_ELEMENT)
      {
        PdbDocElementNode *element = (PdbDocElementNode *) node;

        if (!strcmp (element->name, "trdgrp") ||
            !strcmp (element->name, "trd"))
          {
            if (!pdb_db_handle_translation (db,
                                            element,
                                            reference,
                                            error))
              {
                ret = FALSE;
                break;
              }
          }
      }

  return ret;
}

static PdbDbSection *
pdb_db_section_new (const char *lang_code)
{
  /* Zeroed: its title may be named in a warning before it is filled */
  PdbDbSection *section = g_slice_new0 (PdbDbSection);
  int code_len = strlen (lang_code);

  memcpy (section->lang_code, lang_code, code_len);
  memset (section->lang_code + code_len, 0, 4 - code_len);

  return section;
}

static GList *
pdb_db_flush_translations (PdbDb *db)
{
  GQueue sections;
  GList *keys, *l;

  g_queue_init (&sections);

  keys = g_hash_table_get_keys (db->translations);

  keys = g_list_sort_with_data (keys,
                                pdb_db_compare_language_code,
                                db);
  for (l = keys; l; l = l->next)
    {
      const char *lang_code = l->data;
      const char *lang_name = pdb_lang_get_name (db->lang, lang_code);
      PdbDbTranslationData *data =
        g_hash_table_lookup (db->translations, lang_code);
      PdbDbSection *section = pdb_db_section_new (lang_code);

      if (lang_name == NULL)
        lang_name = lang_code;

      section->title.length = strlen (lang_name);
      section->title.text = g_strdup (lang_name);
      pdb_list_init (&section->title.spans);

      section->text.length = data->buf->len;
      section->text.text = g_string_free (data->buf, FALSE);
      pdb_list_init (&section->text.spans);
      pdb_list_insert_list (section->text.spans.prev, &data->spans);

      data->buf = NULL;
      pdb_list_init (&data->spans);

      g_queue_push_tail (&sections, section);
    }

  g_list_free (keys);

  g_hash_table_remove_all (db->translations);

  return sections.head;
}

static gboolean
pdb_db_resolve_reference (PdbDb *db,
                          const PdbDbReference *ref,
                          int *article_num,
                          int *section_num,
                          int *sence_num)
{
  gboolean ret = TRUE;

  switch (ref->type)
    {
    case PDB_DB_REFERENCE_TYPE_MARK:
      {
        PdbDbMark *mark =
          g_hash_table_lookup (db->marks, ref->d.mark);

        if (mark == NULL)
          {
            /* Try the mark again with increasingly less precision
             * until will find one that matches so that we can at
             * least point to the right article */
            char *mark_copy = g_strdup (ref->d.mark);
            char *dot;

            while ((dot = strrchr (mark_copy, '.')))
              {
                *dot = '\0';

                if ((mark = g_hash_table_lookup (db->marks, mark_copy)))
                  {
                    fprintf (stderr,
                             _("using less precise mark \"%s\" for \"%s\"\n"),
                             mark_copy,
                             ref->d.mark);
                    break;
                  }
              }

            g_free (mark_copy);
          }

        if (mark)
          {
            *article_num = mark->article->article_num;
            *section_num = mark->section->section_num;
            *sence_num = mark->sence;
          }
        else
          {
            *article_num = 0;
            *section_num = 0;
            *sence_num = -1;
            fprintf (stderr,
                     _("no mark found for reference \"%s\"\n"),
                     ref->d.mark);
            ret = FALSE;
          }
      }
      break;

    case PDB_DB_REFERENCE_TYPE_DIRECT:
      {
        *article_num = ref->d.direct.article->article_num;
        *section_num = ref->d.direct.section->section_num;
        *sence_num = -1;
      }
      break;
    }

  return ret;
}

/* Inserts text at the end of a span, making the span longer and
 * moving the spans that start after it */
static void
pdb_db_insert_in_span (PdbDbSpannableString *string,
                       PdbSpan *span,
                       const char *text,
                       int len)
{
  int pos = span->span_start + span->span_length;
  char *rep_text = g_malloc (string->length + len + 1);
  memcpy (rep_text, string->text, pos);
  memcpy (rep_text + pos, text, len);
  memcpy (rep_text + pos + len,
          string->text + pos,
          string->length - pos);
  rep_text[string->length + len] = '\0';
  g_free (string->text);
  string->text = rep_text;
  string->length += len;
  span->span_length += len;

  PdbSpan *s;

  pdb_list_for_each (s, &string->spans, link)
    {
      if (s == span)
        continue;

      if (s->span_start > span->span_start)
        s->span_start += len;
      else if (s->span_start + s->span_length > pos &&
               s->span_start < span->span_start)
        /* The span contains the one that grows */
        s->span_length += len;
    }
}

/* Fills the empty reference span of a sence that takes its
 * definition from another sence, with the title of the target and
 * its sence number in superscript, eg. “konfuzi¹” */
static void
pdb_db_set_sence_title (PdbDb *db,
                        PdbDbLink *link,
                        int article_num,
                        int section_num)
{
  PdbDbArticle *article = db->articles->pdata[article_num];
  PdbDbSection *section = g_list_nth_data (article->sections, section_num);

  g_assert (section != NULL);

  link->span->data1 = article_num;
  link->span->data2 = section_num;

  /* Leave out the superscripts of the title, which mark the
   * officiality, eg. the “*” of “*konfuzi” */
  GString *title = g_string_new (NULL);
  int pos = 0;
  PdbSpan *s;

  pdb_list_for_each (s, &section->title.spans, link)
    {
      if (s->type != PDB_SPAN_SUPERSCRIPT || s->span_start < pos)
        continue;

      g_string_append_len (title,
                           section->title.text + pos,
                           s->span_start - pos);
      pos = s->span_start + s->span_length;
    }

  g_string_append_len (title,
                       section->title.text + pos,
                       section->title.length - pos);

  pdb_db_insert_in_span (link->string, link->span, title->str, title->len);
  g_string_free (title, TRUE);

  if (*link->sence_label)
    {
      const char *buf = link->sence_label;
      int len = strlen (buf);
      PdbSpan *sup = g_slice_new0 (PdbSpan);

      sup->type = PDB_SPAN_SUPERSCRIPT;
      sup->span_start = link->span->span_start + link->span->span_length;
      pdb_list_insert (&link->span->link, &sup->link);
      pdb_db_insert_in_span (link->string, sup, buf, len);
      /* The number is part of the link */
      link->span->span_length += len;
    }
}

static void
pdb_db_set_sncref (PdbDb *db,
                   PdbDbLink *link,
                   int article_num,
                   int section_num,
                   int sence_num)
{
  g_assert (article_num < db->articles->len);

  PdbDbArticle *article = db->articles->pdata[article_num];
  PdbDbSection *section = g_list_nth_data (article->sections, section_num);

  g_assert (section != NULL);

  if (sence_num < 0 || link->string == NULL)
    {
      if (link->reference->type == PDB_DB_REFERENCE_TYPE_MARK)
        {
          fprintf (stderr,
                   _("sncref “%s” doesn’t point to a snc\n"),
                   link->reference->d.mark);
        }
      else
        {
          fprintf (stderr,
                   _("reference to section with title “%.*s” "
                     "contains an invalid sncref\n"),
                   section->title.length,
                   section->title.text);
        }
      pdb_list_remove (&link->span->link);
      pdb_span_free (link->span);
      return;
    }

  /* The number as the target shows it, “1a” for a subsence, rather
   * than the number of its sence, “1” */
  char buf[16];
  if (link->snc_label && *link->snc_label)
    g_strlcpy (buf, link->snc_label, sizeof buf);
  else
    snprintf (buf, sizeof buf, "%i", sence_num);
  /* On its own, a letter as the sence shows it: “la sencoj a) kaj b)” */
  if (link->snc_plain && !link->snc_in_brackets && g_ascii_isalpha (buf[strlen (buf) - 1]))
    g_strlcat (buf, ")", sizeof buf);

  PdbDbSpannableString *string = link->string;
  PdbSpan *span = link->span;
  int pos = span->span_start;

  /* The space after it, which the text lost, the reference being
   * empty until now: “a) kaj”, “b) (filozofia)” */
  if (link->snc_plain && pos < string->length &&
      !strchr (" ,.;:)\n", string->text[pos]))
    g_strlcat (buf, " ", sizeof buf);
  int len = strlen (buf);
  char *rep_text = g_malloc (string->length + len + 1);
  memcpy (rep_text, string->text, pos);
  memcpy (rep_text + pos, buf, len);
  memcpy (rep_text + pos + len,
          string->text + pos,
          string->length - pos);
  rep_text[string->length + len] = '\0';
  g_free (string->text);
  string->text = rep_text;
  string->length += len;
  span->span_length += len;

  PdbSpan *s;
  gboolean after = FALSE;

  pdb_list_for_each (s, &string->spans, link)
    {
      if (s == span)
        {
          after = TRUE;
          continue;
        }

      /* The spans after the number move; those around it, eg. the
       * whole remark, grow: they ended short of their text, by as
       * many letters as numbers inserted */
      if (s->span_start > pos || (s->span_start == pos && after))
        s->span_start += len;
      else if (s->span_start + s->span_length > pos)
        s->span_length += len;
    }

  /* Written as the rest of the text */
  if (link->snc_plain)
    {
      pdb_list_remove (&span->link);
      pdb_span_free (span);
    }
}

static void
pdb_db_resolve_links (PdbDb *db)
{
  int article_num;
  GList *l;

  /* Calculate the article and section numbers */
  for (article_num = 0; article_num < db->articles->len; article_num++)
    {
      PdbDbArticle *article = g_ptr_array_index (db->articles, article_num);
      int section_num;

      article->article_num = article_num;
      for (section_num = 0, l = article->sections;
           l;
           section_num++, l = l->next)
        {
          PdbDbSection *section = l->data;
          section->section_num = section_num;
        }
    }

  /* Resolve all of the links */
  for (l = db->links; l; l = l->next)
    {
      PdbDbLink *link = l->data;
      int article_num, section_num, sence_num;

      if (pdb_db_resolve_reference (db,
                                    link->reference,
                                    &article_num,
                                    &section_num,
                                    &sence_num))
        {
          if (link->sence_label)
            {
              pdb_db_set_sence_title (db, link, article_num, section_num);
            }
          else if (link->span->type == PDB_SPAN_SUPERSCRIPT)
            {
              pdb_db_set_sncref (db,
                                 link,
                                 article_num,
                                 section_num,
                                 sence_num);
            }
          else
            {
              link->span->data1 = article_num;
              link->span->data2 = section_num;

              /* And the sence it points to, if any, in a span of its
               * own over the same text */
              if (sence_num > 0 && link->span->type == PDB_SPAN_REFERENCE)
                {
                  PdbSpan *sense = g_slice_new0 (PdbSpan);
                  sense->type = PDB_SPAN_SENSE;
                  sense->span_start = link->span->span_start;
                  sense->span_length = link->span->span_length;
                  sense->data1 = sence_num;
                  pdb_list_insert (&link->span->link, &sense->link);
                }
            }
        }
      else
        {
          /* Remove the span if we couldn't resolve the reference so
           * that it won't leave a link pointing to the wrong
           * article */
          pdb_list_remove (&link->span->link);
          pdb_span_free (link->span);
        }
    }
}

typedef enum
{
  PDB_DB_STACK_NODE,
  PDB_DB_STACK_CLOSE_SPAN,
  PDB_DB_STACK_ADD_PARAGRAPH,
  PDB_DB_STACK_CLOSING_CHARACTER,
  PDB_DB_STACK_END_COPY
} PdbDbStackType;

typedef struct
{
  PdbDbStackType type;

  union
  {
    PdbDocNode *node;
    PdbSpan *span;
    gunichar character;
  } d;
} PdbDbParseStackEntry;

typedef enum
{
    PDB_DB_QUEUED_SPACE_TYPE_NONE,
    PDB_DB_QUEUED_SPACE_TYPE_SPACE,
    PDB_DB_QUEUED_SPACE_TYPE_PARAGRAPH,
} PdbDbQueuedSpaceType;

typedef struct
{
  GArray *stack;
  GString *buf;
  PdbDbSpannableString *string;
  PdbList spans;
  PdbDbQueuedSpaceType queued_space;
  gboolean skip_children;
  /* While the definition of another sence is copied, the examples in
   * it and everything after them are skipped */
  gboolean copying;
  gboolean skip_copy;
  /* Whether a space was pending before a skipped element, eg. before
   * the source at the end of an example */
  gboolean space_before_skipped;
  /* The text after a source whose first mark, "!" in "<fnt>…</fnt>!", was
   * written before the source: skipped there */
  PdbDocNode *mark_moved_from;
  /* Where the last source of an example ended in buf, or -1: a source
   * right after another one is joined to it, "— A; B" */
  int last_source_end;
} PdbDbParseState;

static PdbDbParseStackEntry *
pdb_db_parse_push_entry (PdbDbParseState *state,
                         PdbDbStackType type)
{
  PdbDbParseStackEntry *entry;

  g_array_set_size (state->stack, state->stack->len + 1);
  entry = &g_array_index (state->stack,
                          PdbDbParseStackEntry,
                          state->stack->len - 1);

  entry->type = type;

  return entry;
}

static PdbDbParseStackEntry *
pdb_db_parse_push_node (PdbDbParseState *state,
                        PdbDocNode *node)
{
  PdbDbParseStackEntry *entry =
    pdb_db_parse_push_entry (state, PDB_DB_STACK_NODE);

  entry->d.node = node;

  return entry;
}

static PdbDbParseStackEntry *
pdb_db_parse_push_add_paragraph (PdbDbParseState *state)
{
  return pdb_db_parse_push_entry (state, PDB_DB_STACK_ADD_PARAGRAPH);
}

static PdbDbParseStackEntry *
pdb_db_parse_push_closing_character (PdbDbParseState *state,
                                     gunichar ch)
{
  PdbDbParseStackEntry *entry =
    pdb_db_parse_push_entry (state, PDB_DB_STACK_CLOSING_CHARACTER);

  entry->d.character = ch;

  return entry;
}

typedef gboolean (* PdbDbElementHandler) (PdbDb *db,
                                          PdbDbParseState *state,
                                          PdbDocElementNode *element,
                                          PdbSpan *span,
                                          GError **error);

typedef struct
{
  const char *name;
  PdbSpanType type;
  PdbDbElementHandler handler;
  gboolean paragraph;
} PdbDbElementSpan;

static void
pdb_db_start_text (PdbDbParseState *state)
{
  if (state->buf->len > 0)
    {
      switch (state->queued_space)
        {
        case PDB_DB_QUEUED_SPACE_TYPE_NONE:
          break;

        case PDB_DB_QUEUED_SPACE_TYPE_SPACE:
          /* We can get doubled-up spaces if the space was flushed
           * because of the start of a span and the new span also
           * begins with a space.
           */
          if (state->buf->str[state->buf->len - 1] != ' ' &&
              state->buf->str[state->buf->len - 1] != '\n' &&
              /* Nor after the quote opening a <ctl>, "<ctl>\n aperto" */
              !g_str_has_suffix (state->buf->str, "\xe2\x80\x9e"))
            g_string_append_c (state->buf, ' ');
          break;

        case PDB_DB_QUEUED_SPACE_TYPE_PARAGRAPH:
          g_string_append (state->buf, "\n\n");
          break;
        }
    }

  state->queued_space = PDB_DB_QUEUED_SPACE_TYPE_NONE;
}

static PdbSpan *
pdb_db_start_span (PdbDbParseState *state,
                   PdbSpanType type)
{
  PdbSpan *span = g_slice_new0 (PdbSpan);
  PdbDbParseStackEntry *entry;

  span->span_start = state->buf->len;
  span->type = type;

  /* Add the span to the end of the list */
  pdb_list_insert (state->spans.prev, &span->link);
  /* Push the span onto the state.stack so that we can
   * fill in the span length once all of the child
   * nodes have been processed */
  entry = pdb_db_parse_push_entry (state, PDB_DB_STACK_CLOSE_SPAN);
  entry->d.span = span;

  return span;
}

/* A quoted word or phrase (<ctl>), eg. the meaning "plenumi": between
 * Esperanto's quotes, as ReVo shows it, „plenumi“ */
static gboolean
pdb_db_handle_ctl (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  pdb_db_start_text (state);
  g_string_append (state->buf, "\xe2\x80\x9e");
  pdb_db_parse_push_closing_character (state, 0x201c);

  return TRUE;
}

static gboolean
pdb_db_handle_aut (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  pdb_db_start_text (state);
  g_string_append (state->buf, "[");
  pdb_db_parse_push_closing_character (state, ']');

  return TRUE;
}

static gboolean
pdb_db_handle_rim (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  PdbSpan *bold_span = g_slice_new0 (PdbSpan);

  pdb_db_start_text (state);

  bold_span->type = PDB_SPAN_BOLD;
  bold_span->span_start = state->buf->len;

  g_string_append (state->buf, "Rim. ");

  bold_span->span_length = (state->buf->len -
                            bold_span->span_start);
  pdb_list_insert (state->spans.prev, &bold_span->link);

  return TRUE;
}

static gboolean
pdb_db_add_replacement (PdbDbParseState *state,
                        const char *name,
                        const PdbDbReplacement *replacements,
                        int n_replacements)
{
  int i;

  for (i = 0; i < n_replacements; i++)
    {
      const PdbDbReplacement *replacement = replacements + i;

      if (!strcmp (name, replacement->name))
        {
          pdb_db_start_text (state);
          g_string_append (state->buf, replacement->replacement);

          return TRUE;
        }
    }

  return FALSE;
}

static gboolean
pdb_db_add_replacement_contents (PdbDbParseState *state,
                                 PdbDocElementNode *element,
                                 const PdbDbReplacement *replacements,
                                 int n_replacements)
{
  GString *buf = g_string_new (NULL);
  gboolean ret;

  pdb_doc_append_element_text (element, buf);
  pdb_trim_buf (buf);
  ret = pdb_db_add_replacement (state, buf->str, replacements, n_replacements);
  g_string_free (buf, TRUE);

  return ret;
}

static void
pdb_db_handle_reference_type (PdbDbParseState *state,
                              PdbDocElementNode *element)
{
  const char *parent_name;
  char **att;

  parent_name = ((PdbDocElementNode *) element->node.parent)->name;

  /* Ignore the icon for the reference if the parent is one of the
   * following types. A comment in the code for the WebOS version of
   * PReVo says that the XSLT for ReVo does this too, but I can't seem
   * to find it anymore */
  if (!strcmp (parent_name, "dif") ||
      !strcmp (parent_name, "rim") ||
      !strcmp (parent_name, "ekz") ||
      !strcmp (parent_name, "klr"))
    return;

  for (att = element->atts; att[0]; att += 2)
    if (!strcmp (att[0], "tip"))
      {
        int i;

        for (i = 0; i < G_N_ELEMENTS (pdb_db_ref_types); i++)
          if (!strcmp (att[1], pdb_db_ref_types[i].name))
            {
              /* The symbol, in a span giving the type of the link */
              PdbSpan *span = g_slice_new0 (PdbSpan);

              pdb_db_start_text (state);

              span->type = PDB_SPAN_LINK_TYPE;
              span->span_start = state->buf->len;
              span->data1 = i;
              g_string_append (state->buf, pdb_db_ref_types[i].replacement);
              span->span_length = state->buf->len - span->span_start;
              pdb_list_insert (state->spans.prev, &span->link);

              break;
            }

        break;
      }
}

static void
pdb_db_add_reference_span (PdbDb *db,
                           PdbDbParseState *state,
                           PdbSpanType span_type,
                           const char *mrk)
{
  pdb_db_start_text (state);

  PdbSpan *span = pdb_db_start_span (state, span_type);

  PdbDbLink *link = g_slice_new0 (PdbDbLink);
  link->span = span;
  link->string = state->string;

  PdbDbReference *reference = g_slice_new (PdbDbReference);
  reference->type = PDB_DB_REFERENCE_TYPE_MARK;
  reference->d.mark = g_strdup (mrk);
  link->reference = reference;

  db->links = g_list_prepend (db->links, link);
}

static gboolean
pdb_db_handle_ref (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  char **att;

  for (att = element->atts; att[0]; att += 2)
    if (!strcmp (att[0], "cel"))
      goto found_cel;

  g_set_error (error,
               PDB_ERROR,
               PDB_ERROR_BAD_FORMAT,
               _("<ref> tag found with a cel attribute"));
  return FALSE;

 found_cel:

  pdb_db_handle_reference_type (state, element);

  pdb_db_add_reference_span (db, state, PDB_SPAN_REFERENCE, att[1]);

  return TRUE;
}

static gboolean
pdb_db_handle_refgrp (PdbDb *db,
                      PdbDbParseState *state,
                      PdbDocElementNode *element,
                      PdbSpan *span,
                      GError **error)
{
  pdb_db_handle_reference_type (state, element);

  return TRUE;
}

static const char *
pdb_db_get_sncref_ref (const PdbDocElementNode *element)
{
  const char *ref = pdb_doc_get_attribute (element, "ref");

  if (ref != NULL)
    return ref;

  /* Instead of specifying the target as an attribute, the sncref tag
   * can also be a child of a <ref> tag. */

  if (element->node.parent == NULL)
    return NULL;

  PdbDocElementNode *parent = (PdbDocElementNode *) element->node.parent;

  if (strcmp (parent->name, "ref"))
    return NULL;

  return pdb_doc_get_attribute (parent, "cel");
}

static PdbDocElementNode *
pdb_db_find_mark_element (PdbDocElementNode *element,
                          const char *mrk);

static char *
pdb_db_get_sence_label (PdbDocElementNode *element);

/* What comes before a reference to a sence: a word it is a sence of,
 * eg. “<tld/>o <sncref/>”; a word that makes it stand on its own, eg.
 * “la sencoj <sncref/>”; or a word that joins it to the reference
 * before, eg. “kaj”, “aŭ”, “,” */
typedef enum
{
  PDB_DB_BEFORE_SNCREF_WORD,
  PDB_DB_BEFORE_SNCREF_SENCE,
  PDB_DB_BEFORE_SNCREF_JOIN
} PdbDbBeforeSncref;

static PdbDbBeforeSncref
pdb_db_before_sncref (const GString *buf)
{
  static const char *const sence_words[] =
    {
      "senco", "sencon", "sencoj", "sencojn", "signifo", "signifon",
      "de", "sub", NULL
    };
  static const char *const join_words[] =
    {
      "kaj", "a\xc5\xad", "\xc4\x9dis", NULL
    };
  int end = buf->len;

  while (end > 0 && buf->str[end - 1] == ' ')
    end--;

  if (end > 0 && buf->str[end - 1] == '(')
    return PDB_DB_BEFORE_SNCREF_SENCE;
  if (end > 0 && buf->str[end - 1] == ',')
    return PDB_DB_BEFORE_SNCREF_JOIN;

  int start = end;

  while (start > 0 && buf->str[start - 1] != ' ' && buf->str[start - 1] != '\n')
    start--;

  for (int i = 0; sence_words[i]; i++)
    if (strlen (sence_words[i]) == end - start &&
        !g_ascii_strncasecmp (buf->str + start, sence_words[i], end - start))
      return PDB_DB_BEFORE_SNCREF_SENCE;

  for (int i = 0; join_words[i]; i++)
    if (strlen (join_words[i]) == end - start &&
        !g_ascii_strncasecmp (buf->str + start, join_words[i], end - start))
      return PDB_DB_BEFORE_SNCREF_JOIN;

  return PDB_DB_BEFORE_SNCREF_WORD;
}

/* Whether the text ends with one of two words, case aside */
static gboolean
pdb_db_ends_with_word (const GString *buf,
                       const char *word1,
                       const char *word2)
{
  int end = buf->len;

  while (end > 0 && buf->str[end - 1] == ' ')
    end--;

  int start = end;

  while (start > 0 && buf->str[start - 1] != ' ' && buf->str[start - 1] != '\n')
    start--;

  return ((strlen (word1) == end - start &&
           !g_ascii_strncasecmp (buf->str + start, word1, end - start)) ||
          (strlen (word2) == end - start &&
           !g_ascii_strncasecmp (buf->str + start, word2, end - start)));
}

/* Whether the element is in a <ref> with text before it, a word or a
 * <tld/>: “<ref …>flanke de<sncref/></ref>”, not “<ref …><sncref/></ref>” */
static gboolean
pdb_db_has_text_before (PdbDocElementNode *element)
{
  PdbDocNode *parent = element->node.parent;

  if (parent == NULL || parent->type != PDB_DOC_NODE_TYPE_ELEMENT ||
      strcmp (((PdbDocElementNode *) parent)->name, "ref"))
    return FALSE;

  for (PdbDocNode *node = parent->first_child;
       node && node != &element->node;
       node = node->next)
    {
      if (node->type == PDB_DOC_NODE_TYPE_ELEMENT)
        return TRUE;

      PdbDocTextNode *text = (PdbDocTextNode *) node;

      for (int i = 0; i < text->len; i++)
        if (!g_ascii_isspace (text->data[i]))
          return TRUE;
    }

  return FALSE;
}

static gboolean
pdb_db_handle_sncref (PdbDb *db,
                      PdbDbParseState *state,
                      PdbDocElementNode *element,
                      GError **error)
{
  const char *ref = pdb_db_get_sncref_ref (element);

  if (ref == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("<sncref> tag found with a target"));
      return FALSE;
    }

  /* The number goes right after the word, even when the source has a
   * space before the tag, eg. “<tld/>i <sncref …/> brutojn”; but not
   * after “senco” or “kaj”, where it stands on its own */
  gboolean plain;
  PdbDbBeforeSncref before = pdb_db_before_sncref (state->buf);

  /* “de” and “sub” are the word itself when they are the text of the
   * reference: “<ref …>De<sncref/></ref>”, “<ref …>flanke de<sncref/>
   * </ref>”, the sense 4 of “de” */
  if (before == PDB_DB_BEFORE_SNCREF_SENCE &&
      pdb_db_ends_with_word (state->buf, "de", "sub") &&
      pdb_db_has_text_before (element))
    before = PDB_DB_BEFORE_SNCREF_WORD;

  switch (before)
    {
    case PDB_DB_BEFORE_SNCREF_SENCE:
      plain = TRUE;
      break;

    case PDB_DB_BEFORE_SNCREF_JOIN:
      /* As the reference before in this text: “la sencoj a) kaj b)”,
       * “<tld/>o¹ aŭ²”; else after its word: “<tld/>oj,<sncref/>” */
      plain = FALSE;
      for (GList *l = db->links; l; l = l->next)
        {
          PdbDbLink *before = l->data;

          if (before->string == state->string && before->is_sncref)
            {
              plain = before->snc_plain;
              break;
            }
        }
      break;

    default:
      plain = FALSE;
      break;
    }

  if (!plain && state->queued_space == PDB_DB_QUEUED_SPACE_TYPE_SPACE)
    state->queued_space = PDB_DB_QUEUED_SPACE_TYPE_NONE;

  pdb_db_start_text (state);
  pdb_db_add_reference_span (db, state, PDB_SPAN_SUPERSCRIPT, ref);

  /* The label of the target, when it is in this article */
  PdbDocElementNode *root = element;

  while (root->node.parent)
    root = (PdbDocElementNode *) root->node.parent;

  PdbDocElementNode *target = pdb_db_find_mark_element (root, ref);

  PdbDbLink *link = db->links->data;
  link->is_sncref = TRUE;
  link->snc_plain = plain;

  int depth = 0;
  for (int i = 0; i < state->buf->len; i++)
    if (state->buf->str[i] == '(')
      depth++;
    else if (state->buf->str[i] == ')' && depth > 0)
      depth--;
  link->snc_in_brackets = depth > 0;

  if (target)
    link->snc_label = pdb_db_get_sence_label (target);

  return TRUE;
}

static gboolean
pdb_db_handle_subdrv (PdbDb *db,
                      PdbDbParseState *state,
                      PdbDocElementNode *element,
                      PdbSpan *span,
                      GError **error)
{
  int sence_num;

  sence_num = pdb_db_get_element_num (element);

  /* We don't need to do anything if this is the only subdrv */
  if (sence_num != -1)
    {
      pdb_db_start_text (state);
      g_string_append_printf (state->buf, "%c. ", sence_num + 'A');
    }

  return TRUE;
}

static PdbDocElementNode *
pdb_db_find_mark_element (PdbDocElementNode *element,
                          const char *mrk)
{
  const char *element_mrk = pdb_doc_get_attribute (element, "mrk");

  if (element_mrk && !strcmp (element_mrk, mrk))
    return element;

  for (PdbDocNode *n = element->node.first_child; n; n = n->next)
    {
      if (n->type == PDB_DOC_NODE_TYPE_ELEMENT)
        {
          PdbDocElementNode *found =
            pdb_db_find_mark_element ((PdbDocElementNode *) n, mrk);

          if (found)
            return found;
        }
    }

  return NULL;
}

/* The number of a sence as it is shown, eg. “2”, or “2b” for a
 * subsence, or an empty string if it isn't numbered */
static char *
pdb_db_get_sence_label (PdbDocElementNode *element)
{
  GString *label = g_string_new (NULL);

  if (element && !strcmp (element->name, "subsnc"))
    {
      int sub_num = pdb_db_get_element_num (element);
      PdbDocElementNode *parent = (PdbDocElementNode *) element->node.parent;

      if (parent && !strcmp (parent->name, "snc"))
        {
          int num = pdb_db_get_element_num (parent);

          if (num != -1)
            g_string_append_printf (label, "%i", num + 1);
        }

      if (sub_num != -1)
        g_string_append_c (label, sub_num + 'a');
    }
  else if (element && !strcmp (element->name, "snc"))
    {
      int num = pdb_db_get_element_num (element);

      if (num != -1)
        g_string_append_printf (label, "%i", num + 1);
    }

  return g_string_free (label, FALSE);
}

/* A sence can have no definition and refer instead to a sence of
 * another word of the article with its ref attribute, eg. the sences
 * of “konfuzo” (the state of being konfuzita) are those of “konfuzi”.
 * The sence then starts with a link to the other sence, followed by
 * its definition without its examples, eg. “1. konfuzi¹: Kunigi kaj
 * miksi…” */
static void
pdb_db_add_sence_ref (PdbDb *db,
                      PdbDbParseState *state,
                      PdbDocElementNode *element,
                      const char *ref)
{
  PdbDocElementNode *root = element;

  while (root->node.parent)
    root = (PdbDocElementNode *) root->node.parent;

  PdbDocElementNode *target = pdb_db_find_mark_element (root, ref);
  PdbDocElementNode *dif =
    target ? pdb_doc_get_child_element (&target->node, "dif") : NULL;

  pdb_db_start_text (state);

  PdbSpan *span = g_slice_new0 (PdbSpan);
  span->type = PDB_SPAN_REFERENCE;
  span->span_start = state->buf->len;
  pdb_list_insert (state->spans.prev, &span->link);

  PdbDbLink *link = g_slice_new0 (PdbDbLink);
  link->span = span;
  link->string = state->string;
  link->sence_label = pdb_db_get_sence_label (target);

  PdbDbReference *reference = g_slice_new (PdbDbReference);
  reference->type = PDB_DB_REFERENCE_TYPE_MARK;
  reference->d.mark = g_strdup (ref);
  link->reference = reference;

  db->links = g_list_prepend (db->links, link);

  if (dif == NULL || dif->node.first_child == NULL)
    {
      g_string_append (state->buf, " ");
      return;
    }

  g_string_append (state->buf, ": ");

  /* Parse the children of the sence after the copied definition */
  state->skip_children = TRUE;
  if (element->node.first_child)
    pdb_db_parse_push_node (state, element->node.first_child);
  pdb_db_parse_push_entry (state, PDB_DB_STACK_END_COPY);
  pdb_db_parse_push_node (state, dif->node.first_child);
  state->copying = TRUE;
}

static gboolean
pdb_db_handle_snc (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  int sence_num;

  sence_num = pdb_db_get_element_num (element);

  /* We don't need to do anything if this is the only sence */
  if (sence_num != -1)
    {
      pdb_db_start_text (state);
      g_string_append_printf (state->buf, "%i. ", sence_num + 1);
    }

  const char *ref = pdb_doc_get_attribute (element, "ref");

  if (ref && !pdb_doc_get_child_element (&element->node, "dif"))
    pdb_db_add_sence_ref (db, state, element, ref);

  return TRUE;
}

static gboolean
pdb_db_handle_subsnc (PdbDb *db,
                      PdbDbParseState *state,
                      PdbDocElementNode *element,
                      PdbSpan *span,
                      GError **error)
{
  int sence_num;

  sence_num = pdb_db_get_element_num (element);

  /* We don't need to do anything if this is the only subsence */
  if (sence_num != -1)
    {
      pdb_db_start_text (state);
      g_string_append_printf (state->buf, "%c) ", sence_num + 'a');
    }

  return TRUE;
}

/* Appends the text of an element, in a span of the given type */
static void
pdb_db_append_element_in_span (PdbDbParseState *state,
                               PdbDocElementNode *element,
                               PdbSpanType type,
                               GHashTable *names)
{
  GString *text = g_string_new (NULL);
  const char *name;
  PdbSpan *span = g_slice_new0 (PdbSpan);

  pdb_doc_append_element_text (element, text);
  pdb_trim_buf (text);

  /* Write the name of the code if it is known */
  name = names ? g_hash_table_lookup (names, text->str) : NULL;

  pdb_db_start_text (state);

  span->type = type;
  span->span_start = state->buf->len;
  g_string_append (state->buf, name ? name : text->str);
  span->span_length = state->buf->len - span->span_start;
  pdb_list_insert (state->spans.prev, &span->link);

  g_string_free (text, TRUE);
}

/* A work of the bibliography: its title, its author's surname and its
 * address, each NULL if ReVo doesn't give it */
typedef struct
{
  char *title;
  char *author;
  char *url;
} PdbDbWork;

static void
pdb_db_work_free (PdbDbWork *work)
{
  g_free (work->title);
  g_free (work->author);
  g_free (work->url);
  g_slice_free (PdbDbWork, work);
}

/* The first element of the given name in a node, at any depth */
static PdbDocElementNode *
pdb_db_find_element (PdbDocNode *node,
                     const char *name)
{
  PdbDocNode *child;

  for (child = node->first_child; child; child = child->next)
    {
      PdbDocElementNode *found;

      if (child->type != PDB_DOC_NODE_TYPE_ELEMENT)
        continue;

      if (!strcmp (((PdbDocElementNode *) child)->name, name))
        return (PdbDocElementNode *) child;

      if ((found = pdb_db_find_element (child, name)))
        return found;
    }

  return NULL;
}

/* The trimmed text of an element, or NULL if there is none */
static char *
pdb_db_get_element_text (PdbDocElementNode *element)
{
  GString *text;

  if (element == NULL)
    return NULL;

  text = g_string_new (NULL);
  pdb_doc_append_element_text (element, text);
  pdb_trim_buf (text);

  if (text->len == 0)
    {
      g_string_free (text, TRUE);
      return NULL;
    }

  return g_string_free (text, FALSE);
}

/* Reads the works of cfg/bibliogr.xml: <vrk mll="F"><aut><a>L. L.</a>
 * <n>Zamenhof</n></aut><tit>Fundamento de Esperanto</tit>…</vrk> */
static GHashTable *
pdb_db_load_works (PdbRevo *revo)
{
  GHashTable *works =
    g_hash_table_new_full (g_str_hash,
                           g_str_equal,
                           g_free,
                           (GDestroyNotify) pdb_db_work_free);
  GError *error = NULL;
  PdbDoc *doc;
  PdbDocNode *node;

  doc = pdb_doc_load (revo, "cfg/bibliogr.xml", &error);

  if (doc == NULL)
    {
      fprintf (stderr, "cfg/bibliogr.xml: %s\n", error->message);
      g_error_free (error);
      return works;
    }

  for (node = pdb_doc_get_root (doc)->node.first_child;
       node;
       node = node->next)
    {
      PdbDocElementNode *element = (PdbDocElementNode *) node;
      PdbDocElementNode *aut;
      PdbDbWork *work;
      const char *code;

      if (node->type != PDB_DOC_NODE_TYPE_ELEMENT ||
          strcmp (element->name, "vrk") ||
          (code = pdb_doc_get_attribute (element, "mll")) == NULL)
        continue;

      work = g_slice_new0 (PdbDbWork);
      work->title =
        pdb_db_get_element_text (pdb_doc_get_child_element (node, "tit"));
      /* The surname if it is marked, "Zamenhof", otherwise the whole
       * name, "Henri Vallienne" */
      aut = pdb_doc_get_child_element (node, "aut");
      if (aut)
        {
          work->author =
            pdb_db_get_element_text (pdb_db_find_element (&aut->node, "n"));
          if (work->author == NULL)
            work->author = pdb_db_get_element_text (aut);
        }
      work->url =
        pdb_db_get_element_text (pdb_doc_get_child_element (node, "url"));

      g_hash_table_insert (works, g_strdup (code), work);
    }

  pdb_doc_free (doc);

  return works;
}

/* The number of an address in assets/sources.txt, from 1 */
static int
pdb_db_get_source_url_number (PdbDb *db,
                              const char *url)
{
  gpointer number = g_hash_table_lookup (db->source_url_numbers, url);

  if (number == NULL)
    {
      char *copy;

      /* The number is written on 16 bits */
      if (db->source_urls->len >= G_MAXUINT16)
        {
          fprintf (stderr, "too many addresses of sources, \"%s\" left out\n", url);
          return 0;
        }

      copy = g_strdup (url);

      g_ptr_array_add (db->source_urls, copy);
      number = GINT_TO_POINTER (db->source_urls->len);
      g_hash_table_insert (db->source_url_numbers, copy, number);
    }

  return GPOINTER_TO_INT (number);
}

/* A part of a source as it is shown: without the spaces, commas or
 * semicolons around it ("P. Fischer-Kotowski,"); NULL if nothing is left,
 * if it is only a dash (ReVo's "<aut>-</aut>"), or if it is an address
 * written as a title. Takes the string. */
static char *
pdb_db_clean_source_part (char *text)
{
  char *start, *end;

  if (text == NULL)
    return NULL;

  start = text;
  while (*start && (g_ascii_isspace (*start) || strchr (",;:", *start)))
    start++;
  end = start + strlen (start);
  while (end > start && (g_ascii_isspace (end[-1]) || strchr (",;:", end[-1])))
    end--;
  *end = '\0';
  memmove (text, start, end - start + 1);

  if (text[strspn (text, "-\xe2\x80\x93\x94 ")] == '\0' ||
      g_str_has_prefix (text, "http://") ||
      g_str_has_prefix (text, "https://"))
    {
      g_free (text);
      return NULL;
    }

  return text;
}

/* An author without the translator ReVo names with them: "P. Kuusberg,
 * trad. A. Lekko", "H. Sienkiewicz trad. L. Zamenhof", "A. Mickiewicz tr.
 * …"; NULL if it is only a translator, "trad. I. Chiussi". Takes the
 * string. */
static char *
pdb_db_without_translator (char *author)
{
  static const char * const marks[] =
    { ", tr", ", Tr", " trad.", " trad ", " Trad.", " tr." };
  char *cut = NULL;
  int i;

  if (author == NULL)
    return NULL;

  if (g_ascii_strncasecmp (author, "trad", 4) == 0 ||
      g_ascii_strncasecmp (author, "tr.", 3) == 0)
    {
      g_free (author);
      return NULL;
    }

  for (i = 0; i < G_N_ELEMENTS (marks); i++)
    {
      char *found = strstr (author, marks[i]);

      if (found && (cut == NULL || found < cut))
        cut = found;
    }

  if (cut)
    *cut = '\0';

  return pdb_db_clean_source_part (author);
}

/* The work of the bibliography with this abbreviation, also with
 * another case when it has none ("Prv" for "PrV"); the first of them in
 * the order of the codes, so that the choice is always the same */
static PdbDbWork *
pdb_db_find_work (PdbDb *db,
                  const char *code)
{
  PdbDbWork *work = g_hash_table_lookup (db->work_details, code);
  GHashTableIter iter;
  gpointer key, value;
  const char *found = NULL;

  if (work)
    return work;

  g_hash_table_iter_init (&iter, db->work_details);
  while (g_hash_table_iter_next (&iter, &key, &value))
    if (!g_ascii_strcasecmp (key, code) &&
        (found == NULL || strcmp (key, found) < 0))
      {
        found = key;
        work = value;
      }

  return work;
}

/* The mark that ends the example after its source, "!" in "<fnt>…</fnt>!",
 * also after the example when the source ends it ("…</fnt></ekz>."): it
 * is the example's own and goes before the source. Returns the text node
 * where it is, and the mark. */
static PdbDocNode *
pdb_db_find_mark_after_source (PdbDocElementNode *element,
                               char *mark)
{
  PdbDocNode *node = element->node.next;
  int level;

  for (level = 0; level < 2; level++)
    {
      while (node && node->type == PDB_DOC_NODE_TYPE_TEXT)
        {
          PdbDocTextNode *text = (PdbDocTextNode *) node;
          int i = 0;

          while (i < text->len && g_ascii_isspace (text->data[i]))
            i++;

          if (i < text->len)
            {
              if (!strchr (".!?", text->data[i]))
                return NULL;
              *mark = text->data[i];
              return node;
            }

          node = node->next;
        }

      if (node)
        return NULL;

      /* The source ends its example: the text after the example */
      node = element->node.parent;
      if (node == NULL ||
          strcmp (((PdbDocElementNode *) node)->name, "ekz"))
        return NULL;
      node = node->next;
    }

  return NULL;
}

/* Whether an element is in a remark */
static gboolean
pdb_db_in_remark (PdbDocElementNode *element)
{
  PdbDocNode *node;

  for (node = element->node.parent; node; node = node->parent)
    if (!strcmp (((PdbDocElementNode *) node)->name, "rim"))
      return TRUE;

  return FALSE;
}

/* Whether a text ends with the mark of the end of a sentence */
static gboolean
pdb_db_ends_with_mark (GString *buf)
{
  return (buf->len > 0 && strchr (".!?", buf->str[buf->len - 1])) ||
    g_str_has_suffix (buf->str, "\xe2\x80\xa6");
}

/* Writes the source of an example after it, " — Zamenhof, Fundamento de
 * Esperanto", in a span of its own (PDB_SPAN_SOURCE) that starts at the
 * dash, so that applications can show it small, or not at all. The author
 * is the surname from the bibliography, or the name the source gives,
 * without its translator; the work is the title from the bibliography, or
 * the one the source gives; the page or the year are left out. Without
 * them, the source's own text, or the text of its address. A source right
 * after another one is joined to it: "— A; B". The example's mark after
 * the source ("!") goes before it, its separator before it (";") after
 * it. data1 is the number of its address in assets/sources.txt: the
 * source's own, eg. an article of Monato, otherwise the work's; 0 if
 * there is none. */
static void
pdb_db_add_example_source (PdbDb *db,
                           PdbDbParseState *state,
                           PdbDocElementNode *element)
{
  PdbDocElementNode *bib, *url_element;
  PdbDbWork *work = NULL;
  char *bib_code, *author, *title;
  const char *url = NULL;
  PdbDocNode *mark_node = NULL;
  PdbSpan *span;
  char punctuation = 0, mark = 0;
  gboolean joined;
  int end;

  bib = pdb_doc_get_child_element (&element->node, "bib");
  bib_code = pdb_db_get_element_text (bib);
  if (bib_code)
    work = pdb_db_find_work (db, bib_code);

  author = pdb_db_without_translator
    (pdb_db_get_element_text (pdb_doc_get_child_element (&element->node,
                                                         "aut")));
  if (author == NULL && work && work->author)
    author = pdb_db_clean_source_part (g_strdup (work->author));

  if (work && work->title)
    title = pdb_db_clean_source_part (g_strdup (work->title));
  else
    title = pdb_db_clean_source_part
      (pdb_db_get_element_text (pdb_doc_get_child_element (&element->node,
                                                           "vrk")));

  /* Only web addresses: ReVo has a few without "http", empty, or a
   * name in their place, and some with spaces */
  if ((url_element = pdb_db_find_element (&element->node, "url")))
    url = pdb_doc_get_attribute (url_element, "ref");
  if (url && !g_str_has_prefix (url, "http://") &&
      !g_str_has_prefix (url, "https://"))
    url = NULL;
  if (url == NULL && work && work->url &&
      g_str_has_prefix (work->url, "http"))
    url = work->url;

  /* A source the bibliography doesn't know, "<fnt>PIV2</fnt>",
   * "<bib>Vikio</bib>", or only an address: as it is written */
  if (author == NULL && title == NULL)
    title = pdb_db_clean_source_part
      (bib_code ? g_strdup (bib_code) :
       url_element ? pdb_db_get_element_text (url_element) :
       pdb_db_get_element_text (element));
  g_free (bib_code);

  if (author == NULL && title == NULL)
    {
      /* No space left before the mark that follows: "abandono." */
      if (state->queued_space == PDB_DB_QUEUED_SPACE_TYPE_SPACE)
        state->space_before_skipped = TRUE;
      return;
    }

  end = state->buf->len;
  while (end > 0 && g_ascii_isspace (state->buf->str[end - 1]))
    end--;
  joined = state->last_source_end >= 0 && end == state->last_source_end;

  if (!joined)
    {
      /* A separator that ReVo puts before the source, "…samtempe);
       * <fnt>", goes after it, unless a span ends with it */
      if (end > 0 && strchr (";,", state->buf->str[end - 1]))
        {
          PdbSpan *other;

          punctuation = state->buf->str[end - 1];

          pdb_list_for_each (other, &state->spans, link)
            if (other->span_length > 0 &&
                other->span_start + other->span_length >= end)
              punctuation = 0;

          if (punctuation)
            g_string_truncate (state->buf, end - 1);
        }
      /* The example's own mark, after the source in ReVo, before it;
       * left out if the example already ends with one ("junularo!
       * <fnt>…</fnt>."). Not in a remark, where the example is in a
       * sentence that goes on after it */
      else if (!pdb_db_in_remark (element) &&
               (mark_node = pdb_db_find_mark_after_source (element, &mark)))
        {
          g_string_truncate (state->buf, end);
          if (!pdb_db_ends_with_mark (state->buf))
            g_string_append_c (state->buf, mark);
          state->mark_moved_from = mark_node;
        }

      state->queued_space = PDB_DB_QUEUED_SPACE_TYPE_SPACE;
    }
  else
    {
      g_string_truncate (state->buf, end);
      state->queued_space = PDB_DB_QUEUED_SPACE_TYPE_NONE;
    }

  pdb_db_start_text (state);

  span = g_slice_new0 (PdbSpan);
  span->type = PDB_SPAN_SOURCE;
  span->span_start = state->buf->len;
  span->data1 = url ? pdb_db_get_source_url_number (db, url) : 0;

  /* The dash with the author: a no-break space between them */
  g_string_append (state->buf, joined ? "; " : "\xe2\x80\x94\xc2\xa0");
  if (author)
    g_string_append (state->buf, author);
  if (author && title)
    g_string_append (state->buf, ", ");
  if (title)
    g_string_append (state->buf, title);

  span->span_length = state->buf->len - span->span_start;
  pdb_list_insert (state->spans.prev, &span->link);
  state->last_source_end = state->buf->len;
  /* No space left before a mark that follows: "Monato." */
  state->space_before_skipped = TRUE;

  if (punctuation)
    g_string_append_c (state->buf, punctuation);

  g_free (author);
  g_free (title);
}

static gboolean
pdb_db_handle_fnt (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  /* The sources are only kept in the remarks, whose text usually
   * refers to them, eg. "Laŭ <fnt><bib>BL</bib></fnt>: …". The work is
   * written with its title from the bibliography if it has an
   * abbreviation, otherwise as "author, title". Its other details, such
   * as the page, are left out. */
  PdbDocElementNode *bib, *aut, *vrk;
  PdbDocElementNode *parent = (PdbDocElementNode *) element->node.parent;

  if (parent && !strcmp (parent->name, "ekz"))
    {
      pdb_db_add_example_source (db, state, element);
      state->skip_children = TRUE;
      return TRUE;
    }

  bib = pdb_doc_get_child_element (&element->node, "bib");
  aut = pdb_doc_get_child_element (&element->node, "aut");
  vrk = pdb_doc_get_child_element (&element->node, "vrk");

  if (bib)
    {
      pdb_db_append_element_in_span (state, bib, PDB_SPAN_ITALIC, db->works);
    }
  else
    {
      if (aut)
        {
          GString *text = g_string_new (NULL);

          pdb_doc_append_element_text (aut, text);
          pdb_trim_buf (text);
          pdb_db_start_text (state);
          g_string_append (state->buf, text->str);
          g_string_free (text, TRUE);

          if (vrk)
            g_string_append (state->buf, ", ");
        }

      if (vrk)
        pdb_db_append_element_in_span (state, vrk, PDB_SPAN_ITALIC, NULL);
    }

  state->skip_children = TRUE;

  return TRUE;
}

/* Writes the name of a field instead of its code, eg. "matematiko"
 * instead of "MAT", in a span so that applications can show it
 * differently */
static void
pdb_db_add_field (PdbDb *db,
                  PdbDbParseState *state,
                  PdbDocElementNode *element)
{
  GString *code = g_string_new (NULL);
  const char *name;

  pdb_doc_append_element_text (element, code);
  pdb_trim_buf (code);

  PdbSpan *span;
  int i;

  name = g_hash_table_lookup (db->fields, code->str);

  pdb_db_start_text (state);
  span = pdb_db_start_span (state, PDB_SPAN_FIELD);
  g_string_append (state->buf, name ? name : code->str);

  /* The number of its code, for its icon */
  for (i = 0; i < db->field_codes->len; i++)
    if (!strcmp (g_ptr_array_index (db->field_codes, i), code->str))
      span->data1 = i + 1;

  g_string_free (code, TRUE);
}

static gboolean
pdb_db_handle_uzo (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  char **att;

  for (att = element->atts; att[0]; att += 2)
    {
      if (!strcmp (att[0], "tip"))
        {
          if (!strcmp (att[1], "stl"))
            state->skip_children =
              pdb_db_add_replacement_contents (state,
                                               element,
                                               pdb_db_styles,
                                               G_N_ELEMENTS (pdb_db_styles));
          else if (!strcmp (att[1], "fak"))
            {
              pdb_db_add_field (db, state, element);
              state->skip_children = TRUE;
            }

          break;
        }
    }

  return TRUE;
}

/* The kind of an abbreviation, from the kod attribute of its <mlg> tag,
 * as stored in the data1 of its span (see PDB_SPAN_ABBREVIATION). ReVo
 * writes some codes in several ways ("ISO-3166", "IS-3166", "ISO 3166",
 * "ISO-3166-1"; "ISO-4217", "4217"; "Co" for IUPAC once) */
static int
pdb_db_get_abbreviation_kind (const char *kod)
{
  if (kod == NULL)
    return 0;
  if (strstr (kod, "3166"))
    return 1;
  if (!strcmp (kod, "IUPAC") || !strcmp (kod, "Co"))
    return 2;
  if (!strcmp (kod, "SI") || !strcmp (kod, "CGS"))
    return 3;
  if (strstr (kod, "4217"))
    return 4;
  if (strstr (kod, "639"))
    return 5;
  if (!strcmp (kod, "E"))
    return 6;
  return 0;
}

/* Writes an abbreviation in a span of its own, with its kind, so that
 * applications can tell what it is: "DE" alone says little */
static gboolean
pdb_db_handle_mlg (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  GString *text = g_string_new (NULL);
  PdbSpan *abbreviation;

  pdb_doc_append_element_text (element, text);
  pdb_trim_buf (text);

  pdb_db_start_text (state);
  abbreviation = pdb_db_start_span (state, PDB_SPAN_ABBREVIATION);
  abbreviation->data1 =
    pdb_db_get_abbreviation_kind (pdb_doc_get_attribute (element, "kod"));
  g_string_append (state->buf, text->str);

  g_string_free (text, TRUE);
  state->skip_children = TRUE;

  return TRUE;
}

static gboolean
pdb_db_handle_ekz (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  /* The example is in an italic span, from the table of the
   * elements. It is also in a span of its own over the same text, so
   * that applications can tell the examples from the other italics. */
  pdb_db_start_span (state, PDB_SPAN_EXAMPLE);

  return TRUE;
}

static gboolean
pdb_db_handle_vspec (PdbDb *db,
                     PdbDbParseState *state,
                     PdbDocElementNode *element,
                     PdbSpan *span,
                     GError **error)
{
  state->skip_children =
    pdb_db_add_replacement_contents (state,
                                     element,
                                     pdb_db_verb_types,
                                     G_N_ELEMENTS (pdb_db_verb_types));

  return TRUE;
}

static gboolean
pdb_db_handle_var (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  /* The forms of a headword are separated by commas, which ReVo
   * mostly writes before a variant ("evolu<tld/>o, <var>…"), but not
   * always ("sam<tld/>ano <var><kap>kun<tld/>ano…" in land.xml): add the
   * missing one, so that the header reads "samlandano, kunlandano" */
  PdbDocElementNode *parent = (PdbDocElementNode *) element->node.parent;
  int end = state->buf->len;

  if (parent == NULL || strcmp (parent->name, "kap"))
    return TRUE;

  while (end > 0 && g_ascii_isspace (state->buf->str[end - 1]))
    end--;

  if (end > 0 && state->buf->str[end - 1] != ',')
    {
      g_string_truncate (state->buf, end);
      g_string_append_c (state->buf, ',');
    }

  return TRUE;
}

/* A link to a page outside the dictionary, in the text: its address,
 * once the entities are expanded ("&PMEG;…"), when it is one */
static gboolean
pdb_db_handle_url (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocElementNode *element,
                   PdbSpan *span,
                   GError **error)
{
  const char *url = pdb_doc_get_attribute (element, "ref");

  if (url && (g_str_has_prefix (url, "http://") ||
              g_str_has_prefix (url, "https://")))
    span->data1 = pdb_db_get_source_url_number (db, url);

  return TRUE;
}

static PdbDbElementSpan
pdb_db_element_spans[] =
  {
    { .name = "var", .type = PDB_SPAN_NONE, .handler = pdb_db_handle_var },
    { .name = "ofc", .type = PDB_SPAN_SUPERSCRIPT },
    /* "10<sup>12</sup>", "CH<sub>3</sub>" */
    { .name = "sup", .type = PDB_SPAN_SUPERSCRIPT },
    { .name = "sub", .type = PDB_SPAN_SUBSCRIPT },
    /* A usage marked wrong, and a word struck through: "edziĝi kun al" */
    { .name = "mis", .type = PDB_SPAN_MISUSE },
    { .name = "ts", .type = PDB_SPAN_STRIKETHROUGH },
    { .name = "ctl", .type = PDB_SPAN_NONE, .handler = pdb_db_handle_ctl },
    /* In formulas, as ReVo shows them: "x" in italics, the sets and
     * spaces in bold, "E" */
    { .name = "k", .type = PDB_SPAN_ITALIC },
    { .name = "g", .type = PDB_SPAN_BOLD },
    { .name = "ekz", .type = PDB_SPAN_ITALIC, .handler = pdb_db_handle_ekz },
    {
      .name = "subdrv",
      .type = PDB_SPAN_NONE,
      .handler = pdb_db_handle_subdrv,
      .paragraph = TRUE
    },
    {
      .name = "snc",
      .type = PDB_SPAN_NONE,
      .handler = pdb_db_handle_snc,
      .paragraph = TRUE
    },
    {
      .name = "subsnc",
      .type = PDB_SPAN_NONE,
      .handler = pdb_db_handle_subsnc,
      .paragraph = TRUE
    },
    {
      .name = "ref",
      .type = PDB_SPAN_NONE,
      .handler = pdb_db_handle_ref
    },
    {
      .name = "refgrp",
      .type = PDB_SPAN_NONE,
      .handler = pdb_db_handle_refgrp
    },
    {
      .name = "rim",
      .type = PDB_SPAN_NOTE,
      .handler = pdb_db_handle_rim,
      .paragraph = TRUE
    },
    { .name = "em", .type = PDB_SPAN_BOLD, },
    { .name = "aut", .type = PDB_SPAN_NONE, .handler = pdb_db_handle_aut },
    { .name = "uzo", .type = PDB_SPAN_NONE, .handler = pdb_db_handle_uzo },
    { .name = "mlg", .type = PDB_SPAN_NONE, .handler = pdb_db_handle_mlg },
    { .name = "fnt", .type = PDB_SPAN_NONE, .handler = pdb_db_handle_fnt },
    {
      .name = "vspec",
      .type = PDB_SPAN_GRAMMAR,
      .handler = pdb_db_handle_vspec
    },
    { .name = "trd", .type = PDB_SPAN_ITALIC, },
    { .name = "url", .type = PDB_SPAN_LINK, .handler = pdb_db_handle_url },
  };

static gboolean
pdb_db_should_ignore_spannable_tag (PdbDocElementNode *element)
{
  /* Skip the sources, except in the remarks, whose text usually refers
   * to them, and in the examples, after which they are written */
  if (!strcmp (element->name, "fnt"))
    {
      PdbDocElementNode *parent;

      parent = (PdbDocElementNode *) element->node.parent;

      return parent == NULL || (strcmp (parent->name, "rim") &&
                                strcmp (parent->name, "ekz"));
    }

  /* Skip adm tags and pictures */
  if (!strcmp (element->name, "adm") ||
      !strcmp (element->name, "bld") ||
      /* Ignore translation groups. They are handled separately */
      !strcmp (element->name, "trdgrp"))
    return TRUE;

  /* We want to ignore kap tags within a drv because they are handled
   * separately and parsed into the section header. However, a kap tag
   * can be found within an enclosing kap tag if it is a variation. In
   * that case we do want it to be processed because the section
   * header will be a comma-separated list. To detect this situation
   * we'll just check if any parents are a kap tag */
  if (!strcmp (element->name, "kap"))
    {
      PdbDocElementNode *parent;

      for (parent = (PdbDocElementNode *) element->node.parent;
           parent;
           parent = (PdbDocElementNode *) parent->node.parent)
        if (!strcmp (parent->name, "kap"))
          goto embedded_kap;

      /* If we make it here, the kap is a toplevel kap */
      return TRUE;
    }
 embedded_kap:

  /* trd tags are handled separately unless they are a direct child of
   * a definition. For example, in the article for ĉerizo the latin
   * name for the fruit is mentioned directly in the article as a
   * translation. In that case we do want to show the contents of the
   * tag. This is also how the XSL templates for ReVo filter the
   * tag. */
  if (!strcmp (element->name, "trd"))
    {
      PdbDocElementNode *parent;

      parent = (PdbDocElementNode *) element->node.parent;

      return parent == NULL || strcmp (parent->name, "dif");
    }

  return FALSE;
}

static gboolean
pdb_db_keeps_space_before (const GString *buf,
                           char mark,
                           const char *next,
                           const char *end);

/* Whether a text is quoted or isn't Reta Vortaro's own prose: an
 * example, but not what Reta Vortaro adds to it, "(vd emancipi)"; its
 * source, the initials of the author of a remark, "pp", an
 * abbreviation marked as such, a note to the editors */
static gboolean
pdb_db_is_quoted (PdbDocNode *node)
{
  gboolean added = FALSE;

  for (node = node->parent; node; node = node->parent)
    {
      const char *name;

      if (node->type != PDB_DOC_NODE_TYPE_ELEMENT)
        continue;

      name = ((PdbDocElementNode *) node)->name;
      if (!strcmp (name, "klr"))
        added = TRUE;
      else if (!strcmp (name, "ekz"))
        {
          if (!added)
            return TRUE;
        }
      else if (!strcmp (name, "fnt") ||
               !strcmp (name, "aut") ||
               !strcmp (name, "bib") ||
               !strcmp (name, "mlg") ||
               !strcmp (name, "adm") ||
               !strcmp (name, "trd") ||
               !strcmp (name, "trdgrp"))
        return TRUE;
    }

  return FALSE;
}

/* Whether a byte is part of a word: a letter, a digit, or a byte of a
 * letter such as "ĉ" */
static gboolean
pdb_db_is_word_byte (char c)
{
  return g_ascii_isalnum (c) || (c & 0x80) || c == '-';
}

/* The character after a text node, as far as a word goes: the first of
 * the next text, which the parser may have cut anywhere, "sk|alo"; a
 * letter for the headword, "sk<tld/>"; otherwise a space */
static char
pdb_db_next_character (PdbDocNode *node)
{
  PdbDocNode *next = node->next;

  if (next == NULL)
    return ' ';
  if (next->type == PDB_DOC_NODE_TYPE_TEXT)
    return ((PdbDocTextNode *) next)->len > 0 ? ((PdbDocTextNode *) next)->data[0] : ' ';
  if (next->type == PDB_DOC_NODE_TYPE_ELEMENT &&
      !strcmp (((PdbDocElementNode *) next)->name, "tld"))
    return 'a';
  return ' ';
}

/* The abbreviation to spell out that starts at p, "ks", "Kp", as a
 * whole word, with what it stands for in expansion and its length in
 * length, a full stop of its own included; FALSE if there is none */
static gboolean
pdb_db_find_spelled_out (PdbDb *db,
                         PdbDbParseState *state,
                         PdbDocNode *node,
                         const char *p,
                         const char *end,
                         GString *expansion,
                         size_t *length)
{
  int i;

  /* At the start of a word */
  if (state->queued_space == PDB_DB_QUEUED_SPACE_TYPE_NONE &&
      state->buf->len > 0 &&
      (pdb_db_is_word_byte (state->buf->str[state->buf->len - 1]) ||
       state->buf->str[state->buf->len - 1] == '.'))
    return FALSE;

  for (i = 0; i < G_N_ELEMENTS (pdb_db_spelled_out); i++)
    {
      const PdbDbSpelledOut *entry = pdb_db_spelled_out + i;
      size_t len = strlen (entry->abbreviation);
      const char *full, *after;

      /* As it is written, or with its first letter in the other
       * case: "vd" for "Vd" */
      if (end - p < len ||
          (*p != entry->abbreviation[0] &&
           *p != (g_ascii_isupper (entry->abbreviation[0]) ?
                  g_ascii_tolower (entry->abbreviation[0]) :
                  g_ascii_toupper (entry->abbreviation[0]))) ||
          strncmp (p + 1, entry->abbreviation + 1, len - 1))
        continue;

      after = p + len;
      if (pdb_db_is_word_byte (after < end ? *after : pdb_db_next_character (node)))
        continue;

      full = g_hash_table_lookup (db->abbreviations, entry->abbreviation);
      if (full == NULL)
        continue;

      /* Its own full stop: always for some, otherwise when the
       * sentence goes on in lower case, "ks. kaj probable"; not before
       * a capital nor at the end of the text, "ks. La franca" */
      if (after < end && *after == '.')
        {
          const char *next = after + 1;

          while (next < end && *next == ' ')
            next++;

          if (entry->takes_stop ||
              (next < end && *next != '\n' &&
               !g_unichar_isupper (g_utf8_get_char_validated (next, end - next))))
            after++;
        }

      g_string_assign (expansion, full);
      if (g_ascii_isupper (*p))
        expansion->str[0] = g_ascii_toupper (expansion->str[0]);
      else
        expansion->str[0] = g_ascii_tolower (expansion->str[0]);
      *length = after - p;

      return TRUE;
    }

  return FALSE;
}

static gboolean
pdb_db_parse_node (PdbDb *db,
                   PdbDbParseState *state,
                   PdbDocNode *node,
                   GError **error)
{
  if (node->next)
    pdb_db_parse_push_node (state, node->next);

  switch (node->type)
    {
    case PDB_DOC_NODE_TYPE_ELEMENT:
      {
        PdbDocElementNode *element = (PdbDocElementNode *) node;

        if (!strcmp (element->name, "tld"))
          {
            pdb_db_start_text (state);
            pdb_db_append_tld (db, state->buf, element->atts);
          }
        else if (pdb_db_should_ignore_spannable_tag (element))
          {
            /* skip */
            if (state->queued_space == PDB_DB_QUEUED_SPACE_TYPE_SPACE)
              state->space_before_skipped = TRUE;
          }
        else if (element->node.first_child)
          {
            int i;

            state->skip_children = FALSE;

            for (i = 0; i < G_N_ELEMENTS (pdb_db_element_spans); i++)
              {
                const PdbDbElementSpan *elem_span =
                  pdb_db_element_spans + i;

                if (!strcmp (elem_span->name, element->name))
                  {
                    PdbSpan *span;

                    if (elem_span->paragraph)
                      {
                        /* Make sure there is a paragraph separator
                         * before and after the remark */
                        state->queued_space =
                          PDB_DB_QUEUED_SPACE_TYPE_PARAGRAPH;
                        pdb_db_parse_push_add_paragraph (state);
                      }

                    if (elem_span->type == PDB_SPAN_NONE)
                      span = NULL;
                    else
                      {
                        pdb_db_start_text (state);
                        span = pdb_db_start_span (state, elem_span->type);
                      }

                    if (elem_span->handler &&
                        !elem_span->handler (db,
                                             state,
                                             element,
                                             span,
                                             error))
                      return FALSE;

                    break;
                  }
              }

            if (!state->skip_children)
              pdb_db_parse_push_node (state,
                                      element->node.first_child);
          }
        else if (!strcmp (element->name, "sncref"))
          {
            if (!pdb_db_handle_sncref (db,
                                       state,
                                       element,
                                       error))
              return FALSE;
          }
      }
      break;

    case PDB_DOC_NODE_TYPE_TEXT:
      {
        PdbDocTextNode *text = (PdbDocTextNode *) node;
        const char *p, *end;
        gboolean quoted = pdb_db_is_quoted (node);
        GString *expansion = g_string_new (NULL);
        size_t length;

        for (p = text->data, end = text->data + text->len;
             p < end;
             p++)
          {
            /* The mark written before the source that this text follows */
            if (node == state->mark_moved_from)
              {
                if (g_ascii_isspace (*p))
                  continue;
                state->mark_moved_from = NULL;
                continue;
              }

            if (g_ascii_isspace (*p))
              {
                if (state->queued_space == PDB_DB_QUEUED_SPACE_TYPE_NONE)
                  state->queued_space = PDB_DB_QUEUED_SPACE_TYPE_SPACE;
              }
            else
              {
                /* Don't leave the space that was before a skipped
                 * element in front of a punctuation mark, eg. in
                 * “neĝo <fnt>…</fnt>.”; nor, from any element, a space
                 * before a comma, a colon or a closing bracket, eg. the
                 * line break at the end of “<refgrp>…\n</refgrp>:” */
                if (state->queued_space == PDB_DB_QUEUED_SPACE_TYPE_SPACE &&
                    ((strchr (",;:)", *p) && !pdb_db_keeps_space_before (state->buf, *p, p + 1, end)) ||
                     (state->space_before_skipped && strchr (".!?", *p))))
                  state->queued_space = PDB_DB_QUEUED_SPACE_TYPE_NONE;
                state->space_before_skipped = FALSE;

                /* "ks", "jc": spelled out, as the reader may not know
                 * them */
                if (!quoted &&
                    pdb_db_find_spelled_out (db, state, node, p, end, expansion, &length))
                  {
                    pdb_db_start_text (state);
                    g_string_append (state->buf, expansion->str);
                    p += length - 1;
                    continue;
                  }

                pdb_db_start_text (state);
                g_string_append_c (state->buf, *p);
              }
          }

        g_string_free (expansion, TRUE);
      }
      break;
    }

  return TRUE;
}

/* Whether a space stays before a mark: in a ratio, "1 : 2 400 000";
 * before the bracket that closes one opened with a space, "( ĉar se … )" */
static gboolean
pdb_db_keeps_space_before (const GString *buf,
                           char mark,
                           const char *next,
                           const char *end)
{
  if (mark == ':')
    {
      while (next < end && *next == ' ')
        next++;
      return (buf->len > 0 && g_ascii_isdigit (buf->str[buf->len - 1]) &&
              next < end && g_ascii_isdigit (*next));
    }

  if (mark == ')')
    {
      int depth = 0;

      for (int i = buf->len - 1; i >= 0; i--)
        {
          if (buf->str[i] == ')')
            depth++;
          else if (buf->str[i] == '(' && depth-- == 0)
            return i + 1 < buf->len && buf->str[i + 1] == ' ';
        }
    }

  return FALSE;
}

static gboolean
pdb_db_parse_spannable_string_upto (PdbDb *db,
                                    PdbDocElementNode *root_element,
                                    PdbDocNode *upto,
                                    PdbDbSpannableString *string,
                                    GError **error)
{
  PdbDbParseState state;

  state.stack = g_array_new (FALSE, FALSE, sizeof (PdbDbParseStackEntry));
  state.buf = g_string_new (NULL);
  state.queued_space = PDB_DB_QUEUED_SPACE_TYPE_NONE;
  state.string = string;
  state.copying = FALSE;
  state.skip_copy = FALSE;
  state.space_before_skipped = FALSE;
  state.mark_moved_from = NULL;
  state.last_source_end = -1;

  pdb_list_init (&state.spans);

  pdb_db_parse_push_node (&state, root_element->node.first_child);

  while (state.stack->len > 0)
    {
      PdbDbParseStackEntry this_entry =
        g_array_index (state.stack, PdbDbParseStackEntry, state.stack->len - 1);

      g_array_set_size (state.stack, state.stack->len - 1);

      switch (this_entry.type)
        {
        case PDB_DB_STACK_CLOSE_SPAN:
          this_entry.d.span->span_length =
            state.buf->len - this_entry.d.span->span_start;
          break;

        case PDB_DB_STACK_ADD_PARAGRAPH:
          state.queued_space = PDB_DB_QUEUED_SPACE_TYPE_PARAGRAPH;
          break;

        case PDB_DB_STACK_CLOSING_CHARACTER:
          if (state.skip_copy)
            break;
          /* A space queued at the end of the element goes after its
           * closing character: „ligas A al B“ por, not „… B “ por */
          if (state.queued_space == PDB_DB_QUEUED_SPACE_TYPE_SPACE)
            {
              state.queued_space = PDB_DB_QUEUED_SPACE_TYPE_NONE;
              pdb_db_start_text (&state);
              g_string_append_unichar (state.buf, this_entry.d.character);
              state.queued_space = PDB_DB_QUEUED_SPACE_TYPE_SPACE;
            }
          else
            {
              pdb_db_start_text (&state);
              g_string_append_unichar (state.buf, this_entry.d.character);
            }
          break;

        case PDB_DB_STACK_END_COPY:
          state.copying = FALSE;
          state.skip_copy = FALSE;
          break;

        case PDB_DB_STACK_NODE:
          if (this_entry.d.node == upto)
            goto done;
          if (state.copying &&
              this_entry.d.node->type == PDB_DOC_NODE_TYPE_ELEMENT &&
              !strcmp (((PdbDocElementNode *) this_entry.d.node)->name,
                       "ekz"))
            state.skip_copy = TRUE;
          if (state.skip_copy)
            break;
          if (!pdb_db_parse_node (db, &state, this_entry.d.node, error))
            goto error;
          break;
        }
    }

 done:
  string->length = state.buf->len;
  string->text = g_string_free (state.buf, FALSE);
  pdb_list_init (&string->spans);
  pdb_list_insert_list (&string->spans, &state.spans);

  g_array_free (state.stack, TRUE);

  return TRUE;

 error:
  g_array_free (state.stack, TRUE);
  g_string_free (state.buf, TRUE);
  pdb_span_free_list (&state.spans);

  return FALSE;
}

static gboolean
pdb_db_parse_spannable_string (PdbDb *db,
                               PdbDocElementNode *root_element,
                               PdbDbSpannableString *string,
                               GError **error)
{
  return pdb_db_parse_spannable_string_upto (db,
                                             root_element,
                                             NULL, /* upto */
                                             string,
                                             error);
}

static void
pdb_db_add_kap_index (PdbDb *db,
                      PdbDocElementNode *kap,
                      PdbDbArticle *article,
                      PdbDbSection *section)
{
  GString *buf = g_string_new (NULL);
  const char *display_name, *real_name;
  PdbDocNode *node;
  PdbDbReference entry;

  for (node = kap->node.first_child; node; node = node->next)
    switch (node->type)
      {
      case PDB_DOC_NODE_TYPE_TEXT:
        {
          PdbDocTextNode *text_node = (PdbDocTextNode *) node;
          g_string_append_len (buf, text_node->data, text_node->len);
        }
        break;

      case PDB_DOC_NODE_TYPE_ELEMENT:
        {
          PdbDocElementNode *element = (PdbDocElementNode *) node;

          if (!strcmp (element->name, "tld") ||
              !strcmp (element->name, "rad"))
            pdb_db_append_tld (db, buf, element->atts);
          else if (!strcmp (element->name, "var"))
            {
              PdbDocElementNode *child_element;

              /* If the kap contains a variation with an embedded kap
               * then we'll recursively add that to the index too */
              child_element =
                pdb_doc_get_child_element (&element->node, "kap");

              if (child_element)
                pdb_db_add_kap_index (db, child_element, article, section);
            }
        }
        break;
      }

  entry.type = PDB_DB_REFERENCE_TYPE_DIRECT;
  entry.d.direct.article = article;
  entry.d.direct.section = section;

  pdb_trim_buf (buf);

  /* If the <kap> tag contains variations then it will be a list with
   * commas in. The variations are ignored in the index text so it
   * will end up as an empty blob of trailing commas. Lets trim
   * these */
  while (buf->len > 0 &&
         (buf->str[buf->len - 1] == ' ' ||
          buf->str[buf->len - 1] == ','))
    g_string_set_size (buf, buf->len - 1);

  if (buf->str[0] == '-' &&
      buf->str[1])
    {
      real_name = buf->str + 1;
      display_name = buf->str;
    }
  else
    {
      real_name = buf->str;
      display_name = NULL;
    }

  pdb_db_add_index_entry (db,
                          "eo", /* language code */
                          real_name,
                          display_name,
                          &entry);

  g_string_free (buf, TRUE);
}

/* A copy of a string with all of a piece replaced */
static char *
pdb_db_replace_all (const char *text,
                    const char *piece,
                    const char *by)
{
  char **parts = g_strsplit (text, piece, -1);
  char *result = g_strjoinv (by, parts);

  g_strfreev (parts);

  return result;
}

static PdbDbSection *
pdb_db_parse_drv (PdbDb *db,
                  PdbDbArticle *article,
                  PdbDocElementNode *root_node,
                  GError **error)
{
  PdbDocElementNode *kap;
  PdbDbSection *section;
  gboolean result = TRUE;

  kap = pdb_doc_get_child_element (&root_node->node, "kap");

  if (kap == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("<drv> tag found with no <kap>"));
      return NULL;
    }

  section = pdb_db_section_new ("eo");

  pdb_db_add_kap_index (db, kap, article, section);

  if (pdb_db_parse_spannable_string (db, kap, &section->title, error))
    {
      if (!pdb_db_parse_spannable_string (db,
                                          root_node,
                                          &section->text,
                                          error))
        result = FALSE;

      if (!result)
        pdb_db_destroy_spannable_string (&section->title);
    }
  else
    result = FALSE;

  if (!result)
    {
      g_slice_free (PdbDbSection, section);
      return NULL;
    }
  else
    return section;
}

static gboolean
pdb_db_add_drv (PdbDb *db,
                PdbDbArticle *article,
                PdbDocElementNode *element,
                GQueue *sections,
                GError **error)
{
  PdbDbSection *section;
  PdbDbReference ref;

  section = pdb_db_parse_drv (db, article, element, error);

  if (section == NULL)
    return FALSE;

  g_queue_push_tail (sections, section);

  ref.type = PDB_DB_REFERENCE_TYPE_DIRECT;
  ref.d.direct.article = article;
  ref.d.direct.section = section;

  if (!pdb_db_find_translations_recursive (db,
                                           element,
                                           &ref,
                                           error))
    return FALSE;

  return TRUE;
}

static gboolean
pdb_db_parse_subart (PdbDb *db,
                     PdbDbArticle *article,
                     PdbDocElementNode *root_node,
                     GQueue *sections,
                     GError **error)
{
  PdbDocNode *node;
  PdbDocElementNode *drv;
  PdbDbSection *section;
  PdbDbReference ref;
  GString *buf;
  int subart_num;

  subart_num = pdb_db_get_element_num (root_node);

  if (subart_num == -1)
    /* It probably doesn't make any sense to have a <subart> on its
     * own, but the article for '-il' seems to do it anyway */
    subart_num = 0;

  section = pdb_db_section_new ("eo");

  buf = g_string_new (NULL);
  pdb_roman_to_text_append (subart_num + 1, buf);
  g_string_append (buf, ".");
  section->title.length = buf->len;
  section->title.text = g_string_free (buf, FALSE);
  pdb_list_init (&section->title.spans);

  ref.type = PDB_DB_REFERENCE_TYPE_DIRECT;
  ref.d.direct.article = article;
  ref.d.direct.section = section;

  /* The subart is assumed to be a spannable string followed by a list
   * of zero or more <drv>s */
  drv = pdb_doc_get_child_element (&root_node->node, "drv");

  if (!pdb_db_parse_spannable_string_upto (db,
                                           root_node,
                                           &drv->node, /* upto */
                                           &section->text,
                                           error))
    {
      pdb_db_destroy_spannable_string (&section->title);
      g_slice_free (PdbDbSection, section);
      return FALSE;
    }

  g_queue_push_tail (sections, section);

  if (!pdb_db_find_translations_recursive_skip (db,
                                                root_node,
                                                "drv",
                                                &ref,
                                                error))
    return FALSE;

  for (node = &drv->node; node; node = node->next)
    switch (node->type)
      {
      case PDB_DOC_NODE_TYPE_ELEMENT:
        {
          PdbDocElementNode *element = (PdbDocElementNode *) node;

          if (!strcmp (element->name, "drv"))
            {
              if (!pdb_db_add_drv (db,
                                   article,
                                   element,
                                   sections,
                                   error))
                return FALSE;
            }
          else if (strcmp (element->name, "adm") &&
                   strcmp (element->name, "trd") &&
                   strcmp (element->name, "trdgrp") &&
                   /* FIXME - this probably shouldn't strip out
                    * <rim> tags here */
                   strcmp (element->name, "rim"))
            {
              g_set_error (error,
                           PDB_ERROR,
                           PDB_ERROR_BAD_FORMAT,
                           _("<%s> tag found in <subart> that has a <drv>"),
                           element->name);
              return FALSE;
            }
        }
        break;

      case PDB_DOC_NODE_TYPE_TEXT:
        {
          PdbDocTextNode *text = (PdbDocTextNode *) node;
          int i;

          for (i = 0; i < text->len; i++)
            if (!g_ascii_isspace (text->data[i]))
              {
                g_set_error (error,
                             PDB_ERROR,
                             PDB_ERROR_BAD_FORMAT,
                             _("Unexpected bare text in <subart> that "
                               "has a <drv>"));
                return FALSE;
              }
        }
        break;
      }

  return TRUE;
}

static gboolean
pdb_db_parse_toplevel_dif (PdbDb *db,
                           PdbDbArticle *article,
                           PdbDocElementNode *root_node,
                           GQueue *sections,
                           GError **error)
{
  PdbDbSection *section;

  section = pdb_db_section_new ("eo");

  section->title.text = g_strdup ("Resumo");
  section->title.length = strlen (section->title.text);
  pdb_list_init (&section->title.spans);

  /* The toplevel definition should directly be a spannable string */
  if (!pdb_db_parse_spannable_string (db,
                                      (PdbDocElementNode *) root_node,
                                      &section->text,
                                      error))
    {
      pdb_db_destroy_spannable_string (&section->title);
      g_slice_free (PdbDbSection, section);
      return FALSE;
    }

  g_queue_push_tail (sections, section);

  return TRUE;
}

static gboolean
get_var_root (PdbDb *db,
              PdbDocElementNode *var,
              GError **error)
{
  PdbDocElementNode *kap = pdb_doc_get_child_element (&var->node, "kap");
  PdbDocElementNode *rad;
  const char *label;

  if (kap == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("<var> found in <kap> with no inner <kap>"));
      return FALSE;
    }

  rad = pdb_doc_get_child_element (&kap->node, "rad");

  /* Sometimes the variant isn’t marked with a <rad> element and the
   * value is just spelled out explicitly in the article. */
  if (rad == NULL)
    return TRUE;

  label = pdb_doc_get_attribute (rad, "var");

  /* Some articles have a variant that isn’t used in the body of the
   * article */
  if (label == NULL)
    return TRUE;

  /* Without the line break around it, if the source has one: “<rad>
   *   fraj</rad>” is “fraj” */
  g_hash_table_insert (db->root_variants,
                       g_strdup (label),
                       g_strstrip (pdb_doc_get_element_text (rad)));

  return TRUE;
}

static gboolean
get_roots (PdbDb *db,
           PdbDocElementNode *kap,
           GError **error)
{
  PdbDocNode *n;
  PdbDocElementNode *elem;
  char *word_root = NULL;

  for (n = kap->node.first_child; n; n = n->next)
    {
      if (n->type != PDB_DOC_NODE_TYPE_ELEMENT)
        continue;

      elem = (PdbDocElementNode *) n;

      if (!strcmp (elem->name, "rad"))
        {
          if (word_root != NULL)
            {
              g_set_error (error,
                           PDB_ERROR,
                           PDB_ERROR_BAD_FORMAT,
                           _("Multiple <rad> elements found in <kap> with no "
                             "var attribute"));
              goto error;
            }

          /* Without the line break around it, if the source has one:
           * “<rad>\n  fraj</rad>” is “fraj” */
          word_root = g_strstrip (pdb_doc_get_element_text (elem));
        }
      else if (!strcmp (elem->name, "var"))
        {
          if (!get_var_root (db, elem, error))
            goto error;
        }
    }

  if (word_root == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("<kap> tag found with no <rad>"));
      goto error;
    }

  db->word_root = word_root;

  return TRUE;

 error:
  g_free (word_root);
  g_hash_table_remove_all (db->root_variants);
  return FALSE;
}

static PdbDbArticle *
pdb_db_parse_article (PdbDb *db,
                      PdbDocElementNode *root_node,
                      GError **error)
{
  PdbDbArticle *article;
  gboolean result = TRUE;
  PdbDocElementNode *kap;
  GQueue sections;

  kap = pdb_doc_get_child_element (&root_node->node, "kap");

  if (kap == NULL)
    {
      g_set_error (error,
                   PDB_ERROR,
                   PDB_ERROR_BAD_FORMAT,
                   _("<art> tag found with no <kap>"));
      return NULL;
    }

  if (!get_roots (db, kap, error))
    return NULL;

  g_queue_init (&sections);

  article = g_slice_new (PdbDbArticle);

  if (pdb_db_parse_spannable_string (db, kap, &article->title, error))
    {
      PdbDocNode *node;

      for (node = root_node->node.first_child; node; node = node->next)
        if (node->type == PDB_DOC_NODE_TYPE_ELEMENT)
          {
            PdbDocElementNode *element = (PdbDocElementNode *) node;

            if (!strcmp (element->name, "drv"))
              {
                if (!pdb_db_add_drv (db,
                                     article,
                                     element,
                                     &sections,
                                     error))
                  {
                    result = FALSE;
                    break;
                  }
              }
            else if (!strcmp (element->name, "subart"))
              {
                if (!pdb_db_parse_subart (db,
                                          article,
                                          element,
                                          &sections,
                                          error))
                  {
                    result = FALSE;
                    break;
                  }
              }
            else if (!strcmp (element->name, "dif"))
              {
                if (!pdb_db_parse_toplevel_dif (db,
                                                article,
                                                element,
                                                &sections,
                                                error))
                  {
                    result = FALSE;
                    break;
                  }
              }
          }

      if (result)
        {
          /* If we didn't find any content then we'll just treat
           * the whole article as if it were a drv */
          if (sections.head == NULL)
            {
              if (!pdb_db_add_drv (db, article, root_node, &sections, error))
                result = FALSE;
            }
          else
            {
              PdbDbReference ref;

              ref.type = PDB_DB_REFERENCE_TYPE_DIRECT;
              ref.d.direct.article = article;
              ref.d.direct.section = sections.head->data;

              if (!pdb_db_find_translations (db,
                                             root_node,
                                             &ref,
                                             error))
                result = FALSE;
            }

          if (result)
            {
              GList *translations = pdb_db_flush_translations (db);
              int length = g_list_length (translations);

              if (length > 0)
                {
                  if (sections.head == NULL)
                    sections.head = translations;
                  else
                    {
                      sections.tail->next = translations;
                      translations->prev = sections.tail;
                    }

                  sections.tail = g_list_last (translations);
                  sections.length += length;
                }
            }
        }

      if (!result)
        pdb_db_destroy_spannable_string (&article->title);
    }
  else
    result = FALSE;

  g_free (db->word_root);
  g_hash_table_remove_all (db->root_variants);

  if (result)
    {
      article->sections = sections.head;
      return article;
    }
  else
    {
      pdb_db_free_section_list (sections.head);
      g_slice_free (PdbDbArticle, article);
      return NULL;
    }
}

static gboolean
pdb_db_parse_articles (PdbDb *db,
                       PdbDocElementNode *root_node,
                       GError **error)
{
  PdbDocNode *node;

  for (node = root_node->node.first_child; node; node = node->next)
    if (node->type == PDB_DOC_NODE_TYPE_ELEMENT)
      {
        PdbDocElementNode *element = (PdbDocElementNode *) node;

        if (!strcmp (element->name, "art"))
          {
            PdbDbArticle *article = pdb_db_parse_article (db, element, error);

            if (article == NULL)
              return FALSE;

            g_ptr_array_add (db->articles, article);
          }
      }

  return TRUE;
}

static void
pdb_db_free_data_cb (void *data,
                     void *user_data)
{
  pdb_db_reference_free (data);
}

static void
pdb_db_get_reference_cb (void *data,
                         int *article_num,
                         int *mark_num,
                         void *user_data)
{
  PdbDb *db = user_data;
  PdbDbReference *ref = data;
  int sence_num;

  pdb_db_resolve_reference (db,
                            ref,
                            article_num,
                            mark_num,
                            &sence_num);
}

static void
pdb_db_add_file_root_mark (PdbDb *db,
                           const char *filename,
                           PdbDbArticle *article)
{
  char *mark_name;

  if (article->sections == NULL)
    return;

  mark_name = g_path_get_basename (filename);

  /* Strip off the extension */
  if (g_str_has_suffix (mark_name, ".xml"))
    mark_name[strlen (mark_name) - 4] = '\0';

  pdb_db_add_mark (db,
                   article,
                   article->sections->data,
                   mark_name);

  g_free (mark_name);
}

static int
compare_string_pointer (const void *pa,
                        const void *pb)
{
  const char *a = *(const char **) pa;
  const char *b = *(const char **) pb;

  return strcmp (a, b);
}

/* Loads a list of codes from a configuration file of ReVo: each
 * element called element_name maps the value of its attribute
 * attribute_name to its text, or to the text of its child called
 * text_name if it isn't NULL. The file is optional: if it can't be
 * loaded, the table is empty and the codes are written as they are. */
static GHashTable *
pdb_db_load_codes (PdbRevo *revo,
                   const char *filename,
                   const char *element_name,
                   const char *attribute_name,
                   const char *text_name)
{
  GHashTable *codes = g_hash_table_new_full (g_str_hash,
                                             g_str_equal,
                                             g_free,
                                             g_free);
  GError *error = NULL;
  PdbDoc *doc;
  PdbDocNode *node;

  doc = pdb_doc_load (revo, filename, &error);

  if (doc == NULL)
    {
      fprintf (stderr, "%s: %s\n", filename, error->message);
      g_error_free (error);
      return codes;
    }

  for (node = pdb_doc_get_root (doc)->node.first_child;
       node;
       node = node->next)
    {
      PdbDocElementNode *element = (PdbDocElementNode *) node;
      const char *code;
      GString *text;

      if (node->type != PDB_DOC_NODE_TYPE_ELEMENT ||
          strcmp (element->name, element_name))
        continue;

      code = pdb_doc_get_attribute (element, attribute_name);

      if (code == NULL)
        continue;

      if (text_name &&
          (element = pdb_doc_get_child_element (node, text_name)) == NULL)
        continue;

      text = g_string_new (NULL);
      pdb_doc_append_element_text (element, text);
      pdb_trim_buf (text);

      g_hash_table_insert (codes,
                           g_strdup (code),
                           g_string_free (text, FALSE));
    }

  pdb_doc_free (doc);

  return codes;
}

PdbDb *
pdb_db_new (PdbRevo *revo,
            GError **error)
{
  PdbDb *db;
  char **files;
  GError *tmp_error;

  db = g_slice_new (PdbDb);
  db->in_example = FALSE;

  db->lang = pdb_lang_new (revo,
                           pdb_db_free_data_cb,
                           pdb_db_get_reference_cb,
                           db,
                           error);

  if (db->lang == NULL)
    {
      g_slice_free (PdbDb, db);
      return NULL;
    }

  db->articles = g_ptr_array_new ();
  db->article_files = g_ptr_array_new_with_free_func (g_free);
  db->links = NULL;

  db->marks = g_hash_table_new_full (g_str_hash,
                                     g_str_equal,
                                     g_free,
                                     (GDestroyNotify) pdb_db_mark_free);

  db->translations = g_hash_table_new_full (g_str_hash,
                                            g_str_equal,
                                            (GDestroyNotify) g_free,
                                            pdb_db_free_translation_data_cb);

  db->root_variants = g_hash_table_new_full (g_str_hash,
                                             g_str_equal,
                                             g_free,
                                             g_free);

  db->fields = pdb_db_load_codes (revo,
                                  "cfg/fakoj.xml",
                                  "fako",
                                  "kodo",
                                  NULL /* text_name */);
  db->works = pdb_db_load_codes (revo,
                                 "cfg/bibliogr.xml",
                                 "vrk",
                                 "mll",
                                 "tit");
  db->work_details = pdb_db_load_works (revo);
  db->abbreviations = pdb_db_load_codes (revo,
                                         "cfg/mallongigoj.xml",
                                         "mallongigo",
                                         "mll",
                                         NULL /* text_name */);

  {
    GHashTableIter iter;
    gpointer code;

    db->field_codes = g_ptr_array_new ();
    g_hash_table_iter_init (&iter, db->fields);
    while (g_hash_table_iter_next (&iter, &code, NULL))
      g_ptr_array_add (db->field_codes, code);
    g_ptr_array_sort (db->field_codes, compare_string_pointer);
  }

  db->source_urls = g_ptr_array_new_with_free_func (g_free);
  db->source_url_numbers = g_hash_table_new (g_str_hash, g_str_equal);

  files = pdb_revo_list_files (revo, "revo/*.xml", error);

  if (files == NULL)
    {
      pdb_db_free (db);
      db = NULL;
    }
  else
    {
      char **file_p;

      /* Sort the filenames so that the output will be in a
       * reproducible order. */
      qsort (files,
             g_strv_length (files),
             sizeof (char *),
             compare_string_pointer);

      for (file_p = files; *file_p; file_p++)
        {
          const char *file = *file_p;
          PdbDoc *doc;

          if ((doc = pdb_doc_load (revo, file, error)) == NULL)
            {
              pdb_db_free (db);
              db = NULL;
              break;
            }
          else
            {
              gboolean parse_result;
              int old_len = db->articles->len;

              parse_result = pdb_db_parse_articles (db,
                                                    pdb_doc_get_root (doc),
                                                    error);
              pdb_doc_free (doc);

              if (parse_result)
                {
                  char *base = g_path_get_basename (file);
                  int i;

                  if (g_str_has_suffix (base, ".xml"))
                    base[strlen (base) - 4] = '\0';

                  for (i = old_len; i < db->articles->len; i++)
                    g_ptr_array_add (db->article_files, g_strdup (base));

                  g_free (base);

                  if (db->articles->len <= old_len)
                    fprintf (stderr,
                             _("no articles found in %s\n"),
                             file);
                  else
                    {
                      /* Some articles directly reference the filename
                       * instead of a real mark so we need to add a mark
                       * for each file */
                      PdbDbArticle *article =
                        g_ptr_array_index (db->articles, db->articles->len - 1);

                      pdb_db_add_file_root_mark (db,
                                                 file,
                                                 article);
                    }
                }
              else
                {
                  pdb_db_free (db);
                  db = NULL;

                  /* Add the filename into the error message */
                  if (error && *error && (*error)->domain == PDB_ERROR)
                    {
                      tmp_error = g_error_new ((*error)->domain,
                                               (*error)->code,
                                               "%s: %s",
                                               file,
                                               (*error)->message);
                      g_error_free (*error);
                      *error = tmp_error;
                    }
                  break;
                }
            }
        }

      g_strfreev (files);
    }

  if (db)
    pdb_db_resolve_links (db);

  return db;
}

static void
pdb_db_free_link_cb (void *ptr,
                     void *user_data)
{
  pdb_db_link_free (ptr);
}

static void
pdb_db_free_link_list (GList *links)
{
  g_list_foreach (links, pdb_db_free_link_cb, NULL);
  g_list_free (links);
}

void
pdb_db_free (PdbDb *db)
{
  int i;

  pdb_lang_free (db->lang);

  for (i = 0; i < db->articles->len; i++)
    {
      PdbDbArticle *article = g_ptr_array_index (db->articles, i);

      pdb_db_destroy_spannable_string (&article->title);
      pdb_db_free_section_list (article->sections);

      g_slice_free (PdbDbArticle, article);
    }

  g_ptr_array_free (db->articles, TRUE);
  g_ptr_array_free (db->article_files, TRUE);

  pdb_db_free_link_list (db->links);

  g_hash_table_destroy (db->marks);
  g_hash_table_destroy (db->translations);
  g_hash_table_destroy (db->root_variants);
  g_ptr_array_free (db->field_codes, TRUE);
  g_hash_table_destroy (db->fields);
  g_hash_table_destroy (db->works);
  g_hash_table_destroy (db->abbreviations);
  g_hash_table_destroy (db->work_details);
  g_hash_table_destroy (db->source_url_numbers);
  g_ptr_array_free (db->source_urls, TRUE);

  g_slice_free (PdbDb, db);
}

static int
pdb_db_get_utf16_length (const char *buf,
                         int byte_length)
{
  const char *p;
  int length = 0;

  /* Calculates the length that the string would have if it was
   * encoded in UTF-16 */
  for (p = buf; p - buf < byte_length; p = g_utf8_next_char (p))
    {
      gunichar ch = g_utf8_get_char (p);

      length++;
      /* If the character is outside the BMP then it
       * will need an extra 16 bit number to encode
       * it */
      if (ch >= 0x10000)
        length++;
    }

  return length;
}

static gboolean
pdb_db_write_string (PdbDb *pdb,
                     const PdbDbSpannableString *string,
                     gboolean single,
                     PdbFile *out,
                     GError **error)
{
  PdbSpan *span;

  if (!pdb_file_write_16 (out, string->length, error) ||
      !pdb_file_write (out, string->text, string->length, error))
    return FALSE;

  pdb_list_for_each (span, &string->spans, link)
    {
      /* Ignore empty spans */
      if (span->span_length > 0)
        {
          guint16 span_length =
            single ?
            span->span_length :
            pdb_db_get_utf16_length (string->text + span->span_start,
                                     span->span_length);
          guint16 span_start =
            single ?
            span->span_start :
            pdb_db_get_utf16_length (string->text, span->span_start);
          guint16 v[4] = { GUINT16_TO_LE (span_length),
                           GUINT16_TO_LE (span_start),
                           GUINT16_TO_LE (span->data1),
                           GUINT16_TO_LE (span->data2) };

          if (!pdb_file_write (out, v, sizeof (v), error) ||
              !pdb_file_write_8 (out, span->type, error))
            return FALSE;
        }
    }

  return pdb_file_write_16 (out, 0, error);
}

static gboolean
pdb_db_save_article (PdbDb *db,
                     PdbDbArticle *article,
                     gboolean single,
                     PdbFile *out,
                     GError **error)
{
  GList *sl;
  int old_pos = out->pos;
  int article_len;

  /* Leave space for the article size */
  if (!pdb_file_seek (out,
                      sizeof (guint32),
                      SEEK_CUR,
                      error))
    return FALSE;

  if (!pdb_db_write_string (db, &article->title, single, out, error))
    return FALSE;

  for (sl = article->sections; sl; sl = sl->next)
    {
      PdbDbSection *section = sl->data;

      if (!pdb_file_write (out, section->lang_code, 3, error) ||
          !pdb_db_write_string (db, &section->title, single, out, error) ||
          !pdb_db_write_string (db, &section->text, single, out, error))
        return FALSE;
    }

  /* Fill in the article size */
  article_len = out->pos - old_pos - sizeof (guint32);
  if (!pdb_file_seek (out,
                      old_pos,
                      SEEK_SET,
                      error) ||
      !pdb_file_write_32 (out, article_len, error) ||
      !pdb_file_seek (out, article_len, SEEK_CUR, error))
    return FALSE;

  return TRUE;
}

/* Whether a span of a title is an officiality mark: a superscript "*"
 * for the Fundamento, or the number of an Oficiala Aldono */
static char *
pdb_db_get_officiality_mark (const PdbDbSpannableString *title,
                             const PdbSpan *span)
{
  if (span->type != PDB_SPAN_SUPERSCRIPT)
    return NULL;

  char *mark = g_strndup (title->text + span->span_start, span->span_length);
  g_strstrip (mark);

  if (!strcmp (mark, "*") ||
      (*mark && strspn (mark, "0123456789") == strlen (mark)))
    return mark;

  g_free (mark);
  return NULL;
}

/* Writes a line for each officiality mark of a title. A title can have
 * several, eg. "*absolut/a" is also in the 1st and 7th additions, and a
 * variant can have its own, eg. "arĥaism/o, 8arkaism/o". The marks are
 * left out of the text. */
static void
pdb_db_add_officiality (GString *out,
                        const PdbDbSpannableString *title,
                        char kind,
                        int article_num,
                        int section_num)
{
  GPtrArray *marks = g_ptr_array_new_with_free_func (g_free);
  GString *text = g_string_new (NULL);
  const PdbSpan *span;
  int pos = 0;
  int i;

  pdb_list_for_each (span, &title->spans, link)
    {
      char *mark = pdb_db_get_officiality_mark (title, span);

      if (mark == NULL || span->span_start < pos)
        {
          g_free (mark);
          continue;
        }

      g_string_append_len (text, title->text + pos, span->span_start - pos);
      pos = span->span_start + span->span_length;

      for (i = 0; i < marks->len; i++)
        if (!strcmp (g_ptr_array_index (marks, i), mark))
          break;
      if (i < marks->len)
        g_free (mark);
      else
        g_ptr_array_add (marks, mark);
    }

  g_string_append_len (text, title->text + pos, title->length - pos);
  g_strstrip (text->str);

  for (i = 0; i < marks->len; i++)
    g_string_append_printf (out,
                            "%s\t%c\t%i\t%i\t%s\n",
                            (const char *) g_ptr_array_index (marks, i),
                            kind,
                            article_num,
                            section_num,
                            text->str);

  g_string_free (text, TRUE);
  g_ptr_array_free (marks, TRUE);
}

/* Writes the list of the official roots and words, for the
 * applications that show what each Oficiala Aldono contains. One line
 * per mark: the level ("*" for the Fundamento, or the number of the
 * addition), R for the root of an article or W for a word, the article,
 * the section of the word (0 for a root), and the text without its
 * mark, eg. "9\tR\t5182\t0\tkomput/i". */
static gboolean
pdb_db_save_officiality (PdbDb *db,
                         const char *dir,
                         GError **error)
{
  GString *out = g_string_new (NULL);
  int article_num;
  gboolean ret;

  for (article_num = 0; article_num < db->articles->len; article_num++)
    {
      PdbDbArticle *article = g_ptr_array_index (db->articles, article_num);
      int section_num = 0;
      GList *l;

      pdb_db_add_officiality (out, &article->title, 'R', article_num, 0);

      for (l = article->sections; l; l = l->next, section_num++)
        {
          PdbDbSection *section = l->data;

          if (!strcmp (section->lang_code, "eo"))
            pdb_db_add_officiality (out,
                                    &section->title,
                                    'W',
                                    article_num,
                                    section_num);
        }
    }

  char *file_name = g_build_filename (dir, "assets", "officiality.txt", NULL);
  ret = g_file_set_contents (file_name, out->str, out->len, error);
  g_free (file_name);
  g_string_free (out, TRUE);

  return ret;
}

/* Writes assets/articles/files.txt: the name of the file of each article,
 * one a line, in the order of the articles, eg. "korvet", to link to its
 * page on Reta Vortaro's site */
static gboolean
pdb_db_save_article_files (PdbDb *db,
                           const char *dir,
                           GError **error)
{
  GString *out = g_string_new (NULL);
  int i;
  gboolean ret;

  for (i = 0; i < db->article_files->len; i++)
    {
      g_string_append (out, g_ptr_array_index (db->article_files, i));
      g_string_append_c (out, '\n');
    }

  char *file_name = g_build_filename (dir, "assets", "articles", "files.txt", NULL);
  ret = g_file_set_contents (file_name, out->str, out->len, error);
  g_free (file_name);
  g_string_free (out, TRUE);

  return ret;
}

/* Without the list, no assets/vikipedio.txt: one left from an earlier
 * run wouldn't match the articles any more */
gboolean
pdb_db_remove_vikipedio (const char *dir)
{
  char *file_name = g_build_filename (dir, "assets", "vikipedio.txt", NULL);

  g_remove (file_name);
  g_free (file_name);

  return TRUE;
}

/* A mark in the x-system, as ReVo writes its marks: "Ĝ" as "Gx" */
static char *
pdb_db_x_system (const char *mark)
{
  static const char * const letters[][2] =
    {
      { "ĉ", "cx" }, { "ĝ", "gx" }, { "ĥ", "hx" }, { "ĵ", "jx" },
      { "ŝ", "sx" }, { "ŭ", "ux" }, { "Ĉ", "Cx" }, { "Ĝ", "Gx" },
      { "Ĥ", "Hx" }, { "Ĵ", "Jx" }, { "Ŝ", "Sx" }, { "Ŭ", "Ux" },
    };
  char *result = g_strdup (mark);
  int i;

  for (i = 0; i < G_N_ELEMENTS (letters); i++)
    {
      char *next = pdb_db_replace_all (result, letters[i][0], letters[i][1]);

      g_free (result);
      result = next;
    }

  return result;
}

/* Writes assets/vikipedio.txt from Reta Vortaro's list of the
 * Vikipedio articles of its words, a JSON array of [title, mark] one a
 * line, eg. ["Hundo","hund"], the mark of a word or the name of the
 * file of an article (its first word), an empty title for none: a line
 * "article\tsection\ttitle" for each word found, eg. "4898\t0\tHundo" */
gboolean
pdb_db_save_vikipedio (PdbDb *db,
                       const char *vikiref,
                       const char *dir,
                       GError **error)
{
  GString *out = g_string_new (NULL);
  GHashTable *seen = g_hash_table_new_full (g_str_hash, g_str_equal, g_free, NULL);
  char *contents, **lines, *file_name;
  gboolean ret;

  if (!g_file_get_contents (vikiref, &contents, NULL, error))
    {
      g_string_free (out, TRUE);
      g_hash_table_destroy (seen);
      return FALSE;
    }

  lines = g_strsplit (contents, "\n", -1);
  g_free (contents);

  for (char **line = lines; *line; line++)
    {
      /* ["title","mark"], */
      char **parts = g_strsplit (*line, "\"", -1);
      PdbDbMark *mark = NULL;
      char *key = NULL;

      if (g_strv_length (parts) >= 5 && parts[1][0])
        {
          mark = g_hash_table_lookup (db->marks, parts[3]);
          /* "gxiraf.Ĝ0o" for the mark "gxiraf.Gx0o" */
          if (mark == NULL)
            {
              char *x = pdb_db_x_system (parts[3]);

              mark = g_hash_table_lookup (db->marks, x);
              g_free (x);
            }
        }

      /* The first title of a word only: ReVo lists some words twice
       * ("Abc", "Aboco") */
      if (mark &&
          !g_hash_table_contains (seen,
                                  (key = g_strdup_printf ("%d\t%d",
                                                          mark->article->article_num,
                                                          mark->section->section_num))))
        {
          g_string_append_printf (out, "%s\t%s\n", key, parts[1]);
          g_hash_table_add (seen, key);
        }
      else if (mark)
        g_free (key);

      g_strfreev (parts);
    }

  g_strfreev (lines);
  g_hash_table_destroy (seen);

  file_name = g_build_filename (dir, "assets", "vikipedio.txt", NULL);
  ret = g_file_set_contents (file_name, out->str, out->len, error);
  g_free (file_name);
  g_string_free (out, TRUE);

  return ret;
}

/* Writes a list of strings one a line, eg. the codes of the fields in
 * assets/fields.txt */
static gboolean
pdb_db_save_lines (GPtrArray *lines,
                   const char *dir,
                   const char *name,
                   GError **error)
{
  GString *out = g_string_new (NULL);
  char *file_name;
  gboolean ret;
  int i;

  for (i = 0; i < lines->len; i++)
    {
      g_string_append (out, g_ptr_array_index (lines, i));
      g_string_append_c (out, '\n');
    }

  file_name = g_build_filename (dir, "assets", name, NULL);
  ret = g_file_set_contents (file_name, out->str, out->len, error);
  g_free (file_name);
  g_string_free (out, TRUE);

  return ret;
}

gboolean
pdb_db_save (PdbDb *db,
             const char *dir,
             GError **error)
{
  gboolean ret = TRUE;
  int group_num;

  if (!pdb_lang_save (db->lang, dir, error))
    return FALSE;

  if (pdb_try_mkdir (error, dir, "assets", "articles", NULL))
    {
      int max_group = (db->articles->len + 15) / 16;

      for (group_num = 0; group_num < max_group; group_num++)
        {
          PdbFile out;
          char *article_name =
            g_strdup_printf ("article-%03xx.bin", group_num);
          gboolean write_status = TRUE;
          char *full_name = g_build_filename (dir,
                                              "assets",
                                              "articles",
                                              article_name,
                                              NULL);

          if (!pdb_file_open (&out, full_name, PDB_FILE_MODE_WRITE, error))
            write_status = FALSE;
          else
            {
              int page_num, max_page;

              if ((db->articles->len & 0xf) && group_num + 1 >= max_group)
                max_page = db->articles->len & 0xf;
              else
                max_page = 16;

              for (page_num = 0; page_num < max_page; page_num++)
                {
                  PdbDbArticle *article =
                    g_ptr_array_index (db->articles, group_num * 16 + page_num);

                  if (!pdb_db_save_article (db,
                                            article,
                                            FALSE, /* single */
                                            &out,
                                            error))
                    {
                      write_status = FALSE;
                      break;
                    }
                }

              if (!pdb_file_close (&out, write_status ? error : NULL))
                write_status = FALSE;
            }

          g_free (full_name);
          g_free (article_name);

          if (!write_status)
            {
              ret = FALSE;
              break;
            }
        }
    }
  else
    ret = FALSE;

  if (ret && !pdb_db_save_lines (db->field_codes, dir, "fields.txt", error))
    ret = FALSE;

  if (ret && !pdb_db_save_lines (db->source_urls, dir, "sources.txt", error))
    ret = FALSE;

  if (ret && !pdb_db_save_officiality (db, dir, error))
    ret = FALSE;

  if (ret && !pdb_db_save_article_files (db, dir, error))
    ret = FALSE;

  return ret;
}

gboolean
pdb_db_save_single (PdbDb *db,
                    const char *filename,
                    GError **error)
{
  PdbFile file;
  gboolean ret = TRUE;

  if (!pdb_file_open (&file, filename, PDB_FILE_MODE_WRITE, error))
    ret = FALSE;
  else
    {
      if (pdb_file_write (&file, pdb_db_magic, sizeof (pdb_db_magic), error) &&
          pdb_file_write_32 (&file, db->articles->len, error) &&
          /* Skip past the article offset table which we'll fill in
           * later */
          pdb_file_seek (&file, db->articles->len * 4, SEEK_CUR, error) &&
          pdb_lang_save_single (db->lang, &file, error))
        {
          int i;
          guint32 *offset_table = g_alloca (db->articles->len *
                                            sizeof (guint32));

          for (i = 0; i < db->articles->len; i++)
            {
              PdbDbArticle *article = g_ptr_array_index (db->articles, i);

              offset_table[i] = GUINT32_TO_LE (file.pos);

              if (!pdb_db_save_article (db,
                                        article,
                                        TRUE, /* single */
                                        &file,
                                        error))
                {
                  ret = FALSE;
                  goto done;
                }
            }

          if (!pdb_file_seek (&file,
                              sizeof (pdb_db_magic) + sizeof (guint32),
                              SEEK_SET,
                              error) ||
              !pdb_file_write (&file,
                               offset_table,
                               db->articles->len * sizeof (guint32),
                               error))
            ret = FALSE;

        done:
          (void) 0;
        }
      else
        ret = FALSE;

      if (!pdb_file_close (&file, ret ? error : NULL))
        ret = FALSE;

      if (!ret)
        g_unlink (filename);
    }

  return ret;
}
