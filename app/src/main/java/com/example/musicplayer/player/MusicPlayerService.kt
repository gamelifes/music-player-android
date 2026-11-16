/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.example.musicplayer.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity
import com.example.musicplayer.R

private const val TAG = "MusicPlayerService"

/**
 * 播放通知渠道。
 *
 * 用 v2 是为了绕开旧版本可能留下的高优先级渠道：
 * Android 在渠道创建后不允许应用再改 importance，
 * 只能换一个新 ID 重新以 IMPORTANCE_LOW 创建。
 * LOW = 通知栏/锁屏/媒体卡片都在，但不会弹横幅、不响铃。
 */
private const val CHANNEL_ID = "music_playback_v2"
private const val LEGACY_CHANNEL_ID = "music_playback"
private const val LEGACY_MEDIA3_CHANNEL_ID = "default_channel_id"

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

        // 先建低优先级渠道，再交给 media3 发通知。
        // 这样前台服务、媒体卡片、锁屏控制都保留，只是不弹横幅。
        ensureSilentPlaybackChannel()

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

    /**
     * 前台服务需要的通知渠道，固定 IMPORTANCE_LOW：
     * - 不弹横幅、不响、不震；
     * - 通知栏媒体卡片、锁屏控制、前台服务保活都不受影响；
     * - 不影响 SuperIslandHelper 自己的那条「灵动岛」通知。
     *
     * 旧渠道（music_playback / Media3 默认 default_channel_id）如果曾被创建成
     * 高优先级，importance 改不回来，所以这里直接删掉，换 v2 重新建。
     */
    private fun ensureSilentPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        manager.deleteNotificationChannel(LEGACY_MEDIA3_CHANNEL_ID)

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "播放控制与歌曲信息，仅显示在通知栏/锁屏，不弹出横幅"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

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
        if (session != null) {
            // 先从 Service 上摘掉，media3 才会同步撤掉通知与前台状态
            runCatching { removeSession(session) }
            session.release()
        }
        mediaSession = null

        // 无论用户划掉还是系统/外部关闭，统一复位引擎状态，避免
        // _state.isPlaying 残留导致下次启动时 togglePlayPause 判断反向。
        PlaybackEngine.release()
        super.onDestroy()
    }
}
