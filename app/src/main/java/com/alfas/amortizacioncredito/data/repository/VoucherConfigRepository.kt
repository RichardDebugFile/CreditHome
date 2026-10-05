package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import com.alfas.amortizacioncredito.data.model.VoucherConfig

class VoucherConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences("voucher_config_prefs", Context.MODE_PRIVATE)

    fun getVoucherConfig(): VoucherConfig {
        val raw = prefs.getString("voucher_config_json", null)
        return VoucherConfig.fromJson(raw)
    }

    fun saveVoucherConfig(config: VoucherConfig) {
        prefs.edit()
            .putString("voucher_config_json", config.toJson())
            .apply()
    }

    fun resetToDefault(): VoucherConfig {
        prefs.edit().remove("voucher_config_json").apply()
        return VoucherConfig.DEFAULT
    }
}
