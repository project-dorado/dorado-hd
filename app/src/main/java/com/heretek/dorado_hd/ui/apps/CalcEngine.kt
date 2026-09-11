package com.heretek.dorado_hd.ui.apps

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure expression evaluator used by the Calculator mini-app. Supports
 * `+ - * / ^`, parentheses, percentages, unary minus, and the scientific
 * functions `sin cos tan ln log sqrt` (radians). Returns null on any parse
 * or math error so the UI can show `error` instead of crashing.
 */
object CalcEngine {

    sealed class Token {
        data class Num(val v: Double) : Token()
        data class Op(val c: Char) : Token()
        data class Fun(val name: String) : Token()
        data object LParen : Token()
        data object RParen : Token()
    }

    private val FUNCTIONS = setOf("sin", "cos", "tan", "ln", "log", "sqrt")
    private val PREC = mapOf('+' to 1, '-' to 1, '*' to 2, '/' to 2, '^' to 3)

    fun tokenize(src: String): List<Token>? {
        val s = src.replace(" ", "").replace("×", "*").replace("x", "*").replace("÷", "/").replace("−", "-")
        if (s.isEmpty()) return null
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    val n = s.substring(i, j).toDoubleOrNull() ?: return null
                    out += Token.Num(n)
                    i = j
                }
                c.isLetter() -> {
                    var j = i
                    while (j < s.length && s[j].isLetter()) j++
                    val name = s.substring(i, j).lowercase()
                    if (name !in FUNCTIONS) return null
                    out += Token.Fun(name)
                    i = j
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> {
                    val prev = out.lastOrNull()
                    val isUnary = c == '-' && (prev == null || prev is Token.Op || prev is Token.LParen || prev is Token.Fun)
                    if (isUnary) {
                        i++
                        if (i >= s.length) return null
                        if (s[i] == '(') {
                            // "-(...)" == "0 - (...)"
                            out += Token.Num(0.0); out += Token.Op('-'); continue
                        }
                        var j = i
                        while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                        val n = s.substring(i, j).toDoubleOrNull() ?: return null
                        out += Token.Num(-n); i = j
                    } else {
                        out += Token.Op(c); i++
                    }
                }
                c == '(' -> { out += Token.LParen; i++ }
                c == ')' -> { out += Token.RParen; i++ }
                c == '%' -> {
                    val last = out.removeLastOrNull() as? Token.Num ?: return null
                    out += Token.Num(last.v / 100.0); i++
                }
                else -> return null
            }
        }
        return out
    }

    fun eval(src: String): Double? {
        val tokens = tokenize(src) ?: return null
        val parser = Parser(tokens)
        val result = parser.parseExpr() ?: return null
        if (parser.pos != tokens.size) return null
        return result.takeIf { it.isFinite() }
    }

    private class Parser(val tokens: List<Token>) {
        var pos = 0
        private fun peek(): Token? = tokens.getOrNull(pos)

        fun parseExpr(): Double? {
            var left = parseTerm() ?: return null
            while (true) {
                val op = (peek() as? Token.Op)?.takeIf { it.c == '+' || it.c == '-' } ?: break
                pos++
                val right = parseTerm() ?: return null
                left = if (op.c == '+') left + right else left - right
            }
            return left
        }

        private fun parseTerm(): Double? {
            var left = parsePower() ?: return null
            while (true) {
                val op = (peek() as? Token.Op)?.takeIf { it.c == '*' || it.c == '/' } ?: break
                pos++
                val right = parsePower() ?: return null
                left = when (op.c) {
                    '*' -> left * right
                    else -> if (right == 0.0) return null else left / right
                }
            }
            return left
        }

        private fun parsePower(): Double? {
            val base = parseUnary() ?: return null
            val op = (peek() as? Token.Op)?.takeIf { it.c == '^' } ?: return base
            pos++
            // Right-associative: 2^3^2 == 2^(3^2).
            val exp = parsePower() ?: return null
            return base.pow(exp)
        }

        private fun parseUnary(): Double? {
            val t = peek() ?: return null
            return when (t) {
                is Token.Op -> {
                    if (t.c != '-' && t.c != '+') return null
                    pos++
                    val v = parseUnary() ?: return null
                    if (t.c == '-') -v else v
                }
                is Token.Fun -> {
                    pos++
                    val v = parseUnary() ?: return null
                    applyFun(t.name, v)
                }
                is Token.Num -> { pos++; t.v }
                is Token.LParen -> {
                    pos++
                    val v = parseExpr() ?: return null
                    if (peek() !is Token.RParen) return null
                    pos++
                    v
                }
                else -> null
            }
        }

        private fun applyFun(name: String, v: Double): Double? = when (name) {
            "sin" -> sin(v)
            "cos" -> cos(v)
            "tan" -> tan(v)
            "ln" -> if (v > 0) ln(v) else return null
            "log" -> if (v > 0) log10(v) else return null
            "sqrt" -> if (v >= 0) sqrt(v) else return null
            else -> return null
        }.takeIf { abs(it) < 1e15 && it.isFinite() }
    }
}

/* ================= Stacked calculator state machine ================= */

/** Angle unit for trig functions (calculator's three radio buttons). */
enum class AngleMode { DEG, RAD, GRAD }

/** One saved parenthesis frame: the operand and operator that opened it. */
data class CalcFrame(val left: Double?, val op: String?)

/**
 * Immutable calculator state. [press] is the only mutator and returns a new
 * state, so the machine is deterministic and unit-testable without Android.
 * Mirrors the device's `Calculator!Calculator`: one pending expression plus a
 * stack for parentheses, repeat-equals, memory, angle mode and a stats
 * population that scientific pages consume.
 */
data class CalculatorState(
    val display: String = "0",
    val value: Double = 0.0,
    val pendingOp: String? = null,
    val left: Double? = null,
    val stack: List<CalcFrame> = emptyList(),
    val memory: Double = 0.0,
    val angle: AngleMode = AngleMode.DEG,
    val population: List<Double> = emptyList(),
    val repeatOp: String? = null,
    val repeatValue: Double = 0.0,
    val error: Boolean = false,
    val hasInput: Boolean = false,
    val landscape: Boolean = false,
    val page: Int = 0,
    val rngSeed: Long = 0x2545F4914F6CDD1DL,
) {
    /** Portrait caps the field at 15 significant digits, landscape at 19. */
    private val digitCap: Int get() = if (landscape) 19 else 15

    fun press(key: String, landscapeMode: Boolean = landscape): CalculatorState {
        // Err locks the machine until a clear key arrives (device behavior).
        if (error && key != "AC" && key != "C") return this
        val s = copy(landscape = landscapeMode)
        return when (key) {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> s.digit(key[0])
            "." -> s.dot()
            "+", "-", "*", "/", "^", "yroot", "logy" -> s.binary(key)
            "=" -> s.equals()
            "C" -> if (s.hasInput) s.clearEntry() else s.clearAll()
            "AC" -> s.clearAll()
            "±" -> s.negate()
            "del" -> s.backspace()
            "(" -> s.leftParen()
            ")" -> s.rightParen()
            "%" -> s.percent()
            "MC" -> s.copy(memory = 0.0)
            "M+" -> s.copy(memory = s.memory + s.value)
            "MR" -> s.insertValue(s.memory)
            "deg" -> s.copy(angle = AngleMode.DEG)
            "rad" -> s.copy(angle = AngleMode.RAD)
            "grad" -> s.copy(angle = AngleMode.GRAD)
            "pi" -> s.insertValue(PI)
            "2pi" -> s.insertValue(2.0 * PI)
            "halfpi" -> s.insertValue(PI / 2.0)
            "e" -> s.insertValue(E)
            "rand" -> {
                val next = s.rngSeed * 6364136223846793005L + 1442695040888963407L
                s.insertValue(((next ushr 11).toDouble() / (1L shl 53).toDouble())).copy(rngSeed = next)
            }
            "sin", "cos", "tan", "asin", "acos", "atan",
            "ln", "log", "sqrt", "cbrt", "sq", "cube", "inv", "fact",
            "exp", "tenx", "twox",
            -> s.unary(key)
            "pop" -> s.copy(population = s.population + s.value, hasInput = false)
            "clearPop" -> s.copy(population = emptyList())
            "sum", "mean", "count", "stdev" -> s.stat(key)
            "sci" -> s.copy(landscape = !s.landscape)
            "fn" -> s.copy(page = (s.page + 1) % SCI_PAGE_COUNT)
            else -> s
        }
    }

    /** What the display renders: grouped entry text or the computed result. */
    fun render(): String = when {
        error -> "Err"
        hasInput -> groupThousands(display)
        else -> display
    }

    private fun digit(d: Char): CalculatorState {
        val cap = digitCap
        if (!hasInput) {
            return copy(display = d.toString(), value = d.digitToInt().toDouble(), hasInput = true)
        }
        if (display.count { it.isDigit() } >= cap) return this
        val next = when (display) {
            "0" -> d.toString()
            "-0" -> "-$d"
            else -> display + d
        }
        return copy(display = next, value = next.toDoubleOrNull() ?: 0.0)
    }

    private fun dot(): CalculatorState = when {
        !hasInput -> copy(display = "0.", value = 0.0, hasInput = true)
        display.contains('.') -> this
        display == "-0" -> copy(display = "-0.", value = -0.0)
        else -> copy(display = display + ".")
    }

    private fun negate(): CalculatorState {
        if (!hasInput) {
            val v = -value
            return copy(value = v, display = formatValue(v, landscape))
        }
        val next = if (display.startsWith("-")) display.substring(1) else "-$display"
        return copy(display = next, value = next.toDoubleOrNull() ?: 0.0)
    }

    private fun backspace(): CalculatorState {
        if (!hasInput) return copy(display = "0", value = 0.0)
        val next = display.dropLast(1)
        return if (next.isEmpty() || next == "-") {
            copy(display = "0", value = 0.0, hasInput = false)
        } else {
            copy(display = next, value = next.toDoubleOrNull() ?: 0.0)
        }
    }

    private fun clearEntry(): CalculatorState = copy(display = "0", value = 0.0, hasInput = false)

    private fun clearAll(): CalculatorState = CalculatorState(
        memory = memory,
        angle = angle,
        population = population,
        landscape = landscape,
        page = page,
        rngSeed = rngSeed,
    )

    private fun binary(op: String): CalculatorState {
        if (pendingOp != null && hasInput) {
            val r = applyOp(pendingOp, left ?: 0.0, value) ?: return error()
            return copy(
                value = r, display = formatValue(r, landscape), left = r,
                pendingOp = op, hasInput = false, repeatOp = null,
            )
        }
        if (pendingOp != null) return copy(pendingOp = op)
        return copy(pendingOp = op, left = value, hasInput = false, repeatOp = null)
    }

    private fun equals(): CalculatorState {
        if (pendingOp != null) {
            val op = pendingOp
            val r = applyOp(op, left ?: 0.0, value) ?: return error()
            return copy(
                value = r, display = formatValue(r, landscape),
                left = null, pendingOp = null, hasInput = false,
                repeatOp = op, repeatValue = value,
            )
        }
        if (repeatOp != null) {
            val r = applyOp(repeatOp, value, repeatValue) ?: return error()
            return copy(value = r, display = formatValue(r, landscape), hasInput = false)
        }
        return copy(hasInput = false, display = formatValue(value, landscape))
    }

    private fun leftParen(): CalculatorState =
        copy(
            stack = stack + CalcFrame(left, pendingOp),
            left = null, pendingOp = null, display = "0", value = 0.0, hasInput = false,
        )

    private fun rightParen(): CalculatorState {
        var s = this
        if (s.pendingOp != null) {
            val r = applyOp(s.pendingOp, s.left ?: 0.0, s.value) ?: return error()
            s = s.copy(value = r, display = formatValue(r, s.landscape), left = null, pendingOp = null)
        }
        val frame = s.stack.lastOrNull() ?: return s.copy(hasInput = false)
        return s.copy(
            stack = s.stack.dropLast(1),
            left = frame.left, pendingOp = frame.op,
            display = formatValue(s.value, s.landscape), hasInput = false,
        )
    }

    private fun percent(): CalculatorState {
        val base = left
        val v = if (pendingOp != null && base != null) base * value / 100.0 else value / 100.0
        if (!v.isFinite()) return error()
        return copy(value = v, display = formatValue(v, landscape), hasInput = false)
    }

    private fun insertValue(v: Double): CalculatorState =
        if (v.isFinite()) copy(value = v, display = formatValue(v, landscape), hasInput = false) else error()

    private fun unary(fn: String): CalculatorState {
        val x = value
        val r: Double? = when (fn) {
            "sin" -> round15(sin(angleToRad(x)))
            "cos" -> round15(cos(angleToRad(x)))
            "tan" -> round15(tan(angleToRad(x)))
            "asin" -> if (x in -1.0..1.0) radToAngle(asin(x)) else null
            "acos" -> if (x in -1.0..1.0) radToAngle(acos(x)) else null
            "atan" -> radToAngle(atan(x))
            "ln" -> if (x > 0) ln(x) else null
            "log" -> if (x > 0) log10(x) else null
            "sqrt" -> if (x >= 0) sqrt(x) else null
            "cbrt" -> cbrt(x)
            "sq" -> x * x
            "cube" -> x * x * x
            "inv" -> if (x != 0.0) 1.0 / x else null
            "fact" -> factorial(x)
            "exp" -> exp(x)
            "tenx" -> 10.0.pow(x)
            "twox" -> 2.0.pow(x)
            else -> null
        }
        return if (r == null || !r.isFinite()) error() else insertValue(r)
    }

    private fun stat(fn: String): CalculatorState {
        val n = population.size
        val mean = if (n == 0) 0.0 else population.sum() / n
        val r = when (fn) {
            "sum" -> population.sum()
            "count" -> n.toDouble()
            "mean" -> if (n == 0) null else mean
            "stdev" -> if (n == 0) null else sqrt(population.sumOf { (it - mean) * (it - mean) } / n)
            else -> null
        } ?: return error()
        return insertValue(r)
    }

    private fun error(): CalculatorState = CalculatorState(
        memory = memory,
        angle = angle,
        population = population,
        landscape = landscape,
        page = page,
        rngSeed = rngSeed,
        error = true,
        display = "Err",
    )

    private fun angleToRad(x: Double): Double = when (angle) {
        AngleMode.DEG -> x * PI / 180.0
        AngleMode.GRAD -> x * PI / 200.0
        AngleMode.RAD -> x
    }

    private fun radToAngle(x: Double): Double = when (angle) {
        AngleMode.DEG -> x * 180.0 / PI
        AngleMode.GRAD -> x * 200.0 / PI
        AngleMode.RAD -> x
    }

    companion object {
        const val SCI_PAGE_COUNT = 4
        private val ERROR_CLEAR_KEYS = setOf("AC", "C")

        private fun round15(x: Double): Double = Math.round(x * 1e15) / 1e15

        /** Add/sub/mul/div/power/yroot/logy with device error detection. */
        fun applyOp(op: String, a: Double, b: Double): Double? {
            val r = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) return null else a / b
                "^" -> a.pow(b)
                "yroot" -> yRoot(a, b) ?: return null
                "logy" -> if (a > 0 && b > 0 && b != 1.0) ln(a) / ln(b) else return null
                else -> return null
            }
            return r.takeIf { it.isFinite() }
        }

        /** Sign-preserving y-th root; even roots of negatives are illegal. */
        fun yRoot(x: Double, y: Double): Double? {
            if (y == 0.0) return null
            val odd = y == kotlin.math.floor(y) && (abs(y.toLong()) % 2L == 1L)
            return when {
                x < 0.0 && !odd -> null
                x < 0.0 -> -(-x).pow(1.0 / y)
                else -> x.pow(1.0 / y)
            }
        }

        /** Input clamped at 180 per `Calculator!Functions.Factorial`. */
        fun factorial(x: Double): Double? {
            if (x < 0.0 || x != kotlin.math.floor(x)) return null
            val n = x.toInt().coerceAtMost(180)
            var r = 1.0
            for (i in 2..n) r *= i
            return r.takeIf { it.isFinite() }
        }

        /**
         * Device number rendering: `N0` thousands grouping, switching to
         * `0.###E+0` (portrait) / `0.#####E+0` (landscape) past the range.
         */
        fun formatValue(v: Double, landscape: Boolean = false): String {
            if (!v.isFinite()) return "Err"
            val negative = v < 0.0 || (v == 0.0 && 1.0 / v < 0.0)
            val a = abs(v)
            val threshold = if (landscape) 1e15 else 1e12
            if (a >= threshold) {
                val decimals = if (landscape) 5 else 3
                val mantissa = "%.${decimals}E".format(a)
                return (if (negative) "-" else "") + trimMantissa(mantissa)
            }
            val plain = if (a == kotlin.math.floor(a) && a < 9.0e18) {
                a.toLong().toString()
            } else {
                trimTrailingZeros("%.${if (landscape) 10 else 6}f".format(a))
            }
            val sign = if (negative && plain != "0") "-" else ""
            return sign + groupThousands(plain)
        }

        private fun trimTrailingZeros(s: String): String {
            if (!s.contains('.')) return s
            val t = s.trimEnd('0').trimEnd('.')
            return if (t.isEmpty()) "0" else t
        }

        private fun trimMantissa(s: String): String {
            val e = s.indexOf('E')
            if (e < 0) return s
            val head = trimTrailingZeros(s.substring(0, e))
            return head + s.substring(e)
        }

        fun groupThousands(s: String): String {
            val dot = s.indexOf('.')
            val intPart = if (dot < 0) s else s.substring(0, dot)
            val frac = if (dot < 0) "" else s.substring(dot)
            val neg = intPart.startsWith("-")
            val digits = if (neg) intPart.substring(1) else intPart
            val grouped = StringBuilder()
            for ((i, c) in digits.withIndex()) {
                if (i > 0 && (digits.length - i) % 3 == 0) grouped.append(',')
                grouped.append(c)
            }
            return (if (neg) "-" else "") + grouped + frac
        }
    }
}
