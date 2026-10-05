package com.alfas.amortizacioncredito.domain.usecase

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.VoucherConfig
import com.alfas.amortizacioncredito.domain.security.P12SignatureHelper
import com.alfas.amortizacioncredito.domain.security.PdfPadesSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class ExportPaymentReceiptPdfUseCase {

    private val currencyFormatter = DecimalFormat("$#,##0.00", DecimalFormatSymbols(Locale.US))

    private fun formatCurrency(amount: Double): String {
        return currencyFormatter.format(amount)
    }

    private fun processFinalPdfBytes(
        rawPdfBytes: ByteArray,
        record: PaymentRecord
    ): ByteArray {
        if (record.signatureType == "ELECTRONIC_P12") {
            val creds = P12SignatureHelper.activeCredentials
            if (creds != null) {
                try {
                    val signed = PdfPadesSigner.signPdfBytes(
                        pdfBytes = rawPdfBytes,
                        privateKey = creds.privateKey,
                        certificateChain = creds.certificateChain,
                        signerName = if (record.signedBy.isNotBlank()) record.signedBy else creds.certInfo.subjectName,
                        reason = "Comprobante Oficial de Pago Digital - Cuota ${record.month}",
                        location = "Ecuador",
                        contactInfo = creds.certInfo.issuerName
                    )
                    if (signed != null) {
                        return signed
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return rawPdfBytes
    }

    fun exportToStream(
        outputStream: OutputStream,
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        previousBalance: Double,
        remainingBalance: Double,
        clientName: String?,
        clientDocumentOrPhone: String?,
        businessProfile: BusinessProfile? = null,
        voucherConfig: VoucherConfig = VoucherConfig.DEFAULT
    ) {
        val document = createPdfDocument(
            record = record,
            month = month,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            interest = interest,
            principal = principal,
            previousBalance = previousBalance,
            remainingBalance = remainingBalance,
            clientName = clientName,
            clientDocumentOrPhone = clientDocumentOrPhone,
            businessProfile = businessProfile,
            voucherConfig = voucherConfig
        )
        try {
            val baos = ByteArrayOutputStream()
            document.writeTo(baos)
            val finalBytes = processFinalPdfBytes(baos.toByteArray(), record)
            outputStream.write(finalBytes)
            outputStream.flush()
        } finally {
            document.close()
        }
    }

    fun generatePdfFile(
        context: Context,
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        previousBalance: Double,
        remainingBalance: Double,
        clientName: String?,
        clientDocumentOrPhone: String?,
        businessProfile: BusinessProfile? = null,
        voucherConfig: VoucherConfig = VoucherConfig.DEFAULT
    ): File {
        val safeClient = (clientName ?: "Cliente").replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "Comprobante_Pago_${safeClient}_Mes_${month}.pdf"
        val pdfDir = File(context.cacheDir, "receipts_pdf")
        if (!pdfDir.exists()) pdfDir.mkdirs()
        val file = File(pdfDir, fileName)

        val document = createPdfDocument(
            record = record,
            month = month,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            interest = interest,
            principal = principal,
            previousBalance = previousBalance,
            remainingBalance = remainingBalance,
            clientName = clientName,
            clientDocumentOrPhone = clientDocumentOrPhone,
            businessProfile = businessProfile,
            voucherConfig = voucherConfig
        )

        try {
            val baos = ByteArrayOutputStream()
            document.writeTo(baos)
            val finalBytes = processFinalPdfBytes(baos.toByteArray(), record)
            FileOutputStream(file).use { out ->
                out.write(finalBytes)
                out.flush()
            }
        } finally {
            document.close()
        }
        return file
    }

    fun sharePdf(
        context: Context,
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        previousBalance: Double,
        remainingBalance: Double,
        clientName: String?,
        clientDocumentOrPhone: String?,
        businessProfile: BusinessProfile? = null,
        voucherConfig: VoucherConfig = VoucherConfig.DEFAULT
    ) {
        val file = generatePdfFile(
            context = context,
            record = record,
            month = month,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            interest = interest,
            principal = principal,
            previousBalance = previousBalance,
            remainingBalance = remainingBalance,
            clientName = clientName,
            clientDocumentOrPhone = clientDocumentOrPhone,
            businessProfile = businessProfile,
            voucherConfig = voucherConfig
        )

        val intent = createShareIntent(
            context = context,
            record = record,
            month = month,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            interest = interest,
            principal = principal,
            previousBalance = previousBalance,
            remainingBalance = remainingBalance,
            clientName = clientName,
            clientDocumentOrPhone = clientDocumentOrPhone,
            businessProfile = businessProfile,
            voucherConfig = voucherConfig
        )
        if (intent != null) {
            context.startActivity(Intent.createChooser(intent, "Compartir Comprobante PDF"))
        }
    }

    fun createShareIntent(
        context: Context,
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        previousBalance: Double,
        remainingBalance: Double,
        clientName: String?,
        clientDocumentOrPhone: String?,
        businessProfile: BusinessProfile? = null,
        voucherConfig: VoucherConfig = VoucherConfig.DEFAULT
    ): Intent? {
        return try {
            val file = generatePdfFile(
                context = context,
                record = record,
                month = month,
                totalMonths = totalMonths,
                scheduledPayment = scheduledPayment,
                interest = interest,
                principal = principal,
                previousBalance = previousBalance,
                remainingBalance = remainingBalance,
                clientName = clientName,
                clientDocumentOrPhone = clientDocumentOrPhone,
                businessProfile = businessProfile,
                voucherConfig = voucherConfig
            )

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Comprobante de Pago - Mes $month - ${clientName ?: "Cliente"}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Adjunto comprobante digital de pago correspondiente a la cuota $month de $totalMonths.\n${businessProfile?.businessName ?: "CreditHome"}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun renderPdfToBitmap(
        context: Context,
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        previousBalance: Double,
        remainingBalance: Double,
        clientName: String?,
        clientDocumentOrPhone: String?,
        businessProfile: BusinessProfile? = null,
        voucherConfig: VoucherConfig = VoucherConfig.DEFAULT
    ): Bitmap? {
        val pdfFile = generatePdfFile(
            context = context,
            record = record,
            month = month,
            totalMonths = totalMonths,
            scheduledPayment = scheduledPayment,
            interest = interest,
            principal = principal,
            previousBalance = previousBalance,
            remainingBalance = remainingBalance,
            clientName = clientName,
            clientDocumentOrPhone = clientDocumentOrPhone,
            businessProfile = businessProfile,
            voucherConfig = voucherConfig
        )

        return try {
            val fileDescriptor = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            val page = pdfRenderer.openPage(0)

            val bitmap = Bitmap.createBitmap(
                page.width * 2,
                page.height * 2,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            pdfRenderer.close()
            fileDescriptor.close()

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createPdfDocument(
        record: PaymentRecord,
        month: Int,
        totalMonths: Int,
        scheduledPayment: Double,
        interest: Double,
        principal: Double,
        previousBalance: Double,
        remainingBalance: Double,
        clientName: String?,
        clientDocumentOrPhone: String?,
        businessProfile: BusinessProfile?,
        voucherConfig: VoucherConfig
    ): PdfDocument {
        val document = PdfDocument()

        val pageWidth = 400
        val pageHeight = 720
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fondo general
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

        // Encabezado con color corporativo
        val headerColor = Color.rgb(30, 58, 138)
        paint.color = headerColor
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 110f, paint)

        // Línea decorativa
        paint.color = Color.rgb(22, 163, 74)
        paint.strokeWidth = 3f
        canvas.drawLine(0f, 110f, pageWidth.toFloat(), 110f, paint)

        // Logotipo o Ícono
        val textLeft = if (voucherConfig.showLogo) 85f else 24f

        if (voucherConfig.showLogo) {
            val bLogoPath = businessProfile?.logoPath ?: ""
            var logoBitmap: Bitmap? = null
            if (bLogoPath.isNotBlank() && File(bLogoPath).exists()) {
                try {
                    logoBitmap = BitmapFactory.decodeFile(bLogoPath)
                } catch (e: Exception) {
                    logoBitmap = null
                }
            }

            if (logoBitmap != null) {
                val destRect = RectF(20f, 20f, 75f, 75f)
                canvas.drawBitmap(logoBitmap, null, destRect, paint)
            } else {
                val logoBg = RectF(20f, 20f, 75f, 75f)
                paint.color = Color.rgb(255, 255, 255)
                canvas.drawRoundRect(logoBg, 10f, 10f, paint)

                paint.color = headerColor
                paint.textSize = 28f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("CH", 47.5f, 57f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
        }

        // Datos del Emisor / Empresa
        val bName = if (voucherConfig.showBusinessName) (businessProfile?.businessName?.ifEmpty { "CreditHome" } ?: "CreditHome") else ""
        paint.color = Color.WHITE
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(bName, textLeft, 38f, paint)

        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.rgb(224, 231, 255)

        val rucText = if (voucherConfig.showRuc && !businessProfile?.ruc.isNullOrBlank()) "RUC: ${businessProfile!!.ruc}" else ""
        val issuerText = if (voucherConfig.showIssuerName && !businessProfile?.issuerName.isNullOrBlank()) "Emisor: ${businessProfile!!.issuerName}" else ""
        val infoLine1 = listOf(rucText, issuerText).filter { it.isNotBlank() }.joinToString(" • ")
        if (infoLine1.isNotBlank()) {
            canvas.drawText(infoLine1, textLeft, 54f, paint)
        }

        val addressText = if (voucherConfig.showAddress && !businessProfile?.address.isNullOrBlank()) businessProfile!!.address else ""
        if (addressText.isNotBlank()) {
            canvas.drawText(addressText, textLeft, 68f, paint)
        }

        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(187, 247, 208)
        canvas.drawText("COMPROBANTE DE PAGO", textLeft, 86f, paint)

        var y = 125f

        // 2. RECIBO DE IDENTIFICACIÓN
        val recBox = RectF(20f, y, pageWidth - 20f, y + 48f)
        paint.color = Color.rgb(243, 244, 246)
        canvas.drawRoundRect(recBox, 8f, 8f, paint)

        val receiptNum = if (record.referenceNumber.isNotBlank()) record.referenceNumber else "N° REC-" + String.format(Locale.US, "%06d", month * 100 + 1)
        paint.color = Color.rgb(17, 24, 39)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(receiptNum, 32f, y + 20f, paint)

        val payDate = record.paymentDate.ifEmpty { "Fecha de Registro" }
        paint.color = Color.rgb(75, 85, 99)
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Fecha: $payDate", 32f, y + 36f, paint)

        // Badge Pagado
        val badge = RectF(pageWidth - 110f, y + 10f, pageWidth - 30f, y + 38f)
        paint.color = Color.rgb(220, 252, 231)
        canvas.drawRoundRect(badge, 6f, 6f, paint)

        paint.color = Color.rgb(22, 101, 52)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PAGADO", badge.centerX(), badge.centerY() + 3.5f, paint)
        paint.textAlign = Paint.Align.LEFT

        y += 58f

        // 3. SECCIÓN DATOS DEL CLIENTE
        val clientBox = RectF(20f, y, pageWidth - 20f, y + 56f)
        paint.color = Color.rgb(249, 250, 251)
        canvas.drawRoundRect(clientBox, 8f, 8f, paint)

        // Borde suave
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(229, 231, 235)
        canvas.drawRoundRect(clientBox, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(107, 114, 128)
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DATOS DEL CLIENTE / DEUDOR", 32f, y + 15f, paint)

        paint.color = Color.rgb(17, 24, 39)
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(clientName?.ifEmpty { "Cliente General" } ?: "Cliente General", 32f, y + 32f, paint)

        paint.color = Color.rgb(75, 85, 99)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val docText = if (voucherConfig.showClientDocOrPhone && !clientDocumentOrPhone.isNullOrBlank()) "Doc/Tel: $clientDocumentOrPhone" else "Cuota Mes $month de $totalMonths"
        canvas.drawText(docText, 32f, y + 46f, paint)

        y += 66f

        // 4. SECCIÓN DESGLOSE FINANCIERO DE LA CUOTA
        val amountPaid = if (record.amountPaid > 0) record.amountPaid else scheduledPayment
        val extraPayment = (amountPaid - scheduledPayment).coerceAtLeast(0.0)

        val finRows = mutableListOf<Triple<String, String, Boolean>>()
        finRows.add(Triple("Cuota:", "Mes $month de $totalMonths", true))
        if (voucherConfig.showPreviousBalance && previousBalance > 0.01) {
            finRows.add(Triple("Saldo Anterior:", formatCurrency(previousBalance), false))
        }
        if (voucherConfig.showScheduledPayment) {
            finRows.add(Triple("Cuota Prevista:", formatCurrency(scheduledPayment), false))
        }
        if (voucherConfig.showPrincipal) {
            finRows.add(Triple("Amortización a Capital:", formatCurrency(principal), false))
        }
        if (voucherConfig.showInterest) {
            finRows.add(Triple("Interés del Período:", formatCurrency(interest), false))
        }
        if (voucherConfig.showExtraPayment && extraPayment > 0.01) {
            finRows.add(Triple("Abono Extraordinario a Capital:", "+ ${formatCurrency(extraPayment)}", true))
        }
        if (voucherConfig.showRemainingBalance) {
            finRows.add(Triple("Nuevo Saldo Pendiente:", formatCurrency(remainingBalance), true))
        }

        val detailHeight = 45f + (finRows.size * 16f) + 28f
        val detailBox = RectF(20f, y, pageWidth - 20f, y + detailHeight)
        paint.color = Color.rgb(249, 250, 251)
        canvas.drawRoundRect(detailBox, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(229, 231, 235)
        canvas.drawRoundRect(detailBox, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(30, 58, 138)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DETALLE Y DESGLOSE FINANCIERO", 32f, y + 18f, paint)

        var rowY = y + 36f
        for ((lbl, v, isBold) in finRows) {
            paint.color = Color.rgb(107, 114, 128)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(lbl, 32f, rowY, paint)

            paint.color = if (lbl.startsWith("Abono Extra")) Color.rgb(22, 163, 74) else if (lbl.startsWith("Nuevo Saldo")) Color.rgb(30, 58, 138) else Color.rgb(31, 41, 55)
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(v, pageWidth - 32f, rowY, paint)
            paint.textAlign = Paint.Align.LEFT
            rowY += 16f
        }

        // Línea divisoria y TOTAL PAGADO
        paint.color = Color.rgb(229, 231, 235)
        canvas.drawLine(32f, rowY - 2f, pageWidth - 32f, rowY - 2f, paint)
        rowY += 13f

        paint.color = Color.rgb(17, 24, 39)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL PAGADO:", 32f, rowY, paint)

        paint.color = Color.rgb(22, 163, 74)
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatCurrency(amountPaid), pageWidth - 32f, rowY, paint)
        paint.textAlign = Paint.Align.LEFT

        y += detailHeight + 10f

        // 5. SECCIÓN SOPORTE DE LA TRANSACCIÓN BANCARIA
        val transRows = mutableListOf<Pair<String, String>>()
        if (voucherConfig.showPaymentMethod) {
            transRows.add("Medio de Pago:" to record.paymentMethod.ifEmpty { "Transferencia" })
        }
        if (voucherConfig.showBankEntity && record.bankOrEntity.isNotBlank()) {
            transRows.add("Banco / Entidad:" to record.bankOrEntity)
        }
        if (voucherConfig.showReferenceNumber && record.referenceNumber.isNotBlank()) {
            transRows.add("N° Referencia / Comp:" to record.referenceNumber)
        }
        if (voucherConfig.showPayerName && record.payerName.isNotBlank()) {
            transRows.add("Titular / Depositante:" to record.payerName)
        }
        if (voucherConfig.showNotes && record.note.isNotBlank()) {
            transRows.add("Observaciones:" to record.note)
        }

        // Custom fields
        val activeCustomFields = voucherConfig.customFields.filter { it.isEnabled }
        activeCustomFields.forEach { cf ->
            transRows.add(cf.label + ":" to cf.value)
        }

        if (transRows.isNotEmpty()) {
            val transHeight = 32f + (transRows.size * 16f)
            val transBox = RectF(20f, y, pageWidth - 20f, y + transHeight)
            paint.color = Color.rgb(243, 244, 246)
            canvas.drawRoundRect(transBox, 8f, 8f, paint)

            paint.color = Color.rgb(30, 58, 138)
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("SOPORTE DE LA TRANSACCIÓN", 32f, y + 18f, paint)

            var tRowY = y + 34f
            for ((lbl, v) in transRows) {
                paint.color = Color.rgb(107, 114, 128)
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(lbl, 32f, tRowY, paint)

                paint.color = Color.rgb(31, 41, 55)
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(v, pageWidth - 32f, tRowY, paint)
                paint.textAlign = Paint.Align.LEFT
                tRowY += 16f
            }
            y += transHeight + 10f
        }

        // 6. FIRMA Y PIE DE PÁGINA
        if (voucherConfig.showSignature) {
            val isElectronicP12 = record.signatureType == "ELECTRONIC_P12"
            y = (y + 10f).coerceAtLeast(pageHeight - if (isElectronicP12) 130f else 110f)

            val sigPath = record.signaturePath.removePrefix("file://")
            var hasDrawnSig = false
            if (sigPath.isNotBlank()) {
                val sigFile = File(sigPath)
                if (sigFile.exists()) {
                    try {
                        val sigBitmap = com.alfas.amortizacioncredito.domain.security.SignatureSecurityVault.loadSignatureBitmap(sigFile.absolutePath)
                        if (sigBitmap != null && sigBitmap.width > 0 && sigBitmap.height > 0) {
                            val maxSigWidth = if (isElectronicP12) 270f else 130f
                            val maxSigHeight = if (isElectronicP12) 76f else 36f
                            val origW = sigBitmap.width.toFloat()
                            val origH = sigBitmap.height.toFloat()
                            val scale = minOf(maxSigWidth / origW, maxSigHeight / origH)
                            val destW = origW * scale
                            val destH = origH * scale
                            val destLeft = (pageWidth - destW) / 2f
                            val destTop = if (isElectronicP12) y else (y + 24f) - destH
                            val destRect = RectF(destLeft, destTop, destLeft + destW, destTop + destH)
                            canvas.drawBitmap(sigBitmap, null, destRect, paint)
                            hasDrawnSig = true
                            if (isElectronicP12) {
                                y += destH + 4f
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (!isElectronicP12) {
                paint.color = Color.rgb(209, 213, 219)
                canvas.drawLine(pageWidth / 2f - 75f, y + 25f, pageWidth / 2f + 75f, y + 25f, paint)

                paint.textAlign = Paint.Align.CENTER
                paint.color = Color.rgb(107, 114, 128)
                paint.textSize = 8f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Firma / Validación Autorizada", pageWidth / 2f, y + 36f, paint)

                val signerDisplayName = when {
                    record.signedBy.isNotBlank() -> record.signedBy
                    voucherConfig.showIssuerName && !businessProfile?.issuerName.isNullOrBlank() -> businessProfile!!.issuerName
                    else -> ""
                }
                if (signerDisplayName.isNotBlank()) {
                    paint.color = Color.rgb(31, 41, 55)
                    paint.textSize = 8.5f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(signerDisplayName, pageWidth / 2f, y + 48f, paint)
                }
            }
        } else {
            y = (y + 10f).coerceAtLeast(pageHeight - 60f)
        }

        if (voucherConfig.showFooterNote) {
            val customFoot = voucherConfig.customFooterText.ifBlank {
                val footerContact = listOfNotNull(
                    bName.takeIf { it.isNotBlank() },
                    businessProfile?.phone?.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
                    businessProfile?.email?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (footerContact.isNotBlank()) footerContact else "Documento digital emitido por $bName"
            }

            paint.color = Color.rgb(156, 163, 175)
            paint.textSize = 7.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(customFoot, pageWidth / 2f, pageHeight - 20f, paint)
        }

        // Pie de página fijo de CreditHome
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 6.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Generado en CreditHome", pageWidth / 2f, pageHeight - 9f, paint)

        document.finishPage(page)
        return document
    }
}
