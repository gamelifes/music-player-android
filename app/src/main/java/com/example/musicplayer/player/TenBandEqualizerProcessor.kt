/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import com.example.musicplayer.MusicApp

/**
 * 十段参数均衡器（Peaking EQ，RBJ Audio EQ Cookbook 系数，Direct Form I 级联，tanh 软限幅）。
 *
 * 严格按 Media3 AudioProcessor 契约实现，三个硬性要求：
 * 1) queueInput() 里向 replaceOutputBuffer() 拿到的输出缓冲写完数据后，必须调用 flip()，
 *    使 getOutput() 返回 position=0 / limit=已写字节数的“读模式”缓冲。
 *    框架只认 position~limit 之间的数据；不写 flip 时 position==limit，框架视为空缓冲，
 *    AudioTrack 收不到任何字节 —— 表现为完全无声、播放进度卡死。
 *    （TeeAudioProcessor / SilenceSkippingAudioProcessor 等官方实现均在写完后的 flip()。）
 * 2) 输入缓冲按只读对待：用绝对索引读取，不移动其 position，处理完一次性
 *    inputBuffer.position(limit) 表示全部消费。
 * 3) 输出长度必须与输入长度一致（完整帧做滤波，末尾不完整帧原样透传），
 *    长度变化会破坏 DefaultAudioSink 的字节/帧计数与 A/V 同步。
 *
 * 线程模型：增益/开关由 UI 线程写入并即时重建 @Volatile 系数快照（无锁读）；
 * 滤波状态（Direct Form I 历史）只存在于音频线程。
 */
@UnstableApi
class TenBandEqualizerProcessor : BaseAudioProcessor() {
    private val logFile: File by lazy {
        val logDir = java.io.File(MusicApp.instance.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        java.io.File(logDir, "equalizer.log")
    }
    private val logWriter by lazy {
        FileWriter(logFile, true)
    }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun logToFile(message: String) {
        val timestamp = timestampFormat.format(Date())
        val logEntry = "$timestamp $message\n"
        try {
            logWriter.write(logEntry)
            logWriter.flush()
        } catch (e: IOException) {
            Log.e("EQ_LOG_ERROR", "Failed to write to log file", e)
        }
    }
    


    companion object {
        private const val TAG = "EQ"
        const val BAND_COUNT = 10
        private const val Q = 1.2f

        val FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        const val MAX_DB = 12f
        const val MIN_DB = -12f
        const val MAX_PREAMP_DB = 12f
        const val MIN_PREAMP_DB = -12f

        val PRESETS: List<Preset> = listOf(
            Preset("自定义", List(BAND_COUNT) { 0f }, 0f),
            Preset("摇滚", listOf(5.5f, 4f, 1.5f, -1.5f, -2.5f, 3f, 5.5f, 6f, 6f, 5.5f), 0f),
            Preset("流行", listOf(-2f, 1f, 2.5f, 3f, 2f, 0f, -1f, -1.5f, -2f, -2.5f), 0f),
            Preset("古典", listOf(4f, 3f, 2f, 0f, 0f, 0f, -2f, -2f, 0f, 3f), 0f),
            Preset("人声", listOf(-3f, -2f, 0f, 2f, 5f, 5.5f, 5f, 2.5f, 0f, -1f), 0f),
            Preset("低音增强", listOf(7f, 6f, 5f, 3.5f, 2f, 0f, 0f, 0f, 0f, 0f), 0f),
            Preset("平坦", List(BAND_COUNT) { 0f }, 0f),
        )

        val FLAT: Preset = Preset("平坦", List(BAND_COUNT) { 0f }, 0f)
    }



    data class Preset(
        val name: String,
        val bandsDb: List<Float>,
        val preampDb: Float
    )

    // ---- 参数（UI 线程写）----
    private val writeLock = Any()
    private val gainsDb = FloatArray(BAND_COUNT)
    private var preampDb = 0f
    private var isEqEnabled = false
    private var stateEpoch = 0

    /** 启用或禁用均衡器 */
    fun setEnabled(enabled: Boolean) {
        synchronized(writeLock) {
            isEqEnabled = enabled
            rebuild()
        }
        logToFile("setEnabled: $enabled")
        onEqChanged?.invoke()
    }

    // ---- 配置快照（音频线程读；rebuild 在 writeLock 内整体替换引用）----
    @Volatile private var coeffs: Array<FloatArray> = identityCoeffs()
    @Volatile private var snapshotIsIdentity = true
    @Volatile private var bypass = true

    private var encoding = C.ENCODING_INVALID
    private var sampleRate = 0
    private var channelCount = 0
    private var frameSizeBytes = 0

    // ---- 滤波状态（音频线程独占）----
    private var states: Array<Array<FloatArray>> = emptyArray()
    private var appliedEpoch = -1

    private var preampLinear = 1f

    var onEqChanged: (() -> Unit)? = null

    // ---------- 公共 API ----------

    fun setAllGains(dbs: List<Float>) {
        synchronized(writeLock) {
            for (i in gainsDb.indices) gainsDb[i] = (dbs.getOrNull(i) ?: 0f).coerceIn(-12f, 12f)
            rebuild()
        }
        logToFile("setAllGains: ${dbs.joinToString()}")
        onEqChanged?.invoke()
    }

    fun setPreamp(db: Float) {
        synchronized(writeLock) {
            preampDb = db.coerceIn(-12f, 12f)
            rebuild()
        }
        logToFile("setPreamp: $db dB")
        onEqChanged?.invoke()
    }

    /** 滤波状态清零（切歌/跳播时 UI 可调用，避免上一首的滤波历史造成咔哒声） */
    fun clearStates() {
        synchronized(writeLock) { stateEpoch++ }
    }

    // ---------- 生命周期 ----------

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {

        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        frameSizeBytes = when (encoding) {
            C.ENCODING_PCM_16BIT -> channelCount * 2
            C.ENCODING_PCM_FLOAT -> channelCount * 4
            else -> 0
        }
        logToFile("onConfigure: sampleRate=${inputAudioFormat.sampleRate}, channelCount=${inputAudioFormat.channelCount}, encoding=${inputAudioFormat.encoding}")
        if (sampleRate <= 0 || frameSizeBytes == 0 || channelCount <= 0) {
            states = emptyArray()
            bypass = true
            logToFile("onConfigure: invalid format, bypass=true")
            return inputAudioFormat
        }
        states = Array(channelCount) { Array(BAND_COUNT) { FloatArray(4) } }
        appliedEpoch = stateEpoch
        rebuild()
        logToFile("onConfigure: valid format, bypass=$bypass, snapshotIsIdentity=$snapshotIsIdentity")
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val runnable =
            !bypass && !snapshotIsIdentity && frameSizeBytes > 0 && states.isNotEmpty()

        if (!runnable || (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT)) {
            // 直通（EQ 关闭 / 恒等 / 不支持的编码）：同样必须 flip()，否则框架视为空缓冲。

            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        logToFile("queueInput: processing audio, remaining=$remaining")


        val k = coeffs
        val st = states
        val preamp = preampLinear
        if (appliedEpoch != stateEpoch) {
            appliedEpoch = stateEpoch
            for (ch in st) for (band in ch) band.fill(0f)
            logToFile("queueInput: filter states cleared due to epoch change")
        }

        try {
            if (encoding == C.ENCODING_PCM_16BIT) {
                processPcm16(inputBuffer, k, st, preamp)
            } else {
                processFloat(inputBuffer, k, st, preamp)
            }
        } catch (t: Throwable) {
            // 任何异常都不允许把坏缓冲交给框架：整段直通并 flip。

             logToFile("DSP error, fallback to passthrough")
             Log.w(TAG, "DSP error, fallback to passthrough", t)
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
        }
    }

    /** 16-bit PCM：从 input 绝对索引读、向 output 顺序写，写完 flip。 */
    private fun processPcm16(
        input: ByteBuffer,
        k: Array<FloatArray>,
        st: Array<Array<FloatArray>>,
        preamp: Float
    ) {
        val limit = input.limit()
        var p = input.position()
        val out = replaceOutputBuffer(input.remaining())
        val framesTotal = input.remaining() / frameSizeBytes

        var frame = 0
        while (frame < framesTotal) {
            for (ch in 0 until channelCount) {
                val x = input.getShort(p).toFloat() / 32768f
                p += 2
                out.putShort(pcm16Of(softClip(filterSample(x, ch, k, st, preamp))))
            }
            frame++
        }
        // 末尾不完整帧原样透传，保证输出长度 == 输入长度
        while (p < limit) {
            out.put(input.get(p))
            p++
        }
        input.position(limit)
        out.flip()
    }

    /** 32-bit float PCM：同上。 */
    private fun processFloat(
        input: ByteBuffer,
        k: Array<FloatArray>,
        st: Array<Array<FloatArray>>,
        preamp: Float
    ) {
        val limit = input.limit()
        var p = input.position()
        val out = replaceOutputBuffer(input.remaining())
        val framesTotal = input.remaining() / frameSizeBytes

        var frame = 0
        while (frame < framesTotal) {
            for (ch in 0 until channelCount) {
                val x = input.getFloat(p)
                p += 4
                out.putFloat(softClip(filterSample(x, ch, k, st, preamp)))
            }
            frame++
        }
        while (p < limit) {
            out.put(input.get(p))
            p++
        }
        input.position(limit)
        out.flip()
    }

    override fun onFlush() {
        logToFile("onFlush: filter states reset")

        appliedEpoch = -1
    }

    override fun onReset() {
        logToFile("onReset: equalizer reset")

        encoding = C.ENCODING_INVALID
        sampleRate = 0
        channelCount = 0
        frameSizeBytes = 0
        states = emptyArray()
        appliedEpoch = -1
    }

    // ---------- DSP ----------

    private fun filterSample(
        x: Float,
        ch: Int,
        k: Array<FloatArray>,
        st: Array<Array<FloatArray>>,
        preamp: Float
    ): Float {

        var v = x * preamp
        val chStates = st[ch]
        for (b in 0 until BAND_COUNT) {
            val c = k[b]
            val s = chStates[b]
            val y = c[0] * v + c[1] * s[0] + c[2] * s[1] - c[3] * s[2] - c[4] * s[3]
            s[1] = s[0]; s[0] = v; s[3] = s[2]; s[2] = y
            v = y
        }

        return v
    }

    private fun softClip(x: Float): Float = tanh(x)

    private fun pcm16Of(v: Float): Short =
        (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    private fun rebuild() {
        val sr = sampleRate
        val enabled = isEqEnabled
        var identity = true
        if (sr <= 0 || !enabled) {
            coeffs = identityCoeffs()
            snapshotIsIdentity = true
            bypass = true
            preampLinear = 1f
            logToFile("rebuild: sr=$sr, enabled=$enabled -> identity coeffs, bypass=true")
            return
        }
        val next = Array(BAND_COUNT) { FloatArray(5) }
        for (i in 0 until BAND_COUNT) {
            val g = gainsDb[i]
            if (kotlin.math.abs(g) > 0.01f) identity = false
            next[i] = computeBand(FREQUENCIES[i].toFloat(), g, sr)
            logToFile("rebuild: band $i freq=${FREQUENCIES[i]}Hz gain=${g}dB -> coeffs=${next[i].joinToString()}")
        }
        val pre = preampDb
        if (kotlin.math.abs(pre) > 0.01f) identity = false
        preampLinear = 10f.pow(pre / 20f)
        coeffs = next
        snapshotIsIdentity = identity
        bypass = false
        logToFile("rebuild: preamp=$pre dB -> linear=$preampLinear, identity=$identity, bypass=$bypass")
    }

    /** RBJ Audio EQ Cookbook —— peaking EQ。输出 {b0,b1,b2,a1,a2}。 */
    private fun computeBand(freqHz: Float, gainDb: Float, sr: Int): FloatArray {
        val f0 = freqHz.coerceIn(1f, sr * 0.45f)
        val a = 10f.pow(gainDb / 40f)
        val w0 = (2.0 * Math.PI * f0 / sr).toFloat()
        val alpha = sin(w0) / (2f * Q)
        val c = cos(w0)

        val b0 = 1f + alpha * a
        val b1 = -2f * c
        val b2 = 1f - alpha * a
        val a0 = 1f + alpha / a
        val a1 = -2f * c
        val a2 = 1f - alpha / a

        if (!a0.isFinite() || a0 == 0f) {
            return floatArrayOf(1f, 0f, 0f, 0f, 0f)
        }
        return floatArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun identityCoeffs(): Array<FloatArray> =
        Array(BAND_COUNT) { floatArrayOf(1f, 0f, 0f, 0f, 0f) }
}
