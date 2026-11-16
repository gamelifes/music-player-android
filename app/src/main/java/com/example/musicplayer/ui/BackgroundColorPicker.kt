/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.ui.theme.parseHexColor
import com.example.musicplayer.ui.theme.toHexString

/**
 * 背景取色器：HSV 取色面板 + 色相条 + HEX 输入。
 *
 * 直接嵌在主题面板里（不另开 Dialog），避免 ModalBottomSheet + Dialog 的窗口叠层问题。
 * 只负责选颜色，surface/onBackground 的推导在 AppPalette.scheme() 里完成。
 */
@Composable
fun BackgroundColorPicker(
    initial: Color,
    onApply: (Color) -> Unit,
    onFollowTheme: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hsv = remember(initial) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hexText by remember { mutableStateOf(initial.toHexString()) }

    fun pickedColor(): Color = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
        )
    )

    fun syncHex() {
        hexText = pickedColor().toHexString()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("背景颜色", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "拖动选色或输入 HEX；浅色底会自动适配文字和状态栏",
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(pickedColor())
                    .border(1.dp, scheme.outlineVariant, CircleShape)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("预览", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                Text(
                    text = pickedColor().toHexString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 饱和度 × 明度
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        fun apply(position: Offset) {
                            saturation = (position.x / size.width).coerceIn(0f, 1f)
                            value = (1f - position.y / size.height).coerceIn(0f, 1f)
                            syncHex()
                        }

                        apply(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) apply(change.position)
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            val x = saturation * size.width
            val y = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(x, y),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(color = pickedColor(), radius = 6.dp.toPx(), center = Offset(x, y))
        }

        Spacer(Modifier.height(12.dp))

        // 色相
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        fun apply(position: Offset) {
                            hue = (position.x / size.width).coerceIn(0f, 1f) * 360f
                            syncHex()
                        }

                        apply(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) apply(change.position)
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    )
                )
            )
            val x = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = pickedColor(),
                radius = 6.dp.toPx(),
                center = Offset(x, size.height / 2f)
            )
        }

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = hexText,
            onValueChange = { raw ->
                val filtered = raw.uppercase()
                    .filterIndexed { index, c ->
                        (c == '#' && index == 0) || c.isDigit() || c in 'A'..'F'
                    }
                    .take(7)
                hexText = filtered
                parseHexColor(filtered)?.let { color ->
                    val values = FloatArray(3)
                    AndroidColor.colorToHSV(color.toArgb(), values)
                    hue = values[0]
                    saturation = values[1]
                    value = values[2]
                }
            },
            label = { Text("HEX") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onFollowTheme) { Text("跟随主题") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = { onApply(pickedColor()) }) { Text("应用") }
        }
    }
}
