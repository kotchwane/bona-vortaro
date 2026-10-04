/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012, 2016  Neil Roberts
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

package uk.co.busydoingnothing.prevo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The choice of the search languages: the languages that the user
 * searches in, and only those, in their order, which is the order of the
 * tabs of the search. On the first start, it is the first screen, with a
 * button to go on to the search; afterwards it is opened from the
 * settings and from the "+" of the search. */
class SelectLanguageActivity : BonaActivity() {

    companion object {
        /** The first start: nothing is saved until "Daŭrigi" */
        const val EXTRA_FIRST_START = "uk.co.busydoingnothing.prevo.FirstStart"

        fun open(context: android.content.Context) {
            context.startActivity(Intent(context, SelectLanguageActivity::class.java))
        }
    }

    private var chosen by mutableStateOf(emptyList<String>())
    private var firstStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firstStart = intent.getBooleanExtra(EXTRA_FIRST_START, false)
        chosen = savedInstanceState?.getStringArrayList("chosen")
            /* Esperanto is ticked to start with, as this is an Esperanto
             * dictionary; it can be unticked */
            ?: if (firstStart) listOf("eo") else BonaSettings.getChosenSearchLanguages(this)

        val languages = LanguageList.getDefault(this).allLanguages.toList()

        setContent {
            BonaTheme {
                SearchLanguagesScreen(
                    firstStart = firstStart,
                    languages = languages,
                    chosen = chosen,
                    onChange = ::change,
                    onContinue = ::continueToSearch,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("chosen", ArrayList(chosen))
    }

    private fun change(codes: List<String>) {
        chosen = codes
        /* Afterwards, each change is saved at once */
        if (!firstStart)
            BonaSettings.setSearchLanguages(this, codes)
    }

    /* At least one language is needed to search in */
    private fun canUntick(remaining: List<String>): Boolean {
        if (remaining.isNotEmpty() || firstStart)
            return true
        Toast.makeText(this, R.string.one_language_needed, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun continueToSearch() {
        BonaSettings.setSearchLanguages(this, chosen)
        /* The articles then show the translations in the same languages,
         * Esperanto apart; with Esperanto alone, they keep all of them */
        val translations = chosen - "eo"
        if (translations.isNotEmpty()) {
            val all = LanguageList.getDefault(this).allLanguages.filter { it.code != "eo" }
            BonaSettings.setTranslationLanguages(this, all, translations)
        }
        startActivity(Screens.searchIntent(this, chosen.first()))
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SearchLanguagesScreen(
        firstStart: Boolean,
        languages: List<Language>,
        chosen: List<String>,
        onChange: (List<String>) -> Unit,
        onContinue: () -> Unit,
        onBack: () -> Unit,
    ) {
        var query by rememberSaveable { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        val listState = rememberLazyListState()
        val reorder = rememberListReorder(listState)

        BonaScaffold(
            topBar = {
                BonaTopBar(
                    title = { Text(stringResource(R.string.my_search_languages)) },
                    /* Nothing to go back to on the first start */
                    onBack = if (firstStart) null else onBack,
                )
            },
            bottomBar = {
                if (firstStart)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.navigationBarsPadding()) {
                            /* What the choice also does to the articles */
                            if (chosen.isNotEmpty())
                                Text(
                                    stringResource(
                                        if (chosen.any { it != "eo" }) R.string.first_translations_same
                                        else R.string.first_translations_all
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                                )
                            Button(
                                onClick = onContinue,
                                enabled = chosen.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(stringResource(R.string.continue_to_search))
                            }
                        }
                    }
            },
        ) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    /* The keyboard covers the bottom bar: its room isn't counted twice */
                    .consumeWindowInsets(padding)
                    .imePadding(),
                state = listState,
            ) {
                item {
                    Text(
                        stringResource(if (firstStart) R.string.welcome_search_languages else R.string.my_languages_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item {
                    RoundedSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.search_language),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                languageChoice(
                    languages = languages,
                    chosen = chosen,
                    onChange = onChange,
                    filter = normaliseQuery(query),
                    reorder = reorder,
                    orderHelp = R.string.search_languages_order_help,
                    canUntick = ::canUntick,
                    onSearchDone = {
                        query = ""
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }
}
