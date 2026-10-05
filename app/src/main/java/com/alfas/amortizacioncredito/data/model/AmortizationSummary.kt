package com.alfas.amortizacioncredito.data.model

data class AmortizationSummary(
    val financedAmount: Double,
    val monthlyPayment: Double,
    val totalInterest: Double,
    val totalCost: Double,
    val totalPaid: Double,
    val totalPrincipalPaid: Double,
    val schedule: List<AmortizationItem>,
    val originalMonths: Int = schedule.size,
    val actualMonths: Int = schedule.size,
    val monthsSaved: Int = 0,
    val interestSaved: Double = 0.0
)
