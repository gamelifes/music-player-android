/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.example.musicplayer.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity

private const val TAG = "MusicPlayerService"

/**
 * 前台播放服务：负责 MediaSession（通知栏、锁屏、耳机/车机控制）以及播放保活。
 * 在小米 HyperOS 设备上，会额外发布「万象超级岛」通知实现灵动岛样式。
 */
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = PlaybackEngine.ensurePlayer(applicationContext)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        SuperIslandHelper.startObserving(applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private var userRequestedStop = false

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉应用：无论是否正在播放，都完全关闭
        userRequestedStop = true
        stopSelf()
    }

    override fun onDestroy() {
        SuperIslandHelper.stopObserving()
        SuperIslandHelper.cancelNotification(this)

        val player = mediaSession?.player
        mediaSession?.release()
        mediaSession = null

        if (userRequestedStop) {
            PlaybackEngine.release()
        } else {
            val stillPlaying = player != null && (player.playWhenReady || player.isPlaying)
            if (stillPlaying) {
                PlaybackEngine.notifyServiceDestroyed()
            } else {
                PlaybackEngine.release()
            }
        }
        super.onDestroy()
    }
}
