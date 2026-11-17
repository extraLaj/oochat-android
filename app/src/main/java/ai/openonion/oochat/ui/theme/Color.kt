package ai.openonion.oochat.ui.theme

import androidx.compose.ui.graphics.Color

// The palette is the brand green ramp on warm neutral greys (W16AAPPLE-49). Nothing else is
// tinted: the roles below are all drawn from these two scales, plus the error reds. The
// steps are Tailwind's `green` and `stone`; only the ones a role uses are listed.
//
// Green 500 is too light to carry text on white (2.3:1), so light mode puts the dark end of
// the ramp on the primary role and the pale end on its container. Dark mode puts Sage on it
// (W16AAPPLE-128): about Green 500's relative luminance (0.42 against 0.41), so the same
// contrast on the dark background, at about half its HSL saturation (40% against 71%). Green 500
// itself read as neon on buttons and links against the warm greys around it. The status and log
// greens on dark surfaces are Sage too. ColorSchemePaletteTest holds every pair that is read as
// text to 4.5:1 and every divider to 3:1, and every green on a dark surface to a muted saturation.

internal val Green50 = Color(0xFFF0FDF4)
internal val Green500 = Color(0xFF22C55E)
internal val Green600 = Color(0xFF16A34A)
internal val Green900 = Color(0xFF14532D)
internal val Green950 = Color(0xFF052E16)

// The agent status chip's two hues (W16AAPPLE-114). They are status, not accent: only the
// Active and Connecting chips wear them, each beside its own icon and word.
internal val Blue100 = Color(0xFFE0E9F8)
internal val Blue300 = Color(0xFF93C5FD)
internal val Blue500 = Color(0xFF3B82F6)
internal val Blue800 = Color(0xFF1E4FA3)
internal val Amber100 = Color(0xFFFBEBD3)
internal val Amber300 = Color(0xFFFCD34D)
internal val Amber500 = Color(0xFFF59E0B)
internal val Amber800 = Color(0xFF8A5300)

/** The brand green, muted for dark surfaces: Green 500's relative luminance at about half its saturation. */
internal val Sage = Color(0xFF6BBF85)

internal val Stone50 = Color(0xFFFAFAF9)
internal val Stone100 = Color(0xFFF5F5F4)
internal val Stone200 = Color(0xFFE7E5E4)
internal val Stone300 = Color(0xFFD6D3D1)
internal val Stone400 = Color(0xFFA8A29E)
internal val Stone500 = Color(0xFF78716C)
internal val Stone600 = Color(0xFF57534E)
internal val Stone700 = Color(0xFF44403C)
internal val Stone800 = Color(0xFF292524)
internal val Stone900 = Color(0xFF1C1917)
internal val Stone950 = Color(0xFF0C0A09)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

val primaryLight = Green900
val onPrimaryLight = White
val primaryContainerLight = Green50
val onPrimaryContainerLight = Green900
val secondaryLight = Stone700
val onSecondaryLight = White
val secondaryContainerLight = Stone100
val onSecondaryContainerLight = Stone800
val tertiaryLight = Stone600
val onTertiaryLight = White
val tertiaryContainerLight = Stone200
val onTertiaryContainerLight = Stone800
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = White
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = White
val onBackgroundLight = Stone900
val surfaceLight = White
val onSurfaceLight = Stone900
val surfaceVariantLight = Stone100
val onSurfaceVariantLight = Stone600
// `outline` is this app's caption/label text colour at 50+ call sites, so it is held to 4.5:1
// on every surface a caption sits on, up to the raised containers. Stone 500 is 4.4:1 on
// surfaceContainer, so this is a step darker.
val outlineLight = Color(0xFF6B655F)
// Dividers are what delimits a group here, so WCAG 1.4.11's 3:1 for meaningful non-text applies.
val outlineVariantLight = Color(0xFF8F8883)
val scrimLight = Black
val inverseSurfaceLight = Stone800
val inverseOnSurfaceLight = Stone100
val inversePrimaryLight = Sage
val surfaceDimLight = Stone200
val surfaceBrightLight = White
val surfaceContainerLowestLight = White
val surfaceContainerLowLight = Stone50
val surfaceContainerLight = Stone100
val surfaceContainerHighLight = Stone200
val surfaceContainerHighestLight = Stone300

val primaryDark = Sage
val onPrimaryDark = Green950
val primaryContainerDark = Green900
val onPrimaryContainerDark = Green50
val secondaryDark = Stone300
val onSecondaryDark = Stone900
val secondaryContainerDark = Stone700
val onSecondaryContainerDark = Stone100
val tertiaryDark = Stone400
val onTertiaryDark = Stone900
val tertiaryContainerDark = Stone700
val onTertiaryContainerDark = Stone100
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Stone900
val onBackgroundDark = Stone100
val surfaceDark = Stone900
val onSurfaceDark = Stone100
val surfaceVariantDark = Stone800
val onSurfaceVariantDark = Stone300
val outlineDark = Stone400
val outlineVariantDark = Stone500
val scrimDark = Black
val inverseSurfaceDark = Stone100
val inverseOnSurfaceDark = Stone800
val inversePrimaryDark = Green900
val surfaceDimDark = Stone900
val surfaceBrightDark = Color(0xFF3A3532)
val surfaceContainerLowestDark = Stone950
val surfaceContainerLowDark = Color(0xFF211E1C)
val surfaceContainerDark = Stone800
val surfaceContainerHighDark = Color(0xFF34302D)
val surfaceContainerHighestDark = Stone700
