/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.example.musicplayer.player.PlaybackEngine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.musicplayer.data.RepeatMode
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onOpenLibrary: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    val engine by viewModel.engineState.collectAsState()
    val song = viewModel.currentSong

    var showLyrics by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    // duration 单一来源：ExoPlayer 回报的真实时长；仅在它还没解析出来时
    // 才短暂兜底 MediaStore 元数据。真实值一旦到位立即覆盖，不会半途跳变。
    val durationMs = engine.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0L
    val positionMs = if (isScrubbing) scrubPosition.toLong() else engine.positionMs

    // 缓冲层只在确实有"未播但已缓存"的区间时显示。
    // 本地文件缓冲几乎瞬满（bufferedPosition == duration），此层没有信息量，
    // 会造成「缓存满了播放没跟上」的误解，故直接隐藏。
    val showBuffered = engine.bufferedMs in 1 until durationMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("正在播放", fontSize = 16.sp)
                        if (song != null) {
                            Text(
                                text = song.album,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Filled.Lyrics,
                            contentDescription = if (showLyrics) "显示封面" else "显示歌词",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 队列非空即可操作，不再只因 currentSong 为 null 就把按钮禁死
                    val controlsEnabled = PlaybackEngine.queueSize() > 0

                    IconButton(onClick = viewModel::previous, enabled = controlsEnabled) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(
                        onClick = viewModel::togglePlayPause,
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = if (engine.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = viewModel::next, enabled = controlsEnabled) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showLyrics) 1f else 1f)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LyricsView(
                        lyrics = viewModel.lyrics,
                        positionMs = positionMs,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AlbumArt(albumArtUri = song?.albumArtUri?.toString())
                }
            }

            if (!showLyrics) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song?.title ?: "未选择歌曲",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song?.artist ?: "点击下方曲库或歌单选择歌曲",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(16.dp))

                // duration 与 fraction 用同一个快照算，避免拖动过程中区间漂移
                val durationRange = durationMs.toFloat().coerceAtLeast(1f)
                val safePosition = positionMs.coerceIn(0L, durationMs)
                val playedFraction = (safePosition.toFloat() / durationRange).coerceIn(0f, 1f)

                Box(modifier = Modifier.fillMaxWidth()) {
                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    )
                    // Buffered segment：仅当缓冲明显领先于播放位置时才显示（流媒体场景）
                    if (showBuffered) {
                        val bufferedFraction =
                            (engine.bufferedMs.toFloat() / durationRange).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferedFraction)
                                .height(6.dp)
                                .align(Alignment.CenterStart)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                        )
                    }
                    // Played segment
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(playedFraction)
                            .height(6.dp)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    // Transparent slider for drag interaction
                    Slider(
                        value = safePosition.toFloat(),
                        onValueChange = {
                            isScrubbing = true
                            scrubPosition = it
                        },
                        onValueChangeFinished = {
                            viewModel.seekTo(scrubPosition.toLong())
                            isScrubbing = false
                        },
                        valueRange = 0f..durationRange,
                        enabled = durationMs > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTime(durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = viewModel.volume,
                        onValueChange = viewModel::updateVolume,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 播放控制：随机 / 循环
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "随机播放",
                            tint = if (engine.shuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                IconButton(onClick = viewModel::cycleRepeatMode) {
                    Icon(
                        imageVector = if (engine.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne
                        else Icons.Filled.Repeat,
                        contentDescription = "循环模式",
                        tint = if (engine.repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { showEq = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Equalizer,
                        contentDescription = "均衡器",
                        modifier = Modifier.size(18.dp),
                        tint = if (viewModel.eqEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

                Spacer(Modifier.height(4.dp))

                // 页面导航：曲库 / 黑名单 / 歌单
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "曲库")
                    }
                    IconButton(onClick = onOpenBlacklist) {
                        Icon(Icons.Filled.Block, contentDescription = "黑名单")
                    }
                    IconButton(onClick = onOpenPlaylists) {
                        Icon(Icons.Filled.LibraryMusic, contentDescription = "歌单")
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }

        // 均衡器弹窗
        if (showEq) {
            val eqSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showEq = false },
                sheetState = eqSheetState
            ) {
                EqualizerSheet(viewModel = viewModel, onDismiss = { showEq = false })
            }
        }
    }
}

@Composable
private fun AlbumArt(albumArtUri: String?) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 优先按高度算边长，父容器不够高时自动缩成能放下的正方形，不会溢出被切
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (albumArtUri != null) {
            AsyncImage(
                model = albumArtUri,
                contentDescription = "专辑封面",
                // 图片自身也裁一次圆角：不依赖父级 clip，保证四角完全一致
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
