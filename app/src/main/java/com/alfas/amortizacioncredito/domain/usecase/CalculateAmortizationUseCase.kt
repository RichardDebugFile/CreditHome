package com.alfas.amortizacioncredito.domain.usecase

import com.alfas.amortizacioncredito.data.model.AmortizationItem
import com.alfas.amortizacioncredito.data.model.AmortizationSummary
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

class CalculateAmortizationUseCase {

    operator fun invoke(
        price: Double,
        downPayment: Double,
        annualRate: Double,
        years: Double,
        desiredMonthlyPayment: Double = 0.0,
        roundUp: Boolean = false,
        isCompoundInterest: Boolean = false,
        isFixedPriceLoan: Boolean = false,
        extraPaymentMonth: Int = 0,
        extraPaymentAmount: Double = 0.0,
        startDate: String = "",
        paymentRecords: Map<Int, PaymentRecord> = emptyMap()
    ): AmortizationSummary {
        val financed = max(0.0, price - downPayment)
        val initialMonths = max(1, (years * 12.0).roundToLong().toInt())
        val monthlyRate = (annualRate / 100.0) / 12.0

        val baseMonthlyPayment: Double
        val compoundTotalInterest: Double

        if (desiredMonthlyPayment > 0.0) {
            baseMonthlyPayment = desiredMonthlyPayment
            compoundTotalInterest = 0.0
        } else if (financed <= 0.0) {
            baseMonthlyPayment = 0.0
            compoundTotalInterest = 0.0
        } else if (isCompoundInterest) {
            // Sistema de Interés Compuesto Directo: M = C * (1 + i)^n
            val totalFutureValue = if (monthlyRate > 0.0) {
                financed * (1.0 + monthlyRate).pow(initialMonths.toDouble())
            } else financed
            compoundTotalInterest = max(0.0, totalFutureValue - financed)
            baseMonthlyPayment = totalFutureValue / initialMonths
        } else {
            // Sistema Francés (Amortización tradicional sobre saldos)
            compoundTotalInterest = 0.0
            baseMonthlyPayment = if (monthlyRate > 0.0) {
                val factor = (1.0 + monthlyRate).pow(initialMonths.toDouble())
                financed * (monthlyRate * factor) / (factor - 1.0)
            } else {
                financed / initialMonths
            }
        }

        // Redondeo sin pérdida: redondea la cuota hacia arriba al entero superior más cercano (solo en cálculo estándar)
        val monthlyPayment = if (roundUp && desiredMonthlyPayment <= 0.0 && baseMonthlyPayment > 0.0) {
            ceil(baseMonthlyPayment)
        } else {
            baseMonthlyPayment
        }

        val schedule = mutableListOf<AmortizationItem>()
        var balanceInCents = (financed * 100).roundToLong()
        val standardPaymentInCents = (monthlyPayment * 100).roundToLong()
        val extraInCents = if (extraPaymentAmount > 0.0 && extraPaymentMonth > 0) {
            (extraPaymentAmount * 100).roundToLong()
        } else {
            0L
        }

        val fixedCompoundInterestPerMonthCents = if (isCompoundInterest && initialMonths > 0) {
            ((compoundTotalInterest / initialMonths) * 100).roundToLong()
        } else 0L

        // En caso de modalidad de monto fijo pactado, generar el plan de letras originales para conservar intereses
        data class ScheduledInstallment(val paymentCents: Long, val interestCents: Long, val principalCents: Long)
        val baseInstallments = mutableListOf<ScheduledInstallment>()
        if (isFixedPriceLoan) {
            var tempBal = balanceInCents
            for (i in 1..initialMonths) {
                val iInt = if (isCompoundInterest) {
                    fixedCompoundInterestPerMonthCents
                } else {
                    (tempBal * monthlyRate).roundToLong()
                }
                val iPay = if (i == initialMonths || standardPaymentInCents >= tempBal + iInt) {
                    tempBal + iInt
                } else {
                    standardPaymentInCents
                }
                val iPrin = maxOf(0L, iPay - iInt)
                tempBal = maxOf(0L, tempBal - iPrin)
                baseInstallments.add(ScheduledInstallment(iPay, iInt, iPrin))
            }
        }
        var remainingDebtCents = if (isFixedPriceLoan) baseInstallments.sumOf { it.paymentCents } else 0L
        var baseInstallmentIndex = 0

        var accumulatedInterestCents = 0L
        var accumulatedPrincipalCents = 0L
        var accumulatedPaymentCents = 0L

        var m = 1
        val maxMonthsSafety = max(initialMonths * 3, 600)

        while (m <= maxMonthsSafety) {
            if (isFixedPriceLoan && remainingDebtCents <= 0L) {
                break
            } else if (!isFixedPriceLoan && balanceInCents <= 0L) {
                break
            }

            // Determinar si hay un pago personalizado registrado para este mes
            val record = paymentRecords[m]
            val hasCustomPaid = record != null && record.isPaid && record.amountPaid > 0.0

            val proposedPaymentCents: Long = if (hasCustomPaid) {
                (record.amountPaid * 100).roundToLong()
            } else if (m == extraPaymentMonth && extraInCents > 0L) {
                standardPaymentInCents + extraInCents
            } else {
                standardPaymentInCents
            }

            val actualPaymentCents: Long
            val principalCents: Long
            val appliedInterestCents: Long
            val extraAppliedCents: Long

            if (isFixedPriceLoan) {
                // MODALIDAD MONTO FIJO PACTADO:
                // Al pagar por adelantado, se cancelan las letras faltantes sin quitar ni descontar sus intereses
                actualPaymentCents = minOf(proposedPaymentCents, remainingDebtCents)
                var unallocated = actualPaymentCents
                var mIntCents = 0L
                var mPrinCents = 0L

                while (unallocated > 0L && baseInstallmentIndex < baseInstallments.size) {
                    val inst = baseInstallments[baseInstallmentIndex]
                    if (unallocated >= inst.paymentCents) {
                        mIntCents += inst.interestCents
                        mPrinCents += inst.principalCents
                        unallocated -= inst.paymentCents
                        baseInstallmentIndex++
                    } else {
                        val fraction = unallocated.toDouble() / inst.paymentCents.toDouble()
                        val intPortion = (inst.interestCents * fraction).roundToLong()
                        val prinPortion = unallocated - intPortion
                        mIntCents += intPortion
                        mPrinCents += prinPortion
                        unallocated = 0L
                        baseInstallmentIndex++
                    }
                }
                if (unallocated > 0L) {
                    mPrinCents += unallocated
                    unallocated = 0L
                }

                appliedInterestCents = mIntCents
                principalCents = mPrinCents
                remainingDebtCents = maxOf(0L, remainingDebtCents - actualPaymentCents)
                balanceInCents = maxOf(0L, balanceInCents - principalCents)
                extraAppliedCents = maxOf(0L, actualPaymentCents - standardPaymentInCents)
            } else {
                // MODALIDAD ESTÁNDAR SOBRE SALDOS (FRANCÉS):
                // Los pagos anticipados reducen capital y disminuyen intereses de cuotas futuras
                val interestCents = if (isCompoundInterest) {
                    minOf(fixedCompoundInterestPerMonthCents, (balanceInCents * monthlyRate).roundToLong().coerceAtLeast(fixedCompoundInterestPerMonthCents))
                } else {
                    (balanceInCents * monthlyRate).roundToLong()
                }

                val payoffAmountCents = balanceInCents + interestCents

                if (proposedPaymentCents >= payoffAmountCents || (m >= initialMonths && !hasCustomPaid && balanceInCents <= standardPaymentInCents)) {
                    actualPaymentCents = payoffAmountCents
                    appliedInterestCents = interestCents
                    principalCents = balanceInCents
                    extraAppliedCents = max(0L, actualPaymentCents - standardPaymentInCents)
                    balanceInCents = 0L
                } else {
                    actualPaymentCents = proposedPaymentCents
                    appliedInterestCents = interestCents
                    val netToPrincipal = actualPaymentCents - appliedInterestCents
                    if (netToPrincipal >= 0L) {
                        principalCents = minOf(netToPrincipal, balanceInCents)
                        balanceInCents = max(0L, balanceInCents - principalCents)
                        extraAppliedCents = if (actualPaymentCents > standardPaymentInCents) {
                            actualPaymentCents - standardPaymentInCents
                        } else 0L
                    } else {
                        principalCents = 0L
                        val unpaidInterest = appliedInterestCents - actualPaymentCents
                        balanceInCents += unpaidInterest
                        extraAppliedCents = 0L
                    }
                }
            }

            accumulatedInterestCents += appliedInterestCents
            accumulatedPrincipalCents += principalCents
            accumulatedPaymentCents += actualPaymentCents

            val itemDueDate = getDueDateForMonth(startDate, m, record?.dueDate)

            schedule.add(
                AmortizationItem(
                    month = m,
                    payment = actualPaymentCents / 100.0,
                    interest = appliedInterestCents / 100.0,
                    principal = principalCents / 100.0,
                    remainingBalance = balanceInCents / 100.0,
                    extraPayment = extraAppliedCents / 100.0,
                    dueDate = itemDueDate
                )
            )

            if (isFixedPriceLoan && remainingDebtCents <= 0L) {
                break
            } else if (!isFixedPriceLoan && balanceInCents <= 0L) {
                break
            }
            m++
        }

        val totalInterest = if (isFixedPriceLoan && baseInstallments.isNotEmpty()) {
            baseInstallments.sumOf { it.interestCents } / 100.0
        } else {
            accumulatedInterestCents / 100.0
        }
        val totalPrincipal = accumulatedPrincipalCents / 100.0
        val totalPaid = if (isFixedPriceLoan && baseInstallments.isNotEmpty()) {
            baseInstallments.sumOf { it.paymentCents } / 100.0
        } else {
            accumulatedPaymentCents / 100.0
        }

        val actualMonths = schedule.size
        val monthsSaved = max(0, initialMonths - actualMonths)

        // Calcular ahorro en intereses respecto al crédito base original
        val baseSummary = calculateBase(financed, initialMonths, monthlyRate)
        val interestSaved = if (isFixedPriceLoan) {
            0.0 // En modalidad de monto fijo pactado, no se descuentan intereses
        } else {
            max(0.0, baseSummary.totalInterest - totalInterest)
        }

        return AmortizationSummary(
            financedAmount = financed,
            monthlyPayment = monthlyPayment,
            totalInterest = totalInterest,
            totalCost = totalPaid + downPayment,
            totalPaid = totalPaid,
            totalPrincipalPaid = totalPrincipal,
            schedule = schedule,
            originalMonths = initialMonths,
            actualMonths = actualMonths,
            monthsSaved = monthsSaved,
            interestSaved = interestSaved
        )
    }

    private fun getDueDateForMonth(startDateStr: String, month: Int, customDueDate: String?): String {
        if (!customDueDate.isNullOrBlank()) {
            return customDueDate
        }
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val baseDate = try {
            if (startDateStr.isNotBlank()) {
                sdf.parse(startDateStr) ?: Date()
            } else {
                Date()
            }
        } catch (e: Exception) {
            Date()
        }

        val calendar = Calendar.getInstance()
        calendar.time = baseDate
        // Si el mes es 1, es la fecha base startDate. Para mes 2, sumamos 1 mes, etc.
        calendar.add(Calendar.MONTH, month - 1)
        return sdf.format(calendar.time)
    }

    private fun calculateBase(financed: Double, months: Int, monthlyRate: Double): AmortizationSummary {
        val payment = if (financed <= 0.0) 0.0
        else if (monthlyRate > 0.0) {
            val factor = (1.0 + monthlyRate).pow(months.toDouble())
            financed * (monthlyRate * factor) / (factor - 1.0)
        } else financed / months

        var balanceCents = (financed * 100).roundToLong()
        val payCents = (payment * 100).roundToLong()
        var totalIntCents = 0L

        for (m in 1..months) {
            val intCents = (balanceCents * monthlyRate).roundToLong()
            var prinCents = payCents - intCents
            if (m == months || prinCents > balanceCents) prinCents = balanceCents
            balanceCents = max(0L, balanceCents - prinCents)
            totalIntCents += intCents
        }
        return AmortizationSummary(
            financedAmount = financed,
            monthlyPayment = payment,
            totalInterest = totalIntCents / 100.0,
            totalCost = financed + (totalIntCents / 100.0),
            totalPaid = financed + (totalIntCents / 100.0),
            totalPrincipalPaid = financed,
            schedule = emptyList()
        )
    }
}
