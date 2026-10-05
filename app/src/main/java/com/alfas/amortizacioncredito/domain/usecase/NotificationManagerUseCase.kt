package com.alfas.amortizacioncredito.domain.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alfas.amortizacioncredito.MainActivity
import com.alfas.amortizacioncredito.data.model.AppNotification
import com.alfas.amortizacioncredito.data.model.ClientProfile
import com.alfas.amortizacioncredito.data.model.NotificationType
import com.alfas.amortizacioncredito.data.repository.NotificationRepository
import android.graphics.BitmapFactory
import com.alfas.amortizacioncredito.R
import java.text.SimpleDateFormat
import java.util.*

class NotificationManagerUseCase(
    private val context: Context,
    private val notificationRepository: NotificationRepository = NotificationRepository(context),
    private val calculateAmortizationUseCase: CalculateAmortizationUseCase = CalculateAmortizationUseCase()
) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val channelId = "credithome_alerts_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Recordatorios de Crédito y Cobranza"
            val descriptionText = "Notificaciones de cuotas vencidas, por vencer y pagos registrados"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun postSystemNotification(id: Int, title: String, message: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val largeIconBmp = try {
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            } catch (_: Exception) {
                null
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_credithome_logo)
                .setColor(0xFF0284C7.toInt())
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            if (largeIconBmp != null) {
                builder.setLargeIcon(largeIconBmp)
            }

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permiso POST_NOTIFICATIONS no concedido aún por el usuario
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scanAndGenerateLoanNotifications(clients: List<ClientProfile>): List<AppNotification> {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = todayCal.time

        var systemNotifIndex = 1000

        for (client in clients) {
            val price = client.price.toDoubleOrNull() ?: 0.0
            val downPayment = client.downPayment.toDoubleOrNull() ?: 0.0
            val rate = client.annualRate.toDoubleOrNull() ?: 0.0
            val years = client.years.toDoubleOrNull() ?: 0.0

            val summary = calculateAmortizationUseCase(
                price = price,
                downPayment = downPayment,
                annualRate = rate,
                years = years,
                roundUp = client.isRoundUpEnabled,
                isCompoundInterest = client.isCompoundInterest,
                isFixedPriceLoan = client.isFixedPriceLoan,
                extraPaymentMonth = client.extraPaymentMonth,
                extraPaymentAmount = client.extraPaymentAmount,
                startDate = client.startDate,
                paymentRecords = client.paymentRecords
            )

            for (item in summary.schedule) {
                val record = client.paymentRecords[item.month]
                val isPaid = record?.isPaid == true

                if (isPaid) {
                    // Si ya está pagada, remover cualquier alerta previa de vencimiento o mora
                    notificationRepository.deleteNotification("overdue_${client.id}_${item.month}")
                    notificationRepository.deleteNotification("due_soon_${client.id}_${item.month}")
                } else if (item.dueDate.isNotBlank()) {
                    try {
                        val dueDate = dateFormat.parse(item.dueDate) ?: continue
                        val diffDays = ((dueDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()

                        if (dueDate.before(today)) {
                            // En mora
                            notificationRepository.deleteNotification("due_soon_${client.id}_${item.month}")
                            val notif = AppNotification(
                                id = "overdue_${client.id}_${item.month}",
                                title = "🔴 Cuota Vencida: ${client.name}",
                                message = "La cuota #${item.month} por $${String.format(Locale.US, "%.2f", item.payment)} venció el ${item.dueDate}.",
                                type = NotificationType.OVERDUE,
                                targetClientId = client.id,
                                targetMonth = item.month
                            )
                            val isNew = notificationRepository.getAllNotifications().none { it.id == notif.id }
                            notificationRepository.addNotification(notif)
                            if (isNew) {
                                postSystemNotification(systemNotifIndex++, notif.title, notif.message)
                            }
                        } else if (diffDays in 0..7) {
                            // Vence pronto (dentro de los próximos 7 días)
                            notificationRepository.deleteNotification("overdue_${client.id}_${item.month}")
                            val dayText = if (diffDays == 0) "¡Vence hoy!" else "Vence en $diffDays día(s) (${item.dueDate})"
                            val notif = AppNotification(
                                id = "due_soon_${client.id}_${item.month}",
                                title = "⚠️ Próximo Vencimiento: ${client.name}",
                                message = "Cuota #${item.month} por $${String.format(Locale.US, "%.2f", item.payment)}: $dayText.",
                                type = NotificationType.DUE_SOON,
                                targetClientId = client.id,
                                targetMonth = item.month
                            )
                            val isNew = notificationRepository.getAllNotifications().none { it.id == notif.id }
                            notificationRepository.addNotification(notif)
                            if (isNew) {
                                postSystemNotification(systemNotifIndex++, notif.title, notif.message)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        return notificationRepository.getAllNotifications()
    }

    fun addPaymentReceivedNotification(clientName: String, month: Int, amount: Double, clientId: String) {
        val notifId = "payment_${clientId}_$month"
        val title = "🟢 Pago Registrado: $clientName"
        val message = "Se confirmó el pago de la cuota #$month por un monto de $${String.format(Locale.US, "%.2f", amount)}."

        // Limpiar alertas de cuota vencida o por vencer de este mes ya que ha sido pagada
        notificationRepository.deleteNotification("overdue_${clientId}_$month")
        notificationRepository.deleteNotification("due_soon_${clientId}_$month")

        val notif = AppNotification(
            id = notifId,
            title = title,
            message = message,
            type = NotificationType.PAYMENT_RECEIVED,
            targetClientId = clientId,
            targetMonth = month
        )
        notificationRepository.addNotification(notif)
        postSystemNotification((clientId.hashCode() + month).coerceAtLeast(1), title, message)
    }

    fun removePaymentReceivedNotification(clientId: String, month: Int) {
        notificationRepository.deleteNotification("payment_${clientId}_$month")
    }

    fun getAllNotifications(): List<AppNotification> = notificationRepository.getAllNotifications()

    fun markAsRead(id: String) = notificationRepository.markAsRead(id)

    fun markAllAsRead() = notificationRepository.markAllAsRead()

    fun deleteNotification(id: String) = notificationRepository.deleteNotification(id)

    fun clearAll() = notificationRepository.clearAll()
}
