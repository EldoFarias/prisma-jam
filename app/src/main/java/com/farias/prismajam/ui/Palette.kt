package com.farias.prismajam.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.farias.prismajam.R
import com.farias.prismajam.data.Progress

/** A visual theme: background, aurora lights and the 7 block colors. */
class GameTheme(
    val id: String,
    @StringRes val nameRes: Int,
    val price: Int,
    val bgTop: Color,
    val bgBottom: Color,
    val aurora: List<Color>,
    /** Block colors for values 1..7. */
    val blocks: List<Color>,
    val stars: Boolean = false,
)

object Themes {
    val all = listOf(
        GameTheme(
            id = "neon", nameRes = R.string.theme_neon, price = 0,
            bgTop = Color(0xFF070716), bgBottom = Color(0xFF140C33),
            aurora = listOf(Color(0xFF7C3AED), Color(0xFF06B6D4), Color(0xFFDB2777)),
            blocks = listOf(
                Color(0xFF22D3EE), Color(0xFFE879F9), Color(0xFFA3E635), Color(0xFFFB923C),
                Color(0xFFF43F5E), Color(0xFF8B5CF6), Color(0xFFFACC15),
            ),
        ),
        GameTheme(
            id = "ocean", nameRes = R.string.theme_ocean, price = 300,
            bgTop = Color(0xFF021526), bgBottom = Color(0xFF03344F),
            aurora = listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF6366F1)),
            blocks = listOf(
                Color(0xFF38BDF8), Color(0xFF2DD4BF), Color(0xFF818CF8), Color(0xFF5EEAD4),
                Color(0xFFFB7185), Color(0xFF60A5FA), Color(0xFFFDE68A),
            ),
        ),
        GameTheme(
            id = "candy", nameRes = R.string.theme_candy, price = 500,
            bgTop = Color(0xFF2A0F2E), bgBottom = Color(0xFF4A1638),
            aurora = listOf(Color(0xFFF472B6), Color(0xFFC4B5FD), Color(0xFFFDBA74)),
            blocks = listOf(
                Color(0xFFFF8FB1), Color(0xFF7FE7C4), Color(0xFFFFE27A), Color(0xFFB9A2FF),
                Color(0xFFFFB38A), Color(0xFF8FD3FF), Color(0xFFFF5C8A),
            ),
        ),
        GameTheme(
            id = "lava", nameRes = R.string.theme_lava, price = 800,
            bgTop = Color(0xFF120404), bgBottom = Color(0xFF2A0A05),
            aurora = listOf(Color(0xFFDC2626), Color(0xFFEA580C), Color(0xFFBE185D)),
            blocks = listOf(
                Color(0xFFFF3B1F), Color(0xFFFF7A1A), Color(0xFFFFC21A), Color(0xFFFF4F81),
                Color(0xFFE11D48), Color(0xFFF97316), Color(0xFFFDE047),
            ),
        ),
        GameTheme(
            id = "galaxy", nameRes = R.string.theme_galaxy, price = 1200,
            bgTop = Color(0xFF02010A), bgBottom = Color(0xFF0B0A2E),
            aurora = listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFF0891B2)),
            blocks = listOf(
                Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF0ABFC), Color(0xFF67E8F9),
                Color(0xFFFDE68A), Color(0xFFC084FC), Color(0xFF93C5FD),
            ),
            stars = true,
        ),
        GameTheme(
            id = "gold", nameRes = R.string.theme_gold, price = 2000,
            bgTop = Color(0xFF0B0904), bgBottom = Color(0xFF1F1606),
            aurora = listOf(Color(0xFFCA8A04), Color(0xFFB45309), Color(0xFFBE185D)),
            blocks = listOf(
                Color(0xFFFACC15), Color(0xFFF59E0B), Color(0xFFFDE68A), Color(0xFFD97706),
                Color(0xFFF9A8D4), Color(0xFFE5E7EB), Color(0xFF34D399),
            ),
            stars = true,
        ),
    )

    fun byId(id: String): GameTheme = all.firstOrNull { it.id == id } ?: all[0]
}

object Palette {
    /** Current theme. Reading it inside composition or drawing subscribes to theme changes. */
    val theme: GameTheme get() = Themes.byId(Progress.themeId)

    val BgTop: Color get() = theme.bgTop
    val BgBottom: Color get() = theme.bgBottom

    val Glass = Color(0x14FFFFFF)
    val GlassBorder = Color(0x26FFFFFF)
    val EmptyCell = Color(0x0FFFFFFF)
    val EmptyCellInner = Color(0x33000000)

    val TextPrimary = Color(0xFFF5F3FF)
    val TextSecondary = Color(0xFFA5A3C9)
    val Gold = Color(0xFFFACC15)
    val Cyan = Color(0xFF22D3EE)
    val Magenta = Color(0xFFE879F9)
    val Danger = Color(0xFFF43F5E)

    /** Block color for board value 1..7 in the current theme. */
    fun block(index: Int): Color = blockIn(theme, index)

    fun blockIn(t: GameTheme, index: Int): Color = t.blocks[(index - 1).coerceIn(0, t.blocks.size - 1)]

    fun lighter(c: Color, f: Float = 0.35f) = lerp(c, Color.White, f)
    fun darker(c: Color, f: Float = 0.35f) = lerp(c, Color.Black, f)

    /** Desaturated gray version, used when the game is over. */
    fun gray(c: Color): Color {
        val l = c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f
        return Color(l * 0.55f, l * 0.55f, l * 0.62f, c.alpha)
    }
}
