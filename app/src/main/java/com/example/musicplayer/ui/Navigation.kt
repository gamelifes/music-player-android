/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.ui

/**
 * 页面路由。不用 NavHost（省依赖），用简单栈管理。
 */
sealed class Screen {
    object Player : Screen()
    object Library : Screen()
    object Blacklist : Screen()
    object Playlists : Screen()
    data class PlaylistDetail(val id: Long, val name: String) : Screen()
}
