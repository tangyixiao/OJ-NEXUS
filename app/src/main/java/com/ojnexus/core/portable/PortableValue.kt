package com.ojnexus.core.portable

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * A minimal, order-preserving JSON value tree plus the strict decoder and the
 * canonical writer for Portable Workspace v1.
 *
 * This file is controller-owned and frozen: the contract validator, the
 * repository, the archive store and the UI all agree on this one representation.
 * It is pure Kotlin (no Android, no Room, no Compose) so unit tests can run on
 * the JVM.
 */
sealed class PortableValue {
    /** Insertion order is preserved for duplicate-key detection and reporting. */
    data class Obj(val entries: LinkedHashMap<String, PortableValue>) : PortableValue()
    data class Arr(val items: ArrayList<PortableValue>) : PortableValue()
    data class Str(val value: String) : PortableValue()
    /** The original JSON number literal, preserved verbatim for canonical output. */
    data class Num(val literal: String) : PortableValue()
    data class Bool(val value: Boolean) : PortableValue()
    data object Null : PortableValue()
}

/**
 * Strict JSON parser + Python-compatible canonical writer.
 *
 * The writer reproduces `json.dumps(doc, sort_keys=True, separators=(",", ":"),
 * ensure_ascii=False) + "\n"` byte-for-byte (contracts/portable/v1/check.py).
 */
object PortableJson {
    private const val MAX_DEPTH = 200

    fun parse(bytes: ByteArray): PortableValue = Parser(decodeUtf8Strict(bytes)).parseDocument()

    fun write(value: PortableValue): ByteArray {
        val sb = StringBuilder()
        append(value, sb)
        sb.append('\n')
        return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    private fun decodeUtf8Strict(bytes: ByteArray): String {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            throw PortableContractException(
                PortableReject(
                    PortableRejectCategory.INVALID_INPUT,
                    "package is not valid UTF-8: ${e.message}",
                    "$",
                ),
            )
        }
    }

    private fun append(v: PortableValue, sb: StringBuilder) {
        when (v) {
            is PortableValue.Obj -> {
                sb.append('{')
                val keys = v.entries.keys.sortedWith(CODE_POINT_ORDER)
                for ((i, k) in keys.withIndex()) {
                    if (i > 0) sb.append(',')
                    appendString(k, sb)
                    sb.append(':')
                    append(v.entries.getValue(k), sb)
                }
                sb.append('}')
            }
            is PortableValue.Arr -> {
                sb.append('[')
                for ((i, item) in v.items.withIndex()) {
                    if (i > 0) sb.append(',')
                    append(item, sb)
                }
                sb.append(']')
            }
            is PortableValue.Str -> appendString(v.value, sb)
            is PortableValue.Num -> sb.append(normalizeNumber(v.literal))
            is PortableValue.Bool -> sb.append(if (v.value) "true" else "false")
            PortableValue.Null -> sb.append("null")
        }
    }

    /** Python's sorted() orders by Unicode code point, not UTF-16 code unit. */
    private val CODE_POINT_ORDER = Comparator<String> { a, b ->
        val ia = a.codePointCount(0, a.length)
        val ib = b.codePointCount(0, b.length)
        var pa = 0
        var pb = 0
        var ca = 0
        var cb = 0
        while (ca < ia && cb < ib) {
            val cpa = a.codePointAt(pa)
            val cpb = b.codePointAt(pb)
            if (cpa != cpb) return@Comparator cpa.compareTo(cpb)
            pa += Character.charCount(cpa)
            pb += Character.charCount(cpb)
            ca++
            cb++
        }
        ia.compareTo(ib)
    }

    private fun appendString(s: String, sb: StringBuilder) {
        sb.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> {
                    sb.append("\\u")
                    val h = c.code.toString(16)
                    sb.append("0".repeat(4 - h.length))
                    sb.append(h)
                }
                else -> sb.append(c)
            }
            i++
        }
        sb.append('"')
    }

    /** v1 numbers are integers; -0 is normalized by Python's int repr. */
    private fun normalizeNumber(literal: String): String =
        if (literal == "-0") "0" else literal

    private class Parser(private val text: String) {
        private var pos = 0

        fun parseDocument(): PortableValue {
            skipWs()
            val value = parseValue(0)
            skipWs()
            if (pos != text.length) fail("trailing data after JSON value")
            return value
        }

        fun skipWs() {
            while (pos < text.length && text[pos] in " \t\n\r") pos++
        }

        fun atEnd(): Boolean = pos >= text.length

        fun fail(message: String): Nothing {
            throw PortableContractException(
                PortableReject(
                    PortableRejectCategory.INVALID_INPUT,
                    "malformed JSON: $message",
                    "offset $pos",
                ),
            )
        }

        fun parseValue(depth: Int): PortableValue {
            if (depth > MAX_DEPTH) fail("document nesting is too deep to decode safely")
            skipWs()
            if (pos >= text.length) fail("unexpected end of input")
            return when (val c = text[pos]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> PortableValue.Str(parseString())
                't' -> parseLiteral("true", PortableValue.Bool(true))
                'f' -> parseLiteral("false", PortableValue.Bool(false))
                'n' -> parseLiteral("null", PortableValue.Null)
                '-' -> parseNumber()
                in '0'..'9' -> parseNumber()
                'N' -> {
                    if (text.startsWith("NaN", pos)) fail("NaN is not a valid JSON value")
                    else fail("unexpected character '$c'")
                }
                'I' -> {
                    if (text.startsWith("Infinity", pos)) fail("Infinity is not a valid JSON value")
                    else fail("unexpected character '$c'")
                }
                else -> fail("unexpected character '$c'")
            }
        }

        private fun parseLiteral(word: String, value: PortableValue): PortableValue {
            if (!text.startsWith(word, pos)) fail("invalid literal")
            pos += word.length
            return value
        }

        private fun parseObject(depth: Int): PortableValue.Obj {
            pos++ // '{'
            val map = LinkedHashMap<String, PortableValue>()
            skipWs()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return PortableValue.Obj(map)
            }
            while (true) {
                skipWs()
                if (pos >= text.length || text[pos] != '"') fail("expected object key string")
                val key = parseString()
                if (map.containsKey(key)) {
                    throw PortableContractException(
                        PortableReject(
                            PortableRejectCategory.INVALID_INPUT,
                            "duplicate object key \"$key\"",
                            key,
                        ),
                    )
                }
                skipWs()
                if (pos >= text.length || text[pos] != ':') fail("expected ':'")
                pos++
                map[key] = parseValue(depth + 1)
                skipWs()
                if (pos >= text.length) fail("unterminated object")
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return PortableValue.Obj(map)
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun parseArray(depth: Int): PortableValue.Arr {
            pos++ // '['
            val list = ArrayList<PortableValue>()
            skipWs()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return PortableValue.Arr(list)
            }
            while (true) {
                list.add(parseValue(depth + 1))
                skipWs()
                if (pos >= text.length) fail("unterminated array")
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return PortableValue.Arr(list)
                    }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= text.length) fail("unterminated escape")
                        when (val e = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val cp = readHex4()
                                if (Character.isHighSurrogate(cp.toChar())) {
                                    if (pos + 1 < text.length && text[pos] == '\\' &&
                                        text[pos + 1] == 'u'
                                    ) {
                                        pos += 2
                                        val low = readHex4()
                                        if (!Character.isLowSurrogate(low.toChar())) {
                                            fail("invalid low surrogate escape")
                                        }
                                        sb.appendCodePoint(
                                            Character.toCodePoint(cp.toChar(), low.toChar()),
                                        )
                                    } else {
                                        fail("lone high surrogate escape")
                                    }
                                } else if (Character.isLowSurrogate(cp.toChar())) {
                                    fail("lone low surrogate escape")
                                } else {
                                    sb.appendCodePoint(cp)
                                }
                            }
                            else -> fail("invalid escape '\\$e'")
                        }
                    }
                    c.code < 0x20 -> fail("unescaped control character in string")
                    else -> sb.append(c)
                }
            }
            fail("unterminated string")
        }

        private fun readHex4(): Int {
            if (pos + 4 > text.length) fail("incomplete unicode escape")
            var v = 0
            repeat(4) {
                val c = text[pos++]
                val d = Character.digit(c, 16)
                if (d < 0) fail("invalid unicode escape")
                v = (v shl 4) or d
            }
            return v
        }

        private fun parseNumber(): PortableValue.Num {
            val start = pos
            if (text[pos] == '-') pos++
            if (pos >= text.length) fail("incomplete number")
            if (text[pos] == '0') {
                pos++
            } else if (text[pos] in '1'..'9') {
                while (pos < text.length && text[pos] in '0'..'9') pos++
            } else {
                fail("invalid number")
            }
            if (pos < text.length && text[pos] == '.') {
                pos++
                if (pos >= text.length || text[pos] !in '0'..'9') fail("invalid number fraction")
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                if (pos >= text.length || text[pos] !in '0'..'9') fail("invalid number exponent")
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            return PortableValue.Num(text.substring(start, pos))
        }
    }
}
