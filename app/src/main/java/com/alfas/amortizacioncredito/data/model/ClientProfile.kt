package com.alfas.amortizacioncredito.data.model

import java.util.UUID

data class ClientProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val documentNumber: String = "", // Cédula, RUC o Pasaporte (Facturación)
    val phone: String = "",          // Teléfono o Celular de contacto
    val address: String = "",        // Dirección o Domicilio fiscal
    val documentOrPhone: String = "", // Campo de compatibilidad legacy
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val startDate: String = "",
    val price: String = "86000",
    val downPayment: String = "25000",
    val annualRate: String = "3.5",
    val years: String = "5",
    val isRoundUpEnabled: Boolean = false,
    val hideInterest: Boolean = false,
    val isCompoundInterest: Boolean = false,
    val isFixedPriceLoan: Boolean = false,
    val extraPaymentMonth: Int = 0,
    val extraPaymentAmount: Double = 0.0,
    val notes: String = "",
    val paymentRecords: Map<Int, PaymentRecord> = emptyMap()
) {
    val displayDocumentNumber: String
        get() = documentNumber.ifEmpty {
            if (documentOrPhone.isNotBlank() && !documentOrPhone.startsWith("+") && documentOrPhone.length in 10..13 && documentOrPhone.all { it.isDigit() }) {
                documentOrPhone
            } else ""
        }

    val displayPhone: String
        get() = phone.ifEmpty {
            if (documentOrPhone.startsWith("+") || (documentOrPhone.isNotBlank() && (documentOrPhone.length < 10 || !documentOrPhone.all { it.isDigit() }))) {
                documentOrPhone
            } else ""
        }

    val displayDocumentOrPhone: String
        get() = when {
            documentNumber.isNotBlank() && phone.isNotBlank() -> "$documentNumber | $phone"
            documentNumber.isNotBlank() -> documentNumber
            phone.isNotBlank() -> phone
            documentOrPhone.isNotBlank() -> documentOrPhone
            else -> ""
        }

    val totalPaidMonths: Int
        get() = paymentRecords.values.count { it.isPaid }

    val totalAmountPaidRecorded: Double
        get() = paymentRecords.values.filter { it.isPaid }.sumOf { it.amountPaid }
}
