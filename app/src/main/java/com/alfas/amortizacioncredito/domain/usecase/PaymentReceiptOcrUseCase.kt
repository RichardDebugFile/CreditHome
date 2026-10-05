package com.alfas.amortizacioncredito.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class OcrReceiptResult(
    val amount: Double? = null,
    val referenceNumber: String? = null,
    val bankOrEntity: String? = null,
    val paymentMethod: String? = null,
    val date: String? = null,
    val payerName: String? = null,
    val savedImagePath: String = "",
    val rawText: String = ""
)

class PaymentReceiptOcrUseCase {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processReceiptImage(context: Context, imageUri: Uri, month: Int): OcrReceiptResult = withContext(Dispatchers.IO) {
        // 1. Cargar imagen corrigiendo orientación EXIF y con límites de memoria seguros
        val uprightBitmap = loadAndCorrectBitmap(context, imageUri)
        val savedPath = if (uprightBitmap != null) {
            saveBitmapToStorage(context, uprightBitmap, month)
        } else {
            ""
        }

        // 2. Ejecutar OCR con ML Kit usando la imagen correctamente orientada
        val inputImage = if (uprightBitmap != null) {
            InputImage.fromBitmap(uprightBitmap, 0)
        } else {
            try {
                InputImage.fromFilePath(context, imageUri)
            } catch (e: Exception) {
                Log.e("PaymentReceiptOcr", "Error loading input image for OCR", e)
                return@withContext OcrReceiptResult(savedImagePath = savedPath)
            }
        }

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    Log.d("PaymentReceiptOcr", "OCR Success! Extracted text length: ${text.text.length}")
                    try {
                        val structured = reconstructStructuredText(text)
                        cont.resume(structured)
                    } catch (e: Exception) {
                        Log.w("PaymentReceiptOcr", "Error reconstructing structured text", e)
                        cont.resume(text.text)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("PaymentReceiptOcr", "OCR Failed", e)
                    cont.resume("")
                }
        }

        Log.d("PaymentReceiptOcr", "OCR Raw Text:\n$visionText")

        // 3. Analizar y parsear el texto extraído
        parseOcrText(visionText, savedPath)
    }

    private fun parseOcrText(rawText: String, savedImagePath: String): OcrReceiptResult {
        if (rawText.isBlank()) {
            return OcrReceiptResult(savedImagePath = savedImagePath)
        }

        return try {
            // Normalización avanzada de caracteres OCR
            val normalizedText = normalizeOcrText(rawText)
            val lines = normalizedText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val fullTextLower = normalizedText.lowercase(Locale.ROOT)

            // A. Detección de Banco / Entidad
            val bank = detectBank(fullTextLower)

            // B. Detección de Método de Pago
            val method = detectPaymentMethod(fullTextLower)

            // C. Detección de Monto ($)
            val amount = extractAmount(normalizedText, lines)

            // D. Detección de N° de Referencia / Comprobante / Secuencial
            val ref = extractReference(normalizedText, lines)

            // E. Detección de Fecha y Hora
            val date = extractDate(normalizedText, lines)

            // F. Detección de Titular / Depositante / Beneficiario
            val payer = extractPayerName(lines)

            OcrReceiptResult(
                amount = amount,
                referenceNumber = ref,
                bankOrEntity = bank,
                paymentMethod = method,
                date = date,
                payerName = payer,
                savedImagePath = savedImagePath,
                rawText = rawText
            )
        } catch (e: Exception) {
            e.printStackTrace()
            OcrReceiptResult(
                savedImagePath = savedImagePath,
                rawText = rawText
            )
        }
    }

    private fun normalizeOcrText(text: String): String {
        return try {
            text
                .replace("\u00A0", " ") // Espacio no divisible
                .replace("\u200B", "")  // Zero-width space
                .replace("‘", "'").replace("’", "'").replace("`", "'")
                // Arreglar espacios en decimales generados por OCR (ej: "700 , 00" -> "700,00", "700 . 00" -> "700.00")
                .replace(Regex("(\\d+)\\s*([,.])\\s*(\\d{2})\\b")) { match ->
                    java.util.regex.Matcher.quoteReplacement("${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}")
                }
                // Arreglar cuando el símbolo $ es reconocido como letra S o s antes de un monto (ej: "S 700,00" -> "$ 700,00")
                .replace(Regex("(?i)\\b[sS]\\s*(\\d+[.,]\\d{2})\\b")) { match ->
                    java.util.regex.Matcher.quoteReplacement("$ ${match.groupValues[1]}")
                }
                .replace(Regex("(?i)\\b[sS]/\\.?\\s*(\\d+[.,]\\d{2})\\b")) { match ->
                    java.util.regex.Matcher.quoteReplacement("$ ${match.groupValues[1]}")
                }
        } catch (_: Exception) {
            text
        }
    }

    private fun detectBank(textLower: String): String? {
        return when {
            textLower.contains("pichincha") -> "Banco Pichincha"
            textLower.contains("guayaquil") -> "Banco Guayaquil"
            textLower.contains("produbanco") -> "Produbanco"
            textLower.contains("pacífico") || textLower.contains("pacifico") -> "Banco del Pacífico"
            textLower.contains("internacional") -> "Banco Internacional"
            textLower.contains("bolivariano") -> "Banco Bolivariano"
            textLower.contains("austro") -> "Banco del Austro"
            textLower.contains("machala") -> "Banco de Machala"
            textLower.contains("solidario") -> "Banco Solidario"
            textLower.contains("deuna") || textLower.contains("de una") -> "Deuna"
            textLower.contains("jep") || textLower.contains("juventud ecuatoriana") -> "Cooperativa JEP"
            textLower.contains("alianza del valle") -> "Coop. Alianza del Valle"
            textLower.contains("policía nacional") || textLower.contains("policia nacional") -> "Coop. Policía Nacional"
            textLower.contains("29 de octubre") || textLower.contains("veintinueve de octubre") -> "Coop. 29 de Octubre"
            textLower.contains("chibuleo") -> "Coop. Chibuleo"
            textLower.contains("osculda") || textLower.contains("tulcan") || textLower.contains("tulcán") -> "Coop. Tulcán"
            textLower.contains("diners") -> "Diners Club"
            textLower.contains("servipagos") || textLower.contains("western union") -> "Servipagos"
            textLower.contains("red facilito") || textLower.contains("facilito") -> "Red Facilito"
            textLower.contains("pago agil") || textLower.contains("pagoágil") -> "PagoÁgil"
            else -> null
        }
    }

    private fun detectPaymentMethod(textLower: String): String {
        return when {
            textLower.contains("depósito") || textLower.contains("deposito") ||
                    textLower.contains("ventanilla") || textLower.contains("corresponsal") ||
                    textLower.contains("pichincha mi vecino") || textLower.contains("banco del barrio") -> "Depósito Bancario"
            textLower.contains("efectivo") || textLower.contains("caja") -> "Efectivo"
            textLower.contains("tarjeta") || textLower.contains("débito") || textLower.contains("debito") ||
                    textLower.contains("crédito") || textLower.contains("credito") ||
                    textLower.contains("visa") || textLower.contains("mastercard") -> "Tarjeta"
            else -> "Transferencia"
        }
    }

    private fun extractAmount(rawText: String, lines: List<String>): Double? {
        val candidatesWithScore = mutableListOf<Pair<Double, Int>>() // (Amount, ConfidenceScore)

        fun parseNumber(str: String): Double? {
            val cleaned = str.trim().replace(" ", "").replace("$", "").replace("USD", "", ignoreCase = true)
            if (cleaned.isBlank()) return null
            return try {
                if (cleaned.contains(",") && cleaned.contains(".")) {
                    if (cleaned.indexOf(",") < cleaned.indexOf(".")) {
                        // Formato: 1,250.50
                        cleaned.replace(",", "").toDoubleOrNull()
                    } else {
                        // Formato: 1.250,50
                        cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
                    }
                } else if (cleaned.contains(",")) {
                    // Formato: 700,00
                    cleaned.replace(",", ".").toDoubleOrNull()
                } else {
                    // Formato: 700.00
                    cleaned.toDoubleOrNull()
                }
            } catch (_: Exception) {
                null
            }
        }

        fun isValidAmount(amount: Double): Boolean {
            // Rango admisible para pagos de cuotas (evita IDs de 9-10 dígitos y años como 2026)
            if (amount < 0.5 || amount > 1000000.0) return false
            // Descartar si coincide exactamente con años recientes
            val currentYear = Calendar.getInstance().get(Calendar.YEAR).toDouble()
            if (amount == currentYear || amount == currentYear - 1 || amount == currentYear + 1) return false
            return true
        }

        // 1. Heurística ULTRA PRIORITARIA: Monto destacado cerca del encabezado de transacción (Banco Pichincha, Guayaquil, Deuna, etc.)
        val headerKeywords = listOf(
            "transferencia exitosa", "transferencia realizada", "transferencia enviada", "transferencia recibida",
            "pago exitoso", "pago realizado", "pago recibido", "transaccion exitosa", "transacción exitosa",
            "envio exitoso", "envío exitoso", "comprobante de transferencia", "comprobante de pago", "comprobante electrónico",
            "comprobante de depósito", "depósito exitoso", "deposito exitoso", "transacción aprobada", "transaccion aprobada",
            "operación exitosa", "operacion exitosa", "pagaste", "enviaste", "realizó con éxito", "realizo con exito"
        )

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase(Locale.ROOT)
            if (headerKeywords.any { lineLower.contains(it) }) {
                // Revisar 4 líneas hacia arriba y 4 líneas hacia abajo del encabezado
                for (offset in -4..4) {
                    val idx = i + offset
                    if (idx in lines.indices) {
                        val targetLine = lines[idx]
                        val targetLower = targetLine.lowercase(Locale.ROOT)
                        // Descartar saldos de cuenta o comisiones bancarias
                        if (targetLower.contains("saldo") || targetLower.contains("disponible") ||
                            targetLower.contains("comisión") || targetLower.contains("comision") ||
                            targetLower.contains("tarifa")
                        ) {
                            continue
                        }

                        val headerAmountRegex = Pattern.compile("(?:USD|\\$|US\\$|S/?)?\\s*([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2}|[0-9]{1,6}\\b)")
                        val hMatcher = headerAmountRegex.matcher(targetLine)
                        while (hMatcher.find()) {
                            hMatcher.group(1)?.let { numStr ->
                                parseNumber(numStr)?.let { amount ->
                                    if (isValidAmount(amount)) {
                                        candidatesWithScore.add(Pair(amount, 160)) // Puntuación máxima
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Heurística de Palabras Clave con palabras intermedias opcionales (ej: "Monto transferido: $594.00", "Valor de la cuota: 594.00")
        // Nota: Se excluye 'saldo' para evitar confundir el saldo disponible de la cuenta con el monto transferido
        val keywordRegex = Pattern.compile(
            "(?i)(?:monto|valor|total|importe|cantidad|debito|débito|pagado|pagaste|transferido|recibido|abono|subtotal|acreditado|cancelado|efectivo|cobrado)(?:\\s+[a-záéíóúñ]+){0,4}\\s*[:$]?\\s*(?:USD|\\$|US\\$|S/?)?\\s*([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2}|[0-9]{1,6}\\b)"
        )
        val matcher = keywordRegex.matcher(rawText)
        while (matcher.find()) {
            matcher.group(1)?.let { numStr ->
                parseNumber(numStr)?.let { amount ->
                    if (isValidAmount(amount)) {
                        candidatesWithScore.add(Pair(amount, 140))
                    }
                }
            }
        }

        // 3. Heurística de Símbolo de Moneda ($ / USD / US$)
        val currencyRegex = Pattern.compile(
            "(?i)(?:USD|\\$|US\\$|S/\\.?|Dólares|Dolares)\\s*([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2}|[0-9]{1,6}\\b)|([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2})\\s*(?:USD|\\$|US\\$|S/\\.?)"
        )
        val cMatcher = currencyRegex.matcher(rawText)
        while (cMatcher.find()) {
            val numStr = (cMatcher.group(1) ?: cMatcher.group(2))
            if (numStr != null) {
                parseNumber(numStr)?.let { amount ->
                    if (isValidAmount(amount)) {
                        candidatesWithScore.add(Pair(amount, 110))
                    }
                }
            }
        }

        // 4. Heurística Multi-Línea ($ en una línea, Número en la siguiente)
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line == "$" || line.equals("USD", ignoreCase = true) || line.equals("US$", ignoreCase = true)) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val nextNumRegex = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2}|[0-9]{1,6}\\b)")
                    val nMatcher = nextNumRegex.matcher(nextLine)
                    if (nMatcher.find()) {
                        nMatcher.group(1)?.let { numStr ->
                            parseNumber(numStr)?.let { amount ->
                                if (isValidAmount(amount)) {
                                    candidatesWithScore.add(Pair(amount, 105))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Heurística de líneas adyacentes: Etiqueta en línea N y número en línea N+1 o N+2
        for (i in lines.indices) {
            val lineLower = lines[i].lowercase(Locale.ROOT)
            val isAmountLabel = lineLower.contains("monto") || lineLower.contains("valor") ||
                    lineLower.contains("total") || lineLower.contains("importe") ||
                    lineLower.contains("débito") || lineLower.contains("debito") ||
                    lineLower.contains("transferido") || lineLower.contains("pagado") ||
                    lineLower.contains("pagaste") || lineLower.contains("abono")

            if (isAmountLabel) {
                for (offset in 1..2) {
                    if (i + offset < lines.size) {
                        val nextLine = lines[i + offset].trim()
                        val nextNumRegex = Pattern.compile("(?:USD|\\$|US\\$)?\\s*([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2}|[0-9]+[.,][0-9]{2}|[0-9]{1,6}\\b)")
                        val nMatcher = nextNumRegex.matcher(nextLine)
                        if (nMatcher.find()) {
                            nMatcher.group(1)?.let { numStr ->
                                parseNumber(numStr)?.let { amount ->
                                    if (isValidAmount(amount)) {
                                        candidatesWithScore.add(Pair(amount, 100))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Formato general de decimales (ej: 700.00 o 700,00)
        val generalRegex = Pattern.compile("\\b([0-9]{1,4}[.,][0-9]{2})\\b")
        val gMatcher = generalRegex.matcher(rawText)
        while (gMatcher.find()) {
            gMatcher.group(1)?.let { numStr ->
                parseNumber(numStr)?.let { amount ->
                    if (isValidAmount(amount)) {
                        candidatesWithScore.add(Pair(amount, 60))
                    }
                }
            }
        }

        if (candidatesWithScore.isEmpty()) {
            Log.d("PaymentReceiptOcr", "No amount candidates found.")
            return null
        }

        // Si existen candidatos de $1.00 o más, descartar comisiones pequeñas (< $1.00, como $0.40 o $0.22)
        val filteredCandidates = if (candidatesWithScore.any { it.first >= 1.0 }) {
            candidatesWithScore.filter { it.first >= 1.0 }
        } else {
            candidatesWithScore
        }

        Log.d("PaymentReceiptOcr", "Amount candidates: $filteredCandidates")
        val bestPick = filteredCandidates.maxByOrNull { it.second }?.first
        Log.d("PaymentReceiptOcr", "Selected amount: $bestPick")
        return bestPick
    }

    private fun reconstructStructuredText(visionText: com.google.mlkit.vision.text.Text): String {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return visionText.text

        // Agrupar líneas que comparten la misma franja vertical (misma fila horizontal en vouchers de 2 columnas)
        val sortedByY = allLines.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()

        for (line in sortedByY) {
            val lineBox = line.boundingBox
            val lineCenterY = if (lineBox != null) (lineBox.top + lineBox.bottom) / 2 else 0
            val lineHeight = if (lineBox != null) maxOf(lineBox.height(), 10) else 20

            val matchingRow = rows.find { row ->
                val rowBox = row.firstOrNull()?.boundingBox
                if (rowBox != null) {
                    val rowCenterY = (rowBox.top + rowBox.bottom) / 2
                    val rowHeight = maxOf(rowBox.height(), 10)
                    kotlin.math.abs(lineCenterY - rowCenterY) <= (minOf(lineHeight, rowHeight) * 0.75)
                } else false
            }

            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        // Ordenar cada fila de izquierda a derecha (left to right) y luego todas las filas de arriba a abajo
        val sortedRows = rows.sortedBy { row -> row.minOfOrNull { it.boundingBox?.top ?: 0 } ?: 0 }
        val reconstructed = sortedRows.joinToString("\n") { row ->
            row.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") { it.text.trim() }
        }

        return "$reconstructed\n\n${visionText.text}"
    }

    private fun extractReference(rawText: String, lines: List<String>): String? {
        // 1. Regex de palabras clave con número adyacente (ej: "N. de comprobante 52770285", "Comprobante: 3326")
        val refKeywords = Pattern.compile(
            "(?i)(?:n[oº\\.]*\\s*(?:de\\s*)?comprobante|n[oº\\.]*\\s*(?:de\\s*)?transacci[oó]n|n[oº\\.]*\\s*(?:de\\s*)?operaci[oó]n|n[oº\\.]*\\s*(?:de\\s*)?control|n[oº\\.]*\\s*(?:de\\s*)?documento|referencia|ref\\.?|secuencial|autorizaci[oó]n|c[oó]digo\\s*(?:de\\s*)?transacci[oó]n)\\s*[:#\\-]?\\s*([0-9A-Za-z\\-]{3,20})"
        )
        val matcher = refKeywords.matcher(rawText)
        while (matcher.find()) {
            val candidate = matcher.group(1)?.trim() ?: continue
            // Debe contener al menos un dígito y no ser una palabra del sistema/banco
            if (candidate.any { it.isDigit() } &&
                !candidate.matches("(?i)^(?:de|en|el|la|por|usd|banco|exitoso|aprobado|transferencia|electronico|electrónico|cuenta|saldo|ahorros|corriente|rotativo|qr|pichincha|guayaquil).*".toRegex())
            ) {
                val cleanRef = candidate.trim().trim(':', '#', '-', '.')
                if (cleanRef.length in 3..20) {
                    return cleanRef
                }
            }
        }

        // 2. Buscar en líneas específicas
        for (i in lines.indices) {
            val line = lines[i]
            val lineLower = line.lowercase(Locale.ROOT)
            val isRefLabel = lineLower.contains("comprobante") ||
                    lineLower.contains("referencia") ||
                    lineLower.contains("secuencial") ||
                    lineLower.contains("transacción") ||
                    lineLower.contains("transaccion") ||
                    lineLower.contains("autorización") ||
                    lineLower.contains("autorizacion") ||
                    ((lineLower.contains("operación") || lineLower.contains("operacion")) && !lineLower.contains("exitosa"))

            // Evitar líneas que sean avisos o enlaces de QR (ej: "Verifica la transacción en este QR")
            if (isRefLabel && !lineLower.contains("verifi") && !lineLower.contains("qr")) {
                val tokens = line.replace("[^0-9A-Za-z\\-]".toRegex(), " ")
                    .trim()
                    .split("\\s+".toRegex())
                    .filter { token ->
                        token.any { it.isDigit() } &&
                                token.length in 3..18 &&
                                !token.matches("(?i)^(?:de|en|el|la|por|usd|banco|exitoso|aprobado|transferencia|electronico|cuenta|saldo|pichincha|guayaquil).*".toRegex())
                    }
                val bestNumber = tokens.maxByOrNull { it.length }
                if (!bestNumber.isNullOrBlank()) {
                    return bestNumber
                }

                // Revisar las siguientes 2 líneas si la actual era sólo la etiqueta (ej. salto de línea entre etiqueta y valor)
                for (offset in 1..2) {
                    if (i + offset < lines.size) {
                        val nextLine = lines[i + offset].trim()
                        if (nextLine.matches("^[0-9A-Za-z\\-]{3,20}$".toRegex()) &&
                            nextLine.any { it.isDigit() } &&
                            !nextLine.matches("(?i)^(?:de|en|el|la|por|usd|banco|exitoso|cuenta|saldo).*".toRegex())
                        ) {
                            return nextLine
                        }
                    }
                }
            }
        }

        return null
    }

    private fun extractDate(rawText: String, lines: List<String>): String? {
        val foundCandidates = mutableListOf<String>()
        val timeRegex = Pattern.compile("(\\b[0-2]?[0-9]:[0-5][0-9](?::[0-5][0-9])?\\b)")
        val timeMatcher = timeRegex.matcher(rawText)
        var foundTime: String? = null
        if (timeMatcher.find()) {
            foundTime = timeMatcher.group(1)?.take(5)
        }

        // 1. Regex para formatos numéricos: 11/08/2026, 11-08-2026, 2026-08-11
        val numericDateRegex = Pattern.compile("(\\b[0-3]?[0-9][/-][0-1]?[0-9][/-]20[2-3][0-9]\\b)")
        val numMatcher = numericDateRegex.matcher(rawText)
        while (numMatcher.find()) {
            val foundDate = numMatcher.group(1)?.replace("-", "/")
            if (foundDate != null) {
                foundCandidates.add(foundDate)
            }
        }

        // 2. Regex para fechas en texto español: "El 11 de agosto de 2026", "11 de agosto del 2026", "11 de ago 2026"
        val spanishMonthMap = mapOf(
            "enero" to "01", "ene" to "01",
            "febrero" to "02", "feb" to "02",
            "marzo" to "03", "mar" to "03",
            "abril" to "04", "abr" to "04",
            "mayo" to "05", "may" to "05",
            "junio" to "06", "jun" to "06",
            "julio" to "07", "jul" to "07",
            "agosto" to "08", "ago" to "08",
            "septiembre" to "09", "setiembre" to "09", "sep" to "09", "set" to "09",
            "octubre" to "10", "oct" to "10",
            "noviembre" to "11", "nov" to "11",
            "diciembre" to "12", "dic" to "12"
        )

        val spanishMonthPattern = spanishMonthMap.keys.joinToString("|")
        val spanishDateRegex = Pattern.compile(
            "(?i)(?:el\\s+)?([0-3]?[0-9])\\s*(?:de|/|-)\\s*($spanishMonthPattern)\\.?(?:\\s*(?:de|del|/|-|,)?\\s*(20[2-3][0-9]))?"
        )

        val spMatcher = spanishDateRegex.matcher(rawText)
        while (spMatcher.find()) {
            val dayStr = spMatcher.group(1)?.padStart(2, '0') ?: "01"
            val monthWord = spMatcher.group(2)?.lowercase(Locale.ROOT)?.trimEnd('.') ?: ""
            val monthNum = spanishMonthMap[monthWord] ?: "01"
            val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
            val yearStr = spMatcher.group(3) ?: currentYear
            foundCandidates.add("$dayStr/$monthNum/$yearStr")
        }

        if (foundCandidates.isEmpty()) return null

        val bestDateOnly = selectMostRecentOrClosestDate(foundCandidates) ?: foundCandidates.first()

        return if (foundTime != null) {
            "$bestDateOnly $foundTime"
        } else {
            val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "$bestDateOnly $nowTime"
        }
    }

    companion object {
        fun selectMostRecentOrClosestDate(dates: List<String>): String? {
            if (dates.isEmpty()) return null
            if (dates.size == 1) return dates.first()

            val dateFormats = listOf(
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US),
                SimpleDateFormat("dd/MM/yyyy", Locale.US),
                SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US),
                SimpleDateFormat("dd-MM-yyyy", Locale.US),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )
            val now = System.currentTimeMillis()

            fun parseMillis(dStr: String): Long? {
                val clean = dStr.trim()
                for (sdf in dateFormats) {
                    try {
                        val d = sdf.parse(clean)
                        if (d != null) return d.time
                    } catch (_: Exception) {}
                }
                return null
            }

            val parsedList = dates.mapNotNull { dStr ->
                val millis = parseMillis(dStr)
                if (millis != null) Pair(dStr, millis) else null
            }

            if (parsedList.isEmpty()) return dates.lastOrNull()

            val nonFuture = parsedList.filter { it.second <= now + 172800000L }
            return if (nonFuture.isNotEmpty()) {
                nonFuture.maxByOrNull { it.second }?.first
            } else {
                parsedList.minByOrNull { Math.abs(it.second - now) }?.first
            }
        }
    }

    private fun extractPayerName(lines: List<String>): String? {
        val nameKeywords = listOf(
            "de:", "de ", "ordenante:", "ordenante", "transferido por:", "transferido por",
            "depositante:", "depositante", "cliente:", "cliente", "titular:", "titular",
            "recibido de:", "recibido de", "pagado por:", "beneficiario:", "a:"
        )

        for (i in lines.indices) {
            val lineLower = lines[i].lowercase(Locale.ROOT)
            for (kw in nameKeywords) {
                if (lineLower.startsWith(kw) || lineLower.contains("$kw:")) {
                    var namePart = if (lineLower.contains(":")) {
                        lines[i].substringAfter(":").trim()
                    } else {
                        lines[i].substring(kw.length).trim()
                    }

                    // Quitar prefijos comunes o palabras como "Banco", "Cuenta"
                    if (namePart.startsWith("Hidalgo", ignoreCase = true) ||
                        namePart.contains(" ") && !namePart.contains("$") && !namePart.matches("^[0-9*\\s]+$".toRegex())
                    ) {
                        if (namePart.length in 3..45) {
                            return cleanName(namePart)
                        }
                    }

                    if (i + 1 < lines.size) {
                        val next = lines[i + 1].trim()
                        if (next.length in 3..45 && !next.contains("$") && !next.matches("^[0-9*\\s]+$".toRegex())) {
                            return cleanName(next)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun cleanName(name: String): String {
        return name.replace("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]".toRegex(), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
    }

    private fun loadAndCorrectBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val rotationDegrees = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (e: Exception) {
                Log.w("PaymentReceiptOcr", "Could not read EXIF orientation: ${e.message}")
                0
            }

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var sampleSize = 1
            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxDim / sampleSize > 2048) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            Log.d("PaymentReceiptOcr", "Loaded image: ${finalBitmap.width}x${finalBitmap.height} (rotated $rotationDegrees deg)")
            finalBitmap
        } catch (e: Exception) {
            Log.e("PaymentReceiptOcr", "Error loading and correcting bitmap", e)
            null
        }
    }

    private fun saveBitmapToStorage(context: Context, bitmap: Bitmap, month: Int): String {
        return try {
            val receiptsDir = File(context.filesDir, "receipts")
            if (!receiptsDir.exists()) receiptsDir.mkdirs()

            val fileName = "voucher_m${month}_${System.currentTimeMillis()}.jpg"
            val destFile = File(receiptsDir, fileName)

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("PaymentReceiptOcr", "Error saving bitmap to internal storage", e)
            ""
        }
    }
}
