/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.ui.theme.AppPalette
import com.example.musicplayer.ui.theme.toHexString

/**
 * 设置面板：外观（主色 / 背景）+ 启动页动画。
 *
 * 以前主色和背景是顶栏一个独立的调色板按钮，和设置分家；现在统一收进这里，
 * 顶栏只剩「歌词 / 设置」两个动作。
 *
 * 取色器仍然作为面板的第二页（`showColorPicker`），不用 Dialog ——
 * ModalBottomSheet 里再开 Dialog 会有窗口叠层和返回键的问题。
 *
 * 启动页那一段的选择写进 `splash` prefs（见 [SplashGate]），**下次冷启动才生效**：
 * 播不播是在 `MainActivity.onCreate` 里同步决定的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    selectedPalette: AppPalette,
    customBackground: Color?,
    splashMode: SplashGate.Mode,
    onSelectPalette: (AppPalette) -> Unit,
    onApplyBackground: (Color?) -> Unit,
    onSelectSplashMode: (SplashGate.Mode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    var showColorPicker by remember { mutableStateOf(false) }
    val effectiveBackground = customBackground ?: scheme.background

    LaunchedEffect(showColorPicker) {
        scrollState.scrollTo(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow
    ) {
        if (showColorPicker) {
            // 取色页按返回键先回到设置列表；不消费的话会直接把整个面板关掉
            androidx.activity.compose.BackHandler { showColorPicker = false }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            if (showColorPicker) {
                BackgroundColorPicker(
                    initial = effectiveBackground,
                    onApply = { color ->
                        onApplyBackground(color)
                        showColorPicker = false
                    },
                    onFollowTheme = {
                        onApplyBackground(null)
                        showColorPicker = false
                    },
                    onCancel = { showColorPicker = false }
                )
                Spacer(Modifier.height(10.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "外观与启动页 · 立即生效",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("完成") }
                }

                Spacer(Modifier.height(6.dp))

                // ---------------- 外观 ----------------
                SectionLabel("外观")

                AppPalette.entries.forEach { palette ->
                    val active = palette == selectedPalette
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) scheme.primary.copy(alpha = 0.10f) else Color.Transparent
                            )
                            .clickable { onSelectPalette(palette) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PaletteSwatch(palette)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(palette.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${palette.en} · ${palette.description}",
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        if (active) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("背景跟随主题", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (customBackground == null) {
                                "当前跟随 · ${selectedPalette.label}"
                            } else {
                                "当前自定义 · ${customBackground.toHexString()}"
                            },
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = customBackground == null,
                        onCheckedChange = { followTheme ->
                            if (followTheme) onApplyBackground(null) else showColorPicker = true
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { showColorPicker = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(effectiveBackground)
                            .border(1.dp, scheme.outlineVariant, CircleShape)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text("自定义背景色", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (customBackground == null) "从当前底色开始调整" else "HSV 取色 / HEX 输入",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "打开取色器",
                        tint = scheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = scheme.outlineVariant)
                Spacer(Modifier.height(14.dp))

                // ---------------- 启动页 ----------------
                SectionLabel("启动页动画")
                Text(
                    text = "下次冷启动生效",
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                SplashGate.Mode.entries.forEach { mode ->
                    val active = mode == splashMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (active) scheme.primary.copy(alpha = 0.10f) else Color.Transparent
                            )
                            .clickable { onSelectSplashMode(mode) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = mode.hint,
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        if (active) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "选「关闭」后，冷启动只剩系统启动页那一帧（剪影圆章），不会再播" +
                        "黑胶冲出动画。安装新版本时默认回到「跟随版本」，会再播一次。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PaletteSwatch(palette: AppPalette) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.background)
            .border(1.dp, palette.primary.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // 左半主色、右半强调色：比渐变更能看清两端的色相
        Row(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(palette.primary)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(palette.tertiary)
            )
        }
    }
}
