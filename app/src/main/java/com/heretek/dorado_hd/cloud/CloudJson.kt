package com.heretek.dorado_hd.cloud

/**
 * Minimal, dependency-free JSON reader/writer. The app deliberately ships no
 * Retrofit/Moshi/kxs dependency (see the other `net/` clients), and `org.json`
 * is not available to plain-JVM unit tests, so the cloud client parses and
 * emits JSON through this small recursive-descent codec. It models values as
 * `Map<String, Any?>`, `List<Any?>`, `String`, `Double`, `Boolean`, and `null`.
 */
internal object CloudJson {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) error("Unexpected trailing content at ${parser.position}")
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?): Map<String, Any?> =
        value as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun asArray(value: Any?): List<Any?> =
        value as? List<Any?> ?: emptyList()

    fun string(container: Map<String, Any?>, key: String): String? =
        container[key] as? String

    fun stringOr(container: Map<String, Any?>, key: String, fallback: String): String =
        string(container, key) ?: fallback

    fun int(container: Map<String, Any?>, key: String, fallback: Int = 0): Int =
        when (val value = container[key]) {
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: fallback
            else -> fallback
        }

    fun long(container: Map<String, Any?>, key: String, fallback: Long = 0): Long =
        when (val value = container[key]) {
            is Double -> value.toLong()
            is String -> value.toLongOrNull() ?: fallback
            else -> fallback
        }

    fun bool(container: Map<String, Any?>, key: String, fallback: Boolean = false): Boolean =
        container[key] as? Boolean ?: fallback

    fun write(value: Any?): String {
        val builder = StringBuilder()
        writeValue(builder, value)
        return builder.toString()
    }

    private fun writeValue(builder: StringBuilder, value: Any?) {
        when (value) {
            null -> builder.append("null")
            is Boolean -> builder.append(if (value) "true" else "false")
            is Int, is Long -> builder.append(value.toString())
            is Double -> builder.append(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
            is String -> writeString(builder, value)
            is Map<*, *> -> {
                builder.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) builder.append(',')
                    first = false
                    writeString(builder, key.toString())
                    builder.append(':')
                    writeValue(builder, item)
                }
                builder.append('}')
            }
            is Iterable<*> -> {
                builder.append('[')
                var first = true
                for (item in value) {
                    if (!first) builder.append(',')
                    first = false
                    writeValue(builder, item)
                }
                builder.append(']')
            }
            else -> writeString(builder, value.toString())
        }
    }

    private fun writeString(builder: StringBuilder, value: String) {
        builder.append('"')
        for (character in value) {
            when (character) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (character < ' ') {
                    builder.append("\\u")
                    builder.append(character.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(character)
                }
            }
        }
        builder.append('"')
    }

    private class Parser(private val text: String) {
        var position = 0
            private set

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) error("Unexpected end of input")
            return when (text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (val next = read()) {
                    ',' -> continue
                    '}' -> return result
                    else -> error("Expected ',' or '}' at $position but found '$next'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                result.add(readValue())
                skipWhitespace()
                when (val next = read()) {
                    ',' -> continue
                    ']' -> return result
                    else -> error("Expected ',' or ']' at $position but found '$next'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                val character = read()
                when (character) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        when (val escaped = read()) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                val hex = text.substring(position, position + 4)
                                position += 4
                                builder.append(hex.toInt(16).toChar())
                            }
                            else -> error("Invalid escape '\\$escaped' at $position")
                        }
                    }
                    else -> builder.append(character)
                }
            }
        }

        private fun readBoolean(): Boolean = when {
            text.startsWith("true", position) -> { position += 4; true }
            text.startsWith("false", position) -> { position += 5; false }
            else -> error("Invalid literal at $position")
        }

        private fun readNull(): Any? {
            if (text.startsWith("null", position)) {
                position += 4
                return null
            }
            error("Invalid literal at $position")
        }

        private fun readNumber(): Double {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && (text[position].isDigit() || text[position] in "+-.eE")) position++
            return text.substring(start, position).toDoubleOrNull()
                ?: error("Invalid number at $start: '${text.substring(start, position)}'")
        }

        private fun peek(): Char? = if (atEnd()) null else text[position]

        private fun read(): Char {
            if (atEnd()) error("Unexpected end of input")
            return text[position++]
        }

        private fun expect(character: Char) {
            val actual = read()
            if (actual != character) error("Expected '$character' at ${position - 1} but found '$actual'")
        }
    }
}
