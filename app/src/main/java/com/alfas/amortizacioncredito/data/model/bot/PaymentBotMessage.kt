package com.alfas.amortizacioncredito.data.model.bot

import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.PaymentRecord
import com.alfas.amortizacioncredito.data.model.SavedSignatureItem
import java.util.UUID

enum class MessageSender {
    BOT,
    USER,
    SYSTEM
}

enum class BotStep {
    IDLE,
    CONFIRM_INIT,
    SELECTING_MONTH,
    SELECTING_METHOD,
    ATTACHING_VOUCHER,
    ASKING_SIGNATURE,
    PICKING_SIGNATURE,
    ASKING_DOWNLOAD_PDF,
    ASKING_NEXT_ACTION,
    COMPLETED
}

data class BotActionChip(
    val id: String,
    val label: String,
    val iconKey: String? = null,
    val payload: String = "",
    val isPrimary: Boolean = false,
    val isDanger: Boolean = false
)

data class PaymentBotMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionChips: List<BotActionChip> = emptyList(),
    val isWarning: Boolean = false,
    val isSuccess: Boolean = false,
    val thumbnailPath: String? = null
)

data class PaymentBotSession(
    val client: ClientProfile? = null,
    val selectedMonth: Int? = null,
    val installmentAmount: Double = 0.0,
    val dueDate: String = "",
    val paymentMethod: String = "Transferencia",
    val voucherPath: String = "",
    val referenceNumber: String = "",
    val bankOrEntity: String = "",
    val signatureItem: SavedSignatureItem? = null,
    val step: BotStep = BotStep.IDLE,
    val isAwaitingCancelConfirmation: Boolean = false,
    val previousStepBeforeCancel: BotStep = BotStep.IDLE,
    val registeredRecord: PaymentRecord? = null,
    val detectedAmounts: List<Double> = emptyList()
)
