package com.alfas.amortizacioncredito.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.alfas.amortizacioncredito.ui.AmortizationUiState
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportAmortizationToExcelUseCase {

    private class SharedStringsTable {
        private val stringMap = mutableMapOf<String, Int>()
        private val stringList = mutableListOf<String>()

        fun getOrAdd(text: String): Int {
            return stringMap.getOrPut(text) {
                val index = stringList.size
                stringList.add(text)
                index
            }
        }

        fun toXml(): String {
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${stringList.size}" uniqueCount="${stringList.size}">
""")
            for (str in stringList) {
                sb.append("  <si><t>").append(escapeXml(str)).append("</t></si>\n")
            }
            sb.append("</sst>")
            return sb.toString()
        }

        private fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }
    }

    fun exportToStream(
        uiState: AmortizationUiState,
        outputStream: OutputStream
    ) {
        val summary = uiState.summary ?: return
        val zip = ZipOutputStream(outputStream)
        val sst = SharedStringsTable()
        val hideInterest = uiState.hideInterest
        val paymentRecords = uiState.paymentRecords
        val activeClient = uiState.activeClient

        fun addZipEntry(path: String, content: String) {
            val entry = ZipEntry(path)
            zip.putNextEntry(entry)
            zip.write(content.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        // 1. [Content_Types].xml
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""
        addZipEntry("[Content_Types].xml", contentTypes)

        // 2. _rels/.rels
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        addZipEntry("_rels/.rels", rootRels)

        // 3. xl/_rels/workbook.xml.rels
        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""
        addZipEntry("xl/_rels/workbook.xml.rels", wbRels)

        // 4. xl/workbook.xml
        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Amortizacion" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
        addZipEntry("xl/workbook.xml", workbook)

        // 5. xl/styles.xml
        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0.00"/>
    <numFmt numFmtId="165" formatCode="0.00%"/>
  </numFmts>
  <fonts count="7">
    <!-- 0: Regular -->
    <font><sz val="10"/><color rgb="FF1E293B"/><name val="Calibri"/></font>
    <!-- 1: Title -->
    <font><b/><sz val="14"/><color rgb="FF1E3A8A"/><name val="Calibri"/></font>
    <!-- 2: Param Label -->
    <font><b/><sz val="10"/><color rgb="FF334155"/><name val="Calibri"/></font>
    <!-- 3: Table Header (White Bold) -->
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <!-- 4: Bold Totals/Values -->
    <font><b/><sz val="10"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <!-- 5: Paid Green -->
    <font><b/><sz val="9"/><color rgb="FF15803D"/><name val="Calibri"/></font>
    <!-- 6: Pending Gray -->
    <font><sz val="9"/><color rgb="FF64748B"/><name val="Calibri"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <!-- 2: Dark Blue Header #1B365D -->
    <fill><patternFill patternType="solid"><fgColor rgb="FF1B365D"/></patternFill></fill>
    <!-- 3: Soft Gray Even Row #F8FAFC -->
    <fill><patternFill patternType="solid"><fgColor rgb="FFF8FAFC"/></patternFill></fill>
    <!-- 4: Footer Background #E2E8F0 -->
    <fill><patternFill patternType="solid"><fgColor rgb="FFE2E8F0"/></patternFill></fill>
    <!-- 5: Soft Green Paid Row #F0FDF4 -->
    <fill><patternFill patternType="solid"><fgColor rgb="FFF0FDF4"/></patternFill></fill>
  </fills>
  <borders count="4">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <!-- 1: Thin data border -->
    <border>
      <left style="thin"><color rgb="FFE2E8F0"/></left>
      <right style="thin"><color rgb="FFE2E8F0"/></right>
      <top style="thin"><color rgb="FFE2E8F0"/></top>
      <bottom style="thin"><color rgb="FFE2E8F0"/></bottom>
    </border>
    <!-- 2: Header border -->
    <border>
      <left style="thin"><color rgb="FF1B365D"/></left>
      <right style="thin"><color rgb="FF1B365D"/></right>
      <top style="medium"><color rgb="FF1B365D"/></top>
      <bottom style="medium"><color rgb="FF1B365D"/></bottom>
    </border>
    <!-- 3: Footer border -->
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="medium"><color rgb="FF94A3B8"/></top>
      <bottom style="double"><color rgb="FF475569"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="16">
    <!-- 0: Default -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <!-- 1: Title -->
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment vertical="center"/></xf>
    <!-- 2: Summary Label -->
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment vertical="center"/></xf>
    <!-- 3: Summary Currency -->
    <xf numFmtId="164" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 4: Summary Percent -->
    <xf numFmtId="165" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 5: Summary Number -->
    <xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 6: Table Header -->
    <xf numFmtId="0" fontId="3" fillId="2" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <!-- 7: Data Month Odd -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <!-- 8: Data Month Even -->
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <!-- 9: Data Currency Odd -->
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 10: Data Currency Even -->
    <xf numFmtId="164" fontId="0" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 11: Footer Label -->
    <xf numFmtId="0" fontId="4" fillId="4" borderId="3" xfId="0" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <!-- 12: Footer Currency -->
    <xf numFmtId="164" fontId="4" fillId="4" borderId="3" xfId="0" applyFont="1" applyNumberFormat="1"><alignment horizontal="right" vertical="center"/></xf>
    <!-- 13: Paid Status Cell (Green bg, bold) -->
    <xf numFmtId="0" fontId="5" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <!-- 14: Pending Status Cell -->
    <xf numFmtId="0" fontId="6" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <!-- 15: Notes Text Cell -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"><alignment horizontal="left" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
        addZipEntry("xl/styles.xml", styles)

        // 6. Generar xl/worksheets/sheet1.xml
        val sb = StringBuilder()

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="18" customWidth="1"/>
    <col min="3" max="3" width="20" customWidth="1"/>
""")
        if (!hideInterest) {
            sb.append("""    <col min="4" max="4" width="18" customWidth="1"/>
    <col min="5" max="5" width="20" customWidth="1"/>
    <col min="6" max="6" width="22" customWidth="1"/>
    <col min="7" max="7" width="30" customWidth="1"/>
""")
        } else {
            sb.append("""    <col min="4" max="4" width="22" customWidth="1"/>
    <col min="5" max="5" width="30" customWidth="1"/>
""")
        }
        sb.append("""  </cols>
  <sheetData>
""")

        var r = 1

        fun cellSharedStr(col: String, row: Int, style: Int, text: String): String {
            val idx = sst.getOrAdd(text)
            return """<c r="$col$row" s="$style" t="s"><v>$idx</v></c>"""
        }

        fun cellNum(col: String, row: Int, style: Int, num: Double): String {
            return """<c r="$col$row" s="$style"><v>${String.format(Locale.US, "%.2f", num)}</v></c>"""
        }

        fun cellInt(col: String, row: Int, style: Int, num: Int): String {
            return """<c r="$col$row" s="$style"><v>$num</v></c>"""
        }

        // Fila 1: Título Principal
        sb.append("""    <row r="$r" ht="28" customHeight="1">
      ${cellSharedStr("A", r, 1, "TABLA DE AMORTIZACIÓN DE CRÉDITO")}
    </row>
""")
        r += 2 // Deja fila 2 vacía

        // Datos del cliente si existe
        if (activeClient != null) {
            sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Cliente:")}
      ${cellSharedStr("B", r, 5, activeClient.name)}
    </row>
""")
            r++
            if (activeClient.documentNumber.isNotEmpty()) {
                sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Cédula / RUC:")}
      ${cellSharedStr("B", r, 5, activeClient.documentNumber)}
    </row>
""")
                r++
            }
            if (activeClient.phone.isNotEmpty()) {
                sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Teléfono / Celular:")}
      ${cellSharedStr("B", r, 5, activeClient.phone)}
    </row>
""")
                r++
            } else if (activeClient.documentOrPhone.isNotEmpty() && activeClient.documentNumber.isEmpty()) {
                sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Contacto / Doc:")}
      ${cellSharedStr("B", r, 5, activeClient.documentOrPhone)}
    </row>
""")
                r++
            }
            if (activeClient.address.isNotEmpty()) {
                sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Dirección Fiscal:")}
      ${cellSharedStr("B", r, 5, activeClient.address)}
    </row>
""")
                r++
            }
            if (activeClient.email.isNotEmpty()) {
                sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Correo Electrónico:")}
      ${cellSharedStr("B", r, 5, activeClient.email)}
    </row>
""")
                r++
            }
            if (activeClient.notes.isNotEmpty()) {
                sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, "Observaciones:")}
      ${cellSharedStr("B", r, 5, activeClient.notes)}
    </row>
""")
                r++
            }
        }

        val price = uiState.priceInput.toDoubleOrNull() ?: 0.0
        val downPayment = uiState.downPaymentInput.toDoubleOrNull() ?: 0.0
        val rate = (uiState.annualRateInput.toDoubleOrNull() ?: 0.0) / 100.0
        val years = uiState.yearsInput.toDoubleOrNull() ?: 0.0

        // Parámetros del Crédito
        fun addParamRow(label: String, valueXml: String) {
            sb.append("""    <row r="$r" ht="19" customHeight="1">
      ${cellSharedStr("A", r, 2, label)}
      $valueXml
    </row>
""")
            r++
        }

        if (uiState.startDateInput.isNotEmpty() || (activeClient != null && activeClient.startDate.isNotEmpty())) {
            val startDateVal = activeClient?.startDate?.ifEmpty { uiState.startDateInput } ?: uiState.startDateInput
            addParamRow("Inicio / 1er Pago:", cellSharedStr("B", r, 5, startDateVal))
        }

        addParamRow("Precio Total:", cellNum("B", r, 3, price))
        addParamRow("Entrada Inicial:", cellNum("B", r, 3, downPayment))
        addParamRow("Monto Financiado:", cellNum("B", r, 3, summary.financedAmount))
        addParamRow("Sistema:", cellSharedStr("B", r, 5, if (uiState.isCompoundInterest) "Interés Compuesto Directo" else "Sistema Francés"))
        if (!hideInterest) {
            addParamRow("Tasa Anual:", cellNum("B", r, 4, rate))
        }
        addParamRow(
            "Plazo Pactado (Años):",
            if (years % 1.0 == 0.0) cellInt("B", r, 5, years.toInt()) else cellNum("B", r, 5, years)
        )
        addParamRow("Cuota Mensual:", cellNum("B", r, 3, summary.monthlyPayment))

        if (summary.monthsSaved > 0) {
            addParamRow("Meses Efectivos:", cellInt("B", r, 5, summary.actualMonths))
            addParamRow("Cuotas Ahorradas:", cellInt("B", r, 5, summary.monthsSaved))
        }

        if (!hideInterest) {
            addParamRow("Interés Total:", cellNum("B", r, 3, summary.totalInterest))
            if (summary.interestSaved > 0.0) {
                addParamRow("Interés Ahorrado:", cellNum("B", r, 3, summary.interestSaved))
            }
        }
        addParamRow("Costo Total:", cellNum("B", r, 3, summary.totalCost))

        // Conteo de pagos
        val paidCount = paymentRecords.values.count { it.isPaid }
        addParamRow("Cuotas Pagadas:", cellSharedStr("B", r, 5, "$paidCount de ${summary.actualMonths}"))

        r++ // Fila vacía separadora

        // Encabezados de la Tabla
        sb.append("""    <row r="$r" ht="25" customHeight="1">
      ${cellSharedStr("A", r, 6, "Mes")}
      ${cellSharedStr("B", r, 6, "Fecha Límite")}
      ${cellSharedStr("C", r, 6, "Fecha Pagado")}
      ${cellSharedStr("D", r, 6, "Cuota")}
""")
        if (hideInterest) {
            sb.append("""      ${cellSharedStr("E", r, 6, "Saldo Restante")}
      ${cellSharedStr("F", r, 6, "Estado del Pago")}
      ${cellSharedStr("G", r, 6, "Observaciones / Voucher")}
""")
        } else {
            sb.append("""      ${cellSharedStr("E", r, 6, "Interés")}
      ${cellSharedStr("F", r, 6, "Capital")}
      ${cellSharedStr("G", r, 6, "Saldo Restante")}
      ${cellSharedStr("H", r, 6, "Estado del Pago")}
      ${cellSharedStr("I", r, 6, "Observaciones / Voucher")}
""")
        }
        sb.append("    </row>\n")
        r++

        // Filas de Amortización Mensual
        summary.schedule.forEachIndexed { index, item ->
            val isEven = (index % 2 == 0)
            val record = paymentRecords[item.month]
            val isPaid = record?.isPaid == true

            val monthStyle = if (isEven) 8 else 7
            val numStyle = if (isEven) 10 else 9
            val textStyle = if (isEven) 8 else 7
            val statusStyle = if (isPaid) 13 else 14
            val statusText = if (isPaid) "PAGADO" else "PENDIENTE"
            val payDateText = if (isPaid && !record?.paymentDate.isNullOrBlank()) record!!.paymentDate.substringBefore(" ") else "-"

            val noteComponents = mutableListOf<String>()
            if (record != null) {
                if (record.isPaid) {
                    val voucherParts = mutableListOf<String>()
                    if (record.paymentMethod.isNotBlank()) voucherParts.add(record.paymentMethod)
                    if (record.referenceNumber.isNotBlank()) voucherParts.add("#${record.referenceNumber}")
                    if (record.bankOrEntity.isNotBlank()) voucherParts.add(record.bankOrEntity)
                    if (voucherParts.isNotEmpty()) {
                        noteComponents.add("[${voucherParts.joinToString(" - ")}]")
                    }
                    if (record.amountPaid > 0 && Math.abs(record.amountPaid - item.payment) > 0.01) {
                        noteComponents.add("Abonó: $${String.format(Locale.US, "%.2f", record.amountPaid)}")
                    }
                }
                if (record.note.isNotBlank()) {
                    noteComponents.add(record.note.trim())
                }
            }
            val noteText = noteComponents.joinToString(" | ")

            sb.append("""    <row r="$r" ht="18" customHeight="1">
      ${cellInt("A", r, monthStyle, item.month)}
      ${cellSharedStr("B", r, textStyle, if (item.dueDate.isNotBlank()) item.dueDate else "-")}
      ${cellSharedStr("C", r, textStyle, payDateText)}
      ${cellNum("D", r, numStyle, item.payment)}
""")
            if (hideInterest) {
                sb.append("""      ${cellNum("E", r, numStyle, item.remainingBalance)}
      ${cellSharedStr("F", r, statusStyle, statusText)}
      ${cellSharedStr("G", r, 15, noteText)}
""")
            } else {
                sb.append("""      ${cellNum("E", r, numStyle, item.interest)}
      ${cellNum("F", r, numStyle, item.principal)}
      ${cellNum("G", r, numStyle, item.remainingBalance)}
      ${cellSharedStr("H", r, statusStyle, statusText)}
      ${cellSharedStr("I", r, 15, noteText)}
""")
            }
            sb.append("    </row>\n")
            r++
        }

        // Fila de Totales
        sb.append("""    <row r="$r" ht="24" customHeight="1">
      ${cellSharedStr("A", r, 11, "Totales")}
      ${cellSharedStr("B", r, 11, "")}
      ${cellSharedStr("C", r, 11, "")}
      ${cellNum("D", r, 12, summary.totalPaid)}
""")
        if (hideInterest) {
            sb.append("""      ${cellNum("E", r, 12, 0.0)}
      ${cellSharedStr("F", r, 11, "$paidCount Pagadas")}
      ${cellSharedStr("G", r, 11, "")}
""")
        } else {
            sb.append("""      ${cellNum("E", r, 12, summary.totalInterest)}
      ${cellNum("F", r, 12, summary.totalPrincipalPaid)}
      ${cellNum("G", r, 12, 0.0)}
      ${cellSharedStr("H", r, 11, "$paidCount Pagadas")}
      ${cellSharedStr("I", r, 11, "")}
""")
        }
        sb.append("    </row>\n")

        val endCol = if (hideInterest) "G" else "I"
        sb.append("""  </sheetData>
  <mergeCells count="1">
    <mergeCell ref="A1:${endCol}1"/>
  </mergeCells>
</worksheet>""")

        addZipEntry("xl/worksheets/sheet1.xml", sb.toString())

        // 7. xl/sharedStrings.xml
        addZipEntry("xl/sharedStrings.xml", sst.toXml())

        zip.finish()
        zip.flush()
    }

    fun createShareIntent(context: Context, uiState: AmortizationUiState): Intent? {
        if (uiState.summary == null) return null
        return try {
            val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val clientPrefix = uiState.activeClient?.name?.replace(" ", "_") ?: "Amortizacion"
            val file = File(cacheDir, "${clientPrefix}_${System.currentTimeMillis()}.xlsx")
            FileOutputStream(file).use { fos ->
                exportToStream(uiState, fos)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val clientLabel = if (uiState.activeClient != null) " - Cliente: ${uiState.activeClient.name}" else ""
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tabla de Amortización$clientLabel")
                putExtra(Intent.EXTRA_TEXT, "Adjunto reporte y trazabilidad de pagos de amortización en formato Excel (.xlsx)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
