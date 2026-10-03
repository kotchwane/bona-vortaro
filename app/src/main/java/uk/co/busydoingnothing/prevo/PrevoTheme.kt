/*
 * PReVo - A portable version of ReVo for Android
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

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/* The design of PReVo: a page of a printed dictionary by day ("papero":
 * cream paper, green ink), and the night of the star of Esperanto by
 * night ("stelnokto": a navy sky, cream ink, a green star). */

/** The green of the flag of Esperanto, for the star */
private val STAR_GREEN = Color(0xFF0F7A35)
private val NIGHT_STAR_GREEN = Color(0xFF47C26B)

private val PaperColors = lightColorScheme(
    /* The ink: the headwords, the links, the badges */
    primary = Color(0xFF1F5132),
    onPrimary = Color(0xFFFFFAEF),
    primaryContainer = Color(0xFFD9E6D3),
    onPrimaryContainer = Color(0xFF12321F),
    secondary = Color(0xFF6E5F49),
    onSecondary = Color(0xFFFFFAEF),
    /* The labels of the fields, the selected chips */
    secondaryContainer = Color(0xFFE6DCC5),
    onSecondaryContainer = Color(0xFF3D3426),
    tertiary = Color(0xFF8A4B2A),
    onTertiary = Color(0xFFFFFAEF),
    tertiaryContainer = Color(0xFFF2DCCD),
    onTertiaryContainer = Color(0xFF3A1A08),
    background = Color(0xFFF4EDDD),
    onBackground = Color(0xFF2B2418),
    surface = Color(0xFFF4EDDD),
    onSurface = Color(0xFF2B2418),
    onSurfaceVariant = Color(0xFF6E5F49),
    surfaceVariant = Color(0xFFE6DCC5),
    surfaceContainerLowest = Color(0xFFFFFAEF),
    surfaceContainerLow = Color(0xFFF8F2E5),
    surfaceContainer = Color(0xFFF0E8D6),
    surfaceContainerHigh = Color(0xFFEBE2CD),
    surfaceContainerHighest = Color(0xFFE4D9C1),
    outline = Color(0xFFA8997E),
    outlineVariant = Color(0xFFD9CDB4),
)

private val NightColors = darkColorScheme(
    /* A soft green for the links and the accents: the headwords are cream */
    primary = Color(0xFF8FD19E),
    onPrimary = Color(0xFF0A1330),
    primaryContainer = Color(0xFF1E4A35),
    onPrimaryContainer = Color(0xFFCDEFD5),
    secondary = Color(0xFFC9BBA0),
    onSecondary = Color(0xFF0A1330),
    secondaryContainer = Color(0xFF243354),
    onSecondaryContainer = Color(0xFFE5DCC6),
    tertiary = Color(0xFFE2B07A),
    onTertiary = Color(0xFF2A1A08),
    tertiaryContainer = Color(0xFF4A3524),
    onTertiaryContainer = Color(0xFFF6DDC2),
    background = Color(0xFF0C1834),
    onBackground = Color(0xFFEDE6D6),
    surface = Color(0xFF0C1834),
    onSurface = Color(0xFFEDE6D6),
    onSurfaceVariant = Color(0xFFAFBBC8),
    surfaceVariant = Color(0xFF1D2A4A),
    surfaceContainerLowest = Color(0xFF101E3E),
    surfaceContainerLow = Color(0xFF111F40),
    surfaceContainer = Color(0xFF142446),
    surfaceContainerHigh = Color(0xFF192B50),
    surfaceContainerHighest = Color(0xFF1F335C),
    outline = Color(0xFF6F7D95),
    outlineVariant = Color(0xFF2C3A5C),
)

/** What the design needs beyond the colours of Material 3. */
@Immutable
class PrevoDesign(
    val dark: Boolean,
    /** The words looked up: the search results, the headwords */
    val headword: Color,
    /** The star of Esperanto, next to the name of the app */
    val star: Color,
    /** The page behind everything: paper, or the night sky */
    val background: Brush,
    /** The search fields */
    val field: Color,
    /** The top bars: the colour of the top of the background, so that
     * the text scrolled under them is hidden without a visible edge */
    val bar: Color,
)

private val PaperDesign = PrevoDesign(
    dark = false,
    headword = PaperColors.primary,
    star = STAR_GREEN,
    background = Brush.verticalGradient(listOf(PaperColors.background, PaperColors.background)),
    field = PaperColors.surfaceContainerLowest,
    bar = PaperColors.background,
)

private val NightDesign = PrevoDesign(
    dark = true,
    headword = Color(0xFFF1E8D2),
    star = NIGHT_STAR_GREEN,
    /* From the deep navy of the sky to a deep teal */
    background = Brush.verticalGradient(listOf(Color(0xFF0A1330), Color(0xFF0D1F3D), Color(0xFF0D2B33))),
    field = Color.White.copy(alpha = 0.10f),
    bar = Color(0xFF0A1330),
)

val LocalPrevoDesign = staticCompositionLocalOf { PaperDesign }

/** The design of the current theme. */
val prevoDesign: PrevoDesign
    @Composable get() = LocalPrevoDesign.current

/* Serif type, as in a printed dictionary */
private val SerifTypography = Typography().let { t ->
    fun androidx.compose.ui.text.TextStyle.serif() = copy(fontFamily = FontFamily.Serif)
    Typography(
        displayLarge = t.displayLarge.serif(),
        displayMedium = t.displayMedium.serif(),
        displaySmall = t.displaySmall.serif(),
        headlineLarge = t.headlineLarge.serif(),
        headlineMedium = t.headlineMedium.serif(),
        headlineSmall = t.headlineSmall.serif(),
        titleLarge = t.titleLarge.serif(),
        titleMedium = t.titleMedium.serif(),
        titleSmall = t.titleSmall.serif(),
        bodyLarge = t.bodyLarge.serif(),
        bodyMedium = t.bodyMedium.serif(),
        bodySmall = t.bodySmall.serif(),
        labelLarge = t.labelLarge.serif(),
        labelMedium = t.labelMedium.serif(),
        labelSmall = t.labelSmall.serif(),
    )
}

/** The theme of PReVo, which follows the light/dark setting. */
@Composable
fun PrevoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()

    CompositionLocalProvider(LocalPrevoDesign provides if (dark) NightDesign else PaperDesign) {
        MaterialTheme(
            colorScheme = if (dark) NightColors else PaperColors,
            typography = SerifTypography,
            content = content,
        )
    }
}

/** The page behind a screen: paper by day, the night sky by night. The
 * screens are transparent above it. */
@Composable
fun Modifier.prevoBackground(): Modifier = background(prevoDesign.background)

/** The top bars have the colour of the top of the background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun prevoTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = prevoDesign.bar,
    scrolledContainerColor = prevoDesign.bar,
)
