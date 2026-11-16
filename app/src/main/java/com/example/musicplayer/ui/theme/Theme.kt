/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 深色优先的主题入口。
 *
 * [palette] 决定主色/辅色，[customBackground] 决定底色 —— 两者独立：
 * null 表示跟随主题当前底色，否则由取色器指定任意颜色。
 */
@Composable
fun MusicTheme(
    palette: AppPalette = AppPalette.CELADON,
    customBackground: Color? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = palette.scheme(customBackground),
        content = content
    )
}
