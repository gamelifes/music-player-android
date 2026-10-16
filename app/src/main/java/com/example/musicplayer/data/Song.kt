/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dataPath: String,
    val albumId: Long,
) {
    val contentUri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    /** 预计算搜索文本：title + artist + album (小写)，避免运行时重复拼接 */
    val searchableText: String
        get() = buildString {
            append(title.lowercase())
            append(' ')
            append(artist.lowercase())
            append(' ')
            append(album.lowercase())
        }
}

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val lyricText: String = "",
    val lyrics: List<LyricLine> = emptyList(),
) {
    val currentSong: Song? get() = songs.getOrNull(currentIndex)
    val hasPrev: Boolean get() = repeatMode == RepeatMode.ALL || currentIndex > 0
    val hasNext: Boolean get() = repeatMode == RepeatMode.ALL || currentIndex in 0 until songs.lastIndex
}
