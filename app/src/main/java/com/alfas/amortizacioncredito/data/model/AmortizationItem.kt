package com.alfas.amortizacioncredito.data.model

data class AmortizationItem(
    val month: Int,
    val payment: Double,
    val interest: Double,
    val principal: Double,
    val remainingBalance: Double,
    val extraPayment: Double = 0.0,
    val dueDate: String = ""
)
