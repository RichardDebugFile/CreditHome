package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.alfas.amortizacioncredito.data.model.BusinessProfile
import java.io.File
import java.io.FileOutputStream

class BusinessProfileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("business_profile_prefs", Context.MODE_PRIVATE)
    private val logoFile = File(context.filesDir, "business_logo.png")

    fun getProfile(): BusinessProfile {
        val name = prefs.getString("business_name", "CreditHome") ?: "CreditHome"
        val issuerName = prefs.getString("issuer_name", "") ?: ""
        val ruc = prefs.getString("ruc", "") ?: ""
        val address = prefs.getString("address", "") ?: ""
        val phone = prefs.getString("phone", "") ?: ""
        val email = prefs.getString("email", "") ?: ""
        val logoPath = if (logoFile.exists()) logoFile.absolutePath else ""

        return BusinessProfile(
            businessName = name,
            issuerName = issuerName,
            ruc = ruc,
            address = address,
            phone = phone,
            email = email,
            logoPath = logoPath
        )
    }

    fun saveProfile(profile: BusinessProfile) {
        prefs.edit()
            .putString("business_name", profile.businessName)
            .putString("issuer_name", profile.issuerName)
            .putString("ruc", profile.ruc)
            .putString("address", profile.address)
            .putString("phone", profile.phone)
            .putString("email", profile.email)
            .apply()
    }

    fun saveLogo(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                FileOutputStream(logoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
            if (logoFile.exists()) logoFile.absolutePath else ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun removeLogo() {
        if (logoFile.exists()) {
            logoFile.delete()
        }
    }
}
