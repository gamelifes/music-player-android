/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.player

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 「默认音乐播放器」的检测与申请。
 *
 * 系统没有统一的「默认播放器」API：
 *  - AOSP 以及 MIUI / HyperOS 的「默认应用」都是用「按 MIME 类型解析到的默认 Activity」表达的；
 *  - 部分 ROM 额外提供 RoleManager 角色。
 *
 * 注意：RoleManager 的公开常量里**没有** ROLE_MUSIC_PLAYER
 * （只有 ROLE_HOME / ROLE_DIALER / ROLE_SMS / ROLE_BROWSER / ROLE_ASSISTANT /
 * ROLE_EMERGENCY / ROLE_NOTES / ROLE_WALLET 等），
 * 所以不能用反射去取常量（一定抛 NoSuchFieldException），
 * 只能直接用角色名字符串去问系统。
 */
object DefaultPlayerHelper {

    private const val PREFS_NAME = "default_player_state"
    private const val KEY_PROMPTED = "has_prompted"
    private const val KEY_ROLE_GRANTED = "role_granted"

    /** 各家 ROM 可能使用的「音乐播放器」角色名。 */
    private val ROLE_CANDIDATES = listOf(
        "android.app.role.role_music_player",
        "android.app.role.MUSIC",
        "android.app.role.MUSIC_PLAYER",
        "android.app.role.ROLE_MUSIC_PLAYER",
        "com.miui.player.role.MUSIC_PLAYER"
    )

    /** 问系统「音频该由谁打开」的候选意图；任一解析到本应用即视为默认播放器。 */
    private val audioIntents: List<Intent> by lazy {
        listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://media/external/audio/media/1"), "audio/*"
            ),
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://media/external/audio/media"), "audio/mpeg"
            ),
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("file:///storage/emulated/0/Music/default_player_check.mp3"), "audio/mpeg"
            ),
            Intent(Intent.ACTION_VIEW).setType("audio/*"),
            Intent(Intent.ACTION_VIEW).setType("audio/mpeg")
        )
    }

    /** 本应用当前是不是系统默认音乐播放器。 */
    fun isDefaultPlayer(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ROLE_GRANTED, false) ||
            isDefaultActivity(context) ||
            isDefaultRole(context)

    /**
     * 系统默认处理音频的 Activity 是不是本应用。
     * 这是最通用的判断：用户在系统「默认应用」里选谁，这里的解析结果就指向谁。
     */
    fun isDefaultActivity(context: Context): Boolean {
        val pm = context.packageManager
        return audioIntents.any { intent ->
            val resolved = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }.getOrNull()
            resolved?.activityInfo?.packageName == context.packageName
        }
    }

    /** 是否持有系统里的「音乐播放器」角色。 */
    fun isDefaultRole(context: Context): Boolean {
        val roleManager = roleManager(context) ?: return false
        return ROLE_CANDIDATES.any { role ->
            runCatching { roleManager.isRoleAvailable(role) }.getOrDefault(false) &&
                runCatching { roleManager.isRoleHeld(role) }.getOrDefault(false)
        }
    }

    /** 系统里可用的「音乐播放器」角色名，没有则返回 null。 */
    fun availableMusicRole(context: Context): String? {
        val roleManager = roleManager(context) ?: return null
        return ROLE_CANDIDATES.firstOrNull { role ->
            runCatching { roleManager.isRoleAvailable(role) }.getOrDefault(false)
        }
    }

    /** 生成「申请音乐播放器角色」的系统弹窗 Intent。 */
    fun createRoleRequestIntent(context: Context, role: String): Intent? {
        val roleManager = roleManager(context) ?: return null
        return runCatching { roleManager.createRequestRoleIntent(role) }.getOrNull()
    }

    /** 是否还没引导过用户（避免每次启动都跳设置页）。 */
    fun shouldPrompt(context: Context): Boolean =
        !prefs(context).getBoolean(KEY_PROMPTED, false)

    fun markPrompted(context: Context) {
        prefs(context).edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    /** 用户在系统弹窗里授予了角色。 */
    fun markRoleGranted(context: Context) {
        prefs(context).edit().putBoolean(KEY_ROLE_GRANTED, true).apply()
    }

    /** 系统「默认应用」设置页。 */
    fun defaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun roleManager(context: Context): RoleManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return context.getSystemService(RoleManager::class.java)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
