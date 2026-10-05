package com.alfas.amortizacioncredito.data.model

import com.alfas.amortizacioncredito.config.AppConfig

data class SmtpConfig(
    val host: String = AppConfig.Smtp.DEFAULT_HOST,
    val port: Int = AppConfig.Smtp.DEFAULT_PORT,
    val senderEmail: String = "",
    val senderName: String = AppConfig.Smtp.DEFAULT_SENDER_NAME,
    val appPassword: String = "", // Contraseña de aplicación de Google de 16 dígitos
    val isEnabled: Boolean = false,
    val alertDaysBefore: List<Int> = listOf(7, 3, 1, 0, -1), // 7 días, 3 días, 1 día, 0 (hoy), -1 (mora)
    val useSsl: Boolean = true
) {
    val isConfigured: Boolean
        get() = senderEmail.isNotBlank() && appPassword.isNotBlank()
}
