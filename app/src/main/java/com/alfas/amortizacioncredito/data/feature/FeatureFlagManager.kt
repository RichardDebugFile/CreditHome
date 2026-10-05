package com.alfas.amortizacioncredito.data.feature

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

import com.alfas.amortizacioncredito.config.AppConfig

enum class FeatureKey(val key: String, val defaultValue: Boolean) {
    AUTH_AND_ROLES("auth_and_roles_enabled", false),
    SOFT_TOKEN_SECURITY("soft_token_security_enabled", false),
    SINGLE_SESSION_ENFORCED("single_session_enforced", false),
    SUPERADMIN_ROLE("superadmin_role_enabled", false),
    AUDIT_LOG("audit_log_enabled", false),
    VPS_AUTO_UPDATE("vps_auto_update_enabled", false),
    SMTP_EMAIL("smtp_email_enabled", false),
    RABBITMQ_SYNC("rabbitmq_sync_enabled", false)
}

class FeatureFlagManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("feature_flags_prefs", Context.MODE_PRIVATE)
    private val flags = mutableMapOf<String, Any>()
    private var vpsServerUrl: String = AppConfig.VPS_SERVER_URL

    init {
        loadFlags()
    }

    private fun loadFlags() {
        try {
            // 1. Cargar desde assets/features.json
            val jsonString = context.assets.open("features.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val featuresObj = root.optJSONObject("features")
            if (featuresObj != null) {
                val keys = featuresObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    flags[k] = featuresObj.get(k)
                }
            }
            vpsServerUrl = featuresObj?.optString("vps_server_url", AppConfig.VPS_SERVER_URL) ?: AppConfig.VPS_SERVER_URL
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Aplicar anulaciones locales si existen
        for (fKey in FeatureKey.entries) {
            if (prefs.contains("override_${fKey.key}")) {
                flags[fKey.key] = prefs.getBoolean("override_${fKey.key}", fKey.defaultValue)
            }
        }
    }

    fun isEnabled(feature: FeatureKey): Boolean {
        val value = flags[feature.key]
        return if (value is Boolean) value else feature.defaultValue
    }

    fun getVpsUrl(): String {
        return prefs.getString("vps_server_url", vpsServerUrl) ?: vpsServerUrl
    }

    fun setOverride(feature: FeatureKey, enabled: Boolean) {
        flags[feature.key] = enabled
        prefs.edit().putBoolean("override_${feature.key}", enabled).apply()
    }

    fun clearOverrides() {
        prefs.edit().clear().apply()
        flags.clear()
        loadFlags()
    }
}
