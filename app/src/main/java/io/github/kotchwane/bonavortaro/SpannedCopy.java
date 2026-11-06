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

import android.util.Log;
import android.content.ClipData;
import android.content.Context;
import android.content.ClipboardManager;
import android.text.Html;
import android.text.Spanned;
import android.os.Build;
import android.widget.Toast;

public abstract class SpannedCopy
{
  public static final String TAG = "bonaclip";

  public static void copyText (Context context,
                               CharSequence label,
                               CharSequence text)
  {
    ClipboardManager clipboard =
      (ClipboardManager) context.getSystemService (Context.CLIPBOARD_SERVICE);

    ClipData clipData;

    if (text instanceof Spanned)
      {
        String htmlText = Html.toHtml ((Spanned) text);

        clipData = ClipData.newHtmlText (label, text, htmlText);
      }
    else
      clipData = ClipData.newPlainText (label, text);

    clipboard.setPrimaryClip (clipData);

    /* Android 13 shows what was copied itself; before, nothing would
     * say it was */
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
      Toast.makeText (context, R.string.copied, Toast.LENGTH_SHORT).show ();
  }
}
