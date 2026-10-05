package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.alfas.amortizacioncredito.data.model.SmtpConfig
import org.json.JSONArray

class SmtpRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("smtp_settings", Context.MODE_PRIVATE)

    fun getConfig(): SmtpConfig {
        val host = prefs.getString("host", "smtp.gmail.com") ?: "smtp.gmail.com"
        val port = prefs.getInt("port", 465)
        val senderEmail = prefs.getString("senderEmail", "") ?: ""
        val senderName = prefs.getString("senderName", "CreditHome Notificaciones") ?: "CreditHome Notificaciones"
        val appPassword = prefs.getString("appPassword", "") ?: ""
        val isEnabled = prefs.getBoolean("isEnabled", false)
        val useSsl = prefs.getBoolean("useSsl", true)

        val alertDaysJson = prefs.getString("alertDays", "[7, 3, 1, 0, -1]") ?: "[7, 3, 1, 0, -1]"
        val alertDays = mutableListOf<Int>()
        try {
            val arr = JSONArray(alertDaysJson)
            for (i in 0 until arr.length()) {
                alertDays.add(arr.getInt(i))
            }
        } catch (_: Exception) {
            alertDays.addAll(listOf(7, 3, 1, 0, -1))
        }

        return SmtpConfig(
            host = host,
            port = port,
            senderEmail = senderEmail,
            senderName = senderName,
            appPassword = appPassword,
            isEnabled = isEnabled,
            alertDaysBefore = alertDays,
            useSsl = useSsl
        )
    }

    fun saveConfig(config: SmtpConfig) {
        val arr = JSONArray()
        config.alertDaysBefore.forEach { arr.put(it) }

        prefs.edit()
            .putString("host", config.host)
            .putInt("port", config.port)
            .putString("senderEmail", config.senderEmail)
            .putString("senderName", config.senderName)
            .putString("appPassword", config.appPassword)
            .putBoolean("isEnabled", config.isEnabled)
            .putString("alertDays", arr.toString())
            .putBoolean("useSsl", config.useSsl)
            .apply()
    }
}
