package com.hourlog.app.ui

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils

fun hourLogColors(seedHex: String, dark: Boolean): ColorScheme {
    val seed = android.graphics.Color.parseColor("#$seedHex")
    fun blend(base: Int, proportion: Float) = Color(ColorUtils.blendARGB(seed, base, proportion))
    var primary = seed
    if (!dark) while (ColorUtils.calculateContrast(android.graphics.Color.WHITE, primary) < 4.5)
        primary = ColorUtils.blendARGB(primary, android.graphics.Color.BLACK, 0.1f)
    var inverseAccent = seed
    val inverseLightSurface = ColorUtils.blendARGB(seed, android.graphics.Color.WHITE, .90f)
    if (dark) while (ColorUtils.calculateContrast(inverseLightSurface, inverseAccent) < 4.5)
        inverseAccent = ColorUtils.blendARGB(inverseAccent, android.graphics.Color.BLACK, .1f)
    if (dark) return darkColorScheme(
        primary = blend(-1, .65f), onPrimary = Color(0xff111318), primaryContainer = blend(0xff111318.toInt(), .70f),
        onPrimaryContainer = Color(0xfff2f2f2), secondary = blend(-1, .60f), onSecondary = Color(0xff111318),
        secondaryContainer = blend(0xff292b30.toInt(), .85f), onSecondaryContainer = Color(0xfff2f2f2),
        tertiary = blend(-1, .65f), onTertiary = Color(0xff111318),
        tertiaryContainer = blend(0xff111318.toInt(), .70f), onTertiaryContainer = Color(0xfff2f2f2),
        surface = blend(0xff111318.toInt(), .96f), background = blend(0xff111318.toInt(), .96f), onBackground = Color(0xfff2f2f2),
        surfaceDim = blend(0xff111318.toInt(), .96f), surfaceBright = blend(0xff36383d.toInt(), .94f),
        surfaceContainerLowest = blend(0xff0c0e13.toInt(), .98f), surfaceContainerLow = blend(0xff191b20.toInt(), .96f),
        surfaceContainer = blend(0xff202227.toInt(), .94f), surfaceContainerHigh = blend(0xff292b30.toInt(), .92f),
        surfaceContainerHighest = blend(0xff33353a.toInt(), .90f), surfaceVariant = blend(0xff33353a.toInt(), .90f),
        surfaceTint = blend(-1,.65f), inverseSurface = Color(inverseLightSurface), inverseOnSurface = Color(0xff191b20), inversePrimary = Color(inverseAccent),
        outline = Color(0xff92949a), outlineVariant = Color(0xff44474d),
        onSurface = Color(0xfff2f2f2), onSurfaceVariant = Color(0xffd0d2d8))
    return lightColorScheme(primary = Color(primary), onPrimary = Color.White, primaryContainer = blend(-1,.84f),
        onPrimaryContainer = Color(0xff191b20), secondary = Color(primary), onSecondary = Color.White,
        secondaryContainer = blend(-1,.90f), onSecondaryContainer = Color(0xff191b20),
        tertiary = Color(primary), onTertiary = Color.White, tertiaryContainer = blend(-1,.84f), onTertiaryContainer = Color(0xff191b20),
        surface = blend(-1,.985f), background = blend(-1,.985f), onBackground = Color(0xff191b20),
        surfaceDim = blend(-1,.87f), surfaceBright = blend(-1,.985f), surfaceContainerLowest = Color.White,
        surfaceContainerLow = blend(-1,.975f), surfaceContainer = blend(-1,.95f), surfaceContainerHigh = blend(-1,.925f),
        surfaceContainerHighest = blend(-1,.90f), surfaceVariant = blend(-1,.90f), surfaceTint = Color(primary),
        inverseSurface = blend(0xff292b30.toInt(),.92f), inverseOnSurface = Color(0xfff2f2f2), inversePrimary = blend(-1,.65f),
        outline = Color(0xff74777e), outlineVariant = Color(0xffc4c6cc),
        onSurface = Color(0xff191b20), onSurfaceVariant = Color(0xff44474d))
}
