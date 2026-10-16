/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.example.musicplayer.player.PlaybackEngine

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                PlaybackEngine.togglePlayPause(context)
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackEngine.next(context)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackEngine.previous(context)
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                PlaybackEngine.pause()
            }
        }
    }
}
