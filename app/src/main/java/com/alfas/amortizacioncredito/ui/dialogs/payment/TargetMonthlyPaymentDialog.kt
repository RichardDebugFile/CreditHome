package com.alfas.amortizacioncredito.ui.dialogs.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.ceil
import com.alfas.amortizacioncredito.ui.theme.GreenSuccess
import com.alfas.amortizacioncredito.ui.theme.GreenSuccessDark
import com.alfas.amortizacioncredito.ui.theme.RedBadgeBg
import com.alfas.amortizacioncredito.ui.theme.RedBadgeText
import com.alfas.amortizacioncredito.ui.theme.RedError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetMonthlyPaymentDialog(
    financedAmount: Double,
    remainingBalance: Double,
    hasPaidMonths: Boolean,
    annualRate: Double,
    originalMonths: Int,
    originalMonthlyPayment: Double,
    originalTotalInterest: Double,
    isCompoundInterest: Boolean,
    onApplyMonths: ((months: Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var useRemainingBalance by remember { mutableStateOf(hasPaidMonths && remainingBalance > 0.0) }
    val baseBalance = if (useRemainingBalance) remainingBalance else financedAmount

    val initialAmount = remember(originalMonthlyPayment) {
        val suggested = if (originalMonthlyPayment > 0) originalMonthlyPayment * 1.25 else 100.0
        String.format(Locale.US, "%.2f", ceil(suggested / 10.0) * 10.0)
    }

    var targetPaymentInput by remember { mutableStateOf(initialAmount) }
    var showConfirmDiscard by remember { mutableStateOf(false) }

    val hasChanges = remember(targetPaymentInput, initialAmount) {
        targetPaymentInput.trim() != initialAmount.trim()
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
            title = { Text("¿Cerrar calculadora?") },
            text = { Text("Has ingresado un monto personalizado. ¿Deseas salir de la simulación?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDiscard = false
                        onDismiss()
                    }
                ) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDiscard = false }) {
                    Text("Continuar")
                }
            }
        )
    }

    val parsedTargetPayment = targetPaymentInput.toDoubleOrNull() ?: 0.0
    val monthlyRate = (annualRate / 100.0) / 12.0
    val minMonthlyInterest = baseBalance * monthlyRate

    // Cálculo dinámico de meses e intereses proyectados
    val simulationResult = remember(baseBalance, monthlyRate, parsedTargetPayment, isCompoundInterest, originalMonths, originalTotalInterest) {
        if (parsedTargetPayment <= minMonthlyInterest || baseBalance <= 0.0) {
            null
        } else {
            var currentBal = baseBalance
            var monthCount = 0
            var totalInterest = 0.0
            var totalPaid = 0.0
            var lastMonthPayment = 0.0

            while (currentBal > 0.001 && monthCount < 1200) {
                monthCount++
                val interest = currentBal * monthlyRate
                totalInterest += interest
                val principalNeeded = currentBal
                val availableForPrincipal = parsedTargetPayment - interest

                if (availableForPrincipal >= principalNeeded) {
                    val actualPayment = principalNeeded + interest
                    totalPaid += actualPayment
                    lastMonthPayment = actualPayment
                    currentBal = 0.0
                    break
                } else {
                    currentBal -= availableForPrincipal
                    totalPaid += parsedTargetPayment
                    lastMonthPayment = parsedTargetPayment
                }
            }

            val monthsSaved = maxOf(0, originalMonths - monthCount)
            val interestSaved = maxOf(0.0, originalTotalInterest - totalInterest)

            ProjectionResult(
                months = monthCount,
                monthsSaved = monthsSaved,
                totalPaid = totalPaid,
                totalInterest = totalInterest,
                interestSaved = interestSaved,
                lastMonthPayment = lastMonthPayment
            )
        }
    }

    AlertDialog(
        onDismissRequest = attemptDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Meta de Cuota Mensual",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "¿En cuántos meses liquido pagando \$X al mes?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Selector de saldo base (si hay historial)
                if (hasPaidMonths && remainingBalance > 0.0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Calcular plazo sobre:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = useRemainingBalance,
                                    onClick = { useRemainingBalance = true },
                                    label = { Text("Saldo Actual (${formatCurrency(remainingBalance)})", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !useRemainingBalance,
                                    onClick = { useRemainingBalance = false },
                                    label = { Text("Total (${formatCurrency(financedAmount)})", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Capital a Liquidar:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatCurrency(baseBalance), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Campo de Monto Deseado a Pagar Mensualmente
                OutlinedTextField(
                    value = targetPaymentInput,
                    onValueChange = { targetPaymentInput = it },
                    label = { Text("Monto que deseas pagar al mes ($)") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips de sugerencia rápida
                if (originalMonthlyPayment > 0.0) {
                    Text(
                        text = "Sugerencias de cuota:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val presets = listOf(
                            "+15%" to (originalMonthlyPayment * 1.15),
                            "+25%" to (originalMonthlyPayment * 1.25),
                            "+50%" to (originalMonthlyPayment * 1.50),
                            "Doble (2x)" to (originalMonthlyPayment * 2.00)
                        )
                        presets.forEach { (label, value) ->
                            SuggestionChip(
                                onClick = {
                                    targetPaymentInput = String.format(Locale.US, "%.2f", value)
                                },
                                label = { Text("$label (${formatCurrency(value)})", fontSize = 10.sp) }
                            )
                        }
                    }
                }

                // Tarjeta de Resultados o Alerta
                if (parsedTargetPayment <= 0.0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ingresa el valor mensual que te gustaría abonar para calcular en cuánto tiempo terminarás de pagar.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (parsedTargetPayment <= minMonthlyInterest) {
                    Surface(
                        color = RedBadgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = RedError, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "El monto debe ser mayor a ${formatCurrency(minMonthlyInterest)} (interés mensual generado) para poder amortizar capital.",
                                color = RedBadgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (simulationResult != null) {
                    // Resultados calculados con éxito
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Plazo necesario:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatMonthsToYears(simulationResult.months),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    color = GreenSuccess,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${simulationResult.months} Meses",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider()

                            // Ahorro de tiempo
                            if (simulationResult.monthsSaved > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FastForward, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Te ahorras ${simulationResult.monthsSaved} cuotas (${formatMonthsToYears(simulationResult.monthsSaved)}) de deuda.",
                                        fontSize = 11.sp,
                                        color = GreenSuccessDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Interés y Ahorro financiero
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Interés total a pagar:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatCurrency(simulationResult.totalInterest), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                if (simulationResult.interestSaved > 0.0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Ahorro en intereses:", fontSize = 11.sp, color = GreenSuccessDark)
                                        Text(formatCurrency(simulationResult.interestSaved), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GreenSuccessDark)
                                    }
                                }
                            }

                            // Nota de la última cuota si es menor
                            if (simulationResult.lastMonthPayment < parsedTargetPayment - 0.01) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "💡 Nota: En el mes ${simulationResult.months} (última cuota) solo pagarás ${formatCurrency(simulationResult.lastMonthPayment)} para cancelar el saldo final.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (simulationResult != null && onApplyMonths != null) {
                    Button(
                        onClick = {
                            onApplyMonths(simulationResult.months)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aplicar Plazo (${simulationResult.months}m)")
                    }
                } else {
                    Button(onClick = attemptDismiss) {
                        Text("Listo")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = attemptDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private data class ProjectionResult(
    val months: Int,
    val monthsSaved: Int,
    val totalPaid: Double,
    val totalInterest: Double,
    val interestSaved: Double,
    val lastMonthPayment: Double
)

private fun formatMonthsToYears(months: Int): String {
    val years = months / 12
    val remainingMonths = months % 12
    return when {
        years > 0 && remainingMonths > 0 -> "$years año(s) y $remainingMonths mes(es)"
        years > 0 -> "$years año(s)"
        else -> "$months mes(es)"
    }
}

private val currencyFormatter = ThreadLocal.withInitial {
    DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US))
}

private fun formatCurrency(amount: Double): String {
    return currencyFormatter.get()?.format(amount) ?: String.format(Locale.US, "$%,.2f", amount)
}
