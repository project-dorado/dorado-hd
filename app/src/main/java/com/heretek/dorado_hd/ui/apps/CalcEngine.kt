package com.heretek.dorado_hd.ui.apps

import kotlin.math.abs
import kotlin.math.cos
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
