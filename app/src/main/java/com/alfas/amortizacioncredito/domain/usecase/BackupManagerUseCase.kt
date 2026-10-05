package com.alfas.amortizacioncredito.domain.usecase

import android.content.Context
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import com.alfas.amortizacioncredito.data.repository.BusinessProfileRepository
import com.alfas.amortizacioncredito.data.repository.ClientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.domain.security.SecurityManager

data class BackupManifestInfo(
    val version: Int = 1,
    val appName: String = "CreditHome",
    val exportDate: String = "",
    val totalClients: Int = 0,
    val totalPayments: Int = 0,
    val businessName: String = "",
    val issuerName: String = ""
)

data class BackupRestoreResult(
    val success: Boolean,
    val message: String,
    val restoredClients: Int = 0,
    val restoredReceipts: Int = 0
)

class BackupManagerUseCase(private val context: Context) {

    private val clientRepository = ClientRepository(context)
    private val businessProfileRepository = BusinessProfileRepository(context)
    private val securityManager = SecurityManager(context)

    suspend fun exportBackup(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val clients = clientRepository.getAllClients()
            val profile = businessProfileRepository.getProfile()
            val totalPayments = clients.sumOf { it.paymentRecords.size }

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date())

            val manifestObj = JSONObject().apply {
                put("version", 2) // Versión 2 con cifrado de bóveda AES-256-GCM
                put("encrypted", true)
                put("appName", "CreditHome")
                put("exportTimestamp", System.currentTimeMillis())
                put("exportDate", dateStr)
                put("totalClients", clients.size)
                put("totalPayments", totalPayments)
                put("businessName", profile.businessName)
                put("issuerName", profile.issuerName)
            }

            val profileObj = JSONObject().apply {
                put("businessName", profile.businessName)
                put("issuerName", profile.issuerName)
                put("ruc", profile.ruc)
                put("address", profile.address)
                put("phone", profile.phone)
                put("email", profile.email)
            }

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // 1. Manifest
                zipOut.putNextEntry(ZipEntry("manifest.json"))
                zipOut.write(manifestObj.toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Business Profile Cifrado con AES-256-GCM
                val encryptedProfile = securityManager.encryptString(profileObj.toString(2))
                zipOut.putNextEntry(ZipEntry("business_profile.json"))
                zipOut.write(encryptedProfile.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 3. Clientes Cifrados con AES-256-GCM (clients_portfolio.json)
                val clientsFile = File(context.filesDir, "clients_portfolio.json")
                if (clientsFile.exists()) {
                    val rawClientsJson = clientsFile.readText(Charsets.UTF_8)
                    val encryptedClients = securityManager.encryptString(rawClientsJson)
                    zipOut.putNextEntry(ZipEntry("clients_portfolio.json"))
                    zipOut.write(encryptedClients.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                // 4. Logo de la Empresa si existe
                val logoFile = File(context.filesDir, "business_logo.png")
                if (logoFile.exists()) {
                    zipOut.putNextEntry(ZipEntry("images/business_logo.png"))
                    logoFile.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }

                // 5. Fotos de Comprobantes Bancarios en filesDir/receipts
                val receiptsDir = File(context.filesDir, "receipts")
                if (receiptsDir.exists() && receiptsDir.isDirectory) {
                    val files = receiptsDir.listFiles() ?: emptyArray()
                    for (file in files) {
                        if (file.isFile && file.length() > 0) {
                            zipOut.putNextEntry(ZipEntry("images/receipts/${file.name}"))
                            file.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }

                // 6. Firmas Guardadas y Archivos de Firma Cifrados
                val signaturesFile = File(context.filesDir, "saved_signatures.json")
                if (signaturesFile.exists() && signaturesFile.length() > 0) {
                    val rawSigs = signaturesFile.readText(Charsets.UTF_8)
                    val encryptedSigs = securityManager.encryptString(rawSigs)
                    zipOut.putNextEntry(ZipEntry("saved_signatures.json"))
                    zipOut.write(encryptedSigs.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                val secureSigsDir = File(context.filesDir, "secure_signatures")
                if (secureSigsDir.exists() && secureSigsDir.isDirectory) {
                    val sigFiles = secureSigsDir.listFiles() ?: emptyArray()
                    for (sigFile in sigFiles) {
                        if (sigFile.isFile && sigFile.length() > 0) {
                            zipOut.putNextEntry(ZipEntry("signatures/files/${sigFile.name}"))
                            sigFile.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }

                // 7. Usuarios y Roles Cifrados
                val usersFile = File(context.filesDir, "company_users.json")
                if (usersFile.exists() && usersFile.length() > 0) {
                    val rawUsers = usersFile.readText(Charsets.UTF_8)
                    val encryptedUsers = securityManager.encryptString(rawUsers)
                    zipOut.putNextEntry(ZipEntry("company_users.json"))
                    zipOut.write(encryptedUsers.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                // 8. Registro de Auditoría Cifrado
                val auditFile = File(context.filesDir, "company_audit_log.json")
                if (auditFile.exists() && auditFile.length() > 0) {
                    val rawAudit = auditFile.readText(Charsets.UTF_8)
                    val encryptedAudit = securityManager.encryptString(rawAudit)
                    zipOut.putNextEntry(ZipEntry("company_audit_log.json"))
                    zipOut.write(encryptedAudit.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun inspectBackup(inputStream: InputStream): BackupManifestInfo? = withContext(Dispatchers.IO) {
        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.json") {
                        val content = zipIn.bufferedReader(Charsets.UTF_8).readText()
                        val obj = JSONObject(content)
                        return@withContext BackupManifestInfo(
                            version = obj.optInt("version", 1),
                            appName = obj.optString("appName", "CreditHome"),
                            exportDate = obj.optString("exportDate", ""),
                            totalClients = obj.optInt("totalClients", 0),
                            totalPayments = obj.optInt("totalPayments", 0),
                            businessName = obj.optString("businessName", ""),
                            issuerName = obj.optString("issuerName", "")
                        )
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun restoreBackup(
        inputStream: InputStream,
        mergeWithExisting: Boolean = false
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            var restoredClientsCount = 0
            var restoredReceiptsCount = 0
            var extractedClientsJson: String? = null

            val receiptsDir = File(context.filesDir, "receipts")
            if (!receiptsDir.exists()) receiptsDir.mkdirs()

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "clients_portfolio.json" -> {
                            val rawContent = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            extractedClientsJson = securityManager.decryptString(rawContent)
                        }
                        name == "business_profile.json" -> {
                            val rawContent = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            val jsonStr = securityManager.decryptString(rawContent)
                            val obj = JSONObject(jsonStr)
                            val profile = BusinessProfile(
                                businessName = obj.optString("businessName", "CreditHome"),
                                issuerName = obj.optString("issuerName", ""),
                                ruc = obj.optString("ruc", ""),
                                address = obj.optString("address", ""),
                                phone = obj.optString("phone", ""),
                                email = obj.optString("email", ""),
                                logoPath = File(context.filesDir, "business_logo.png").absolutePath
                            )
                            businessProfileRepository.saveProfile(profile)
                        }
                        name == "images/business_logo.png" -> {
                            val logoFile = File(context.filesDir, "business_logo.png")
                            FileOutputStream(logoFile).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                        name.startsWith("images/receipts/") -> {
                            val fileName = name.substringAfterLast("/")
                            if (fileName.isNotBlank()) {
                                val destFile = File(receiptsDir, fileName)
                                // Protección contra Zip Slip / Path Traversal
                                if (destFile.canonicalPath.startsWith(receiptsDir.canonicalPath)) {
                                    FileOutputStream(destFile).use { out ->
                                        zipIn.copyTo(out)
                                    }
                                    restoredReceiptsCount++
                                }
                            }
                        }
                        name == "saved_signatures.json" -> {
                            val rawContent = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            val decrypted = securityManager.decryptString(rawContent)
                            try {
                                val sigArray = org.json.JSONArray(decrypted)
                                val currentSecureDir = File(context.filesDir, "secure_signatures")
                                val adjustedArray = org.json.JSONArray()
                                for (k in 0 until sigArray.length()) {
                                    val sObj = sigArray.getJSONObject(k)
                                    val oldPath = sObj.optString("filePath", "")
                                    val fileName = oldPath.substringAfterLast("/").substringAfterLast("\\")
                                    if (fileName.isNotBlank()) {
                                        sObj.put("filePath", File(currentSecureDir, fileName).absolutePath)
                                    }
                                    adjustedArray.put(sObj)
                                }
                                val target = File(context.filesDir, "saved_signatures.json")
                                target.writeText(adjustedArray.toString(2), Charsets.UTF_8)
                            } catch (_: Exception) {
                                val target = File(context.filesDir, "saved_signatures.json")
                                target.writeText(decrypted, Charsets.UTF_8)
                            }
                        }
                        name.startsWith("signatures/files/") -> {
                            val fileName = name.substringAfterLast("/")
                            if (fileName.isNotBlank()) {
                                val secureSigsDir = File(context.filesDir, "secure_signatures")
                                if (!secureSigsDir.exists()) secureSigsDir.mkdirs()
                                val destFile = File(secureSigsDir, fileName)
                                if (destFile.canonicalPath.startsWith(secureSigsDir.canonicalPath)) {
                                    FileOutputStream(destFile).use { out ->
                                        zipIn.copyTo(out)
                                    }
                                }
                            }
                        }
                        name == "company_users.json" -> {
                            val rawContent = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            val decrypted = securityManager.decryptString(rawContent)
                            val target = File(context.filesDir, "company_users.json")
                            target.writeText(decrypted, Charsets.UTF_8)
                        }
                        name == "company_audit_log.json" -> {
                            val rawContent = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            val decrypted = securityManager.decryptString(rawContent)
                            val target = File(context.filesDir, "company_audit_log.json")
                            target.writeText(decrypted, Charsets.UTF_8)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (extractedClientsJson != null) {
                // Adaptar rutas de comprobantes al almacenamiento del teléfono actual (Cross-Device compatibility)
                val currentReceiptsDirPath = receiptsDir.absolutePath
                val currentClients = if (mergeWithExisting) clientRepository.getAllClients().toMutableList() else mutableListOf()

                val jsonArray = org.json.JSONArray(extractedClientsJson)
                val importedClients = mutableListOf<com.alfas.amortizacioncredito.data.model.ClientProfile>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id")
                    val name = obj.optString("name")
                    val documentNumber = obj.optString("documentNumber", "")
                    val phone = obj.optString("phone", "")
                    val address = obj.optString("address", "")
                    val documentOrPhone = obj.optString("documentOrPhone", "")
                    val email = obj.optString("email", "")
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    val startDate = obj.optString("startDate", "")
                    val price = obj.optString("price", "86000")
                    val downPayment = obj.optString("downPayment", "40000")
                    val annualRate = obj.optString("annualRate", "3.0")
                    val years = obj.optString("years", "5")
                    val isRoundUpEnabled = obj.optBoolean("isRoundUpEnabled", false)
                    val hideInterest = obj.optBoolean("hideInterest", false)
                    val isCompoundInterest = obj.optBoolean("isCompoundInterest", false)
                    val isFixedPriceLoan = obj.optBoolean("isFixedPriceLoan", false)
                    val extraPaymentMonth = obj.optInt("extraPaymentMonth", 0)
                    val extraPaymentAmount = obj.optDouble("extraPaymentAmount", 0.0)
                    val notes = obj.optString("notes", "")

                    val paymentMap = mutableMapOf<Int, com.alfas.amortizacioncredito.data.model.PaymentRecord>()
                    val paymentsArray = obj.optJSONArray("paymentRecords")
                    if (paymentsArray != null) {
                        for (j in 0 until paymentsArray.length()) {
                            val pObj = paymentsArray.getJSONObject(j)
                            val m = pObj.getInt("month")
                            val isPaid = pObj.optBoolean("isPaid", false)
                            val paymentDate = pObj.optString("paymentDate", "")
                            val dueDate = pObj.optString("dueDate", "")
                            val amountPaid = pObj.optDouble("amountPaid", 0.0)
                            val paymentMethod = pObj.optString("paymentMethod", "Transferencia")
                            val referenceNumber = pObj.optString("referenceNumber", "")
                            val bankOrEntity = pObj.optString("bankOrEntity", "")
                            val payerName = pObj.optString("payerName", "")
                            val rawReceiptPath = pObj.optString("receiptImagePath", "")

                            // Reasignar rutas de imágenes al directorio de este teléfono
                            val adjustedReceiptPath = if (rawReceiptPath.isNotBlank()) {
                                val paths = rawReceiptPath.split("|").filter { it.isNotBlank() }
                                paths.joinToString("|") { p ->
                                    val fileName = p.substringAfterLast("/").substringAfterLast("\\")
                                    File(currentReceiptsDirPath, fileName).absolutePath
                                }
                            } else ""

                            val note = pObj.optString("note", "")
                            paymentMap[m] = com.alfas.amortizacioncredito.data.model.PaymentRecord(
                                month = m,
                                isPaid = isPaid,
                                paymentDate = paymentDate,
                                dueDate = dueDate,
                                amountPaid = amountPaid,
                                paymentMethod = paymentMethod,
                                referenceNumber = referenceNumber,
                                bankOrEntity = bankOrEntity,
                                payerName = payerName,
                                receiptImagePath = adjustedReceiptPath,
                                note = note
                            )
                        }
                    }

                    importedClients.add(
                        com.alfas.amortizacioncredito.data.model.ClientProfile(
                            id = id,
                            name = name,
                            documentNumber = documentNumber,
                            phone = phone,
                            address = address,
                            documentOrPhone = documentOrPhone,
                            email = email,
                            createdAt = createdAt,
                            startDate = startDate,
                            price = price,
                            downPayment = downPayment,
                            annualRate = annualRate,
                            years = years,
                            isRoundUpEnabled = isRoundUpEnabled,
                            hideInterest = hideInterest,
                            isCompoundInterest = isCompoundInterest,
                            isFixedPriceLoan = isFixedPriceLoan,
                            extraPaymentMonth = extraPaymentMonth,
                            extraPaymentAmount = extraPaymentAmount,
                            notes = notes,
                            paymentRecords = paymentMap
                        )
                    )
                }

                if (mergeWithExisting) {
                    for (imported in importedClients) {
                        val idx = currentClients.indexOfFirst { it.id == imported.id }
                        if (idx != -1) {
                            currentClients[idx] = imported
                        } else {
                            currentClients.add(imported)
                        }
                    }
                    for (client in currentClients) {
                        clientRepository.saveOrUpdateClient(client)
                    }
                    restoredClientsCount = currentClients.size
                } else {
                    // Sobrescribir todo de forma limpia
                    for (client in clientRepository.getAllClients()) {
                        clientRepository.deleteClient(client.id)
                    }
                    for (client in importedClients) {
                        clientRepository.saveOrUpdateClient(client)
                    }
                    restoredClientsCount = importedClients.size
                }
            }

            BackupRestoreResult(
                success = true,
                message = if (mergeWithExisting) "Respaldo fusionado con éxito" else "Copia de seguridad restaurada exitosamente",
                restoredClients = restoredClientsCount,
                restoredReceipts = restoredReceiptsCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BackupRestoreResult(
                success = false,
                message = "Error al restaurar copia de seguridad: ${e.localizedMessage ?: "Archivo no válido"}"
            )
        }
    }
}
