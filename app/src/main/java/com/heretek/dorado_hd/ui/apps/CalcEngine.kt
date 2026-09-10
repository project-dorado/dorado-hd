package com.heretek.dorado_hd.ui.apps

/**
 * Pure expression evaluator used by the Calculator mini-app. Supports
 * `+ - * /`, parentheses, percentages, and unary minus. Returns null on
 * any parse or math error so the UI can show `error` instead of crashing.
 */
object CalcEngine {

    sealed class Token {
        data class Num(val v: Double) : Token()
        data class Op(val c: Char) : Token()
        data object LParen : Token()
        data object RParen : Token()
    }

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
                c == '+' || c == '-' || c == '*' || c == '/' -> {
                    val prev = out.lastOrNull()
                    val isUnary = c == '-' && (prev == null || prev is Token.Op || prev is Token.LParen)
                    if (isUnary) {
                        i++
                        if (i >= s.length) return null
                        if (s[i] == '(') {
                            out += Token.Op('-'); out += Token.Num(0.0); continue
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
        val prec = mapOf('+' to 1, '-' to 1, '*' to 2, '/' to 2)
        val ops = ArrayDeque<Char>()
        val out = ArrayDeque<Double>()
        for (t in tokens) {
            when (t) {
                is Token.Num -> out.addLast(t.v)
                is Token.Op -> {
                    val pNow = prec[t.c] ?: 0
                    while (ops.isNotEmpty()) {
                        val top = ops.last()
                        if (top == '(') break
                        val pTop = prec[top] ?: 0
                        if (pTop >= pNow) {
                            if (!applyOp(out, top)) return null
                            ops.removeLast()
                        } else break
                    }
                    ops.addLast(t.c)
                }
                is Token.LParen -> ops.addLast('(')
                is Token.RParen -> {
                    while (ops.isNotEmpty() && ops.last() != '(') {
                        if (!applyOp(out, ops.last())) return null
                        ops.removeLast()
                    }
                    if (ops.isEmpty()) return null
                    ops.removeLast()
                }
            }
        }
        while (ops.isNotEmpty()) {
            val c = ops.removeLast()
            if (c == '(') return null
            if (!applyOp(out, c)) return null
        }
        return if (out.size == 1) out.last() else null
    }

    private fun applyOp(out: ArrayDeque<Double>, op: Char): Boolean {
        if (out.size < 2) return false
        val b = out.removeLast()
        val a = out.removeLast()
        val r = when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (b == 0.0) return false else a / b
            else -> return false
        }
        out.addLast(r)
        return true
    }
}
