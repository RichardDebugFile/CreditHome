package com.alfas.amortizacioncredito.domain.bot

import com.alfas.amortizacioncredito.data.model.ClientProfile
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

data class ParsedPaymentIntent(
    val client: ClientProfile?,
    val specificMonth: Int? = null,
    val amount: Double? = null,
    val paymentMethod: String? = null,
    val isPaymentIntent: Boolean = false,
    val rawText: String = ""
)

class PaymentIntentParser {

    private val paymentKeywords = listOf(
        "pago", "pagar", "pago", "cancelo", "abono", "cancelo",
        "cuota", "letra", "mes", "deposito", "transferencia", "efectivo"
    )

    private val spanishMonths = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    fun parse(input: String, clients: List<ClientProfile>): ParsedPaymentIntent {
        val normalizedInput = normalize(input)
        val hasPaymentKeyword = paymentKeywords.any { normalizedInput.contains(it) }

        val detectedClient = findBestMatchingClient(normalizedInput, clients)
        val detectedMonth = extractMonthOrInstallment(normalizedInput)
        val detectedAmount = extractAmount(normalizedInput)
        val detectedMethod = extractPaymentMethod(normalizedInput)

        val isPaymentIntent = hasPaymentKeyword || detectedClient != null

        return ParsedPaymentIntent(
            client = detectedClient,
            specificMonth = detectedMonth,
            amount = detectedAmount,
            paymentMethod = detectedMethod,
            isPaymentIntent = isPaymentIntent,
            rawText = input
        )
    }

    private fun findBestMatchingClient(
        normalizedInput: String,
        clients: List<ClientProfile>
    ): ClientProfile? {
        if (clients.isEmpty()) return null

        var bestClient: ClientProfile? = null
        var maxScore = 0

        for (client in clients) {
            val normalizedName = normalize(client.name)
            val normalizedDoc = normalize(client.displayDocumentNumber)
            val normalizedPhone = normalize(client.displayPhone)

            var score = 0

            // 1. Coincidencia por documento o teléfono directo
            if (normalizedDoc.isNotBlank() && normalizedInput.contains(normalizedDoc)) {
                score += 100
            }
            if (normalizedPhone.isNotBlank() && normalizedInput.contains(normalizedPhone)) {
                score += 100
            }

            // 2. Coincidencia por nombre completo exacto
            if (normalizedName.isNotBlank() && normalizedInput.contains(normalizedName)) {
                score += 80
            }

            // 3. Coincidencia por palabras del nombre (ej. "Gabriel" en "Gabriel Hidalgo")
            val nameTokens = normalizedName.split("\\s+".toRegex()).filter { it.length >= 3 }
            for (token in nameTokens) {
                if (normalizedInput.contains(token)) {
                    score += 25
                }
            }

            if (score > maxScore) {
                maxScore = score
                bestClient = client
            }
        }

        // Requiere al menos una coincidencia significativa
        return if (maxScore >= 25) bestClient else null
    }

    private fun extractMonthOrInstallment(normalizedInput: String): Int? {
        // Buscar "cuota 3", "letra 4", "mes 5", "cuota #3"
        val regexCuota = Pattern.compile("(?:cuota|letra|mes|num(?:ero)?)\\s*#?\\s*(\\d+)")
        val matcher = regexCuota.matcher(normalizedInput)
        if (matcher.find()) {
            val num = matcher.group(1)?.toIntOrNull()
            if (num != null && num in 1..360) {
                return num
            }
        }

        // Buscar mes en español (enero, febrero, etc.)
        for ((monthName, monthIndex) in spanishMonths) {
            if (normalizedInput.contains(monthName)) {
                return monthIndex
            }
        }

        return null
    }

    private fun extractAmount(normalizedInput: String): Double? {
        // Buscar $120, $ 150.50, 150 dolares, etc.
        val regex = Pattern.compile("(?:\\$|usd|dolares)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(?:dolares|usd)?")
        val matcher = regex.matcher(normalizedInput)
        while (matcher.find()) {
            val candidate = matcher.group(1)?.toDoubleOrNull()
            // Descartar números muy pequeños que probablemente son cuotas (1..12) a menos que lleven signo $
            if (candidate != null && (candidate > 12.0 || normalizedInput.contains("$"))) {
                return candidate
            }
        }
        return null
    }

    private fun extractPaymentMethod(normalizedInput: String): String? {
        return when {
            normalizedInput.contains("transferencia") || normalizedInput.contains("deposito") || normalizedInput.contains("transfer") -> "Transferencia"
            normalizedInput.contains("efectivo") || normalizedInput.contains("cash") -> "Efectivo"
            normalizedInput.contains("cheque") -> "Cheque"
            else -> null
        }
    }

    private fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return noAccents.lowercase(Locale.ROOT).trim()
    }
}
