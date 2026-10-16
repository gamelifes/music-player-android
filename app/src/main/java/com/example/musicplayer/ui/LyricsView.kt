package com.example.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.LyricLine
import com.example.musicplayer.data.LyricParser
import kotlinx.coroutines.delay

/** 可视高度很小时（横屏等）的兜底留白 */
private val MIN_VERTICAL_PADDING = 80.dp

/**
 * 歌词视图：自动滚动居中 + A2 逐词高亮。
 *
 * 关键点：LazyColumn 的 key 用「行号 + timeMs」复合，避免合成歌词出现
 * 多行共享同一 timeMs 时 Compose 抛「Key was already used」崩溃（真机复现）。
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // 当前激活行索引（未到第一句返回 -1，调用方已夹逼）
    val activeIndex = LyricParser.indexAt(lyrics, positionMs)
    val safeActiveIndex = activeIndex.coerceIn(0, (lyrics.size - 1).coerceAtLeast(0))

    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词\n把同名 .lrc 文件放在歌曲同一目录即可自动加载",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }

    val activeLine = lyrics.getOrNull(safeActiveIndex)
    val nextLine = lyrics.getOrNull(safeActiveIndex + 1)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current.density
        val viewportHeightDp = (viewportHeightPx / density).dp

        // 上下留白 ≥ 视口一半，保证每一行都能被滚到中线
        val verticalPadding = (viewportHeightDp.value / 2f).dp
            .coerceAtLeast(MIN_VERTICAL_PADDING)

        // 布局完成后再滚动，避免首帧 layoutInfo 未就绪崩溃
        LaunchedEffect(safeActiveIndex, lyrics, viewportHeightPx) {
            if (viewportHeightPx <= 0f) return@LaunchedEffect
            // 等首帧布局：visibleItemsInfo 为空说明还没完成首次排版
            var waited = 0
            while (listState.layoutInfo.visibleItemsInfo.isEmpty() && waited < 100) {
                delay(16)
                waited++
            }
            if (listState.layoutInfo.visibleItemsInfo.isEmpty()) return@LaunchedEffect
            runCatching { listState.animateScrollToItem(safeActiveIndex, 0) }
        }

        // 渐变遮罩高度 = 视口的 12%（对称），保证清晰区足够放下整行歌词
        val fadeHeight = (viewportHeightDp.value * 0.12f).coerceAtLeast(8f).dp

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 28.dp,
                    vertical = verticalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 复合 key：行号打头，彻底规避重复 timeMs
                itemsIndexed(
                    items = lyrics,
                    key = { index, line -> "$index-${line.timeMs}" }
                ) { _, line ->
                    LyricRow(
                        line = line,
                        isActive = line === activeLine,
                        positionMs = positionMs,
                        lineDurationMs = nextLine?.timeMs?.minus(line.timeMs)
                            ?.takeIf { it > 0 }
                            ?.coerceAtLeast(1L)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    positionMs: Long,
    lineDurationMs: Long?
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    // 非当前行：整体压暗
    val inactiveColor = onSurface.copy(alpha = 0.28f)
    // 当前行「还没唱到」的部分：中等亮度，作为已唱高亮部分的底色
    val pendingColor = onSurface.copy(alpha = 0.75f)

    val annotated = when {
        !isActive -> buildAnnotatedString { append(line.text) }

        // A2 格式：按词级时间戳逐词高亮（保留的核心亮点）
        line.hasWordTiming -> {
            // activeWordIndex 已由 parser 夹逼到合法区间，防御性再夹一次
            val activeWord = LyricParser.activeWordIndex(line, positionMs)
                .coerceIn(0, (line.words.size - 1).coerceAtLeast(0))
            buildAnnotatedString {
                line.words.forEachIndexed { i, w ->
                    when {
                        // 已唱过的词：主色但淡一点，形成拖尾
                        i < activeWord ->
                            withStyle(SpanStyle(color = primary.copy(alpha = 0.6f))) { append(w.text) }
                        // 正在唱的词：最亮
                        i == activeWord -> withStyle(SpanStyle(color = primary)) { append(w.text) }
                        // 未唱到的词：继承 Text 的 pendingColor
                        else -> append(w.text)
                    }
                }
            }
        }

        // 普通 LRC：把该行时长均摊到每个字符做扫光。
        // 注意：这是**估算**，普通 LRC 里并没有逐字时间戳。
        lineDurationMs != null && lineDurationMs > 0 -> {
            val elapsed = (positionMs - line.timeMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / lineDurationMs).coerceIn(0f, 1f)
            val litChars = (progress * line.text.length)
                .toInt()
                .coerceIn(0, line.text.length)
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) { append(line.text.take(litChars)) }
                append(line.text.drop(litChars))
            }
        }

        else -> buildAnnotatedString { append(line.text) }
    }

    Text(
        text = annotated,
        // 底色必须是「暗色」，已唱部分才靠 span 显出主色形成对比
        color = if (isActive) pendingColor else inactiveColor,
        fontSize = if (isActive) 20.sp else 16.sp,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}