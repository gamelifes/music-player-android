/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_state")

/**
 * 播放状态持久化：应用退出后重新进入可恢复「上次播放的曲子 / 队列来源 / 进度」。
 *
 * 只存轻量标量（songId / playlistId / positionMs），不缓存队列实体，
 * 队列在恢复时用标量从 Room + MediaStore 重新组装，避免列表过期。
 */
class PlaybackPrefs(private val context: Context) {

    private object Keys {
        val SONG_ID = longPreferencesKey("last_song_id")
        val PLAYLIST_ID = longPreferencesKey("last_playlist_id")
        val IS_PLAYLIST_SOURCE = androidx.datastore.preferences.core.booleanPreferencesKey("last_is_playlist")
        val POSITION_MS = longPreferencesKey("last_position_ms")
        val SHUFFLE = androidx.datastore.preferences.core.booleanPreferencesKey("last_shuffle")
        val REPEAT_MODE = stringPreferencesKey("last_repeat_mode")
        val VOLUME = floatPreferencesKey("last_volume")

        // 均衡器：开关 + 10 段增益 + 预衰减
        val EQ_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("eq_enabled")
        val EQ_GAINS = stringPreferencesKey("eq_gains")
        val EQ_PREAMP = floatPreferencesKey("eq_preamp")
    }

    data class Snapshot(
        val songId: Long = -1L,
        val playlistId: Long = -1L,
        val isPlaylistSource: Boolean = false,
        val positionMs: Long = 0L,
        val shuffle: Boolean = false,
        val repeatMode: RepeatMode = RepeatMode.OFF,
        val volume: Float = 1f,
        val eqEnabled: Boolean = false,
        val eqGains: List<Float> = emptyList(),
        val eqPreampDb: Float = 0f,
    ) {
        val hasSong: Boolean get() = songId > 0L
    }

    val snapshot: Flow<Snapshot> = context.playbackDataStore.data.map { p ->
        Snapshot(
            songId = p[Keys.SONG_ID] ?: -1L,
            playlistId = p[Keys.PLAYLIST_ID] ?: -1L,
            isPlaylistSource = p[Keys.IS_PLAYLIST_SOURCE] ?: false,
            positionMs = p[Keys.POSITION_MS] ?: 0L,
            shuffle = p[Keys.SHUFFLE] ?: false,
            repeatMode = runCatching {
                RepeatMode.valueOf(p[Keys.REPEAT_MODE] ?: RepeatMode.OFF.name)
            }.getOrDefault(RepeatMode.OFF),
            volume = p[Keys.VOLUME] ?: 1f,
            eqEnabled = p[Keys.EQ_ENABLED] ?: false,
            eqGains = p[Keys.EQ_GAINS]
                ?.split(',')
                ?.mapNotNull { it.toFloatOrNull() }
                ?: emptyList(),
            eqPreampDb = p[Keys.EQ_PREAMP] ?: 0f,
        )
    }

    suspend fun read(): Snapshot = snapshot.first()

    suspend fun saveSong(songId: Long, playlistId: Long, isPlaylistSource: Boolean) {
        context.playbackDataStore.edit { p ->
            p[Keys.SONG_ID] = songId
            p[Keys.PLAYLIST_ID] = playlistId
            p[Keys.IS_PLAYLIST_SOURCE] = isPlaylistSource
        }
    }

    suspend fun savePosition(positionMs: Long) {
        // 进度变化频繁，调用方负责节流；这里避开不必要的 value 写入
        if (positionMs < 0L) return
        context.playbackDataStore.edit { p ->
            if (p[Keys.POSITION_MS] == positionMs) return@edit
            p[Keys.POSITION_MS] = positionMs
        }
    }

    suspend fun saveSettings(shuffle: Boolean, repeatMode: RepeatMode, volume: Float) {
        context.playbackDataStore.edit { p ->
            p[Keys.SHUFFLE] = shuffle
            p[Keys.REPEAT_MODE] = repeatMode.name
            p[Keys.VOLUME] = volume.coerceIn(0f, 1f)
        }
    }

    /** 保存均衡器设置。[gains] 长度用调用方保证为 10 段 */
    suspend fun saveEqualizer(enabled: Boolean, gains: List<Float>, preampDb: Float) {
        context.playbackDataStore.edit { p ->
            p[Keys.EQ_ENABLED] = enabled
            p[Keys.EQ_GAINS] = gains.joinToString(",")
            p[Keys.EQ_PREAMP] = preampDb
        }
    }

    suspend fun clear() {
        context.playbackDataStore.edit { it.clear() }
    }
}
