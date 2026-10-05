package com.alfas.amortizacioncredito.data.model

data class PaymentRecord(
    val month: Int,
    val isPaid: Boolean = false,
    val paymentDate: String = "",
    val dueDate: String = "",
    val amountPaid: Double = 0.0,
    val paymentMethod: String = "Transferencia",
    val referenceNumber: String = "",
    val bankOrEntity: String = "",
    val payerName: String = "",
    val receiptImagePath: String = "",
    val note: String = "",
    val signaturePath: String = "",
    val signatureType: String = "", // "DIGITAL_CANVAS", "ELECTRONIC_P12", "SCANNED_PHOTO"
    val signedBy: String = "",
    val signedAt: Long = 0L,
    val signatureDetails: String = "",
    val emailSentCount: Int = 0,
    val lastEmailSentAt: Long = 0L
) {
    val isSigned: Boolean
        get() = signaturePath.isNotBlank() || signatureDetails.isNotBlank()
}
