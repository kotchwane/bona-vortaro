/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2012, 2013  Neil Roberts
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

package uk.co.busydoingnothing.prevo;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import java.util.Vector;

public class LanguagesAdapter extends BaseAdapter
  implements Filterable
{
  private Language[] mainLanguages =
  {
    new Language ("esperanto", "eo")
  };

  private LanguageList languageList;
  private Language[] languages;

  private Context context;
  private LanguagesFilter filter;
  private Language[] filteredLanguages;

  static final int TYPE_HEADER = 0;
  static final int TYPE_LANGUAGE = 1;
  static final int TYPE_COUNT = 2;

  public LanguagesAdapter (Context context)
  {
    this.context = context;
    this.languageList = LanguageList.getDefault (context);
    this.languages = getLanguages ();
  }

  private Language[] getLanguages ()
  {
    /* All of the languages except Esperanto, which is always the first
     * main language. This doesn't depend on which translations are
     * shown in the articles, otherwise hiding the translations of a
     * language would also make it impossible to search in it. */
    Language[] allLanguages = languageList.getAllLanguages ();
    Vector<Language> result = new Vector<Language> ();

    for (int i = 0; i < allLanguages.length; i++)
      if (!allLanguages[i].getCode ().equals ("eo"))
        result.add (allLanguages[i]);

    return result.toArray (new Language[result.size ()]);
  }

  @Override
  public int getCount ()
  {
    if (filteredLanguages == null)
      return mainLanguages.length + languages.length + 2;
    else
      return filteredLanguages.length + 1;
  }

  @Override
  public Object getItem (int position)
  {
    if (filteredLanguages == null)
      {
        if (position == 0)
          return context.getString (R.string.main_languages);

        position--;

        if (position < mainLanguages.length)
          return mainLanguages[position];

        position -= mainLanguages.length;

        if (position == 0)
          return context.getString (R.string.all_languages);

        return languages[position - 1];
      }
    else if (position == 0)
      return context.getString (R.string.all_languages);
    else
      return filteredLanguages[position - 1];
  }

  @Override
  public long getItemId (int position)
  {
    return position;
  }

  @Override
  public int getItemViewType (int position)
  {
    if (filteredLanguages == null)
      {
        if (position == 0 || position == mainLanguages.length + 1)
          return TYPE_HEADER;
        else
          return TYPE_LANGUAGE;
      }
    else if (position == 0)
      return TYPE_HEADER;
    else
      return TYPE_LANGUAGE;
  }

  @Override
  public int getViewTypeCount ()
  {
    return TYPE_COUNT;
  }

  @Override
  public View getView (int position, View convertView, ViewGroup parent)
  {
    TextView tv;

    if (convertView == null)
      {
        LayoutInflater layoutInflater = LayoutInflater.from (context);
        int id;

        switch (getItemViewType (position))
          {
          case TYPE_HEADER:
            id = android.R.layout.preference_category;
            break;

          case TYPE_LANGUAGE:
            id = android.R.layout.simple_list_item_1;
            break;

          default:
            throw new IllegalStateException ();
          }

        tv = (TextView) layoutInflater.inflate (id, parent, false);
      }
    else
      {
        tv = (TextView) convertView;
      }

    tv.setText (getItem (position).toString ());

    return tv;
  }

  @Override
  public boolean hasStableIds ()
  {
    return false;
  }

  @Override
  public boolean isEmpty ()
  {
    return false;
  }

  @Override
  public boolean isEnabled (int position)
  {
    return getItemViewType (position) == TYPE_LANGUAGE;
  }

  @Override
  public boolean areAllItemsEnabled ()
  {
    return false;
  }

  @Override
  public LanguagesFilter getFilter ()
  {
    if (filter == null)
      filter = new LanguagesFilter (languages);

    return filter;
  }

  public void setMainLanguages (String[] languages)
  {
    Language[] mainLanguages = new Language[languages.length + 1];

    /* Preserve the 'esperanto' language */
    mainLanguages[0] = this.mainLanguages[0];

    for (int i = 0; i < languages.length; i++)
      {
        String languageName = languageList.getLanguageName (languages[i]);
        mainLanguages[i + 1] = new Language (languageName, languages[i]);
      }

    this.mainLanguages = mainLanguages;

    notifyDataSetChanged ();
  }

  private class LanguagesFilter extends Filter
  {
    private Language[] languages;

    public LanguagesFilter (Language[] languages)
    {
      /* The list of languages never changes so it is safe to use it
       * from the filtering thread */
      this.languages = languages;
    }

    @Override
    public FilterResults performFiltering (CharSequence filter)
    {
      FilterResults ret = new FilterResults ();

      if (filter.length () == 0)
        {
          ret.values = null;
          ret.count = 0;
        }
      else
        {
          String filterString = Hats.removeHats (filter);
          Vector<Language> result = new Vector<Language> ();

          for (int i = 0; i < languages.length; i++)
            {
              Language language = languages[i];

              if (language.getName ().startsWith (filterString))
                result.add (language);
            }


          ret.values = result.toArray (new Language[result.size ()]);
          ret.count = result.size ();
        }

      return ret;
    }

    @Override
    public void publishResults (CharSequence filter, FilterResults results)
    {
      filteredLanguages = (Language[]) results.values;
      notifyDataSetChanged ();
    }
  }
}
