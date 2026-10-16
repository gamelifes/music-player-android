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

/**
 * 前台播放服务：负责 MediaSession（通知栏、锁屏、耳机/车机控制）以及播放保活。
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉应用时，如果没在播放就停掉服务
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // 先取 player 再 release session，否则拿不到播放状态
        val player = mediaSession?.player

        mediaSession?.release()
        mediaSession = null

        // 服务消亡不等于播放停止：
        // 系统在后台限制/内存压力下会杀服务，但 UI（同进程）仍在用这个 player。
        // 此时若 release() 掉 ExoPlayer，player 变 null，
        // 之后 togglePlayPause → ensurePlayer 建出一个空 player → 静音且按钮像卡死。
        val stillPlaying = player != null && (player.playWhenReady || player.isPlaying)
        if (stillPlaying) {
            // 只复位启动标志，保留播放实例，下次播放能重新把服务拉起来
            PlaybackEngine.notifyServiceDestroyed()
        } else {
            PlaybackEngine.release()
        }
        super.onDestroy()
    }
}
