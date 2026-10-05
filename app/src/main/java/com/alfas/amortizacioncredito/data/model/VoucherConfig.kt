package com.alfas.amortizacioncredito.data.model

import org.json.JSONArray
import org.json.JSONObject

data class CustomVoucherField(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val value: String = "",
    val isEnabled: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("label", label)
            put("value", value)
            put("isEnabled", isEnabled)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomVoucherField {
            return CustomVoucherField(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                label = json.optString("label", ""),
                value = json.optString("value", ""),
                isEnabled = json.optBoolean("isEnabled", true)
            )
        }
    }
}

data class VoucherConfig(
    // Desglose Financiero
    val showPreviousBalance: Boolean = false,
    val showRemainingBalance: Boolean = false, // "Cuánto le falta por pagar"
    val showScheduledPayment: Boolean = true,
    val showPrincipal: Boolean = true,
    val showInterest: Boolean = true,
    val showExtraPayment: Boolean = true,
    val showTotalReceived: Boolean = true,

    // Soporte de Transacción
    val showPaymentMethod: Boolean = true,
    val showBankEntity: Boolean = true,
    val showReferenceNumber: Boolean = true,
    val showPayerName: Boolean = true,
    val showNotes: Boolean = true,

    // Datos de Empresa y Cliente
    val showLogo: Boolean = true,
    val showBusinessName: Boolean = true,
    val showIssuerName: Boolean = true,
    val showRuc: Boolean = true,
    val showAddress: Boolean = true,
    val showClientDocOrPhone: Boolean = true,

    // Pie de página y Firma
    val showSignature: Boolean = true,
    val showFooterNote: Boolean = true,
    val customFooterText: String = "",

    // Campos Personalizados adicionales
    val customFields: List<CustomVoucherField> = emptyList()
) {
    fun toJson(): String {
        val json = JSONObject().apply {
            put("showPreviousBalance", showPreviousBalance)
            put("showRemainingBalance", showRemainingBalance)
            put("showScheduledPayment", showScheduledPayment)
            put("showPrincipal", showPrincipal)
            put("showInterest", showInterest)
            put("showExtraPayment", showExtraPayment)
            put("showTotalReceived", showTotalReceived)
            put("showPaymentMethod", showPaymentMethod)
            put("showBankEntity", showBankEntity)
            put("showReferenceNumber", showReferenceNumber)
            put("showPayerName", showPayerName)
            put("showNotes", showNotes)
            put("showLogo", showLogo)
            put("showBusinessName", showBusinessName)
            put("showIssuerName", showIssuerName)
            put("showRuc", showRuc)
            put("showAddress", showAddress)
            put("showClientDocOrPhone", showClientDocOrPhone)
            put("showSignature", showSignature)
            put("showFooterNote", showFooterNote)
            put("customFooterText", customFooterText)

            val customArr = JSONArray()
            customFields.forEach { customArr.put(it.toJson()) }
            put("customFields", customArr)
        }
        return json.toString()
    }

    companion object {
        val DEFAULT = VoucherConfig()

        fun fromJson(jsonStr: String?): VoucherConfig {
            if (jsonStr.isNullOrBlank()) return DEFAULT
            return try {
                val json = JSONObject(jsonStr)
                val customList = mutableListOf<CustomVoucherField>()
                val customArr = json.optJSONArray("customFields")
                if (customArr != null) {
                    for (i in 0 until customArr.length()) {
                        val obj = customArr.optJSONObject(i)
                        if (obj != null) {
                            customList.add(CustomVoucherField.fromJson(obj))
                        }
                    }
                }

                VoucherConfig(
                    showPreviousBalance = json.optBoolean("showPreviousBalance", false),
                    showRemainingBalance = json.optBoolean("showRemainingBalance", false),
                    showScheduledPayment = json.optBoolean("showScheduledPayment", true),
                    showPrincipal = json.optBoolean("showPrincipal", true),
                    showInterest = json.optBoolean("showInterest", true),
                    showExtraPayment = json.optBoolean("showExtraPayment", true),
                    showTotalReceived = json.optBoolean("showTotalReceived", true),
                    showPaymentMethod = json.optBoolean("showPaymentMethod", true),
                    showBankEntity = json.optBoolean("showBankEntity", true),
                    showReferenceNumber = json.optBoolean("showReferenceNumber", true),
                    showPayerName = json.optBoolean("showPayerName", true),
                    showNotes = json.optBoolean("showNotes", true),
                    showLogo = json.optBoolean("showLogo", true),
                    showBusinessName = json.optBoolean("showBusinessName", true),
                    showIssuerName = json.optBoolean("showIssuerName", true),
                    showRuc = json.optBoolean("showRuc", true),
                    showAddress = json.optBoolean("showAddress", true),
                    showClientDocOrPhone = json.optBoolean("showClientDocOrPhone", true),
                    showSignature = json.optBoolean("showSignature", true),
                    showFooterNote = json.optBoolean("showFooterNote", true),
                    customFooterText = json.optString("customFooterText", ""),
                    customFields = customList
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}
