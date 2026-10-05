package com.alfas.amortizacioncredito.data.model

enum class AuditActionType(val displayName: String) {
    CREATE_CLIENT("Creación de Cliente"),
    EDIT_CLIENT("Edición de Cliente"),
    DELETE_CLIENT("Eliminación de Cliente"),
    RECORD_PAYMENT("Registro de Pago"),
    CANCEL_PAYMENT("Anulación de Pago"),
    BACKUP_EXPORT("Exportación de Copia"),
    BACKUP_RESTORE("Restauración de Copia"),
    CHANGE_PROFILE("Cambio de Perfil Emisor"),
    CREATE_USER("Creación de Usuario"),
    EDIT_USER("Edición de Usuario"),
    DELETE_USER("Eliminación de Usuario"),
    CREATE_COMPANY("Creación de Empresa"),
    CHANGE_PERMISSIONS("Cambio de Permisos"),
    LOGIN("Inicio de Sesión"),
    LOGOUT("Cierre de Sesión"),
    SETTINGS_CHANGED("Ajustes del Sistema"),
    EMAIL_SENT("Envío de Correo"),
    RECEIPT_SIGNED("Firma de Comprobante")
}

data class AuditLogEntry(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = "master",
    val username: String = "Usuario Máster",
    val userRole: String = "MASTER",
    val companyName: String = "CreditHome",
    val action: AuditActionType,
    val description: String,
    val details: String = ""
)
