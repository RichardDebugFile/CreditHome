package com.alfas.amortizacioncredito.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Fila reutilizable clave-valor para comprobantes, detalles de cliente, resúmenes de préstamos
 * y vistas estructuradas de información financiera.
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelWeight: Float = 0.42f,
    valueWeight: Float = 0.58f,
    fontSize: TextUnit = 11.sp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            color = labelColor,
            modifier = Modifier.weight(labelWeight)
        )
        Text(
            text = value,
            fontSize = fontSize,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(valueWeight)
        )
    }
}

/**
 * Alias directo y semántico para comprobantes y recibos de pago.
 */
@Composable
fun VoucherRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 11.sp
) {
    KeyValueRow(
        label = label,
        value = value,
        isBold = isBold,
        valueColor = valueColor,
        fontSize = fontSize
    )
}
