package com.alfas.amortizacioncredito.ui.dialogs.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.domain.usecase.CalculateAmortizationUseCase
import java.util.Locale
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.ui.theme.BlueBadgeBg
import com.alfas.amortizacioncredito.ui.theme.BlueBadgeBorder
import com.alfas.amortizacioncredito.ui.theme.CreditHomeBlue
import com.alfas.amortizacioncredito.ui.theme.CreditHomeBlueDark
import com.alfas.amortizacioncredito.ui.theme.GreenBadgeText
import com.alfas.amortizacioncredito.ui.theme.OrangeDeep
import com.alfas.amortizacioncredito.ui.theme.Slate700
import com.alfas.amortizacioncredito.ui.theme.Slate900

@Composable
fun EarlyPaymentDialog(
    price: Double,
    downPayment: Double,
    rate: Double,
    years: Double,
    isRoundUp: Boolean,
    isFixedPriceLoan: Boolean = false,
    currentExtraMonth: Int,
    currentExtraAmount: Double,
    paymentRecords: Map<Int, com.alfas.amortizacioncredito.data.model.PaymentRecord> = emptyMap(),
    onDismiss: () -> Unit,
    onApply: (month: Int, amount: Double) -> Unit,
    onClear: () -> Unit
) {
    val initialMonthStr = remember { if (currentExtraMonth > 0) currentExtraMonth.toString() else "12" }
    val initialAmountStr = remember { if (currentExtraAmount > 0) currentExtraAmount.toString() else "5000" }

    var monthInput by remember { mutableStateOf(initialMonthStr) }
    var amountInput by remember { mutableStateOf(initialAmountStr) }
    var showConfirmDiscard by remember { mutableStateOf(false) }

    val hasChanges = remember(monthInput, amountInput, initialMonthStr, initialAmountStr) {
        monthInput.trim() != initialMonthStr.trim() || amountInput.trim() != initialAmountStr.trim()
    }

    val attemptDismiss = {
        if (hasChanges) {
            showConfirmDiscard = true
        } else {
            onDismiss()
        }
    }

    if (showConfirmDiscard) {
        AlertDialog(
            onDismissRequest = { showConfirmDiscard = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("¿Descartar simulación?") },
            text = { Text("Has ingresado valores para simular el abono. Si sales ahora sin aplicar, no se guardarán en la tabla.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDiscard = false
                        onDismiss()
                    }
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDiscard = false }) {
                    Text("Continuar Editando")
                }
            }
        )
    }

    val calculateUseCase = remember { CalculateAmortizationUseCase() }

    val parsedMonth = monthInput.toIntOrNull() ?: 1
    val parsedAmount = amountInput.toDoubleOrNull() ?: 0.0

    // Simulación en tiempo real dentro del modal
    val simulationResult = remember(price, downPayment, rate, years, isRoundUp, isFixedPriceLoan, parsedMonth, parsedAmount, paymentRecords) {
        if (parsedAmount > 0 && parsedMonth > 0) {
            calculateUseCase(
                price = price,
                downPayment = downPayment,
                annualRate = rate,
                years = years,
                roundUp = isRoundUp,
                isFixedPriceLoan = isFixedPriceLoan,
                extraPaymentMonth = parsedMonth,
                extraPaymentAmount = parsedAmount,
                paymentRecords = paymentRecords
            )
        } else {
            null
        }
    }

    val baseResult = remember(price, downPayment, rate, years, isRoundUp, isFixedPriceLoan, paymentRecords) {
        calculateUseCase(
            price = price,
            downPayment = downPayment,
            annualRate = rate,
            years = years,
            roundUp = isRoundUp,
            isFixedPriceLoan = isFixedPriceLoan,
            paymentRecords = paymentRecords
        )
    }

    AlertDialog(
        onDismissRequest = attemptDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Savings,
                contentDescription = null,
                tint = CreditHomeBlue,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Simular Abono a Capital",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Abonar dinero directamente a capital reduce el tiempo total de pago, manteniendo exactamente la misma cuota mensual pactada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Entradas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = monthInput,
                        onValueChange = { monthInput = it },
                        label = { Text("Mes de abono") },
                        placeholder = { Text("Ej: 12") },
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Monto extra ($)") },
                        placeholder = { Text("Ej: 5000") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.3f)
                    )
                }

                // Tarjeta de resultados de la simulación
                if (simulationResult != null && simulationResult.monthsSaved > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = BlueBadgeBg // Azul claro tenue
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CreditHomeBlueDark,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Resultado de la Simulación",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CreditHomeBlueDark
                                )
                            }

                            HorizontalDivider(color = BlueBadgeBorder)

                            // Cuota fija
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cuota mensual:", fontSize = 12.sp, color = Slate700)
                                Text(
                                    text = "${formatCurrency(simulationResult.monthlyPayment)} (Pactada fija)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                            }

                            // Plazo reducido
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Plazo total de pago:", fontSize = 12.sp, color = Slate700)
                                Text(
                                    text = "De ${simulationResult.originalMonths} a ${simulationResult.actualMonths} meses",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenBadgeText
                                )
                            }

                            // Cuotas ahorradas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("¡Cuotas que te ahorras!:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GreenBadgeText)
                                Text(
                                    text = "${simulationResult.monthsSaved} cuotas menos",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenBadgeText
                                )
                            }

                            // Ahorro en intereses
                            if (simulationResult.interestSaved > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ahorro en intereses:", fontSize = 12.sp, color = Slate700)
                                    Text(
                                        text = formatCurrency(simulationResult.interestSaved),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OrangeDeep
                                    )
                                }
                            } else if (isFixedPriceLoan) {
                                Text(
                                    text = "ℹ️ Monto fijo acordado: Las cuotas avanzadas se descuentan del plazo sin quitar intereses.",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else if (parsedAmount > 0) {
                    Text(
                        text = "Ingresa un mes y monto válido para ver la reducción de cuotas.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedMonth > 0 && parsedAmount > 0) {
                        onApply(parsedMonth, parsedAmount)
                    }
                    onDismiss()
                }
            ) {
                Text("Aplicar a la Tabla")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentExtraMonth > 0 && currentExtraAmount > 0) {
                    TextButton(
                        onClick = {
                            onClear()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Quitar Abono")
                    }
                }
                TextButton(onClick = attemptDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}

private val currencyFormatter = ThreadLocal.withInitial {
    java.text.DecimalFormat("$#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale.US))
}

private fun formatCurrency(amount: Double): String {
    return currencyFormatter.get()?.format(amount) ?: String.format(Locale.US, "$%,.2f", amount)
}
