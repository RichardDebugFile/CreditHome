package com.alfas.amortizacioncredito.data.model

enum class UserRole(val displayName: String, val badgeText: String) {
    SUPERADMIN("Superusuario (Auditor)", "🛡️ Superusuario"),
    MASTER("Administrador Máster", "👑 Máster"),
    OPERADOR("Operador / Cobrador", "✍️ Operador"),
    VISUALIZADOR("Visualizador (Solo Lectura)", "👁️ Visualizador")
}

data class UserPermissions(
    val canEditClients: Boolean = false,
    val canRecordPayments: Boolean = false,
    val canDeleteRecords: Boolean = false,
    val canViewReceipts: Boolean = true,
    val canExportReports: Boolean = true,
    val canManageBackups: Boolean = false
) {
    companion object {
        val MASTER_PERMISSIONS = UserPermissions(
            canEditClients = true,
            canRecordPayments = true,
            canDeleteRecords = true,
            canViewReceipts = true,
            canExportReports = true,
            canManageBackups = true
        )
        val OPERATOR_PERMISSIONS = UserPermissions(
            canEditClients = true,
            canRecordPayments = true,
            canDeleteRecords = false,
            canViewReceipts = true,
            canExportReports = true,
            canManageBackups = false
        )
        val VIEWER_PERMISSIONS = UserPermissions(
            canEditClients = false,
            canRecordPayments = false,
            canDeleteRecords = false,
            canViewReceipts = true,
            canExportReports = true,
            canManageBackups = false
        )
    }
}
