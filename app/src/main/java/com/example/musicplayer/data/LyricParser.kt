/*
 * Author: lihaihua
 * Config: ai coding
 * Repository: https://github.com/gamelifes/music-player-android.git
 */

package com.example.musicplayer.data

/** 一个词（卡拉OK 高亮的最小单位），[endMs] 为下一个词的起点 */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/** 一行歌词，[timeMs] 为该行开始演唱的时刻 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    /** 词级时间戳（A2 格式）。为空表示只有行级时间戳 */
    val words: List<LyricWord> = emptyList()
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()
}

object LyricParser {

    /** 行级标签：[00:12.34] 或 [00:12.3] */
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** 词级标签（A2 增强格式）：<00:12.34> */
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    private val META_TAG = Regex("""^\[(ti|ar|al|by|offset|length|re|ve):(.*)]$""", RegexOption.IGNORE_CASE)

    private val EXTRA_META = Regex("""^<.*>$""")

    /**
     * 解析 LRC 歌词，兼容三种写法：
     *
     * 1. **行级**（最常见）：`[00:12.34]歌词内容` —— 只能做逐行高亮
     * 2. **A2 增强**（可逐词）：`[00:12.34]<00:12.34>Hello <00:12.90>world`
     * 3. **词级**：`[00:12.34]Hello [00:12.90]world` —— 多个行标签连用
     */
    fun parse(content: String): List<LyricLine> {
        if (content.isBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (META_TAG.matches(line)) return@forEach
            if (EXTRA_META.matches(line)) return@forEach

            val parsed = parseLine(line)
            if (parsed.isNotEmpty()) {
                lines += parsed
            } else if (!line.startsWith("[") && !line.startsWith("<") && lines.isEmpty()) {
                // 无任何时间标签的纯文本，当作从头一直显示
                lines += LyricLine(0L, line)
            }
        }

        // 给每个词补上结束时间
        val normalized = lines.map { line ->
            if (!line.hasWordTiming) return@map line
            val words = line.words.mapIndexed { i, w ->
                val end = line.words.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
                if (w.endMs == Long.MAX_VALUE) w.copy(endMs = end) else w
            }
            line.copy(words = words)
        }

        return normalized
            .mapIndexed { i, line -> i to line }
            .sortedWith(compareBy({ it.second.timeMs }, { it.first }))
            .map { it.second }
    }

    private fun parseLine(line: String): List<LyricLine> {
        val lineTags = TIME_TAG.findAll(line).toList()
        if (lineTags.isEmpty()) return emptyList()

        val tail = line.substring(lineTags.last().range.last + 1)

        // ---- A2 增强格式：<mm:ss.xx>word<mm:ss.xx>word
        val wordTags = WORD_TAG.findAll(tail).toList()
        if (wordTags.isNotEmpty()) {
            val times = wordTags.map { toMillis(it) }
            val pieces = mutableListOf<String>()
            var prevEnd = 0
            wordTags.forEach { m ->
                pieces += tail.substring(prevEnd, m.range.first)
                prevEnd = m.range.last + 1
            }
            pieces += tail.substring(prevEnd)

            val words = pieces.mapIndexed { i, text ->
                LyricWord(
                    startMs = times[i],
                    endMs = times.getOrNull(i + 1) ?: Long.MAX_VALUE,
                    text = text
                )
            }
            val flat = words.joinToString("") { it.text }.trim()
            return listOf(LyricLine(times.first(), flat.ifEmpty { tail.trim() }, words))
        }

        // ---- 词级格式：[00:12.34]word1 [00:12.90]word2
        if (lineTags.size > 1) {
            val times = lineTags.map { toMillis(it) }
            val words = lineTags.mapIndexed { i, m ->
                val nextStart = lineTags.getOrNull(i + 1)?.range?.first ?: line.length
                LyricWord(
                    startMs = times[i],
                    endMs = times.getOrNull(i + 1) ?: Long.MAX_VALUE,
                    text = line.substring(m.range.last + 1, nextStart).trim()
                )
            }.filter { it.text.isNotEmpty() }
            if (words.isNotEmpty()) {
                return listOf(
                    LyricLine(times.first(), words.joinToString(" ") { it.text }, words)
                )
            }
        }

        // ---- 普通行级格式
        return lineTags.map { LyricLine(toMillis(it), tail.trim()) }
    }

    private fun toMillis(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: return 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: return 0L
        val fracText = match.groupValues[3]
        val frac = when (fracText.length) {
            1 -> (fracText.toLongOrNull() ?: 0L) * 100
            2 -> (fracText.toLongOrNull() ?: 0L) * 10
            3 -> fracText.toLongOrNull() ?: 0L
            else -> 0L
        }
        return minutes * 60_000 + seconds * 1_000 + frac
    }

    /** 二分查找当前应高亮的歌词行下标，返回 -1 表示还未到第一句 */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    /**
     * 当前行内正在唱的词的下标。
     * 返回值已夹逼到 [0, words.lastIndex]：没有词级时间戳或时间戳异常时返回 0
     * （即整行视为「第一个词」），上层无需再判越界。
     */
    fun activeWordIndex(line: LyricLine, positionMs: Long): Int {
        if (line.words.isEmpty()) return -1
        var idx = 0
        line.words.forEachIndexed { i, w ->
            if (positionMs >= w.startMs) idx = i
        }
        return idx.coerceIn(0, line.words.size - 1)
    }
}
