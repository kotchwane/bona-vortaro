/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2013  Neil Roberts
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

package io.github.kotchwane.bonavortaro

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** An invisible first screen that opens the right one: the search, in
 * the language used last, or the choice of the search languages if the
 * user hasn't made it yet. */
open class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BonaSettings.getChosenSearchLanguages(this).isEmpty())
            startActivity(
                Intent(this, SelectLanguageActivity::class.java)
                    .putExtra(SelectLanguageActivity.EXTRA_FIRST_START, true)
            )
        else
            Screens.goSearch(this)

        /* Out of the way of the back button */
        finish()
    }
}

/** The activity that the launcher opens. It keeps the name of the
 * original first screen, as changing it can break the shortcuts of the
 * users. */
class LanguagesActivity : StartActivity()
