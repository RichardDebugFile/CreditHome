package com.alfas.amortizacioncredito.domain.security

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Generador nativo puro en Kotlin de Códigos QR (ISO/IEC 18004) para estampado de firmas electrónicas.
 * No requiere dependencias externas pesadas.
 */
object QrCodeGenerator {

    private val EXP_TABLE = IntArray(512)
    private val LOG_TABLE = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            EXP_TABLE[i + 255] = x
            LOG_TABLE[x] = i
            x = (x shl 1)
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return EXP_TABLE[LOG_TABLE[x] + LOG_TABLE[y]]
    }

    private fun rsComputeEcc(data: ByteArray, eccCount: Int): ByteArray {
        var gen = intArrayOf(1)
        for (i in 0 until eccCount) {
            val nextGen = IntArray(gen.size + 1)
            val factor = EXP_TABLE[i]
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gfMul(gen[j], factor)
                nextGen[j + 1] = nextGen[j + 1] xor gen[j]
            }
            gen = nextGen
        }

        val result = IntArray(eccCount)
        for (b in data) {
            val coef = (b.toInt() and 0xFF) xor result[0]
            for (j in 0 until eccCount - 1) {
                result[j] = result[j + 1] xor gfMul(gen[eccCount - 1 - j], coef)
            }
            result[eccCount - 1] = gfMul(gen[0], coef)
        }

        val eccBytes = ByteArray(eccCount)
        for (i in 0 until eccCount) {
            eccBytes[i] = result[i].toByte()
        }
        return eccBytes
    }

    // Tabla de capacidades (Versión, Total Data Codewords, ECC Codewords) para ECC Level M
    private val VERSION_SPECS_M = arrayOf(
        intArrayOf(1, 16, 10, 21),
        intArrayOf(2, 28, 16, 25),
        intArrayOf(3, 44, 26, 29),
        intArrayOf(4, 64, 18, 33),
        intArrayOf(5, 86, 24, 37),
        intArrayOf(6, 108, 16, 41),
        intArrayOf(7, 124, 18, 45),
        intArrayOf(8, 154, 22, 49),
        intArrayOf(9, 182, 22, 53),
        intArrayOf(10, 216, 26, 57)
    )

    private val ALIGNMENT_PATTERN_POS = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
        intArrayOf(6, 22, 38),
        intArrayOf(6, 24, 42),
        intArrayOf(6, 26, 46),
        intArrayOf(6, 28, 50)
    )

    /**
     * Genera un Bitmap con el código QR del texto especificado.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 300): Bitmap? {
        return try {
            val rawBytes = content.toByteArray(Charsets.UTF_8)
            var chosenVer = -1
            for (spec in VERSION_SPECS_M) {
                val maxDataBytes = spec[1] - 3 // Overhead de modo byte (4 bits) + longitud (8 o 16 bits)
                if (rawBytes.size <= maxDataBytes) {
                    chosenVer = spec[0]
                    break
                }
            }

            if (chosenVer == -1) chosenVer = 10
            val spec = VERSION_SPECS_M[chosenVer - 1]
            val totalDataCodewords = spec[1]
            val eccCodewordsPerBlock = spec[2]
            val matrixDim = spec[3]

            // 1. Bit Buffer
            val bitBuffer = mutableListOf<Int>()

            fun appendBits(value: Int, numBits: Int) {
                for (i in numBits - 1 downTo 0) {
                    bitBuffer.add((value shr i) and 1)
                }
            }

            // Mode indicator: 0100 (Byte mode)
            appendBits(4, 4)

            // Character count indicator (8 bits para ver 1-9, 16 bits para ver 10)
            val charCountBits = if (chosenVer < 10) 8 else 16
            appendBits(rawBytes.size, charCountBits)

            // Data bytes
            for (b in rawBytes) {
                appendBits(b.toInt() and 0xFF, 8)
            }

            // Terminator
            val termLen = minOf(4, (totalDataCodewords * 8) - bitBuffer.size)
            appendBits(0, termLen)

            // Pad to 8-bit boundary
            while (bitBuffer.size % 8 != 0) {
                bitBuffer.add(0)
            }

            // Pad bytes 0xEC, 0x11
            val padBytes = intArrayOf(0xEC, 0x11)
            var padIdx = 0
            while (bitBuffer.size < totalDataCodewords * 8) {
                appendBits(padBytes[padIdx], 8)
                padIdx = (padIdx + 1) % 2
            }

            val dataCodewords = ByteArray(totalDataCodewords)
            for (i in 0 until totalDataCodewords) {
                var byteVal = 0
                for (b in 0 until 8) {
                    byteVal = (byteVal shl 1) or bitBuffer[i * 8 + b]
                }
                dataCodewords[i] = byteVal.toByte()
            }

            // 2. Error Correction Codewords
            val eccCodewords = rsComputeEcc(dataCodewords, eccCodewordsPerBlock)

            // Final codeword stream
            val allCodewords = ByteArray(totalDataCodewords + eccCodewords.size)
            System.arraycopy(dataCodewords, 0, allCodewords, 0, totalDataCodewords)
            System.arraycopy(eccCodewords, 0, allCodewords, totalDataCodewords, eccCodewords.size)

            // 3. Matriz QR
            val modules = Array(matrixDim) { BooleanArray(matrixDim) }
            val isFunction = Array(matrixDim) { BooleanArray(matrixDim) }

            fun setFunctionModule(r: Int, c: Int, isDark: Boolean) {
                if (r in 0 until matrixDim && c in 0 until matrixDim) {
                    modules[r][c] = isDark
                    isFunction[r][c] = true
                }
            }

            // Finder Patterns (7x7) + Separator (8x8)
            fun placeFinder(top: Int, left: Int) {
                for (r in -1..7) {
                    for (c in -1..7) {
                        val row = top + r
                        val col = left + c
                        if (row in 0 until matrixDim && col in 0 until matrixDim) {
                            val isDark = (r in 0..6 && (c == 0 || c == 6)) ||
                                    (c in 0..6 && (r == 0 || r == 6)) ||
                                    (r in 2..4 && c in 2..4)
                            setFunctionModule(row, col, isDark)
                        }
                    }
                }
            }

            placeFinder(0, 0)
            placeFinder(0, matrixDim - 7)
            placeFinder(matrixDim - 7, 0)

            // Timing Patterns
            for (i in 8 until matrixDim - 8) {
                val isDark = (i % 2 == 0)
                setFunctionModule(6, i, isDark)
                setFunctionModule(i, 6, isDark)
            }

            // Alignment Patterns
            val alignPos = ALIGNMENT_PATTERN_POS[chosenVer - 1]
            for (r in alignPos) {
                for (c in alignPos) {
                    if (isFunction[r][c]) continue
                    for (dr in -2..2) {
                        for (dc in -2..2) {
                            val isDark = (dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0))
                            setFunctionModule(r + dr, c + dc, isDark)
                        }
                    }
                }
            }

            // Dark Module
            setFunctionModule(4 * chosenVer + 9, 8, true)

            // Format Information (ECC M: 00, Mask 0: 000 -> Format Bits: 101010000010010)
            val formatBits = intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0)
            var fIdx = 0
            for (i in 0..8) {
                if (i != 6) {
                    setFunctionModule(8, i, formatBits[fIdx] == 1)
                    fIdx++
                }
            }
            for (i in 7 downTo 0) {
                if (i != 6) {
                    setFunctionModule(i, 8, formatBits[fIdx] == 1)
                    fIdx++
                }
            }

            fIdx = 0
            for (i in matrixDim - 1 downTo matrixDim - 7) {
                setFunctionModule(i, 8, formatBits[fIdx] == 1)
                fIdx++
            }
            for (i in matrixDim - 8 until matrixDim) {
                setFunctionModule(8, i, formatBits[fIdx] == 1)
                fIdx++
            }

            // 4. Place Data Bits con Mask 0 ((r + c) % 2 == 0)
            var bitIdx = 0
            val totalBits = allCodewords.size * 8
            var goingUp = true

            var col = matrixDim - 1
            while (col > 0) {
                if (col == 6) col--

                val rows = if (goingUp) (matrixDim - 1 downTo 0).toList() else (0 until matrixDim).toList()
                for (row in rows) {
                    for (cOffset in 0..1) {
                        val c = col - cOffset
                        if (!isFunction[row][c]) {
                            var bit = 0
                            if (bitIdx < totalBits) {
                                val bytePos = bitIdx / 8
                                val bitPos = 7 - (bitIdx % 8)
                                bit = ((allCodewords[bytePos].toInt() shr bitPos) and 1)
                                bitIdx++
                            }
                            val mask = if ((row + c) % 2 == 0) 1 else 0
                            val finalBit = bit xor mask
                            modules[row][c] = (finalBit == 1)
                        }
                    }
                }
                col -= 2
                goingUp = !goingUp
            }

            // 5. Render to Bitmap
            val quietZone = 2
            val totalDim = matrixDim + quietZone * 2
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            val paint = android.graphics.Paint().apply {
                color = Color.BLACK
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = false
            }

            val moduleSize = sizePx.toFloat() / totalDim.toFloat()
            for (r in 0 until matrixDim) {
                for (c in 0 until matrixDim) {
                    if (modules[r][c]) {
                        val left = (c + quietZone) * moduleSize
                        val top = (r + quietZone) * moduleSize
                        canvas.drawRect(left, top, left + moduleSize, top + moduleSize, paint)
                    }
                }
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
