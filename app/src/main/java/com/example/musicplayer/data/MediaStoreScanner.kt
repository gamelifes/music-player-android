/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore

/**
 * 从 MediaStore 扫描设备上的音频文件。
 */
object MediaStoreScanner {

    fun scan(context: Context): List<Song> {
        // 用 EXTERNAL_CONTENT_URI 而不是 VOLUME_EXTERNAL：
        // 后者是 API 29 才引入的，minSdk 24 直接调用会在低版本设备上崩。
        @Suppress("DEPRECATION")
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<Song>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val albumIdIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val rawTitle = cursor.getStringOrEmpty(titleIdx)
                        val unknownArtist = "<unknown>"
                        val artist = cursor.getStringOrEmpty(artistIdx)
                        result += Song(
                            id = id,
                            title = rawTitle.ifBlank { "未知曲目" },
                            artist = artist.ifBlank { unknownArtist }
                                .takeUnless { it == unknownArtist }
                                ?: unknownArtist.trim('<', '>'),
                            album = cursor.getStringOrEmpty(albumIdx).ifBlank { "未知专辑" },
                            durationMs = cursor.getLong(durationIdx),
                            dataPath = if (dataIdx >= 0) cursor.getStringOrEmpty(dataIdx) else "",
                            albumId = if (albumIdIdx >= 0) cursor.getLong(albumIdIdx) else 0L
                        )
                    }
                }
        }
        return result
    }

    /**
     * 尝试在音乐同目录找到同名 .lrc 歌词文件。
     */
    fun findLyricFile(song: Song): String? {
        val path = song.dataPath
        if (path.isBlank()) return null
        val lrc = path.substringBeforeLast('.', path) + ".lrc"
        val txt = path.substringBeforeLast('.', path) + ".txt"
        return when {
            java.io.File(lrc).exists() -> lrc
            java.io.File(txt).exists() -> txt
            else -> null
        }
    }

    private fun Cursor.getStringOrEmpty(idx: Int): String =
        if (idx >= 0 && !isNull(idx)) getString(idx).orEmpty() else ""

    private const val MIN_DURATION_MS = 5_000L
}
