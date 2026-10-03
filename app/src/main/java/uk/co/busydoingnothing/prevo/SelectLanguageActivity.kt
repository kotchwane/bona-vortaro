/*
 * PReVo - A portable version of ReVo for Android
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
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The choice of the search languages: the languages that the user
 * searches in, and only those, in their order, which is the order of the
 * tabs of the search. On the first start, it is the first screen, with a
 * button to go on to the search; afterwards it is opened from the
 * settings and from the "+" of the search. */
class SelectLanguageActivity : AppCompatActivity() {

    companion object {
        /** The first start: nothing is saved until "Daŭrigi" */
        const val EXTRA_FIRST_START = "uk.co.busydoingnothing.prevo.FirstStart"

        fun open(context: android.content.Context) {
            context.startActivity(Intent(context, SelectLanguageActivity::class.java))
        }
    }

    private var chosen by mutableStateOf(emptyList<String>())
    private var firstStart = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        /* The colours of the icons of the system bars follow the theme only
         * when they are set, and the screen isn't recreated any more when
         * the theme changes */
        enableEdgeToEdge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        firstStart = intent.getBooleanExtra(EXTRA_FIRST_START, false)
        chosen = savedInstanceState?.getStringArrayList("chosen")
            /* Esperanto is ticked to start with, as this is an Esperanto
             * dictionary; it can be unticked */
            ?: if (firstStart) listOf("eo") else MyLanguages.get(this)

        val languages = LanguageList.getDefault(this).allLanguages.toList()

        setContent {
            PrevoTheme {
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
            MyLanguages.set(this, codes)
    }

    /* At least one language is needed to search in */
    private fun canUntick(remaining: List<String>): Boolean {
        if (remaining.isNotEmpty() || firstStart)
            return true
        Toast.makeText(this, R.string.one_language_needed, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun continueToSearch() {
        MyLanguages.set(this, chosen)
        startActivity(MenuHelper.createSearchIntent(this, chosen.first()))
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
        val listState = rememberLazyListState()
        val reorder = rememberListReorder(listState)

        Scaffold(
            modifier = Modifier.prevoBackground(),
            containerColor = Color.Transparent,
            /* The colour of the text, which a transparent container doesn't give */
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    colors = prevoTopBarColors(),
                    title = { Text(stringResource(R.string.my_search_languages)) },
                    navigationIcon = {
                        /* Nothing to go back to on the first start */
                        if (!firstStart)
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                            }
                    },
                )
            },
            bottomBar = {
                if (firstStart)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Button(
                            onClick = onContinue,
                            enabled = chosen.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(R.string.continue_to_search))
                        }
                    }
            },
        ) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                )
            }
        }
    }
}
