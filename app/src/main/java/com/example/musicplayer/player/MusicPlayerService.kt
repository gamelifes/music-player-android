/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.example.musicplayer.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R

private const val TAG = "MusicPlayerService"
private const val CHANNEL_ID = "music_playback"

/** 通知 id：不能和 SuperIslandHelper 的 1001 冲突，否则两条通知会互相覆盖 */
private const val NOTIFICATION_ID = 1002

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
        val session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
        mediaSession = session

        // 通知栏「播放卡片」交给 media3 生成：自带播放/暂停/上一首/下一首按钮
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_music_note)
        setMediaNotificationProvider(notificationProvider)

        // 关键：必须 addSession。
        // 只有把 session 登记到 Service 上，media3 才会接管
        // 「通知栏播放卡片」与「前台服务提升(startForeground)」。
        // 否则这个服务只是普通后台服务：应用切到其他应用后会被系统
        // （HyperOS/MIUI 尤其激进）回收，onDestroy 里 release 掉 session，
        // 系统媒体卡片也就跟着消失了。
        addSession(session)

        SuperIslandHelper.startObserving(applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private var userRequestedStop = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉应用：无论是否正在播放，都完全关闭
        userRequestedStop = true
        stopSelf()
    }

    override fun onDestroy() {
        SuperIslandHelper.stopObserving()
        SuperIslandHelper.cancelNotification(this)

        val session = mediaSession
        val player = session?.player
        if (session != null) {
            // 先从 Service 上摘掉，media3 才会同步撤掉通知与前台状态
            runCatching { removeSession(session) }
            session.release()
        }
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
