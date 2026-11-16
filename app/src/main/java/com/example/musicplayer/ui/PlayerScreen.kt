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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import com.example.musicplayer.ui.theme.isLightBackground
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

    // 启动页动画的播放时机：面板里点一下立即落盘，下次冷启动生效
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var splashMode by remember { mutableStateOf(SplashGate.mode(context)) }

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
                    // 歌词/封面切换放第一位：它是播放页最常用的动作
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            // 图标说"点一下会得到什么"：正在显示封面 → 歌词图标
                            imageVector = if (showLyrics) Icons.Filled.Album else Icons.Filled.Lyrics,
                            contentDescription = if (showLyrics) "显示封面" else "显示歌词",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 主色/背景已经并进设置面板，这里不再单独放调色板
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = if (showSettings) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
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

                Spacer(Modifier.height(24.dp))

                // S4 细线进度：视觉 2dp，拖拽时才出现滑块 + 时间气泡
                ThinSeekBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedMs = if (showBuffered) engine.bufferedMs else 0L,
                    isScrubbing = isScrubbing,
                    onScrub = {
                        isScrubbing = true
                        scrubPosition = it.toFloat()
                    },
                    onScrubFinished = {
                        viewModel.seekTo(it)
                        isScrubbing = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(positionMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTime(durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左图标：一键静音；右图标：一键满音量
                    IconButton(
                        onClick = { viewModel.updateVolume(0f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (viewModel.volume <= 0f) Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "静音",
                            modifier = Modifier.size(18.dp),
                            tint = if (viewModel.volume <= 0f) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SegmentVolumeBar(
                        value = viewModel.volume,
                        onValueChange = viewModel::updateVolume,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { viewModel.updateVolume(1f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "满音量",
                            modifier = Modifier.size(18.dp),
                            tint = if (viewModel.volume >= 1f) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 播放控制：随机 / 循环
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = viewModel::toggleShuffle,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (engine.shuffle) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "随机播放",
                            tint = if (engine.shuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                IconButton(
                    onClick = viewModel::cycleRepeatMode,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (engine.repeatMode != RepeatMode.OFF)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                ) {
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
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (viewModel.eqEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
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

        // 设置面板：外观（主色 / 背景）+ 启动页动画
        if (showSettings) {
            SettingsSheet(
                selectedPalette = viewModel.palette,
                customBackground = viewModel.customBackground,
                splashMode = splashMode,
                onSelectPalette = viewModel::applyPalette,
                onApplyBackground = viewModel::applyBackground,
                onSelectSplashMode = { mode ->
                    splashMode = mode
                    SplashGate.setMode(context, mode)
                },
                onDismiss = { showSettings = false }
            )
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
    // 浅色底上，低透明度的粉彩会发灰；提高不透明度让占位封面保持色彩
    val isLight = MaterialTheme.colorScheme.background.isLightBackground()
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 优先按高度算边长，父容器不够高时自动缩成能放下的正方形，不会溢出被切
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isLight) 0.82f else 0.55f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = if (isLight) 0.72f else 0.45f)
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
