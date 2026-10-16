# Kotlin 音乐播放器 (Android)

Kotlin + Jetpack Compose + Media3(ExoPlayer) 写的本地音乐播放器。

**作者**：lihaihua  
**配置**：ai coding  
**仓库**：https://github.com/gamelifes/music-player-android.git

## 功能

| 模块 | 说明 |
| --- | --- |
| 播放控制 | 播放 / 暂停 / 上一首 / 下一首 / 进度拖拽 / 快进快退 10s / 音量 |
| 播放列表 | MediaStore 扫描本机音乐、列表展示、点击切歌、随机播放、循环（关 / 列表 / 单曲） |
| 歌词 | 自动读取歌曲同目录同名 `.lrc`，逐行高亮 + 自动滚动 |
| 均衡器 | **真正的 10 段**图示均衡器 + 预放大 + 8 种预设，PCM 域自实现 |
| 其他 | 前台服务 + MediaSession：通知栏 / 锁屏 / 耳机按键控制，音频焦点抢占 |

## 10 段均衡器是怎么实现的

系统自带的 `android.media.audiofx.Equalizer` 频段数由硬件决定（多数设备只有 5 段），
所以这里在 PCM 域自己做滤波：

- `player/TenBandEqualizerProcessor.kt` 继承 Media3 的 `BaseAudioProcessor`，
  挂到 `DefaultAudioSink.audioProcessors` 上。
- 10 个频段中心频率：31.5 / 63 / 125 / 250 / 500 / 1k / 2k / 4k / 8k / 16k Hz。
- 每段一级 RBJ **peaking biquad**，Q = 1.41，增益 ±12 dB，级联后总增益大幅抬升，
  所以配了独立的 **预放大（Preamp）** ±12 dB 来做补偿。
- 用 Direct Form II 转置结构保存每声道滤波器状态，参数以不可变快照 + `@Volatile`
  整体替换，避免 UI 线程改参数和音频线程读系数之间的竞态。
- 支持输入 `PCM_16BIT` / `PCM_FLOAT` / `PCM_INT`，统一输出 `PCM_16BIT`。
- 关闭开关时完全旁路，不做任何滤波计算。

## 目录结构

```
app/src/main/java/com/example/musicplayer/
├── MainActivity.kt                    # 权限申请 + 页面调度
├── MusicApp.kt
├── data/
│   ├── Song.kt                        # 数据模型 + UI 状态
│   ├── MediaStoreScanner.kt           # 扫描系统音乐 + 查找同名 .lrc
│   └── LyricParser.kt                 # LRC 解析 + 二分查找当前行
├── player/
│   ├── PlaybackEngine.kt              # 全局单例 ExoPlayer + StateFlow 状态
│   ├── MusicPlayerService.kt          # MediaSessionService 前台服务
│   └── TenBandEqualizerProcessor.kt   # 10 段 biquad 均衡器
└── ui/
    ├── PlayerScreen.kt                # 主界面：封面 / 进度 / 控制栏 / 音量
    ├── PlaylistScreen.kt              # 播放列表
    ├── LyricsView.kt                  # 歌词滚动组件
    ├── EqualizerSheet.kt              # 10 段均衡器面板（自绘竖向滑块）
    └── theme/Theme.kt
```

## 构建

工程已包含 `gradle-wrapper.jar` 和 `local.properties`，`./gradlew assembleDebug` 可直接出包。

**方式 A：用 Android Studio**

1. `File > Open`，选择 `KotlinMusicPlayer` 目录。
2. 等待 Gradle Sync 完成（需要 JDK 17；`Settings > Build Tools > Gradle > Gradle JDK` 选 17）。
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`，产物在 `app/build/outputs/apk/debug/`。

**方式 B：命令行**

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
.\gradlew.bat assembleDebug          # Debug 包
.\gradlew.bat assembleRelease        # Release 包（需自备签名配置）
.\gradlew.bat lintDebug              # 静态检查
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### ⚠️ 关于 Gradle 发行版源（重要）

`gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 现在指向**本机本地文件**：

```properties
distributionUrl=file\:/D:/gradle/.gradle/wrapper/dists/gradle-8.11.1-all.zip
```

这么配有两个原因，都是为了绕开被墙 / 超时的网络：

1. `services.gradle.org` 在很多国内网络下连不上，wrapper 下载 Gradle 会超时。
2. Android Studio 为了 Kotlin DSL 的代码提示，会额外去 `services.gradle.org` 下载
   `gradle-8.10.2-src.zip`，失败后报
   `Failed to transform gradle-8.10.2-src.zip to match attributes {artifactType=src-directory}`。
   **改用 `-all` 发行版即可避免**，因为它自带 Gradle 源码，IDE 不需要再单独下载。

如果这台机器上的 zip 被清理了，把该行换成（任选其一）：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-all.zip
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-all.zip
```

`local.properties` 里的 `sdk.dir` 同理，是本机路径，换机器要改。

## 使用

1. 首次启动会申请「音乐 / 音频」权限（Android 13+ 是 `READ_MEDIA_AUDIO`）。
2. 主界面点右下角队列图标打开播放列表，选一首歌即可开始播放。
3. 顶部歌词按钮切换封面 / 歌词视图；底部均衡器图标打开 10 段面板。
4. 想让歌词自动加载：把 `歌曲名.lrc` 放到歌曲同一目录，重新扫描即可。

### 歌词格式（决定能做到什么程度的高亮）

播放器支持三种 LRC 写法，**逐词高亮需要 A2 增强格式**：

| 格式 | 写法 | 高亮效果 |
| --- | --- | --- |
| 行级（最常见） | `[00:12.34]歌词内容` | 逐行高亮 + 整行扫光（**按字数均摊估算**） |
| **A2 增强** | `[00:12.34]<00:12.34>Hello <00:12.90>world` | **真·逐词高亮** |
| 词级 | `[00:12.34]Hello [00:12.90]world` | 真·逐词高亮 |

A2 示例（`<>` 里是每个词的起始时间）：

```lrc
[ti:示例歌曲]
[ar:示例歌手]
[00:00.50]<00:00.50>这是一句 <00:01.20>带 <00:01.80>逐词 <00:02.50>时间戳的歌词
[00:05.00]第二句没有词级时间戳，走估算扫光
```

> 注意：普通 LRC 文件里**没有真正的逐字时间戳**，所以那种格式下播放器的「逐字」是按行时长均摊出来的视觉效果，不是真实演唱数据。

## 已知限制

- 均衡器是软件 DSP，会多占一点 CPU；低端机上建议播放前先想好参数。
- `.lrc` 需要与歌曲同目录同名才能被找到（`MediaStore` 不索引歌词文件）。
- 当前版本播放列表仅在内存中，App 被杀后清空；想持久化可以接 Room。
- `albumart` 那套 URI 在 Android 10+ 已废弃，这里仍可用，个别机型可能取不到封面（会显示默认占位图）。
