package com.alfas.amortizacioncredito.domain.usecase

import android.content.Context
import android.util.Base64
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.SmtpConfig
import com.alfas.amortizacioncredito.data.repository.SmtpRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.alfas.amortizacioncredito.domain.service.GmailSmtpSender

class EmailNotificationUseCase(
    private val context: Context,
    private val smtpRepository: SmtpRepository = SmtpRepository(context),
    private val smtpSender: GmailSmtpSender = GmailSmtpSender(),
    private val calculateAmortizationUseCase: CalculateAmortizationUseCase = CalculateAmortizationUseCase()
) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    suspend fun sendTestEmail(recipientEmail: String, businessProfile: BusinessProfile): Result<Unit> {
        val config = smtpRepository.getConfig()
        val subject = "✅ Prueba de Conexión SMTP - ${businessProfile.businessName.ifBlank { "CreditHome" }}"
        val html = generateTestEmailHtml(businessProfile, config.senderEmail)
        return smtpSender.sendEmail(config, recipientEmail, subject, html, "Prueba de conexión SMTP exitosa.")
    }

    suspend fun sendPaymentReminderEmail(
        config: SmtpConfig,
        businessProfile: BusinessProfile,
        client: ClientProfile,
        month: Int,
        totalMonths: Int,
        dueDate: String,
        amount: Double,
        remainingBalance: Double,
        daysRemaining: Int
    ): Result<Unit> {
        if (client.email.isBlank()) {
            return Result.failure(Exception("El cliente '${client.name}' no tiene correo electrónico registrado."))
        }

        val subject = if (daysRemaining < 0) {
            "🔴 Aviso de Cuota Vencida (#$month) - ${businessProfile.businessName.ifBlank { "CreditHome" }}"
        } else if (daysRemaining == 0) {
            "⚠️ ¡Tu cuota vence HOY! (#$month) - ${businessProfile.businessName.ifBlank { "CreditHome" }}"
        } else {
            "🔔 Recordatorio de Pago (#$month) - Vence en $daysRemaining días - ${businessProfile.businessName.ifBlank { "CreditHome" }}"
        }

        val html = generateReminderHtml(
            businessProfile = businessProfile,
            clientName = client.name,
            month = month,
            totalMonths = totalMonths,
            dueDate = dueDate,
            amount = amount,
            remainingBalance = remainingBalance,
            daysRemaining = daysRemaining
        )

        return smtpSender.sendEmail(config, client.email, subject, html)
    }

    suspend fun sendSignedReceiptEmail(
        config: SmtpConfig,
        businessProfile: BusinessProfile,
        client: ClientProfile,
        record: PaymentRecord,
        totalMonths: Int,
        scheduledPayment: Double,
        remainingBalance: Double
    ): Result<Unit> {
        if (client.email.isBlank()) {
            return Result.failure(Exception("El cliente '${client.name}' no tiene correo electrónico registrado."))
        }
        if (!record.isSigned) {
            return Result.failure(Exception("El comprobante debe estar firmado antes de enviarse al cliente."))
        }

        val subject = "📄 Comprobante Oficial de Pago Cuota #${record.month} - ${businessProfile.businessName.ifBlank { "CreditHome" }}"

        val signatureBase64 = if (record.signaturePath.isNotBlank()) {
            try {
                val bmp = com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault.loadSignatureBitmap(record.signaturePath)
                if (bmp != null) {
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                } else null
            } catch (_: Exception) { null }
        } else null

        val html = generateSignedReceiptHtml(
            businessProfile = businessProfile,
            clientName = client.name,
            clientDoc = client.documentOrPhone,
            record = record,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            remainingBalance = remainingBalance,
            signatureBase64 = signatureBase64
        )

        return smtpSender.sendEmail(config, client.email, subject, html)
    }

    fun generateTestEmailHtml(businessProfile: BusinessProfile, senderEmail: String): String {
        val company = businessProfile.businessName.ifBlank { "CreditHome" }
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: Arial, sans-serif; background-color: #f1f5f9; padding: 20px; color: #1e293b;">
                <div style="max-width: 580px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;">
                    <div style="background-color: #2563eb; padding: 24px; text-align: center; color: white;">
                        <h1 style="margin: 0; font-size: 22px;">$company</h1>
                        <p style="margin: 6px 0 0 0; opacity: 0.9; font-size: 13px;">Verificación de Servidor SMTP de Google</p>
                    </div>
                    <div style="padding: 24px;">
                        <div style="background-color: #f0fdf4; border-left: 4px solid #16a34a; padding: 14px; border-radius: 4px; margin-bottom: 20px;">
                            <strong style="color: #166534; font-size: 15px;">🎉 ¡Conexión SMTP Establecida con Éxito!</strong>
                            <p style="margin: 6px 0 0 0; font-size: 13px; color: #14532d;">El servidor de correo de Google ($senderEmail) está configurado correctamente y listo para enviar notificaciones de cobro y comprobantes firmados.</p>
                        </div>
                        <p style="font-size: 12px; color: #64748b; text-align: center; margin-top: 24px;">Generado automáticamente por CreditHome App.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun generateReminderHtml(
        businessProfile: BusinessProfile,
        clientName: String,
        month: Int,
        totalMonths: Int,
        dueDate: String,
        amount: Double,
        remainingBalance: Double,
        daysRemaining: Int
    ): String {
        val company = businessProfile.businessName.ifBlank { "CreditHome" }
        val ruc = businessProfile.ruc.ifBlank { "N/A" }
        val statusColor = if (daysRemaining < 0) "#dc2626" else if (daysRemaining == 0) "#d97706" else "#2563eb"
        val statusTitle = if (daysRemaining < 0) "Cuota Vencida (En Mora)" else if (daysRemaining == 0) "¡Vencimiento Hoy!" else "Próximo Vencimiento"

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: Arial, sans-serif; background-color: #f8fafc; padding: 20px; color: #1e293b;">
                <div style="max-width: 580px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;">
                    <div style="background-color: $statusColor; padding: 24px; text-align: center; color: white;">
                        <h1 style="margin: 0; font-size: 22px;">$company</h1>
                        <p style="margin: 4px 0 0 0; opacity: 0.9; font-size: 13px;">RUC / Emisor: $ruc</p>
                        <span style="display: inline-block; margin-top: 12px; background: rgba(255,255,255,0.25); padding: 4px 14px; border-radius: 20px; font-weight: bold; font-size: 13px;">$statusTitle</span>
                    </div>
                    <div style="padding: 24px;">
                        <p style="font-size: 15px; margin-top: 0;">Estimado(a) <strong>$clientName</strong>,</p>
                        <p style="font-size: 13.5px; color: #475569; line-height: 1.5;">Le recordamos los detalles de su próxima cuota de financiamiento con <strong>$company</strong>:</p>
                        
                        <div style="background-color: #f1f5f9; border-radius: 8px; padding: 18px; margin: 18px 0;">
                            <table style="width: 100%; font-size: 13.5px; border-collapse: collapse;">
                                <tr>
                                    <td style="padding: 6px 0; color: #64748b;">Número de Cuota:</td>
                                    <td style="padding: 6px 0; font-weight: bold; text-align: right;">#$month de $totalMonths</td>
                                </tr>
                                <tr>
                                    <td style="padding: 6px 0; color: #64748b;">Fecha Límite de Pago:</td>
                                    <td style="padding: 6px 0; font-weight: bold; text-align: right; color: $statusColor;">$dueDate</td>
                                </tr>
                                <tr>
                                    <td style="padding: 6px 0; color: #64748b; font-size: 15px;">Monto a Cancelar:</td>
                                    <td style="padding: 6px 0; font-weight: 800; font-size: 18px; text-align: right; color: #166534;">$${String.format(Locale.US, "%,.2f", amount)}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 6px 0; color: #64748b;">Saldo Capital Pendiente:</td>
                                    <td style="padding: 6px 0; font-weight: bold; text-align: right;">$${String.format(Locale.US, "%,.2f", remainingBalance)}</td>
                                </tr>
                            </table>
                        </div>

                        ${if (businessProfile.issuerName.isNotBlank() || businessProfile.phone.isNotBlank()) """
                        <div style="background-color: #fffbeb; border: 1px solid #fef3c7; border-radius: 6px; padding: 12px; font-size: 12.5px; color: #92400e;">
                            <strong>Información de Contacto / Pago:</strong><br>
                            ${if (businessProfile.issuerName.isNotBlank()) "Titular: " + businessProfile.issuerName + "<br>" else ""}
                            ${if (businessProfile.phone.isNotBlank()) "Teléfono: " + businessProfile.phone + "<br>" else ""}
                            ${if (businessProfile.email.isNotBlank()) "Correo: " + businessProfile.email else ""}
                        </div>
                        """ else ""}

                        <p style="font-size: 12px; color: #94a3b8; text-align: center; margin-top: 24px; border-top: 1px solid #f1f5f9; padding-top: 14px;">
                            Este es un recordatorio automático emitido por el sistema financiero de $company.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun generateSignedReceiptHtml(
        businessProfile: BusinessProfile,
        clientName: String,
        clientDoc: String,
        record: PaymentRecord,
        totalMonths: Int,
        scheduledPayment: Double,
        remainingBalance: Double,
        signatureBase64: String?
    ): String {
        val company = businessProfile.businessName.ifBlank { "CreditHome" }
        val ruc = businessProfile.ruc.ifBlank { "N/A" }
        val signedDate = if (record.signedAt > 0) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(record.signedAt)) else record.paymentDate

        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="font-family: Arial, sans-serif; background-color: #f8fafc; padding: 20px; color: #1e293b;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); border: 1px solid #e2e8f0;">
                    <div style="background-color: #166534; padding: 24px; text-align: center; color: white;">
                        <h1 style="margin: 0; font-size: 22px;">$company</h1>
                        <p style="margin: 4px 0 0 0; opacity: 0.9; font-size: 13px;">RUC / Emisor: $ruc</p>
                        <span style="display: inline-block; margin-top: 12px; background: rgba(255,255,255,0.25); padding: 4px 16px; border-radius: 20px; font-weight: bold; font-size: 13px;">✓ COMPROBANTE OFICIAL DE PAGO</span>
                    </div>
                    <div style="padding: 24px;">
                        <div style="text-align: center; margin-bottom: 20px;">
                            <span style="font-size: 13px; color: #64748b;">Monto Pagado:</span>
                            <div style="font-size: 28px; font-weight: 800; color: #166534;">$${String.format(Locale.US, "%,.2f", record.amountPaid)}</div>
                            <span style="font-size: 12px; background-color: #dcfce7; color: #166534; font-weight: bold; padding: 2px 10px; border-radius: 12px;">PAGADO EN TOTALIDAD</span>
                        </div>

                        <div style="background-color: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; padding: 16px; margin-bottom: 20px;">
                            <table style="width: 100%; font-size: 13px; border-collapse: collapse;">
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">Cliente:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">$clientName</td>
                                </tr>
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">Cédula / Documento:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">${clientDoc.ifBlank { "N/A" }}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">Cuota Cancelada:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">#${record.month} de $totalMonths</td>
                                </tr>
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">Fecha de Pago:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">${record.paymentDate}</td>
                                </tr>
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">Método / Canal:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">${record.paymentMethod} ${if (record.bankOrEntity.isNotBlank()) "(${record.bankOrEntity})" else ""}</td>
                                </tr>
                                ${if (record.referenceNumber.isNotBlank()) """
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">N° Comprobante / Ref:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">${record.referenceNumber}</td>
                                </tr>
                                """ else ""}
                                <tr>
                                    <td style="padding: 5px 0; color: #64748b;">Saldo Capital Restante:</td>
                                    <td style="padding: 5px 0; font-weight: bold; text-align: right;">$${String.format(Locale.US, "%,.2f", remainingBalance)}</td>
                                </tr>
                            </table>
                        </div>

                        <!-- Sección de Firma Oficial -->
                        <div style="border: 1px solid #cbd5e1; border-radius: 8px; padding: 14px; text-align: center; background-color: #ffffff; margin-bottom: 16px;">
                            <div style="font-size: 11px; font-weight: bold; color: #475569; letter-spacing: 0.5px; margin-bottom: 8px;">FIRMA DE AUTORIZACIÓN / EMISOR</div>
                            ${if (signatureBase64 != null) """
                            <img src="data:image/png;base64,$signatureBase64" style="max-height: 80px; max-width: 240px; margin: 4px auto;" alt="Firma Oficial">
                            """ else """
                            <div style="font-size: 13px; font-weight: bold; color: #1e293b; padding: 10px 0;">[Firma Registrada en el Sistema]</div>
                            """}
                            <div style="font-size: 11px; color: #64748b; margin-top: 4px;">
                                ${if (record.signedBy.isNotBlank()) "Firmado por: " + record.signedBy + "<br>" else ""}
                                ${if (record.signatureDetails.isNotBlank()) record.signatureDetails + "<br>" else ""}
                                Fecha de Firma: $signedDate
                            </div>
                        </div>

                        <p style="font-size: 11.5px; color: #94a3b8; text-align: center; margin: 0;">
                            Documento emitido electrónicamente con validez legal por $company.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
