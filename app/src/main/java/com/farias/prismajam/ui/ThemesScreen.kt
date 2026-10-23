package com.farias.prismajam.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.farias.prismajam.R
import com.farias.prismajam.audio.Sfx
import com.farias.prismajam.audio.SoundManager
import com.farias.prismajam.data.Progress

@Composable
fun ThemesScreen(sound: SoundManager, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    fun onThemeClick(theme: GameTheme) {
        when {
            Progress.themeId == theme.id -> Unit
            Progress.owns(theme.id) -> {
                Progress.selectTheme(theme.id)
                sound.play(Sfx.PICK)
            }
            Progress.buyTheme(theme.id, theme.price) -> {
                Progress.selectTheme(theme.id)
                sound.play(Sfx.COIN)
            }
            else -> {
                sound.play(Sfx.INVALID, 0.5f)
                val missing = theme.price - Progress.coins
                Toast.makeText(context, context.getString(R.string.need_coins, missing), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Palette.TextPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.themes), style = Styles.gradient(30))
                Spacer(Modifier.weight(1f))
                CoinPill(Progress.coins)
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Themes.all.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { theme ->
                                ThemeCard(theme, Modifier.weight(1f)) { onThemeClick(theme) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(theme: GameTheme, modifier: Modifier, onClick: () -> Unit) {
    val selected = Progress.themeId == theme.id
    val owned = Progress.owns(theme.id)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Palette.Glass)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Brush.horizontalGradient(listOf(Palette.Cyan, Palette.Magenta))
                else Brush.horizontalGradient(listOf(Palette.GlassBorder, Palette.GlassBorder)),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ThemePreview(theme)
            if (!owned) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(theme.nameRes), style = Styles.plain(17, Palette.TextPrimary, FontWeight.Black))
        Spacer(Modifier.height(8.dp))
        val pillShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(pillShape)
                .background(
                    when {
                        selected -> Brush.horizontalGradient(listOf(Color(0x3334D399), Color(0x3334D399)))
                        owned -> Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6)))
                        else -> Brush.horizontalGradient(listOf(Color(0x40FACC15), Color(0x40F59E0B)))
                    },
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                selected -> {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.in_use), style = Styles.plain(15, Color(0xFF34D399), FontWeight.Bold))
                }
                owned -> Text(stringResource(R.string.use), style = Styles.plain(15, Color.White, FontWeight.Bold))
                else -> {
                    CoinIcon(Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(theme.price.toString(), style = Styles.plain(16, Palette.Gold, FontWeight.Black))
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(theme: GameTheme) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (size.width < 2f || size.height < 2f) return@Canvas
        drawRect(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
        val glow = size.width * 0.6f
        drawCircle(
            Brush.radialGradient(listOf(theme.aurora[0].copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.2f), glow),
            glow, Offset(size.width * 0.2f, size.height * 0.2f),
        )
        drawCircle(
            Brush.radialGradient(listOf(theme.aurora[2].copy(alpha = 0.35f), Color.Transparent), Offset(size.width * 0.9f, size.height), glow),
            glow, Offset(size.width * 0.9f, size.height),
        )
        if (theme.stars) {
            val pts = listOf(0.1f to 0.15f, 0.82f to 0.12f, 0.92f to 0.55f, 0.06f to 0.7f, 0.5f to 0.08f, 0.35f to 0.9f, 0.7f to 0.88f)
            for ((x, y) in pts) drawCircle(Color.White.copy(alpha = 0.8f), 1.5.dp.toPx(), Offset(size.width * x, size.height * y))
        }
        val cell = size.height * 0.27f
        val gap = cell * 0.06f
        // A small "board": 3 rows x 4 cols with a few gems
        val pattern = listOf(
            intArrayOf(1, 2, 0, 7),
            intArrayOf(3, 6, 4, 0),
            intArrayOf(0, 5, 1, 2),
        )
        val totalW = 4 * cell + 3 * gap
        val totalH = 3 * cell + 2 * gap
        val ox = (size.width - totalW) / 2f
        val oy = (size.height - totalH) / 2f
        pattern.forEachIndexed { r, row ->
            row.forEachIndexed { c, v ->
                val tl = Offset(ox + c * (cell + gap), oy + r * (cell + gap))
                if (v == 0) drawEmptyCell(tl, cell)
                else drawGem(tl, cell, Palette.blockIn(theme, v), glow = false)
            }
        }
    }
}
