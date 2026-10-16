/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicApp
import com.example.musicplayer.data.LyricLine
import com.example.musicplayer.data.LyricParser
import com.example.musicplayer.data.MediaStoreScanner
import com.example.musicplayer.data.PlaybackPrefs
import com.example.musicplayer.data.RepeatMode
import com.example.musicplayer.data.Song
import com.example.musicplayer.data.db.PlaylistEntity
import com.example.musicplayer.player.PlaybackEngine
import com.example.musicplayer.player.TenBandEqualizerProcessor
import com.example.musicplayer.player.TenBandEqualizerProcessor.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as MusicApp).repository
    private val playbackPrefs = (app as MusicApp).playbackPrefs

    /** 上次落盘的播放进度，避免每 200ms 的 tick 都写盘 */
    private var lastPersistedPosition = 0L

    // ------------------------------------------------------- Compose 状态

    var currentSong by mutableStateOf<Song?>(null)
        private set
    var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** 当前播放来源：0=曲库, 1=歌单 */
    var currentSource by mutableIntStateOf(0)
        private set
    /** 当前播放的歌单 ID（currentSource=1 时有效） */
    var currentPlaylistId by mutableLongStateOf(-1L)
        private set

    // ------------------------------------------------------------ 均衡器

    /** 是否启用均衡（关闭时仍是全 0dB 直通，不从音频链路摘除） */
    var eqEnabled by mutableStateOf(false)
        private set

    /** 10 段增益 (dB)，供 UI 逐段绑定 */
    var eqBands by mutableStateOf<List<Float>>(emptyList())
        private set

    /** 预衰减 (dB) */
    var eqPreampDb by mutableFloatStateOf(0f)
        private set



    @JvmName("setEqEnabledState")
    fun updateEqEnabled(enabled: Boolean) {
        eqEnabled = enabled
        applyEqualizerToEngine()
        persistEqualizer()
    }

    fun setEqBandGain(band: Int, gainDb: Float) {
        val current = eqBands.toMutableList()
        if (band !in current.indices) return
        current[band] = gainDb
        eqBands = current
        applyEqualizerToEngine()
        persistEqualizer()
    }

    fun setEqAllGains(values: List<Float>) {
        eqBands = values.take(TenBandEqualizerProcessor.BAND_COUNT)
        applyEqualizerToEngine()
        persistEqualizer()
    }

    fun setEqPreamp(db: Float) {
        eqPreampDb = db
        PlaybackEngine.equalizer.setPreamp(db)
        persistEqualizer()
    }

    fun applyEqPreset(preset: Preset) {
        eqBands = preset.bandsDb.take(TenBandEqualizerProcessor.BAND_COUNT)
        eqPreampDb = preset.preampDb
        applyEqualizerToEngine()
        persistEqualizer()
    }

    /** 把当前 UI 状态推到音频引擎 */
    private fun applyEqualizerToEngine() {
        val eq = PlaybackEngine.equalizer
        val bands = eqBands.ifEmpty { List(TenBandEqualizerProcessor.BAND_COUNT) { 0f } }
        eq.setAllGains(bands)
        eq.setPreamp(eqPreampDb)
        eq.setEnabled(eqEnabled)  // 启用/禁用均衡器
        if (!eqEnabled) {
            eq.setAllGains(List(TenBandEqualizerProcessor.BAND_COUNT) { 0f })
            eq.setPreamp(0f)
        }
        eq.clearStates()
    }

    private fun persistEqualizer() {
        viewModelScope.launch {
            runCatching {
                playbackPrefs.saveEqualizer(
                    enabled = eqEnabled,
                    gains = eqBands.ifEmpty { List(TenBandEqualizerProcessor.BAND_COUNT) { 0f } },
                    preampDb = eqPreampDb
                )
            }
        }
    }

    val engineState = PlaybackEngine.state

    // ------------------------------------------------------- Flow 状态

    /** MediaStore 扫描出的全部歌曲（含黑名单内的） */
    private val allSongs = MutableStateFlow<List<Song>>(emptyList())

    /**
     * 黑名单 songId 集合。
     * 必须是"常驻维护"的：WhileSubscribed 的 .value 在没界面订阅时只会是初值，
     * 而切歌判断和启动恢复都是在没界面订阅的时刻读它，会漏判。
     * 所以在 init 里常驻 collect，保证任何时刻 .value 都是最新的。
     */
    private val _blacklistIds = MutableStateFlow<Set<Long>>(emptySet())
    val blacklistIds: StateFlow<Set<Long>> = _blacklistIds.asStateFlow()

    /**
     * 现算曲库 = 全部歌曲 − 黑名单。
     * 不依赖订阅，任何时候调都拿到真实结果——启动恢复、播放跳转都走这里。
     */
    private fun computeLibrary(): List<Song> {
        val black = _blacklistIds.value
        val all = allSongs.value
        return if (black.isEmpty()) all else all.filter { it.id !in black }
    }

    /** 曲库（UI 订阅用） */
    val library: StateFlow<List<Song>> = combine(allSongs, _blacklistIds) { all, black ->
        if (black.isEmpty()) all else all.filter { it.id !in black }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 黑名单里的歌曲（解析回 Song 用于展示） */
    val blacklistSongs: StateFlow<List<Song>> = combine(allSongs, _blacklistIds) { all, black ->
        if (black.isEmpty()) emptyList() else all.filter { it.id in black }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 自建歌单列表（持久化） */
    val playlists: StateFlow<List<PlaylistEntity>> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 每个歌单的曲目数（key = playlistId） */
    val playlistSongCounts: StateFlow<Map<Long, Int>> = repository.observeSongCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 当前打开的歌单 id（-1 表示没打开） */
    private val selectedPlaylistId = MutableStateFlow(-1L)

    /** 当前打开歌单的曲目（songId 从 Room 流，Song 从扫描结果解析） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistSongs: StateFlow<List<Song>> = selectedPlaylistId
        .flatMapLatest { id ->
            if (id < 0) flowOf(emptyList())
            else combine(repository.observePlaylistSongIds(id), allSongs) { ids, all ->
                val byId = all.associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ----------------------------------------------------------- 搜索

    /** 曲库搜索查询词 */
    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: MutableStateFlow<String> = _libraryQuery

    /** 歌单搜索查询词 */
    private val _playlistQuery = MutableStateFlow("")
    val playlistQuery: MutableStateFlow<String> = _playlistQuery

    /** 曲库搜索结果（防抖 300ms + 去重） */
    val librarySearchResults: StateFlow<List<Song>> = combine(
        library,
        _libraryQuery.debounce(300).distinctUntilChanged()
    ) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { it.searchableText.contains(query.lowercase()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 歌单搜索结果（防抖 300ms + 去重） */
    val playlistSearchResults: StateFlow<List<Song>> = combine(
        playlistSongs,
        _playlistQuery.debounce(300).distinctUntilChanged()
    ) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { it.searchableText.contains(query.lowercase()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 黑名单常驻监听：这是"读即时值"可靠的前提。
        // 若改成 WhileSubscribed，切歌判断/启动恢复时会读到空集，黑名单形同虚设。
        viewModelScope.launch {
            repository.observeBlacklistIds().collect { ids ->
                _blacklistIds.value = ids.toSet()
            }
        }

        PlaybackEngine.onCurrentIndexChanged = { index ->
            val queue = PlaybackEngine.getCurrentQueue()
            val song = queue.getOrNull(index)
            if (song != null && song.id != currentSong?.id) {
                currentSong = song
                loadLyrics(song)
            }
        }

        // 队列是 setQueue 时的快照，运行中途拉黑不会回流到队列。
        // 这里注入判断，轮到黑名单曲时由引擎主动切走。
        PlaybackEngine.skipPredicate = { songId -> songId in blacklistIds.value }



        // 启动：先扫描曲库，拿到 allSongs 后恢复上次播放状态
        viewModelScope.launch {
            scanLibrary()

            val saved = withContext(Dispatchers.IO) { playbackPrefs.read() }
            if (saved.hasSong) {
                restorePlayback(saved.songId, saved.playlistId, saved.isPlaylistSource, saved.positionMs)
            }
            PlaybackEngine.setShuffle(saved.shuffle)
            PlaybackEngine.setRepeatMode(saved.repeatMode)
            updateVolume(saved.volume)

            // 均衡器恢复（默认关闭，全 0dB 直通）
            eqEnabled = saved.eqEnabled
            eqBands = saved.eqGains.takeIf { it.size == TenBandEqualizerProcessor.BAND_COUNT }
                ?: List(TenBandEqualizerProcessor.BAND_COUNT) { 0f }
            eqPreampDb = saved.eqPreampDb
            applyEqualizerToEngine()

            startPositionPersistLoop()
        }
    }

    /** 每 5 秒节流落盘一次播放进度 */
    private fun startPositionPersistLoop() {
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val pos = PlaybackEngine.state.value.positionMs
                if (pos - lastPersistedPosition >= 4_000 || lastPersistedPosition == 0L && pos > 0) {
                    lastPersistedPosition = pos
                    runCatching { playbackPrefs.savePosition(pos) }
                }
            }
        }
    }

    /** 切歌/切歌单时落盘来源，恢复队列用 */
    private fun persistSource(songId: Long, playlistId: Long, isPlaylist: Boolean) {
        viewModelScope.launch {
            runCatching {
                playbackPrefs.saveSong(songId, playlistId, isPlaylist)
                playbackPrefs.savePosition(0L)
                lastPersistedPosition = 0L
            }
        }
    }

    /** 扫描完成后重建上次的队列，只回显不自动播放 */
    private fun restorePlayback(songId: Long, playlistId: Long, isPlaylist: Boolean, positionMs: Long) {
        viewModelScope.launch {
            // 用现算而非 library.value：后者依赖订阅者存在，启动这一刻没人订阅，只会拿到空
            val queue: List<Song> = if (isPlaylist && playlistId > 0) {
                val ids = withContext(Dispatchers.IO) { repository.playlistSongIdsSnapshot(playlistId) }
                val byId = allSongs.value.associateBy { it.id }
                ids.mapNotNull { byId[it] }
            } else {
                computeLibrary()
            }
            if (queue.isEmpty()) return@launch

            // 上次那首若已被拉黑，就不恢复到它：回到队首，避免一启动就在放黑名单曲
            val rawIndex = queue.indexOfFirst { it.id == songId }
            val index = if (rawIndex < 0) 0 else rawIndex
            val song = queue[index]
            currentSong = song
            currentSource = if (isPlaylist) 1 else 0
            currentPlaylistId = if (isPlaylist) playlistId else -1L
            loadLyrics(song)

            // 只把 MediaItem 装载好，不调 play()，不开前台服务
            PlaybackEngine.restoreQueue(getApplication(), queue, index, positionMs)
        }
    }

    override fun onCleared() {
        // 退出前兜底落盘进度 + 设置
        val pos = PlaybackEngine.state.value.positionMs
        if (pos > 0) {
            viewModelScope.launch {
                runCatching { playbackPrefs.savePosition(pos) }
                runCatching {
                    playbackPrefs.saveSettings(
                        shuffle = PlaybackEngine.state.value.shuffle,
                        repeatMode = PlaybackEngine.state.value.repeatMode,
                        volume = volume
                    )
                }
            }
        }
        PlaybackEngine.onCurrentIndexChanged = null
        PlaybackEngine.skipPredicate = null
        super.onCleared()
    }

    // ------------------------------------------------------------ 扫描

    fun scanLibrary() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val context = getApplication<Application>()
            val songs = withContext(Dispatchers.IO) { MediaStoreScanner.scan(context) }
            allSongs.value = songs
            isLoading = false
            if (songs.isEmpty()) {
                errorMessage = "未找到音乐文件，请确认已授予读取音频权限。"
            }
        }
    }

    // ------------------------------------------------------------ 播放控制

    fun playSongAt(songs: List<Song>, index: Int) {
        val song = songs.getOrNull(index) ?: return
        currentSong = song
        loadLyrics(song)
        currentSource = 0
        currentPlaylistId = -1L
        PlaybackEngine.setQueue(getApplication(), songs, index)
        PlaybackEngine.play(getApplication())
        persistSource(song.id, -1L, false)
    }

    // 同样用现算：不再隐含"必须 LibraryScreen 先组合并订阅 library"这个时序前提
    fun playFromLibrary(index: Int) = playSongAt(computeLibrary(), index)

    fun playSong(song: Song) {
        val library = computeLibrary()
        val index = library.indexOfFirst { it.id == song.id }
        if (index >= 0) playSongAt(library, index)
    }

    fun playSongFromPlaylist(song: Song) {
        val songs = playlistSongs.value
        val index = songs.indexOfFirst { it.id == song.id }
        if (index >= 0) playFromPlaylist(index)
    }

    fun playFromPlaylist(index: Int) {
        val songs = playlistSongs.value
        val song = songs.getOrNull(index) ?: return
        currentSong = song
        loadLyrics(song)
        currentSource = 1
        currentPlaylistId = selectedPlaylistId.value
        PlaybackEngine.setQueue(getApplication(), songs, index)
        PlaybackEngine.play(getApplication())
        persistSource(song.id, selectedPlaylistId.value, true)
    }

    fun togglePlayPause() = PlaybackEngine.togglePlayPause(getApplication())

    fun next() = PlaybackEngine.next()

    fun previous() = PlaybackEngine.previous()

    fun seekTo(positionMs: Long) = PlaybackEngine.seekTo(positionMs)

    fun cycleRepeatMode() {
        val nextMode = when (PlaybackEngine.state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        PlaybackEngine.setRepeatMode(nextMode)
        if (nextMode == RepeatMode.ONE && PlaybackEngine.state.value.shuffle) {
            PlaybackEngine.setShuffle(false)
        }
        persistSettings()
    }

    fun toggleShuffle() {
        val engine = PlaybackEngine.state.value
        if (!engine.shuffle && engine.repeatMode == RepeatMode.ONE) {
            PlaybackEngine.setRepeatMode(RepeatMode.ALL)
        }
        PlaybackEngine.setShuffle(!engine.shuffle)
        persistSettings()
    }

    fun updateVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        PlaybackEngine.setVolume(volume)
        persistSettings()
    }

    /** 播放设置变更后异步落盘 */
    private fun persistSettings() {
        viewModelScope.launch {
            runCatching {
                playbackPrefs.saveSettings(
                    shuffle = PlaybackEngine.state.value.shuffle,
                    repeatMode = PlaybackEngine.state.value.repeatMode,
                    volume = volume
                )
            }
        }
    }

    // ------------------------------------------------------------ 黑名单

    fun blacklist(songId: Long) {
        viewModelScope.launch {
            // 1. 拉黑 + 同步从所有歌单移除（一个事务，无中间态）
            runCatching { repository.blacklistAndRemoveFromPlaylists(songId) }

            // 2. 若拉黑的正是当前播放曲，立即跳到下一首，不让它继续放完
            if (currentSong?.id == songId) {
                PlaybackEngine.skipCurrent()
            }
        }
    }

    fun unblacklist(songId: Long) {
        viewModelScope.launch { repository.unblacklist(songId) }
    }

    // ------------------------------------------------------------ 歌单

    fun selectPlaylist(id: Long) {
        selectedPlaylistId.value = id
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            onCreated(id)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addToPlaylist(playlistId, songId) }
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeFromPlaylist(playlistId, songId) }
    }

    fun clearError() {
        errorMessage = null
    }

    /** 清空曲库搜索 */
    fun clearLibrarySearch() {
        _libraryQuery.value = ""
    }

    /** 清空歌单搜索 */
    fun clearPlaylistSearch() {
        _playlistQuery.value = ""
    }

    // ------------------------------------------------------------ 歌词

    private fun loadLyrics(song: Song) {
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) {
                val file = MediaStoreScanner.findLyricFile(song)?.let(::File)
                if (file != null && file.exists() && file.length() < 512 * 1024) {
                    runCatching { LyricParser.parse(file.readText()) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            }
            lyrics = lines
        }
    }
}
