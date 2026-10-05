package com.alfas.amortizacioncredito.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfas.amortizacioncredito.ui.theme.*

enum class StatusBadgeType {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    NEUTRAL
}

/**
 * Badge o pastilla de estado visual reutilizable (PAGADO, PENDIENTE, VENCIDO, FIRMADO, etc.).
 * Unifica los estilos, colores y tipografía en toda la aplicación.
 */
@Composable
fun AppStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    type: StatusBadgeType = StatusBadgeType.SUCCESS,
    icon: ImageVector? = null,
    showIcon: Boolean = true,
    fontSize: TextUnit = 10.sp,
    iconSize: Dp = 12.dp
) {
    val (bgColor, textColor, defaultIcon) = when (type) {
        StatusBadgeType.SUCCESS -> Triple(GreenBadgeBg, GreenBadgeText, Icons.Default.CheckCircle)
        StatusBadgeType.WARNING -> Triple(AmberBadgeBg, AmberBadgeText, Icons.Default.WarningAmber)
        StatusBadgeType.ERROR -> Triple(RedBadgeBg, RedBadgeText, Icons.Default.ErrorOutline)
        StatusBadgeType.INFO -> Triple(BlueBadgeBg, BlueInfoDark, Icons.Default.Info)
        StatusBadgeType.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Schedule
        )
    }

    val finalIcon = icon ?: defaultIcon

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon) {
                Icon(
                    imageVector = finalIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
