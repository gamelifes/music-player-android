/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * S4「细线」播放进度条。
 *
 * 视觉只有 2dp 发丝线，平时不显示滑块；
 * 按下/拖动时才浮出白点 + 时间气泡，松手后 seek。
 * 触控热区固定 44dp，不随视觉高度缩水。
 */
@Composable
fun ThinSeekBar(
    positionMs: Long,
    durationMs: Long,
    isScrubbing: Boolean,
    onScrub: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0L,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val thumbColor = MaterialTheme.colorScheme.onBackground
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val bubbleContent = MaterialTheme.colorScheme.inverseOnSurface
    val haptic = LocalHapticFeedback.current

    val duration = durationMs.coerceAtLeast(0L)
    val fraction = if (duration > 0) {
        (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f
    val bufferedFraction = if (duration > 0) {
        (bufferedMs.toFloat() / duration).coerceIn(0f, fraction.coerceAtLeast(0f))
    } else 0f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val width = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 2.dp.toPx()
            val centerY = size.height / 2f
            val radius = trackHeight / 2f

            drawRoundRect(
                color = onSurface.copy(alpha = 0.16f),
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )

            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = onSurface.copy(alpha = 0.10f),
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(size.width * bufferedFraction, trackHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            if (fraction > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(primary, tertiary)),
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(size.width * fraction, trackHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            if (isScrubbing) {
                val thumbRadius = 8.dp.toPx()
                val x = (size.width * fraction).coerceIn(thumbRadius, size.width - thumbRadius)
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(x, centerY)
                )
                drawCircle(
                    color = primary.copy(alpha = 0.32f),
                    radius = thumbRadius + 4.dp.toPx(),
                    center = Offset(x, centerY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        if (isScrubbing) {
            val bubbleWidth = 56.dp
            val maxX = (width - bubbleWidth).coerceAtLeast(0.dp)
            val x = (width * fraction - bubbleWidth / 2f).coerceIn(0.dp, maxX)
            Surface(
                color = bubbleColor,
                contentColor = bubbleContent,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = x, y = (-22).dp)
            ) {
                Text(
                    text = formatTime(positionMs),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "播放进度"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        onScrubFinished((target.coerceIn(0f, 1f) * duration).roundToLong())
                        true
                    }
                }
                .pointerInput(duration) {
                    if (duration <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        fun xToMs(x: Float): Long =
                            ((x / size.width).coerceIn(0f, 1f) * duration).roundToLong()

                        var lastMs = xToMs(down.position.x)
                        onScrub(lastMs)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastMs = xToMs(change.position.x)
                            if (change.pressed) onScrub(lastMs)
                            change.consume()
                            if (!change.pressed) break
                        }
                        onScrubFinished(lastMs)
                    }
                }
        )
    }
}

/**
 * S3「分段 LED」音量条。
 *
 * 固定 [segments] 段（默认 10），段宽按可用宽度自适应；
 * 已点亮段从 primary 渐变到 tertiary，未点亮段只留 10% 白。
 * 段数固定、宽度自适应；音量按 1% 吸附，当前段按比例部分点亮，
 * 触感每 5% 给一次（每段 2 下），既能微调又不会一直震。
 */
@Composable
fun SegmentVolumeBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    segments: Int = 10,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val haptic = LocalHapticFeedback.current
    val latestOnChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    val safeSegments = segments.coerceAtLeast(1)
    val exact = value.coerceIn(0f, 1f) * safeSegments
    val fullSegments = exact.toInt().coerceIn(0, safeSegments)
    val partial = (exact - fullSegments).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = "音量"
                // 触摸按 1% 调整；无障碍步进用 5%，避免 TalkBack 要滑 100 次
                progressBarRangeInfo =
                    ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f, VOLUME_TICK_STEPS - 1)
                setProgress { target ->
                    val snapped = (target.coerceIn(0f, 1f) * VOLUME_STEPS)
                        .roundToInt() / VOLUME_STEPS.toFloat()
                    latestOnChange(snapped)
                    true
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 4.dp.toPx()
            val height = 10.dp.toPx()
            val maxCorner = 3.dp.toPx()
            val corner = CornerRadius(maxCorner, maxCorner)
            val segmentWidth = ((size.width - gap * (safeSegments - 1)) / safeSegments)
                .coerceAtLeast(1f)
            val top = (size.height - height) / 2f

            for (i in 0 until safeSegments) {
                val left = i * (segmentWidth + gap)
                // 底槽：未点亮段也保留形状，整条轨道不会「缺一段」
                drawRoundRect(
                    color = onSurface.copy(alpha = 0.10f),
                    topLeft = Offset(left, top),
                    size = Size(segmentWidth, height),
                    cornerRadius = corner
                )

                val litRatio = when {
                    i < fullSegments -> 1f
                    i == fullSegments -> partial
                    else -> 0f
                }
                if (litRatio > 0f) {
                    val litWidth = (segmentWidth * litRatio).coerceAtLeast(1f)
                    val litCorner = minOf(maxCorner, litWidth / 2f)
                    val ratio = if (safeSegments > 1) i / (safeSegments - 1f) else 0f
                    drawRoundRect(
                        color = lerp(primary, tertiary, ratio),
                        topLeft = Offset(left, top),
                        size = Size(litWidth, height),
                        cornerRadius = CornerRadius(litCorner, litCorner)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(safeSegments) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        var lastTick = (currentValue * VOLUME_TICK_STEPS).roundToInt()
                        fun apply(x: Float) {
                            val fraction = (x / size.width).coerceIn(0f, 1f)
                            val snapped = (fraction * VOLUME_STEPS).roundToInt()
                                .coerceIn(0, VOLUME_STEPS) / VOLUME_STEPS.toFloat()
                            val tick = (snapped * VOLUME_TICK_STEPS).roundToInt()
                            if (tick != lastTick) {
                                lastTick = tick
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            latestOnChange(snapped)
                        }

                        apply(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) apply(change.position.x)
                            change.consume()
                            if (!change.pressed) break
                        }
                    }
                }
        )
    }
}

/** 音量精度：100 级 = 1% */
private const val VOLUME_STEPS = 100

/** 触感刻度：20 格 = 每 5% 一次 */
private const val VOLUME_TICK_STEPS = 20
