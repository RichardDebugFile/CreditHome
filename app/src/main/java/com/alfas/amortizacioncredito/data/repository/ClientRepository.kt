package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ClientRepository(context: Context) {

    private val storageFile = File(context.filesDir, "clients_portfolio.json")

    @Synchronized
    fun getAllClients(): List<ClientProfile> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val jsonContent = storageFile.readText()
            val jsonArray = JSONArray(jsonContent)
            val list = mutableListOf<ClientProfile>()

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

                val paymentMap = mutableMapOf<Int, PaymentRecord>()
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
                        val receiptImagePath = pObj.optString("receiptImagePath", "")
                        val note = pObj.optString("note", "")
                        val signaturePath = pObj.optString("signaturePath", "")
                        val signatureType = pObj.optString("signatureType", "")
                        val signedBy = pObj.optString("signedBy", "")
                        val signedAt = pObj.optLong("signedAt", 0L)
                        val signatureDetails = pObj.optString("signatureDetails", "")
                        val emailSentCount = pObj.optInt("emailSentCount", 0)
                        val lastEmailSentAt = pObj.optLong("lastEmailSentAt", 0L)

                        paymentMap[m] = PaymentRecord(
                            month = m,
                            isPaid = isPaid,
                            paymentDate = paymentDate,
                            dueDate = dueDate,
                            amountPaid = amountPaid,
                            paymentMethod = paymentMethod,
                            referenceNumber = referenceNumber,
                            bankOrEntity = bankOrEntity,
                            payerName = payerName,
                            receiptImagePath = receiptImagePath,
                            note = note,
                            signaturePath = signaturePath,
                            signatureType = signatureType,
                            signedBy = signedBy,
                            signedAt = signedAt,
                            signatureDetails = signatureDetails,
                            emailSentCount = emailSentCount,
                            lastEmailSentAt = lastEmailSentAt
                        )
                    }
                }

                list.add(
                    ClientProfile(
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
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun saveOrUpdateClient(client: ClientProfile) {
        val clients = getAllClients().toMutableList()
        val index = clients.indexOfFirst { it.id == client.id }
        if (index != -1) {
            clients[index] = client
        } else {
            clients.add(0, client)
        }
        writeClients(clients)
    }

    @Synchronized
    fun deleteClient(id: String) {
        val clients = getAllClients().filter { it.id != id }
        writeClients(clients)
    }

    @Synchronized
    fun updatePaymentRecord(clientId: String, record: PaymentRecord): ClientProfile? {
        val clients = getAllClients().toMutableList()
        val index = clients.indexOfFirst { it.id == clientId }
        if (index != -1) {
            val current = clients[index]
            val updatedMap = current.paymentRecords.toMutableMap()
            updatedMap[record.month] = record
            val updatedClient = current.copy(paymentRecords = updatedMap)
            clients[index] = updatedClient
            writeClients(clients)
            return updatedClient
        }
        return null
    }

    private fun writeClients(clients: List<ClientProfile>) {
        try {
            val jsonArray = JSONArray()
            for (client in clients) {
                val obj = JSONObject().apply {
                    put("id", client.id)
                    put("name", client.name)
                    put("documentNumber", client.documentNumber)
                    put("phone", client.phone)
                    put("address", client.address)
                    put("documentOrPhone", client.displayDocumentOrPhone)
                    put("email", client.email)
                    put("createdAt", client.createdAt)
                    put("startDate", client.startDate)
                    put("price", client.price)
                    put("downPayment", client.downPayment)
                    put("annualRate", client.annualRate)
                    put("years", client.years)
                    put("isRoundUpEnabled", client.isRoundUpEnabled)
                    put("hideInterest", client.hideInterest)
                    put("isCompoundInterest", client.isCompoundInterest)
                    put("isFixedPriceLoan", client.isFixedPriceLoan)
                    put("extraPaymentMonth", client.extraPaymentMonth)
                    put("extraPaymentAmount", client.extraPaymentAmount)
                    put("notes", client.notes)

                    val pArray = JSONArray()
                    for ((_, p) in client.paymentRecords) {
                        pArray.put(
                            JSONObject().apply {
                                put("month", p.month)
                                put("isPaid", p.isPaid)
                                put("paymentDate", p.paymentDate)
                                put("dueDate", p.dueDate)
                                put("amountPaid", p.amountPaid)
                                put("paymentMethod", p.paymentMethod)
                                put("referenceNumber", p.referenceNumber)
                                put("bankOrEntity", p.bankOrEntity)
                                put("payerName", p.payerName)
                                put("receiptImagePath", p.receiptImagePath)
                                put("note", p.note)
                                put("signaturePath", p.signaturePath)
                                put("signatureType", p.signatureType)
                                put("signedBy", p.signedBy)
                                put("signedAt", p.signedAt)
                                put("signatureDetails", p.signatureDetails)
                                put("emailSentCount", p.emailSentCount)
                                put("lastEmailSentAt", p.lastEmailSentAt)
                            }
                        )
                    }
                    put("paymentRecords", pArray)
                }
                jsonArray.put(obj)
            }
            storageFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
