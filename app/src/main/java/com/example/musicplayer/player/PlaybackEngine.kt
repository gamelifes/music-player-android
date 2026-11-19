/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.example.musicplayer.player

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.musicplayer.data.RepeatMode
import com.example.musicplayer.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 全局播放器引擎。UI 与前台服务共享同一个 ExoPlayer 实例，
 * 避免 Service / Activity 跨进程绑定带来的复杂度。
 */
object PlaybackEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** 自动切歌/队列切换时回调当前下标，供 ViewModel 同步「正在播放」的歌曲与歌词 */
    var onCurrentIndexChanged: ((Int) -> Unit)? = null

    /** 十段均衡器实例，UI 直接调节；音频链路上始终生效 */
    val equalizer: TenBandEqualizerProcessor = TenBandEqualizerProcessor()

    /**
     * 需要立即跳过的曲目判断（例如已拉黑）。
     * 队列是 setQueue 时的快照，运行中途拉黑不会回流到队列，
     * 所以轮到那首时必须主动切走，否则黑名单曲仍会被放出来。
     */
    var skipPredicate: ((Long) -> Boolean)? = null

    /** 当前播放队列，用于按下标精准取 Song */
    private var currentQueue: List<Song> = emptyList()

    fun getCurrentQueue(): List<Song> = currentQueue

    /** 当前队列长度，供 UI 判断播放控制是否可用 */
    fun queueSize(): Int = currentQueue.size

    /** 当前播放下标 */
    fun currentIndex(): Int = player?.currentMediaItemIndex ?: -1

    private var player: ExoPlayer? = null
    private var positionTicker: Job? = null
    private var serviceStarted = false

    /** 待应用到下一次创建的 ExoPlayer 实例上的持久化设置，保证重启后显示与实际一致 */
    private var pendingVolume: Float = 1f

    /**
     * 播放器被释放时（服务被系统回收、通知被划掉）记住的播放位置。
     * 重新创建 ExoPlayer 时从这里续上，而不是回到第 0 首、0 秒。
     */
    private var resumeIndex: Int = 0
    private var resumePositionMs: Long = 0L

    /** 一次 STATE_ENDED 只处理一次，防止 listener + ticker 双重触发 */
    private var endHandled = false

    /** 连锁跳过的计数，防止整队都被跳过时死循环 */
    private var skipHops = 0

    data class EngineState(
        val currentIndex: Int = -1,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedMs: Long = 0L,
        val repeatMode: RepeatMode = RepeatMode.OFF,
        val shuffle: Boolean = false,
    )

    fun ensurePlayer(context: Context): ExoPlayer {
        player?.let { return it }

        // 把 10 段均衡器注入音频链路。
        // 必须显式关闭 offload：否则 ExoPlayer 会把音频交给硬解通道，
        // 那条路完全绕过 AudioProcessor，表现为"滑块能动但没效果"。
        val audioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false)
            // Media3 1.4.1 没有 setOffloadModeEnabled，改为用 provider 直接声明不支持 offload。
            // 必须关掉 offload：否则 ExoPlayer 把音频交给硬解通道，那条路完全绕过
            // AudioProcessor，表现为"滑块能动但没效果"。
            .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
            .setAudioProcessors(arrayOf(equalizer))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = audioSink
        }

        val exo = ExoPlayer.Builder(context, renderersFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 音频焦点：handleAudioFocus=true 让 ExoPlayer 自管焦点，
        // 配合 setHandleAudioBecomingNoisy 实现拔耳机/来电自动暂停
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startTicker() else stopTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                endHandled = false
                val idx = if (mediaItem != null) exo.currentMediaItemIndex else -1
                _state.update {
                    it.copy(
                        currentIndex = idx,
                        positionMs = 0L,
                        durationMs = if (idx >= 0) it.durationMs else 0L
                    )
                }

                // 轮到黑名单曲立即切走。过渡回调里直接 seek 有重入风险，
                // post 到下一轮主线程消息再执行；hop 计数兜底防死循环。
                val songId = mediaItem?.mediaId?.toLongOrNull()
                val pred = skipPredicate
                if (pred != null && songId != null && pred(songId) && skipHops < MAX_SKIP_HOPS) {
                    skipHops++
                    scope.launch(Dispatchers.Main) { skipCurrent() }
                    return
                }
                skipHops = 0
                onCurrentIndexChanged?.invoke(idx)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        durationMs = exo.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        positionMs = exo.currentPosition.coerceAtLeast(0L)
                    )
                }
                // 结束只由 ExoPlayer 的 STATE_ENDED 驱动一次；
                // REPEAT_ALL / REPEAT_ONE / 非末首全部交给 ExoPlayer 原生处理
                if (playbackState == Player.STATE_ENDED) {
                    onTrackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackEngine", "Player error: ${error.message}", error)
                endHandled = false
                _state.update { it.copy(isPlaying = false) }
            }
        })

        player = exo
        // 若媒体项已丢失（例如服务 onDestroy 后重建），但队列尚在，则恢复媒体上下文。
        // setMediaItems 后必须 prepare()，否则 mediaItemCount 可能仍为 0，
        // 导致后续 togglePlayPause 提前返回而无法播放。
        if (currentQueue.isNotEmpty() && exo.mediaItemCount == 0) {
            val items = currentQueue.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.contentUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }
            // 注意：这里 player 还没赋值，不能用 currentIndex() 取下标
            // （恒为 -1 → 永远回到第 0 首）。用 release() 记下的位置续播。
            exo.setMediaItems(items, resumeIndex.coerceIn(0, items.lastIndex), resumePositionMs)
            exo.prepare()
        }
        // 音量/随机/循环一律以 state 为唯一来源：即使播放器曾被释放，
        // UI 上看到的模式也会原样作用到新实例上，不会出现「显示开、实际关」。
        exo.volume = pendingVolume
        exo.shuffleModeEnabled = _state.value.shuffle
        exo.repeatMode = _state.value.repeatMode.toExoRepeatMode()
        return exo
    }

    // ------------------------------------------------------------- 播放控制

    fun setQueue(context: Context, songs: List<Song>, startIndex: Int) {
        val exo = ensurePlayer(context)
        currentQueue = songs
        resumeIndex = startIndex.coerceAtLeast(0)
        resumePositionMs = 0L
        skipHops = 0
        val items = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
        }
        exo.setMediaItems(items, startIndex.coerceAtLeast(0), 0L)
        exo.prepare()
    }

    /**
     * 恢复上次播放的队列：装载 MediaItem 并 seek 到上次进度，
     * 但不调用 play()，也不启动前台服务（用户主动播放时才起）。
     */
    fun restoreQueue(context: Context, songs: List<Song>, startIndex: Int, positionMs: Long) {
        val exo = ensurePlayer(context)
        currentQueue = songs
        resumeIndex = startIndex.coerceAtLeast(0)
        resumePositionMs = positionMs.coerceAtLeast(0L)
        skipHops = 0
        val items = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
        }
        exo.setMediaItems(items, startIndex.coerceAtLeast(0), positionMs.coerceAtLeast(0L))
        exo.prepare()
        _state.update {
            it.copy(
                currentIndex = startIndex.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0L),
                isPlaying = false // 仅恢复队列，不自动播放
            )
        }
    }

    fun play(context: Context) {
        val exo = ensurePlayer(context)
        // 先进入播放态再拉服务：
        // 服务起来时 media3 会看 playWhenReady + 播放状态，
        // 已经是播放态才会立刻 startForeground，通知栏的播放卡片马上出现。
        exo.play()
        startPlaybackService(context)
    }

    /**
     * 启动前台播放服务，拿到 MediaSession 与通知栏控制。
     *
     * 这里用普通 startService 而不是 startForegroundService：
     * media3 在「开始播放」时会自己把服务提升为前台服务
     * （MediaNotificationManager 内部会 startForegroundService + startForeground），
     * 这样即使遇到空队列/坏文件导致没能及时提升前台，
     * 也不会因为 startForegroundService 未在 5 秒内调用 startForeground 而被系统判超时。
     */
    private fun startPlaybackService(context: Context) {
        // 不能用 serviceStarted 提前返回：
        // 服务被系统/用户划掉后该标志仍为 true，会导致再也起不来 → 真机上播放停摆
        val intent = Intent(context, MusicPlayerService::class.java)
        val started = runCatching {
            context.startService(intent)
            true
        }.getOrElse {
            // 后台启动受限（Android 8+）时退回前台服务启动方式
            runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrDefault(false)
        }
        serviceStarted = started
    }

    @Suppress("unused")
    fun pause() {
        player?.pause()
    }

    fun togglePlayPause(context: Context) {
        val exo = ensurePlayer(context)
        // 无队列时无法播放，直接返回
        if (currentQueue.isEmpty()) return
        // 正在播放 -> 暂停
        if (exo.isPlaying) {
            exo.pause()
            return
        }
        // 未在播放：先确保媒体项存在且 player 已 prepare。
        // 关闭灵动岛/服务挂起后，ExoPlayer 可能进入 STATE_IDLE 或媒体项被回收，
        // 此时直接 play() 只设置 playWhenReady，不会真正开始播放；
        // 系统播放卡片走 MediaSession 时 Media3 会自动做这一步，所以它能续播。
        if (exo.mediaItemCount == 0) {
            val items = currentQueue.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.contentUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }
            exo.setMediaItems(items, resumeIndex.coerceIn(0, items.lastIndex), resumePositionMs)
        }
        if (exo.playbackState == Player.STATE_IDLE) {
            exo.prepare()
        }
        exo.play()
        startPlaybackService(context)
    }

    fun seekTo(positionMs: Long) {
        val exo = player ?: return
        val target = positionMs.coerceAtLeast(0L)
        // 仅在 duration 有效时夹住上界，避免拖过真实结尾直接触发结束
        val dur = exo.duration
        val clamped = if (dur > 0) target.coerceAtMost(dur - 1) else target
        exo.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun next() {
        player?.seekToNextMediaItem()
    }

    fun previous() {
        player?.seekToPreviousMediaItem()
    }

    /** 用于耳机按键/媒体按钮：确保服务在后台运行并执行切歌 */
    fun next(context: Context) {
        startPlaybackService(context)
        player?.seekToNextMediaItem()
    }

    /** 用于耳机按键/媒体按钮：确保服务在后台运行并执行切歌 */
    fun previous(context: Context) {
        startPlaybackService(context)
        player?.seekToPreviousMediaItem()
    }

    /**
     * 主动跳过当前曲（拉黑等场景）。
     * 有下一首（或 REPEAT_ALL 可回绕）则切过去；REPEAT_OFF 且在末首时
     * seekToNextMediaItem 是 no-op，必须显式停下，否则被拉黑的歌会继续放完。
     */
    fun skipCurrent() {
        val exo = player ?: return
        val index = exo.currentMediaItemIndex
        val hasNextItem = index >= 0 && index < exo.mediaItemCount - 1
        val canWrap = exo.repeatMode == Player.REPEAT_MODE_ALL
        if (hasNextItem || canWrap) {
            exo.seekToNextMediaItem()
        } else {
            exo.seekTo(0L)
            exo.pause()
            _state.update { it.copy(positionMs = 0L) }
        }
    }

    @Suppress("unused")
    fun jumpTo(index: Int) {
        val exo = player ?: return
        if (index !in 0 until exo.mediaItemCount) return
        exo.seekTo(index, 0L)
        exo.play()
    }

    @Suppress("unused")
    fun seekForward() {
        val exo = player ?: return
        exo.seekTo((exo.currentPosition + 10_000L).coerceAtMost(maxOf(0L, exo.duration)))
    }

    @Suppress("unused")
    fun seekBackward() {
        val exo = player ?: return
        exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L))
    }

    fun setVolume(volume: Float) {
        pendingVolume = volume.coerceIn(0f, 1f)
        player?.volume = pendingVolume
    }

    fun setRepeatMode(mode: RepeatMode) {
        // 先写 state，再同步给播放器。
        // 播放器可能尚未创建（服务被系统回收后刚打开应用、还没点歌），
        // 以前这里直接 return，导致循环按钮整个失灵；现在改为
        // 「状态先生效，播放器创建时自动应用」。
        _state.update { it.copy(repeatMode = mode) }
        player?.repeatMode = mode.toExoRepeatMode()
    }

    fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(shuffle = enabled) }
        player?.shuffleModeEnabled = enabled
    }

    private fun RepeatMode.toExoRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    /** 服务被销毁（用户划掉任务/系统回收）时由 Service 回调，复位启动标志 */
    fun notifyServiceDestroyed() {
        serviceStarted = false
    }

    fun release() {
        stopTicker()
        // 先记下播放位置，再释放播放器。服务被系统回收后重新打开应用时，
        // 从这里续播同一首、同一进度，而不是回到第 0 首。
        player?.let { exo ->
            runCatching {
                if (exo.mediaItemCount > 0 && exo.currentMediaItemIndex >= 0) {
                    resumeIndex = exo.currentMediaItemIndex
                    resumePositionMs = exo.currentPosition.coerceAtLeast(0L)
                }
            }
        }
        player?.release()
        player = null
        // 不清空 currentQueue/resume 位置：保留队列供重新打开应用时快速恢复，
        // 避免服务 onDestroy 后队列丢失、播放按钮无反应或从第 0 首重放。
        serviceStarted = false
        endHandled = false
        skipHops = 0
        // 只回落播放态，保留当前曲、随机、循环模式——它们是用户设置，
        // 不能因为服务被系统杀掉就被悄悄清成「关」。
        _state.update {
            it.copy(
                isPlaying = false,
                positionMs = resumePositionMs,
                bufferedMs = 0L
            )
        }
    }

    // ---------------------------------------------------------- 播放结束处理

    /**
     * 仅在 REPEAT_OFF 且播完末首时需要处理：回到 0 并暂停。
     *
     * REPEAT_ALL / REPEAT_ONE / 非末首全部交给 ExoPlayer 原生处理——
     * 它已经按 repeatMode 自动切歌与循环，代码再手动 seekToNextMediaItem/play()
     * 会造成「一次结束跳两首」和与 STATE_ENDED 的双重触发。
     */
    private fun onTrackEnded() {
        val exo = player ?: return
        if (endHandled) return
        endHandled = true

        val isLastItem = exo.currentMediaItemIndex >= exo.mediaItemCount - 1
        if (exo.repeatMode == Player.REPEAT_MODE_OFF && isLastItem) {
            // 先暂停再 seek：STATE_ENDED 时 playWhenReady 仍是 true，
            // 先 seek 会瞬间重新起播，表现为「最后一首放完又从头响一下」。
            exo.pause()
            exo.seekTo(0, 0L)
            _state.update { it.copy(positionMs = 0L) }
        }
    }

    // ---------------------------------------------------------- 进度定时器

    private fun startTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = scope.launch {
            while (true) {
                val exo = player
                if (exo != null) {
                    val pos = exo.currentPosition.coerceAtLeast(0L)
                    val dur = exo.duration.takeIf { d -> d > 0 }
                    val buf = exo.bufferedPosition.coerceAtLeast(0L)
                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = dur ?: it.durationMs,
                            // 缓冲值仅在有效时才更新，避免本地文件瞬满后回摆
                            bufferedMs = if (dur != null) buf else it.bufferedMs
                        )
                    }
                    // 不在这里做结束判定：ExoPlayer 的 STATE_ENDED 才是唯一权威，
                    // 且 repeatMode 已设置时它会自动切歌。手工判定会与原生双重触发。
                }
                delay(TICK_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun stopTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    private const val TICK_INTERVAL_MS = 200L

    /** 连续跳过的上限，兜底防止「整队都该跳过」时死循环 */
    private const val MAX_SKIP_HOPS = 50
}
