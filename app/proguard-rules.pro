# 保留 Media3 / Compose 所需类
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# 自定义 AudioProcessor 被反射调用（R8 全模式下需连同 Companion 一起 keep，
# 否则 const val 常量会被内联成私有，Kotlin 编译期就访问不到了）
-keep class com.example.musicplayer.player.TenBandEqualizerProcessor { *; }
-keep class com.example.musicplayer.player.TenBandEqualizerProcessor$Companion { *; }
-keep class com.example.musicplayer.player.TenBandEqualizerProcessor$Preset { *; }
-keep class com.example.musicplayer.player.MusicPlayerService { *; }