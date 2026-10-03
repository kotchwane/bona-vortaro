/*
 * PReVo - A portable version of ReVo for Android
 * Copyright (C) 2013  Neil Roberts
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

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/* This activity is just like a landing page to select the right
 * initial activity: the search, in the language used last, or the
 * choice of the search languages if the user hasn't made it yet */

public class StartActivity extends AppCompatActivity
{
  @Override
  public void onCreate (Bundle savedInstanceState)
  {
    super.onCreate (savedInstanceState);

    /* Until the user has chosen the languages they search in, the first
     * screen is the choice of these languages */
    if (MyLanguages.INSTANCE.get (this).isEmpty ())
      {
        Intent intent = new Intent (this, SelectLanguageActivity.class);
        intent.putExtra (SelectLanguageActivity.EXTRA_FIRST_START, true);
        startActivity (intent);
      }
    else
      MenuHelper.goSearch (this);

    /* Finish this activity to get it out of the call stack */
    finish ();
  }
}
