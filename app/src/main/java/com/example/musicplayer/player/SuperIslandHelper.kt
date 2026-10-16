/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import com.example.musicplayer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object SuperIslandHelper {
    private const val CHANNEL_ID = "music_super_island"
    private const val NOTIFICATION_ID = 1001

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var updateJob: Job? = null

    fun isXiaomiHyperOs(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER
            Build.MODEL.contains("MI", ignoreCase = true) ||
                Build.MODEL.contains("Redmi", ignoreCase = true) ||
                Build.MODEL.contains("POCO", ignoreCase = true) ||
                manufacturer.equals("Xiaomi", ignoreCase = true) ||
                manufacturer.equals("Redmi", ignoreCase = true) ||
                manufacturer.equals("POCO", ignoreCase = true)
        } catch (e: Exception) {
            true
        }
    }

    fun startObserving(context: Context) {
        if (!isXiaomiHyperOs()) return

        updateJob?.cancel()
        updateJob = scope.launch {
            PlaybackEngine.state.collectLatest { state ->
                val queue = PlaybackEngine.getCurrentQueue()
                val song = queue.getOrNull(state.currentIndex)
                if (song != null) {
                    val playInfo = PlayInfo(
                        title = song.title,
                        artist = song.artist,
                        albumArtUri = song.albumArtUri,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs
                    )
                    postSuperIslandNotification(context, playInfo, state.isPlaying)
                }
            }
        }
    }

    fun stopObserving() {
        updateJob?.cancel()
    }

    private fun postSuperIslandNotification(
        context: Context,
        playInfo: PlayInfo,
        isPlaying: Boolean
    ) {
        if (!isXiaomiHyperOs()) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player Island",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        val title = playInfo.title
        val artist = playInfo.artist
        val positionMs = playInfo.positionMs
        val durationMs = playInfo.durationMs

        val islandParamsJson = buildIslandParamsJson(playInfo, isPlaying, positionMs, durationMs)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addExtras("miui.focus.param", islandParamsJson)

        playInfo.albumArtUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bitmap ->
                        builder.setLargeIcon(bitmap)
                        builder.addExtras("miui.focus.pic_aod", bitmap)
                        builder.addExtras("miui.focus.pic_image", bitmap)
                        builder.addExtras("miui.focus.pic_highlight", bitmap)
                    }
                }
            } catch (e: Exception) {
            }
        }

        val toggleIntent = android.content.Intent(context, PlayControlReceiver::class.java).apply {
            action = PlayControlReceiver.ACTION_TOGGLE_PLAY
        }
        val togglePending = PendingIntentCompat.getBroadcast(
            context, 1, toggleIntent,
            PendingIntentCompat.FLAG_IMMUTABLE or PendingIntentCompat.FLAG_UPDATE_CURRENT
        )

        val nextIntent = android.content.Intent(context, PlayControlReceiver::class.java).apply {
            action = PlayControlReceiver.ACTION_NEXT
        }
        val nextPending = PendingIntentCompat.getBroadcast(
            context, 2, nextIntent,
            PendingIntentCompat.FLAG_IMMUTABLE or PendingIntentCompat.FLAG_UPDATE_CURRENT
        )

        val prevIntent = android.content.Intent(context, PlayControlReceiver::class.java).apply {
            action = PlayControlReceiver.ACTION_PREV
        }
        val prevPending = PendingIntentCompat.getBroadcast(
            context, 3, prevIntent,
            PendingIntentCompat.FLAG_IMMUTABLE or PendingIntentCompat.FLAG_UPDATE_CURRENT
        )

        val actionsBundle = Bundle().apply {
            putParcelable("miui.focus.action_play_pause", togglePending)
            putParcelable("miui.focus.action_next", nextPending)
            putParcelable("miui.focus.action_prev", prevPending)
        }
        builder.addExtras("miui.focus.actions", actionsBundle)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildIslandParamsJson(
        playInfo: PlayInfo?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ): String {
        if (playInfo == null) return ""

        val title = escapeJson(playInfo.title)
        val artist = escapeJson(playInfo.artist)
        val progress = formatTime(positionMs)
        val total = formatTime(durationMs)

        return """
            {
                "baseInfo": {
                    "title": "$title",
                    "content": "$artist",
                    "type": 1,
                    "useHighLight": true
                },
                "hintInfo": {
                    "type": ${if (isPlaying) 1 else 0},
                    "title": "$progress",
                    "content": "$total",
                    "useHighLight": true
                },
                "actionInfo": {
                    "action": "miui.focus.action_play_pause"
                }
            }
        """.trimIndent()
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    data class PlayInfo(
        val title: String,
        val artist: String,
        val albumArtUri: Uri? = null,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )
}

class PlayControlReceiver : android.content.BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE_PLAY = "com.example.musicplayer.TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.musicplayer.NEXT"
        const val ACTION_PREV = "com.example.musicplayer.PREV"
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        when (intent.action) {
            ACTION_TOGGLE_PLAY -> PlaybackEngine.togglePlayPause(context)
            ACTION_NEXT -> PlaybackEngine.next()
            ACTION_PREV -> PlaybackEngine.previous()
        }
    }
}