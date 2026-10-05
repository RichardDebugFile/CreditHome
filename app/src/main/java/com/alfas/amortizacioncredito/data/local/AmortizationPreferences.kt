package com.alfas.amortizacioncredito.data.local

import android.content.Context
import android.content.SharedPreferences
import com.alfas.amortizacioncredito.ui.AmortizationUiState

class AmortizationPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("amortizacion_prefs", Context.MODE_PRIVATE)

    fun loadState(): AmortizationUiState {
        return AmortizationUiState(
            priceInput = prefs.getString(KEY_PRICE, "86000") ?: "86000",
            downPaymentInput = prefs.getString(KEY_DOWN_PAYMENT, "40000") ?: "40000",
            annualRateInput = prefs.getString(KEY_RATE, "3.0") ?: "3.0",
            yearsInput = prefs.getString(KEY_YEARS, "5") ?: "5",
            startDateInput = prefs.getString(KEY_START_DATE, "") ?: "",
            isRoundUpEnabled = prefs.getBoolean(KEY_ROUND_UP, false),
            hideInterest = prefs.getBoolean(KEY_HIDE_INTEREST, false),
            isCompoundInterest = prefs.getBoolean(KEY_COMPOUND_INTEREST, false),
            isFixedPriceLoan = prefs.getBoolean(KEY_FIXED_PRICE_LOAN, false),
            extraPaymentMonth = prefs.getInt(KEY_EXTRA_MONTH, 0),
            extraPaymentAmount = prefs.getString(KEY_EXTRA_AMOUNT, "0.0")?.toDoubleOrNull() ?: 0.0
        )
    }

    fun saveState(state: AmortizationUiState) {
        prefs.edit()
            .putString(KEY_PRICE, state.priceInput)
            .putString(KEY_DOWN_PAYMENT, state.downPaymentInput)
            .putString(KEY_RATE, state.annualRateInput)
            .putString(KEY_YEARS, state.yearsInput)
            .putString(KEY_START_DATE, state.startDateInput)
            .putBoolean(KEY_ROUND_UP, state.isRoundUpEnabled)
            .putBoolean(KEY_HIDE_INTEREST, state.hideInterest)
            .putBoolean(KEY_COMPOUND_INTEREST, state.isCompoundInterest)
            .putBoolean(KEY_FIXED_PRICE_LOAN, state.isFixedPriceLoan)
            .putInt(KEY_EXTRA_MONTH, state.extraPaymentMonth)
            .putString(KEY_EXTRA_AMOUNT, state.extraPaymentAmount.toString())
            .apply()
    }

    fun getActiveClientId(): String? {
        return prefs.getString(KEY_ACTIVE_CLIENT_ID, null)
    }

    fun saveActiveClientId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_CLIENT_ID, id).apply()
    }

    companion object {
        private const val KEY_PRICE = "key_price"
        private const val KEY_DOWN_PAYMENT = "key_down_payment"
        private const val KEY_RATE = "key_rate"
        private const val KEY_YEARS = "key_years"
        private const val KEY_START_DATE = "key_start_date"
        private const val KEY_ROUND_UP = "key_round_up"
        private const val KEY_HIDE_INTEREST = "key_hide_interest"
        private const val KEY_COMPOUND_INTEREST = "key_compound_interest"
        private const val KEY_FIXED_PRICE_LOAN = "key_fixed_price_loan"
        private const val KEY_EXTRA_MONTH = "key_extra_month"
        private const val KEY_EXTRA_AMOUNT = "key_extra_amount"
        private const val KEY_ACTIVE_CLIENT_ID = "key_active_client_id"
    }
}
