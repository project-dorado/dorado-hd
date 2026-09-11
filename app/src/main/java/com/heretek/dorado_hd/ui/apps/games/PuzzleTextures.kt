package com.heretek.dorado_hd.ui.apps.games

import com.heretek.dorado_hd.ui.apps.engine3d.TextureData
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Original procedural artwork for the 3D Picture Puzzle: seven categories
 * (six abstract themes plus number grids), each generated in code. No
 * Microsoft imagery is used or derived.
 */
object PuzzleTextures {

    const val SIZE = 256

    private val PALETTES = listOf(
        // animals
        intArrayOf(0xFF2E4A62.toInt(), 0xFFE8B96A.toInt(), 0xFFB85C38.toInt(), 0xFFF2E8D5.toInt()),
        // architecture
        intArrayOf(0xFF1B2430.toInt(), 0xFF7A8B99.toInt(), 0xFFD8C99B.toInt(), 0xFF425664.toInt()),
        // cartoons
        intArrayOf(0xFFF25C54.toInt(), 0xFFF7B267.toInt(), 0xFFF79D65.toInt(), 0xFFF4845F.toInt()),
        // plants
        intArrayOf(0xFF1E3A2B.toInt(), 0xFF4C9A63.toInt(), 0xFFA8D5A2.toInt(), 0xFFE3EFD8.toInt()),
        // scenery
        intArrayOf(0xFF20344B.toInt(), 0xFF3E6D8E.toInt(), 0xFFE9C46A.toInt(), 0xFF8AB17D.toInt()),
        // other
        intArrayOf(0xFF231F20.toInt(), 0xFF9A8C98.toInt(), 0xFFC9ADA7.toInt(), 0xFFF2E9E4.toInt()),
    )

    /** Generate the full picture for a category; [index] varies the seed. */
    fun picture(category: Int, index: Int, size: Int = SIZE): TextureData {
        val pixels = IntArray(size * size)
        val palette = PALETTES[category % PALETTES.size]
        val rng = Random(category * 1009 + index * 7919)
        val bg = palette[0]
        for (i in pixels.indices) pixels[i] = bg
        when (category % PALETTES.size) {
            0 -> drawAnimals(pixels, size, palette, rng)
            1 -> drawArchitecture(pixels, size, palette, rng)
            2 -> drawCartoons(pixels, size, palette, rng)
            3 -> drawPlants(pixels, size, palette, rng)
            4 -> drawScenery(pixels, size, palette, rng)
            else -> drawPatterns(pixels, size, palette, rng)
        }
        return TextureData(size, size, pixels)
    }

    /** Crop a tile out of a full picture with a small inset gutter. */
    fun tile(source: TextureData, gridSize: Int, row: Int, col: Int): TextureData {
        val cell = source.width / gridSize
        val gutter = maxOf(1, cell / 32)
        val out = IntArray(cell * cell)
        for (y in 0 until cell) {
            for (x in 0 until cell) {
                val sx = (col * cell + x).coerceIn(0, source.width - 1)
                val sy = (row * cell + y).coerceIn(0, source.height - 1)
                val edge = x < gutter || y < gutter || x >= cell - gutter || y >= cell - gutter
                out[y * cell + x] = if (edge) 0xFF101010.toInt() else source.pixels[sy * source.width + sx]
            }
        }
        return TextureData(cell, cell, out)
    }

    /** Hint overlay: the tile's home number, red for front, green for back. */
    fun number(number: Int, front: Boolean, size: Int = 128): TextureData {
        val pixels = IntArray(size * size)
        val bg = if (front) 0xFF3A0C0C.toInt() else 0xFF0C2A10.toInt()
        val fg = if (front) 0xFFFF5B5B.toInt() else 0xFF6BE07A.toInt()
        for (i in pixels.indices) pixels[i] = bg
        drawNumber(pixels, size, number, fg, size / 2, size / 2, size / 3)
        return TextureData(size, size, pixels)
    }

    // ---- primitive drawing (pure IntArray operations) ----

    private fun put(pixels: IntArray, size: Int, x: Int, y: Int, color: Int) {
        if (x in 0 until size && y in 0 until size) pixels[y * size + x] = color
    }

    private fun rect(pixels: IntArray, size: Int, x0: Int, y0: Int, w: Int, h: Int, color: Int) {
        for (y in y0 until (y0 + h).coerceAtMost(size)) {
            for (x in x0 until (x0 + w).coerceAtMost(size)) {
                if (x >= 0 && y >= 0) pixels[y * size + x] = color
            }
        }
    }

    private fun circle(pixels: IntArray, size: Int, cx: Int, cy: Int, radius: Int, color: Int) {
        val r2 = radius * radius
        for (y in (cy - radius)..(cy + radius)) {
            for (x in (cx - radius)..(cx + radius)) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) put(pixels, size, x, y, color)
            }
        }
    }

    private fun line(pixels: IntArray, size: Int, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0), 1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            put(pixels, size, (x0 + (x1 - x0) * t).toInt(), (y0 + (y1 - y0) * t).toInt(), color)
        }
    }

    private fun drawAnimals(pixels: IntArray, size: Int, palette: IntArray, rng: Random) {
        for (n in 0 until 4) {
            val cx = rng.nextInt(size / 4, size * 3 / 4)
            val cy = rng.nextInt(size / 4, size * 3 / 4)
            val r = rng.nextInt(size / 10, size / 6)
            val color = palette[1 + n % 3]
            circle(pixels, size, cx, cy, r, color)
            circle(pixels, size, cx - r / 2, cy - r, r / 3, color)
            circle(pixels, size, cx + r / 2, cy - r, r / 3, color)
            circle(pixels, size, cx - r / 3, cy - r / 4, r / 6, 0xFF131313.toInt())
            circle(pixels, size, cx + r / 3, cy - r / 4, r / 6, 0xFF131313.toInt())
        }
    }

    private fun drawArchitecture(pixels: IntArray, size: Int, palette: IntArray, rng: Random) {
        var x = 0
        while (x < size) {
            val w = rng.nextInt(size / 10, size / 5)
            val h = rng.nextInt(size / 3, size * 3 / 4)
            val color = palette[1 + rng.nextInt(3)]
            rect(pixels, size, x, size - h, w, h, color)
            for (wy in size - h + 6 until size - 6 step 14) {
                rect(pixels, size, x + 5, wy, w - 10, 4, 0xFFF7F3E8.toInt())
            }
            x += w + 4
        }
    }

    private fun drawCartoons(pixels: IntArray, size: Int, palette: IntArray, rng: Random) {
        for (n in 0 until 6) {
            val cx = rng.nextInt(size)
            val cy = rng.nextInt(size)
            val r = rng.nextInt(size / 12, size / 6)
            val color = palette[1 + rng.nextInt(3)]
            val points = 5
            for (i in 0 until points * 2) {
                val angle = i * 3.14159265f / points - 1.5708f
                val radius = if (i % 2 == 0) r else r / 2
                val x = (cx + cos(angle) * radius).toInt()
                val y = (cy + sin(angle) * radius).toInt()
                circle(pixels, size, x, y, r / 6, color)
            }
        }
    }

    private fun drawPlants(pixels: IntArray, size: Int, palette: IntArray, rng: Random) {
        for (n in 0 until 5) {
            val x = rng.nextInt(size)
            val color = palette[1 + rng.nextInt(3)]
            line(pixels, size, x, size, x + rng.nextInt(-30, 30), size / 3, color)
            var y = size / 3
            var branch = x
            while (y < size - 10) {
                val nx = branch + rng.nextInt(-40, 40)
                line(pixels, size, branch, y, nx, y + size / 8, color)
                circle(pixels, size, nx, y + size / 8, size / 24, palette[3])
                branch = nx
                y += size / 8
            }
        }
    }

    private fun drawScenery(pixels: IntArray, size: Int, palette: IntArray, rng: Random) {
        circle(pixels, size, size / 4 + rng.nextInt(size / 2), size / 4, size / 8, palette[2])
        val horizon = size / 2
        for (hill in 0 until 3) {
            val base = horizon + hill * size / 7
            for (x in 0 until size) {
                val y = base - (sin(x / 24f + hill) * size / 14).toInt()
                rect(pixels, size, x, y, 1, size - y, palette[3 - hill.coerceAtMost(2)])
            }
        }
    }

    private fun drawPatterns(pixels: IntArray, size: Int, palette: IntArray, rng: Random) {
        val cell = size / 8
        for (y in 0 until size step cell) {
            for (x in 0 until size step cell) {
                val color = palette[(x / cell + y / cell) % palette.size]
                if (((x / cell) + (y / cell)) % 3 == 0) {
                    rect(pixels, size, x, y, cell, cell, color)
                } else {
                    circle(pixels, size, x + cell / 2, y + cell / 2, cell / 2 - 2, color)
                }
            }
        }
    }

    /** Tiny 5x7 vector digits, scaled; enough for puzzle numbers 1..36. */
    private fun drawNumber(pixels: IntArray, size: Int, number: Int, color: Int, cx: Int, cy: Int, height: Int) {
        val text = number.toString()
        val digitWidth = height * 4 / 7
        val spacing = digitWidth / 2
        val totalWidth = text.length * digitWidth + (text.length - 1) * spacing
        var x = cx - totalWidth / 2
        for (ch in text) {
            drawDigit(pixels, size, ch - '0', x, cy - height / 2, digitWidth, height, color)
            x += digitWidth + spacing
        }
    }

    private val DIGITS = arrayOf(
        "01110", "10001", "10001", "11111", "10001", "10001", "10001",
        "00100", "01100", "00100", "00100", "00100", "00100", "01110",
        "01110", "10001", "00001", "00110", "01000", "10000", "11111",
        "11110", "00001", "00001", "01110", "00001", "00001", "11110",
        "00010", "00110", "01010", "10010", "11111", "00010", "00010",
        "11111", "10000", "11110", "00001", "00001", "10001", "01110",
        "00110", "01000", "10000", "11110", "10001", "10001", "01110",
        "11111", "00001", "00010", "00100", "00100", "01000", "01000",
        "01110", "10001", "10001", "01110", "10001", "10001", "01110",
        "01110", "10001", "10001", "01111", "00001", "00010", "01100",
    )

    private fun drawDigit(
        pixels: IntArray,
        size: Int,
        digit: Int,
        x0: Int,
        y0: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        val pattern = DIGITS[digit.coerceIn(0, 9)]
        val cellW = width / 5f
        val cellH = height / 7f
        for (row in 0 until 7) {
            for (col in 0 until 5) {
                if (pattern[row * 5 + col] == '1') {
                    for (dy in 0 until cellH.toInt().coerceAtLeast(1)) {
                        for (dx in 0 until cellW.toInt().coerceAtLeast(1)) {
                            put(
                                pixels, size,
                                x0 + (col * cellW).toInt() + dx,
                                y0 + (row * cellH).toInt() + dy,
                                color,
                            )
                        }
                    }
                }
            }
        }
    }
}
