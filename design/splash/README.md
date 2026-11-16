# 启动页「黑胶冲出」

> **当前实现**：`app/src/main/java/com/example/musicplayer/ui/SplashStage.kt`
> 素材：`app/src/main/res/drawable-nodpi/splash_art.webp`（1350×2400，528 KB，由 `build_splash_art.py` 从
> `assets/gen-poster-vinyl-01.png` cover-crop 而来）、`splash_icon.webp`（系统启动页图标）
> 只在**首次安装 / 版本更新后**播放一次，见 `ui/SplashGate.kt`。

## 这段动画在讲什么

她压在一张**半透明琥珀黑胶**里。盘面在转，她顶，光从她身后涨起来，
一炸——盘片散开、白闪、镜头一顿，全彩的她从里面冲出来。

四拍（毫秒，`TOTAL_MS = 1600` + `FADE_MS = 280`）：

| 时间 | 内容 |
| --- | --- |
| 0 – 300 | 透过黑胶：暖琥珀 Multiply 透射、沟槽同心圆、一道柔光扇叶绕盘心旋转 |
| 300 – 780 | 顶：她身后的暖光涨起来，介质变"厚" 12%，镜头 1.04 → 1.07 |
| 780 – 1080 | 炸开：硬白闪（+横向光带）、七片扇形盘片散开、两块发光碎片扑向镜头、波形冲击环 |
| 1080 – 1600 | 定格：全彩、扫光过发、暗角收拢 |
| 1600 – 1880 | 淡出到主界面 |

## 三条失败路线（别再走）

1. **压暗海报 → 上色**：会变成"撕贴纸"。高潮必须是介质碎裂，不是颜色出现。
   所以第一帧就是全彩，只是隔着一层暖色介质。
2. **在画面上画裂缝**：白线 = 头发丝/划痕；"暗缝 + 两条受光棱" = 金属杆。
   四轮都失败。放大图上的矢量线永远像刻在玻璃上的涂鸦。
3. **碎片用深色填充**：在亮画面上就是"打了几个洞"。碎片必须**受光**（暖白 + 暗边），
   而且数量要少（6 片，2 片大的），十几片小碎屑等于屏幕上落灰。

## 实现要点

- 介质是**实体**：七片扇形（`Pane`）拼成整块。闭合时相邻重叠 3° → 看不出缝，等价于均匀透射；
  炸开时各自沿中线飞出 + 自转，缝张开，全彩从缝里回来。碎裂是物体在动。
- 扇形填充用 `BlendMode.Multiply` 的琥珀色，所以盘片飞过全彩画面时是"一片暖色介质掠过"，
  不是贴一块半透明色块。
- 碎片形状是**唱片切片**（内弧 + 外弧 + 两条直边），不是随机多边形——六边形看起来像"洞"。
- 底图 `splash_art.webp` 已按 1080×2400 预先 cover-crop 到 1350×2400，设备只做 1:1 blit，
  不做每帧 GPU 放大。`FilterQuality.Low`。
- 绘制量：1 次 `drawImage` + 约 40 个圆 / 线 / 多边形。
- 系统「减弱动画」(`ANIMATOR_DURATION_SCALE == 0`) 时整段跳过，直接进主界面。

## 实测（模拟器 1080×2400）

| 指标 | 值 |
| --- | --- |
| 进程 → onCreate | 24–42 ms |
| 进程 → 首帧（系统启动页撤下） | 440–670 ms |
| 进程 → 可交互 | 2.4–2.7 s（含动画） |
| 动画本身 | 1.60 s + 0.28 s 淡出 |

## 设置开关（`SplashGate` + `ui/SettingsSheet.kt`）

播放页顶栏现在是「歌词 / 设置」两个图标（原来的调色板按钮已经并进设置面板的「外观」段，
`ThemePickerSheet.kt` 已删除）。设置面板 = 外观（主色 / 背景 / 自定义背景色）+ 启动页动画。

启动页三选一，写进 `shared_prefs/splash.xml` 的 `mode`：

| 模式 | id | 行为 |
| --- | --- | --- |
| 跟随版本（默认） | `auto` | 首次安装 / 版本升级后第一次打开播一次 |
| 每次启动 | `always` | 每次冷启动都播（调动画时用） |
| 关闭 | `off` | 从不播，只留系统启动页那一帧 |

播不播在 `MainActivity.onCreate` 里**同步**决定，所以用 SharedPreferences 而不是 DataStore，
改完必须**下次冷启动**才生效。日志里能看到当前决策：

```
D SplashTiming: splash: full (version 1.1, mode always)
D SplashTiming: splash: skipped (mode off)
```

`versionName` 变了（升级）不会重置 `mode`，但 `auto` 模式会因为它和 `played_version` 不一致再播一次。

## 工具
| 文件 | 作用 |
| --- | --- |
| `build_splash_art.py` | 把 `assets/gen-poster-vinyl-01.png` cover-crop 成 `splash_art.webp` |
| `capture_run.ps1` | 装 APK → 清 `shared_prefs/splash.xml` → screenrecord → 拉回 `capture.mp4` |
| `capture_frames.py` | 抽帧 / 拼 contact sheet：`python capture_frames.py 3.7 4.3 0.04` |
| `capture-window.png` | 最近一次关键帧（每 40–60 ms 一帧） |
| `assets/gen-storyboard-01.png` | 2×2 分镜（四拍的视觉稿） |
| `assets/gen-poster-vinyl-01.png` | 主视觉原稿 864×1536 |

## 调参入口（`SplashStage.kt` 顶部）

```
TOTAL_MS    = 1600   // 主时间轴
FADE_MS     = 280    // 最后淡出
CRACK_START = 300f   // 开始"顶"（光涨起来）
BURST_AT    = 780f   // 炸开
SETTLE_AT   = 1080f  // 定格
PLAY_ANIMATION = true // false = 完全不做动画
```

时间轴内部还有几处关键数值，改之前先看注释：
`thick = 0.34f`（透射浓度，超过 0.45 就变成"老照片"）、
`panes` 的 `dist`（飞出距离）与 `halfW`（扇形宽度，必须 > 180/n 才无缝）、
`flash` 的三个关键帧（白闪持续 160 ms，再长就变成雾）。
