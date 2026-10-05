package com.alfas.amortizacioncredito.data.model

data class AppUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val companyRucOrId: String = "",
    val companyName: String = "CreditHome",
    val permissions: UserPermissions = UserPermissions.VIEWER_PERMISSIONS,
    val pinHash: String = "",
    val softTokenId: String = "",
    val activeSessionId: String = "",
    val allowSuperadminAudit: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
