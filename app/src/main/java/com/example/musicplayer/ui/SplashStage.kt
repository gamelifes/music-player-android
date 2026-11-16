package com.example.musicplayer.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.musicplayer.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 启动页「黑胶冲出」。
 *
 * 她压在一张**半透明琥珀黑胶**里。盘面在转，她顶，光从她身后涨起来，
 * 一炸——七片盘片散开、白闪、镜头一顿，全彩的她从里面冲出来。
 *
 * 为什么是「透射」而不是「压暗海报」：
 *   压暗海报 → 冲出来 = 上色，会变成"撕贴纸"（前两版死在这）。
 *   半透明黑胶 → 第一帧就是**全彩**，只是隔着一层暖色介质；高潮来自"介质碎裂"，
 *   不是"颜色出现"。整段没有一秒是灰的。
 *
 * 为什么"介质"是七片扇形拼的：
 *   在画面上画裂缝（第一版：白线；第二版：暗缝 + 两条受光棱）永远像划痕 / 金属杆，
 *   试了四轮都不行。改成**实体**就成立了：整块闭合时无缝（= 均匀的暖透射），
 *   炸开时七片各自飞走，缝张开、颜色从缝里回来 —— 碎裂是物体在动，不是画上去的线。
 *
 * 四拍（毫秒）：
 *   0 –  300   透过黑胶：盘面在转，暖光在扫，她压在里面（暖透射，不是灰）
 *   300 –  780 顶：她身后的光涨起来，介质变"厚"一点，镜头慢慢推近
 *   780 – 1080 炸开：硬白闪 + 横向光带 + 盘片散开 + 两块发光的碎片扑向镜头
 *   1080 – 1600 定格：全彩，扫光过发，暗角收拢
 *   1600 – 1880 淡出到主界面
 *
 * 全部 Canvas 矢量绘制：1 次 drawImage + 几十个圆/线/多边形，比上一版水花
 * （144 次 drawImage）轻一个数量级。只在「首次安装 / 版本更新后」播放，见 [SplashGate]。
 */

/** 改成 false 就完全不做动画，系统启动页之后直接进主界面。 */
private const val PLAY_ANIMATION = true

/** 主时间轴（四拍）总长。 */
private const val TOTAL_MS = 1600

/** 最后一拍：淡出。 */
private const val FADE_MS = 280

/** 拍 1 结束 / 拍 2 开始：裂纹开始长。 */
private const val CRACK_START = 300f

/** 拍 2 结束 / 拍 3 开始：炸开。 */
private const val BURST_AT = 780f

/** 拍 3 结束 / 拍 4 开始：定格。 */
private const val SETTLE_AT = 1080f

/** 黑胶的暖色透射（Multiply：保住对比度，只染暖 + 压暗一点）。 */
private val VINYL_AMBER = Color(0xFF6B3D1C)
private val VINYL_COOL = Color(0xFF14202A)

/** 断裂处与冲击锋的光。 */
private val CRACK_LIGHT = Color(0xFFFFF1DA)

/** 扫光：冷一点，压在暖色盘面上才有"玻璃/乙烯基"的感觉。 */
private val SHEEN = Color(0xFFDCEBFF)

private val TWO_PI = (Math.PI * 2).toFloat()

@Composable
fun SplashStage(
    background: Color,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    val art = ImageBitmap.imageResource(R.drawable.splash_art)
    val panes = remember { buildPanes() }
    val shards = remember { buildShards() }
    val flakes = remember { buildFlakes() }
    val dust = remember { buildDust() }

    val skip = reduceMotion || !PLAY_ANIMATION
    val progress = remember { Animatable(if (skip) 1f else 0f) }
    val fade = remember { Animatable(if (skip) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (skip) {
            onFinished()
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(durationMillis = TOTAL_MS, easing = LinearEasing))
        fade.animateTo(0f, tween(durationMillis = FADE_MS, easing = LinearOutSlowInEasing))
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 冲击抖动：整块画布跟着晃，不是让画面里的东西各自晃。
                // 读 progress 的 State，所以每帧重算 layer，不需要重组。
                .graphicsLayer {
                    val hit = settleShake(progress.value * TOTAL_MS)
                    translationX = hit.x
                    translationY = hit.y
                }
        ) {
            drawSplash(art, progress.value * TOTAL_MS, fade.value, panes, shards, flakes, dust)
        }
    }
}

/**
 * 炸开瞬间的镜头抖动：三下、越来越小、220ms 收干净。
 * 冲击感主要来自这个 —— 位移量很小（最大 9px），但它作用在整幅画上。
 */
private fun settleShake(q: Float): Offset {
    val t = span(q, BURST_AT + 10f, BURST_AT + 230f)
    if (t <= 0f || t >= 1f) return Offset.Zero
    val decay = (1f - t) * (1f - t)
    val amp = 9f * decay
    return Offset(
        x = amp * 0.55f * cos(t * 9.2f),
        y = amp * sin(t * 7.4f + 0.6f)
    )
}

private fun DrawScope.drawSplash(
    art: ImageBitmap,
    q: Float,
    fade: Float,
    panes: List<Pane>,
    shards: List<Shard>,
    flakes: List<Flake>,
    dust: List<Dust>
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.40f

    // 开场 200ms 从近黑浮起来（系统启动页是纯色底，这样接得上，不会"跳"）
    val intro = smooth(span(q, 0f, 200f))
    val alpha = fade * intro

    // ---------- 1. 底图：全彩，四段镜头 ----------
    // 冲击 = 速度差，不是位移。先微微往回缩 3%（蓄力），
    // 然后 110ms 推 22% 再弹回 —— 这一下才是"被撞了"。
    val zoom = seq(
        q,
        0f to 1.04f,
        BURST_AT - 40f to 1.005f,
        BURST_AT + 50f to 1.22f,
        SETTLE_AT + 150f to 1.00f,
        TOTAL_MS.toFloat() to 0.985f
    )
    drawArt(art, zoom)

    // ---------- 2. 介质：七片扇形乙烯基压成的一整块 ----------
    // 关键：这层不是"滤镜"，是**实体**。整块闭合时看不出缝（= 均匀的暖透射），
    // 炸开时七片扇形从盘心散开，缝越张越大，全彩从缝里透出来。
    // 这样"碎裂"是物体在动，不是在画上画线 —— 之前两版画裂缝都像划痕。
    val open = smooth(span(q, BURST_AT + 60f, BURST_AT + 480f))
    val push = smooth(span(q, CRACK_START, BURST_AT))
    val keep = 1f - open
    if (open < 0.999f) {
        val a = alpha
        // 拍 2 起介质会"变厚"一点（她顶得越用力，隔着的东西越明显）
        val thick = 0.34f * (1f + 0.12f * push)
        val solid = thick * (1f - smooth(span(open, 0.70f, 1f)))

        // 拍 2：她身后的光在涨（她在里面顶）
        if (push > 0.01f) {
            val px = w * 0.56f
            val py = h * 0.30f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFC88A).copy(alpha = 0.12f * push * a),
                        Color.Transparent
                    ),
                    center = Offset(px, py),
                    radius = w * 0.62f
                ),
                radius = w * 0.62f,
                center = Offset(px, py)
            )
        }

        // 七片盘片
        panes.forEach { p ->
            val u = ((open - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            val e = 1f - (1f - u).pow(2.6f)
            val rot = p.spin * e
            val midR = Math.toRadians((p.mid + rot).toDouble()).toFloat()
            val ax = cx + cos(midR) * w * p.dist * e
            val ay = cy + sin(midR) * w * p.dist * e * 0.92f
            val hw = Math.toRadians((p.halfW * (1f - 0.20f * e)).toDouble()).toFloat()
            val R = w * 2.5f
            val a0 = midR - hw
            val a1 = midR + hw

            val path = Path().apply {
                moveTo(ax, ay)
                var s = 0
                while (s <= 5) {
                    val aa = a0 + (a1 - a0) * (s / 5f)
                    lineTo(ax + cos(aa) * R, ay + sin(aa) * R)
                    s++
                }
                close()
            }
            drawPath(
                path = path,
                color = VINYL_AMBER.copy(alpha = solid * a),
                blendMode = BlendMode.Multiply
            )
            // 断面：一条**宽而软**的受光带（细白线会变成"划痕"，前面几版都死在这）
            val ea = (0.85f * smooth(span(e, 0.02f, 0.30f)) * fade)
            if (ea > 0.01f) {
                drawLine(
                    color = Color(0xFFFFD9A6).copy(alpha = 0.13f * ea),
                    start = Offset(ax, ay),
                    end = Offset(ax + cos(a0) * R, ay + sin(a0) * R),
                    strokeWidth = 40f
                )
                drawLine(
                    color = Color(0xFFFFE7C4).copy(alpha = 0.20f * ea),
                    start = Offset(ax, ay),
                    end = Offset(ax + cos(a0) * R, ay + sin(a0) * R),
                    strokeWidth = 10f
                )
            }
        }

        drawRect(VINYL_COOL.copy(alpha = 0.08f * keep * a), blendMode = BlendMode.Multiply)

        // 沟槽：同心圆，每 6 圈一条稍亮的"轨迹"。盘片一散就跟着消失。
        val grooveA = a * (1f - smooth(span(open, 0f, 0.30f)))
        if (grooveA > 0.002f) {
            var i = 0
            while (i < 32) {
                val r = w * (0.04f + i * 0.056f)
                if (r > w * 1.5f) break
                val bright = if (i % 6 == 0) 1.9f else 1f
                val ga = (0.050f + 0.040f * (0.5f + 0.5f * sin(i * 1.7f))) * bright * grooveA
                drawCircle(
                    color = Color(0xFFFFE9CC).copy(alpha = ga),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.3f)
                )
                i++
            }
        }

        // 盘面在转：一道很宽的柔光"扇叶"绕盘心扫过去。
        // 这是第一拍唯一的"动"，没有它前 300ms 就是一张静止的图。
        val spokeDeg = -18f + q * 0.115f
        val spokeRad = Math.toRadians(spokeDeg.toDouble()).toFloat()
        val sdx = cos(spokeRad)
        val sdy = sin(spokeRad)
        val sw = w * 0.30f
        val sp = Path().apply {
            moveTo(cx - sdx * w * 1.4f, cy - sdy * w * 1.4f)
            lineTo(cx + sdx * w * 1.4f, cy + sdy * w * 1.4f)
            lineTo(cx + sdx * w * 1.4f - sdy * sw, cy + sdy * w * 1.4f + sdx * sw)
            lineTo(cx - sdx * w * 1.4f - sdy * sw, cy - sdy * w * 1.4f + sdx * sw)
            close()
        }
        drawPath(
            path = sp,
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, SHEEN.copy(alpha = 0.07f * keep * a), Color.Transparent),
                start = Offset(cx - sdy * sw * 0.5f, cy + sdx * sw * 0.5f),
                end = Offset(cx + sdy * sw * 0.5f, cy - sdx * sw * 0.5f)
            )
        )

        // 扫光：一条斜着扫过盘面的冷光带（乙烯基的高光）
        val deg = -46f + q * 0.045f
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        val dx = cos(rad)
        val dy = sin(rad)
        val nx = -dy
        val ny = dx
        val L = w * 1.7f
        val W = w * 0.16f
        val band = Path().apply {
            moveTo(cx - dx * L - nx * W, cy - dy * L - ny * W)
            lineTo(cx + dx * L - nx * W, cy + dy * L - ny * W)
            lineTo(cx + dx * L + nx * W, cy + dy * L + ny * W)
            lineTo(cx - dx * L + nx * W, cy - dy * L + ny * W)
            close()
        }
        drawPath(
            path = band,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    SHEEN.copy(alpha = 0.13f * keep * a),
                    Color.Transparent
                ),
                start = Offset(cx - nx * W, cy - ny * W),
                end = Offset(cx + nx * W, cy + ny * W)
            )
        )
    }

    // ---------- 3. 炸开 ----------
    val burst = span(q, BURST_AT, SETTLE_AT + 140f)

    // 波形冲击环：一圈带振幅刻度的椭圆
    if (burst > 0f && burst < 1f) {
        val rad = w * (0.10f + 0.95f * (1f - (1f - burst).pow(2f)))
        val ra = (1f - burst).pow(1.8f) * 0.34f * fade
        if (ra > 0.008f) {
            var i = 0
            while (i < 44) {
                val ang = i / 44f * TWO_PI
                val amp = 0.012f + 0.045f * abs(sin(i * 1.9f + 0.7f))
                val c = cos(ang)
                val s = sin(ang)
                drawLine(
                    color = CRACK_LIGHT.copy(alpha = ra),
                    start = Offset(cx + c * rad, cy + s * rad * 0.60f),
                    end = Offset(cx + c * rad * (1f + amp), cy + s * rad * 0.60f * (1f + amp)),
                    strokeWidth = 2.6f,
                    cap = StrokeCap.Round
                )
                i++
            }
        }
    }

    // 冲击速度线：数量少、细、从远处射出 —— 多了会变成漫画"集中线"
    val streak = span(q, BURST_AT, BURST_AT + 260f)
    if (streak > 0f && streak < 1f) {
        val sa = (1f - streak).pow(2f) * 0.26f * fade
        if (sa > 0.01f) {
            var i = 0
            while (i < 9) {
                val ang = i / 9f * TWO_PI + 0.35f
                val c = cos(ang)
                val s = sin(ang)
                val r0 = w * (0.34f + 0.02f * (i % 3))
                val r1 = r0 + w * (0.12f + 0.18f * streak)
                drawLine(
                    color = Color(0xFFFFE7C4).copy(alpha = sa),
                    start = Offset(cx + c * r0, cy + s * r0 * 0.9f),
                    end = Offset(cx + c * r1, cy + s * r1 * 0.9f),
                    strokeWidth = 1.7f,
                    cap = StrokeCap.Round
                )
                i++
            }
        }
    }

    // 乐谱碎片：小而少，不能变成白色纸屑
    val flakeT = span(q, BURST_AT, TOTAL_MS.toFloat())
    if (flakeT > 0f) {
        flakes.forEach { f ->
            val u = (flakeT - f.delay).coerceIn(0f, 1f)
            if (u <= 0f) return@forEach
            val d = f.dist * w * (1f - (1f - u).pow(2f))
            val x = cx + cos(f.angle) * d
            val y = cy + sin(f.angle) * d * 0.8f + 0.40f * w * u * u
            val hw = w * 0.007f
            val hh = w * 0.016f
            val rot = f.spin * u
            val fa = (1f - smooth(span(u, 0.40f, 1f))) * 0.30f * fade
            if (fa <= 0.01f) return@forEach
            val c = cos(rot)
            val sn = sin(rot)
            fun corner(px: Float, py: Float) =
                Offset(x + px * c - py * sn, y + px * sn + py * c)
            val c0 = corner(-hw, -hh)
            val c1 = corner(hw, -hh)
            val c2 = corner(hw, hh)
            val c3 = corner(-hw, hh)
            val paper = Path().apply {
                moveTo(c0.x, c0.y)
                lineTo(c1.x, c1.y)
                lineTo(c2.x, c2.y)
                lineTo(c3.x, c3.y)
                close()
            }
            drawPath(paper, Color(0xFFE8DCC2).copy(alpha = fa))
            // 谱线（很小的碎片，一条就够）
            drawLine(
                color = Color(0xFF6B5B44).copy(alpha = fa * 0.8f),
                start = Offset(c0.x + (c3.x - c0.x) * 0.5f, c0.y + (c3.y - c0.y) * 0.5f),
                end = Offset(c1.x + (c2.x - c1.x) * 0.5f, c1.y + (c2.y - c1.y) * 0.5f),
                strokeWidth = 1f
            )
        }
    }

    // 尘埃
    if (q > 180f) {
        val da = (0.10f + 0.08f * sin(q * 0.004f)) * fade * smooth(span(q, 180f, 650f))
        dust.forEach { p ->
            val x = p.x * w + sin(q * 0.0007f + p.phase) * w * 0.01f
            val y = p.y * h - q * 0.004f * p.speed * h
            drawCircle(Color(0xFFFFE7C4).copy(alpha = da * p.alpha), p.r * w, Offset(x, y))
        }
    }

    // 黑胶碎片：先画远的，白闪之后再把三块大的甩到镜头前
    val normalShards = shards.filter { !it.hero }
    val heroShards = shards.filter { it.hero }
    drawShards(this, normalShards, q, cx, cy, w, fade)

    // 闪：两帧半的硬切。之前 160ms 的柔光是"雾"，不是"闪"；
    // 白闪必须短，否则它会把推镜的顿挫糊掉。
    val flash = seq(q, BURST_AT - 10f to 0f, BURST_AT + 30f to 1f, BURST_AT + 75f to 0.55f, BURST_AT + 105f to 0f)
    if (flash > 0.004f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFBF4).copy(alpha = 0.88f * flash * fade),
                    Color(0xFFFFF1DA).copy(alpha = 0.40f * flash * fade),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.34f
            ),
            radius = w * 0.34f,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFE8C6).copy(alpha = 0.22f * flash * fade),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 1.10f
            ),
            radius = w * 1.10f,
            center = Offset(cx, cy)
        )
        // 横向光带：镜头光晕的那一条，一下就把"闪"变成"炸"
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFF6E8).copy(alpha = 0.42f * flash * fade),
                    Color.Transparent
                ),
                startY = cy - w * 0.09f,
                endY = cy + w * 0.09f
            )
        )
    }

    // 爆点的暖光（从断口溢出来）
    val bloom = (1f - smooth(span(q, BURST_AT, SETTLE_AT + 340f))) * fade
    if (bloom > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFCE93).copy(alpha = 0.14f * bloom),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = Offset(cx, cy)
        )
    }

    drawShards(this, heroShards, q, cx, cy, w, fade)

    // ---------- 5. 定格：扫光 + 暗角 ----------
    val settle = smooth(span(q, SETTLE_AT, TOTAL_MS.toFloat()))
    if (settle > 0.01f) {
        val sweepX = -w * 0.4f + settle * w * 1.6f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFCFA0).copy(alpha = 0.09f * settle * fade),
                    Color.Transparent
                ),
                start = Offset(sweepX - w * 0.35f, 0f),
                end = Offset(sweepX + w * 0.35f, h * 0.9f)
            )
        )
    }

    val vig = 0.32f + 0.30f * settle
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF07090A).copy(alpha = vig)),
            center = Offset(w * 0.5f, h * 0.45f),
            radius = h * 0.78f
        )
    )
}

/** 全彩底图：cover 裁切 + 以人物脸部为轴心做 zoom。 */
private fun DrawScope.drawArt(art: ImageBitmap, zoom: Float) {
    val w = size.width
    val h = size.height
    val iw = art.width.toFloat()
    val ih = art.height.toFloat()
    val s0 = max(w / iw, h / ih)
    val fx = w * 0.54f
    val fy = h * 0.30f

    val s = s0 * zoom
    val bx = (w - iw * s0) / 2f
    val by = (h - ih * s0) / 2f
    val ox = fx + (bx - fx) * zoom
    val oy = fy + (by - fy) * zoom

    drawImage(
        image = art,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(art.width, art.height),
        dstOffset = IntOffset(ox.toInt(), oy.toInt()),
        dstSize = IntSize((iw * s).toInt(), (ih * s).toInt()),
        filterQuality = FilterQuality.Low
    )
}

/** 碎片：有厚度的薄片（深色本体 + 断口受光边），不是"尖刺"，也不是"纸屑"。 */
private fun drawShards(
    scope: DrawScope,
    list: List<Shard>,
    q: Float,
    cx: Float,
    cy: Float,
    w: Float,
    fade: Float
) {
    // 飞行窗口要盖过白闪（否则它们全被闪盖住，观众根本没看见）
    val t = span(q, BURST_AT - 20f, BURST_AT + 520f)
    if (t <= 0f) return
    list.forEach { s ->
        val u = ((t - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
        if (u <= 0f) return@forEach
        val ease = 1f - (1f - u).pow(2.1f)
        val d = s.dist * w * ease
        val x = cx + cos(s.angle) * d
        val y = cy + sin(s.angle) * d * 0.85f + 0.34f * w * u * u
        val a = (1f - smooth(span(u, 0.66f, 1f))) * fade
        if (a <= 0.01f) return@forEach
        val r = w * s.size * (0.55f + 0.80f * ease)
        val rot = s.spin * u
        val path = shardPath(s.verts, x, y, r, rot)

        // 本体：**被背后那道光打透**的一片乙烯基 —— 是亮的，不是一块黑纸板。
        // 之前用深色填充，在亮画面上就是"打了两个洞"，怎么调都难看。
        scope.drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF7C489).copy(alpha = 0.90f * a),
                    Color(0xFF6A4526).copy(alpha = 0.95f * a)
                ),
                start = Offset(x - r * 0.7f, y - r * 0.7f),
                end = Offset(x + r * 0.8f, y + r * 0.8f)
            )
        )
        // 断口：暗边 + 内侧一道白（有厚度的切口）
        scope.drawPath(
            path = path,
            color = Color(0xFF3A2413).copy(alpha = (if (s.hero) 0.55f else 0.40f) * a),
            style = Stroke(width = if (s.hero) 3.4f else 2.2f)
        )
        val c = cos(rot)
        val sn = sin(rot)
        // 外弧那一条（v1→v3）最吃光：一条更亮的白
        val v1 = s.verts[1]
        val v3 = s.verts[3]
        scope.drawLine(
            color = Color(0xFFFFF2DC).copy(alpha = (if (s.hero) 0.95f else 0.65f) * a),
            start = Offset(x + (v1.x * c - v1.y * sn) * r, y + (v1.x * sn + v1.y * c) * r),
            end = Offset(x + (v3.x * c - v3.y * sn) * r, y + (v3.x * sn + v3.y * c) * r),
            strokeWidth = if (s.hero) 2.8f else 1.6f,
            cap = StrokeCap.Round
        )
        // 切面上的一道沟槽：说明这是唱片，不是碎玻璃
        if (s.hero) {
            val lo = Offset(
                x + (v1.x * c - v1.y * sn) * r * 0.62f,
                y + (v1.x * sn + v1.y * c) * r * 0.62f
            )
            val hi = Offset(
                x + (v3.x * c - v3.y * sn) * r * 0.82f,
                y + (v3.x * sn + v3.y * c) * r * 0.82f
            )
            scope.drawLine(
                color = Color(0xFFFFDDB8).copy(alpha = 0.26f * a),
                start = lo,
                end = hi,
                strokeWidth = 2f
            )
            scope.drawLine(
                color = Color(0xFF2A1B12).copy(alpha = 0.30f * a),
                start = Offset(lo.x, lo.y + r * 0.12f),
                end = Offset(hi.x, hi.y + r * 0.12f),
                strokeWidth = 2f
            )
        }
    }
}

private fun shardPath(verts: List<Offset>, cx: Float, cy: Float, r: Float, rot: Float): Path {
    val c = cos(rot)
    val s = sin(rot)
    val p = Path()
    verts.forEachIndexed { i, v ->
        val x = cx + (v.x * c - v.y * s) * r
        val y = cy + (v.x * s + v.y * c) * r
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

// ---------------- 数据 ----------------

/**
 * 一片盘片：整块介质是这些扇形拼起来的。
 * [mid] 扇形的中线角度（度），[halfW] 半张角，[dist] 飞出多远（屏宽），
 * [spin] 自转（度），[delay] 0..1 谁先松。
 */
private data class Pane(
    val mid: Float,
    val halfW: Float,
    val dist: Float,
    val spin: Float,
    val delay: Float
)

/** 一块碎片：方向 / 最终距离 / 尺寸 / 自转 / 出生延迟 / 形状（单位多边形）。 */
private data class Shard(
    val angle: Float,
    val dist: Float,
    val size: Float,
    val spin: Float,
    val delay: Float,
    val verts: List<Offset>,
    val hero: Boolean
)

private data class Flake(
    val angle: Float,
    val dist: Float,
    val spin: Float,
    val delay: Float
)

private data class Dust(
    val x: Float,
    val y: Float,
    val r: Float,
    val alpha: Float,
    val phase: Float,
    val speed: Float
)

private fun buildPanes(): List<Pane> {
    val rnd = java.util.Random(53)
    val n = 7
    return List(n) { k ->
        val mid = (k + 0.5f) * 360f / n
        Pane(
            mid = mid,
            // 比 180/n 宽 3°，闭合时相邻两片重叠一点，看不到缝
            halfW = 180f / n + 3f,
            dist = 1.05f + rnd.nextFloat() * 0.60f,
            spin = (rnd.nextFloat() - 0.5f) * 44f,
            delay = rnd.nextFloat() * 0.10f
        )
    }
}

private fun buildShards(): List<Shard> {
    val rnd = java.util.Random(7)
    // 少而大：十几片小碎屑会变成"屏幕上落灰"，六片大的才像碎片
    return List(6) { i ->
        // 形状 = 唱片的一块扇形（内弧 + 外弧），不是随手一个多边形。
        // 六边形读起来像"打了洞"，扇形切片才读得出"唱片碎了"。
        val a0 = rnd.nextFloat() * TWO_PI
        val spanA = 0.45f + rnd.nextFloat() * 0.60f
        val r0 = 0.22f + rnd.nextFloat() * 0.16f
        val r1 = 0.92f + rnd.nextFloat() * 0.22f
        fun at(a: Float, r: Float) = Offset(cos(a) * r, sin(a) * r)
        val verts = listOf(
            at(a0, r0),
            at(a0 + spanA * 0.22f, r1 * 0.96f),
            at(a0 + spanA * 0.55f, r1),
            at(a0 + spanA * 0.82f, r1 * 0.97f),
            at(a0 + spanA, r0 * 1.10f)
        )
        val hero = i < 2
        Shard(
            // 大的只走画面的两侧和下方，绝不超过她的眼睛
            angle = if (hero) 2.45f + i * 1.90f else rnd.nextFloat() * TWO_PI,
            dist = if (hero) 1.15f + rnd.nextFloat() * 0.25f else 0.45f + rnd.nextFloat() * 0.60f,
            size = if (hero) 0.110f + rnd.nextFloat() * 0.050f else 0.045f + rnd.nextFloat() * 0.030f,
            spin = (rnd.nextFloat() - 0.5f) * 180f,
            delay = if (hero) 0.04f + rnd.nextFloat() * 0.05f else rnd.nextFloat() * 0.24f,
            verts = verts,
            hero = hero
        )
    }
}

private fun buildFlakes(): List<Flake> {
    val rnd = java.util.Random(31)
    return List(3) {
        Flake(
            angle = rnd.nextFloat() * TWO_PI,
            dist = 0.30f + rnd.nextFloat() * 0.65f,
            spin = (rnd.nextFloat() - 0.5f) * 420f,
            delay = rnd.nextFloat() * 0.30f
        )
    }
}

private fun buildDust(): List<Dust> {
    val rnd = java.util.Random(41)
    return List(34) {
        Dust(
            x = rnd.nextFloat(),
            y = 0.15f + rnd.nextFloat() * 0.9f,
            r = 0.0012f + rnd.nextFloat() * 0.0026f,
            alpha = 0.25f + rnd.nextFloat() * 0.55f,
            phase = rnd.nextFloat() * 6.28f,
            speed = 0.4f + rnd.nextFloat() * 1.2f
        )
    }
}

// ---------------- 小工具 ----------------

private fun span(v: Float, a: Float, b: Float): Float =
    ((v - a) / (b - a)).coerceIn(0f, 1f)

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

/** 关键帧插值：keys 必须是按时间递增的 (毫秒, 值)。 */
private fun seq(q: Float, vararg keys: Pair<Float, Float>): Float {
    if (q <= keys.first().first) return keys.first().second
    for (i in 1 until keys.size) {
        val (x0, y0) = keys[i - 1]
        val (x1, y1) = keys[i]
        if (q <= x1) {
            val u = ((q - x0) / (x1 - x0)).coerceIn(0f, 1f)
            return y0 + (y1 - y0) * smooth(u)
        }
    }
    return keys.last().second
}
