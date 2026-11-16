package com.example.musicplayer.ui

import android.content.Context

/**
 * 「什么时候播完整启动动画」的开关。
 *
 * 完整启动动画约 1.9s（见 [SplashStage] 顶部的四拍常量）。
 * 冷启动本身只要 0.5s，所以默认只在**这个版本第一次打开**时播一次，
 * 日常冷启直接进界面。三种模式由设置面板切换，写在同一个 prefs 文件里：
 *
 *   AUTO    跟随版本 —— 首次安装 / 版本升级后第一打开播一次（默认）
 *   ALWAYS  每次启动 —— 每次冷启动都播（调动画时用）
 *   OFF     关闭     —— 从不播，只留系统启动页那一帧
 *
 * 用 SharedPreferences 而不是 DataStore：这个值要在 `onCreate` 里同步读到，
 * 拿不到就会多播一段 1.9s 的动画。
 */
object SplashGate {

    /** 启动动画的播放策略。顺序即设置面板里的显示顺序。 */
    enum class Mode(val id: String, val label: String, val hint: String) {
        AUTO("auto", "跟随版本", "首次安装、或版本更新后第一次打开时播一次"),
        ALWAYS("always", "每次启动", "每次冷启动都播完整动画（调动画时用）"),
        OFF("off", "关闭", "直接进主界面，只保留系统启动页那一帧");

        companion object {
            fun from(id: String?): Mode = entries.firstOrNull { it.id == id } ?: AUTO
        }
    }

    private const val FILE = "splash"
    private const val KEY_PLAYED = "played_version"
    private const val KEY_MODE = "mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 当前 versionName；取不到就返回空串。 */
    fun versionName(context: Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    fun mode(context: Context): Mode =
        Mode.from(prefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putString(KEY_MODE, mode.id).apply()
    }

    /** 这次冷启动要不要播完整动画。取不到版本号时返回 true（宁可多播，也不要静默跳过）。 */
    fun shouldPlay(context: Context): Boolean = when (mode(context)) {
        Mode.ALWAYS -> true
        Mode.OFF -> false
        Mode.AUTO -> {
            val version = versionName(context)
            version.isEmpty() || prefs(context).getString(KEY_PLAYED, null) != version
        }
    }

    fun markPlayed(context: Context) {
        val version = versionName(context)
        if (version.isEmpty()) return
        prefs(context).edit().putString(KEY_PLAYED, version).apply()
    }
}
