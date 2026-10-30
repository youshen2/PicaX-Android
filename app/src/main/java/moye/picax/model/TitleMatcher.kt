package moye.picax.model

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Same title candidates, bracket handling and bounded edit distance as ComicTitleMatcher.swift. */
object TitleMatcher {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        .codePoints().filter { Character.isLetterOrDigit(it) }.toArray().let { String(it, 0, it.size) }
    fun searchTitle(title: String) = strip(title.split('|', '｜', '\n').first()).ifBlank { title }
    fun candidates(title: String): List<String> {
        val value = title.trim().replace(" / ", "|").replace(" ／ ", "|")
        val aliases = value.split('|', '｜', '\n')
        fun candidate(text: String, minimum: Int): String? {
            val normalized = normalize(text)
            val cjk = normalized.codePoints().anyMatch { it in 0x2e80..0x9fff || it in 0xac00..0xd7af || it in 0x20000..0x3134f }
            return normalized.takeIf { it.codePointCount(0, it.length) >= (if(cjk) minOf(minimum, 2) else minimum) && !it.all(Char::isDigit) }
        }
        return buildList {
            if(aliases.size == 1 && normalize(strip(value)) == normalize(value)) candidate(title, 2)?.let(::add)
            aliases.forEach { candidate(strip(it).ifBlank { it }, 4)?.let(::add) }
        }.distinct()
    }
    private fun strip(value: String): String {
        val closings = mapOf('[' to ']', '【' to '】', '［' to '］')
        val stack = ArrayDeque<Char>()
        return buildString {
            value.forEach { char ->
                when {
                    char in closings -> { stack.addLast(closings.getValue(char)); if(stack.size == 1) append(' ') }
                    stack.lastOrNull() == char -> { stack.removeLast(); if(stack.isEmpty()) append(' ') }
                    stack.isEmpty() -> append(char)
                }
            }
        }.trim()
    }
    fun language(value: String): String? = when(val normalized = normalize(value)) {
        "", "na", "unknown", "other", "其他", "未知" -> null
        "chinese", "chinesetranslated", "中文", "简体中文", "繁體中文", "繁体中文", "中国語" -> "chinese"
        "japanese", "日本語", "日文" -> "japanese"
        "english", "英文", "英語" -> "english"
        else -> normalized
    }
    fun matches(a: Comic, b: Comic, threshold: Int = 95): Boolean {
        val left = language(a.language); val right = language(b.language)
        if(left != null && right != null && left != right) return false
        return candidates(a.title).any { x -> candidates(b.title).any { y -> similar(x, y, threshold) } }
    }
    fun similar(a: String, b: String, threshold: Int): Boolean {
        if(a.isEmpty() || b.isEmpty()) return false
        if(a == b) return true
        val x = a.codePoints().toArray(); val y = b.codePoints().toArray()
        val limit = max(x.size, y.size) * (100 - threshold.coerceIn(50, 100)) / 100
        if(abs(x.size - y.size) > limit) return false
        var previous = IntArray(y.size + 1) { it }; var next = IntArray(y.size + 1)
        x.forEachIndexed { i, c ->
            next[0] = i + 1
            var minimum = next[0]
            y.forEachIndexed { j, d -> next[j + 1] = minOf(previous[j + 1] + 1, next[j] + 1, previous[j] + if(c == d) 0 else 1); minimum = minOf(minimum, next[j + 1]) }
            if(minimum > limit) return false
            val swap = previous; previous = next; next = swap
        }
        return previous.last() <= limit
    }
}
