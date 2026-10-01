/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2012, 2013, 2016  Neil Roberts
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
import android.content.Intent;
import android.content.SharedPreferences;

public class MenuHelper
{
  public static final String PREVO_PREFERENCES =
    "PrevoPreferences";
  public static final String PREF_LAST_LANGUAGE =
    "lastLanguage";
  public static final String PREF_FONT_SIZE =
    "fontSize";

  public static Intent createSearchIntent (Context context,
                                           String language)
  {
    Intent intent = new Intent (context, SearchActivity.class);

    intent.putExtra (SearchActivity.EXTRA_LANGUAGE, language);
    intent.putExtra (SearchActivity.EXTRA_USE_LANGUAGE, true);

    return intent;
  }

  public static void goSearch (Context context)
  {
    SharedPreferences prefs =
      context.getSharedPreferences (PREVO_PREFERENCES,
                                    Context.MODE_PRIVATE);
    String defaultLanguage = prefs.getString (PREF_LAST_LANGUAGE, "eo");

    /* The language may have disappeared in an update of the
     * dictionary data */
    if (!LanguageList.getDefault (context).hasLanguage (defaultLanguage))
      defaultLanguage = "eo";

    Intent intent = new Intent (context, SearchActivity.class);
    intent.putExtra (SearchActivity.EXTRA_LANGUAGE, defaultLanguage);

    context.startActivity (intent);
  }

  public static void goChooseLanguage (Context context)
  {
    Intent intent = new Intent (context, SelectLanguageActivity.class);
    context.startActivity (intent);
  }

  public static void goPreferences (Context context)
  {
    Intent intent = new Intent (context, PreferenceActivity.class);
    context.startActivity (intent);
  }
}
