/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import com.example.musicplayer.data.MusicRepository
import com.example.musicplayer.data.PlaybackPrefs
import com.example.musicplayer.data.db.MusicDatabase

class MusicApp : Application() {
    companion object {
        lateinit var instance: MusicApp
            private set

        /** 进程启动时刻，只用于启动耗时诊断（SplashTiming 日志）。 */
        val startMs = android.os.SystemClock.uptimeMillis()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        cleanupLegacyNotificationChannels()
    }

    /**
     * 旧版本除媒体通知外，还会额外发一条「歌曲名称信息横幅」（渠道 id
     * music_super_island）。这条通知已经移除，进程启动时顺手删掉旧渠道，
     * 避免系统通知设置里一直残留这个分类。
     */
    private fun cleanupLegacyNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)
            ?.deleteNotificationChannel("music_super_island")
    }

    val database: MusicDatabase by lazy {
        Room.databaseBuilder(this, MusicDatabase::class.java, MusicDatabase.NAME).build()
    }

    val repository: MusicRepository by lazy {
        MusicRepository(database.blacklistDao(), database.playlistDao())
    }

    val playbackPrefs: PlaybackPrefs by lazy {
        PlaybackPrefs(this)
    }

    fun getAppContext(): Context = this
}
