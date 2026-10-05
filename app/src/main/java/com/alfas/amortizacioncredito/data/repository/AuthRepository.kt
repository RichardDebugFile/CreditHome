package com.alfas.amortizacioncredito.data.repository

import android.content.Context
import com.alfas.amortizacioncredito.data.model.AppUser
import com.alfas.amortizacioncredito.data.model.UserPermissions
import com.alfas.amortizacioncredito.data.model.UserRole
import com.alfas.amortizacioncredito.domain.security.SecurityManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AuthRepository(private val context: Context) {

    private val file = File(context.filesDir, "company_users.json")
    private val sessionPrefs = context.getSharedPreferences("auth_session_prefs", Context.MODE_PRIVATE)
    private val securityManager = SecurityManager(context)

    /**
     * No existe ninguna cuenta por defecto: en el primer arranque con autenticación
     * habilitada, el propio usuario debe registrar la cuenta Máster con su PIN.
     */
    fun needsInitialSetup(): Boolean {
        return getAllUsers().none { it.isActive && it.pinHash.isNotBlank() && !isLegacyDefaultMaster(it) }
    }

    // Instalaciones antiguas crearon "admin" con PIN 1234; se invalida y se obliga a registrar uno nuevo.
    private fun isLegacyDefaultMaster(user: AppUser): Boolean {
        return user.id == LEGACY_DEFAULT_MASTER_ID && user.pinHash == securityManager.hashPin(LEGACY_DEFAULT_PIN)
    }

    fun createInitialMaster(username: String, displayName: String, pin: String): AppUser {
        check(needsInitialSetup()) { "Ya existen cuentas registradas en este dispositivo" }
        securityManager.validatePinStrength(pin)?.let { throw IllegalArgumentException(it) }
        require(username.isNotBlank()) { "El nombre de usuario es obligatorio" }

        getAllUsers().filter { isLegacyDefaultMaster(it) }.forEach { deleteUser(it.id) }

        val masterUser = AppUser(
            id = UUID.randomUUID().toString(),
            username = username.trim().lowercase(),
            displayName = displayName.trim().ifBlank { username.trim() },
            role = UserRole.MASTER,
            companyRucOrId = "",
            companyName = "CreditHome",
            permissions = UserPermissions.MASTER_PERMISSIONS,
            pinHash = securityManager.hashPin(pin),
            softTokenId = securityManager.getDeviceFingerprint(),
            activeSessionId = "",
            allowSuperadminAudit = false,
            isActive = true
        )
        saveUser(masterUser)
        return masterUser
    }

    fun getAllUsers(): List<AppUser> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<AppUser>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val permObj = obj.optJSONObject("permissions")
                val permissions = if (permObj != null) {
                    UserPermissions(
                        canEditClients = permObj.optBoolean("canEditClients", false),
                        canRecordPayments = permObj.optBoolean("canRecordPayments", false),
                        canDeleteRecords = permObj.optBoolean("canDeleteRecords", false),
                        canViewReceipts = permObj.optBoolean("canViewReceipts", true),
                        canExportReports = permObj.optBoolean("canExportReports", true),
                        canManageBackups = permObj.optBoolean("canManageBackups", false)
                    )
                } else {
                    val r = UserRole.valueOf(obj.optString("role", UserRole.VISUALIZADOR.name))
                    if (r == UserRole.MASTER) UserPermissions.MASTER_PERMISSIONS else UserPermissions.VIEWER_PERMISSIONS
                }

                list.add(
                    AppUser(
                        id = obj.getString("id"),
                        username = obj.getString("username"),
                        displayName = obj.getString("displayName"),
                        role = UserRole.valueOf(obj.optString("role", UserRole.VISUALIZADOR.name)),
                        companyRucOrId = obj.optString("companyRucOrId", ""),
                        companyName = obj.optString("companyName", "CreditHome"),
                        permissions = permissions,
                        pinHash = obj.optString("pinHash", ""),
                        softTokenId = obj.optString("softTokenId", ""),
                        activeSessionId = obj.optString("activeSessionId", ""),
                        allowSuperadminAudit = obj.optBoolean("allowSuperadminAudit", false),
                        isActive = obj.optBoolean("isActive", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveUser(user: AppUser) {
        val current = getAllUsers().toMutableList()
        val index = current.indexOfFirst { it.id == user.id || it.username.equals(user.username, ignoreCase = true) }
        if (index != -1) {
            current[index] = user
        } else {
            current.add(user)
        }
        saveAllUsers(current)
    }

    fun deleteUser(userId: String) {
        val current = getAllUsers().filter { it.id != userId }
        saveAllUsers(current)
    }

    fun login(username: String, pin: String): AppUser? {
        val users = getAllUsers()
        val user = users.find { it.username.equals(username.trim(), ignoreCase = true) && it.isActive } ?: return null
        if (isLegacyDefaultMaster(user)) return null
        if (!securityManager.verifyPin(pin, user.pinHash)) return null

        val newSessionId = securityManager.generateSessionId()
        val deviceFingerprint = securityManager.getDeviceFingerprint()

        // Actualizar activeSessionId para cumplir con Sesión Única
        val updatedUser = user.copy(
            activeSessionId = newSessionId,
            softTokenId = deviceFingerprint
        )
        saveUser(updatedUser)

        sessionPrefs.edit()
            .putString("logged_user_id", user.id)
            .putString("active_session_id", newSessionId)
            .apply()

        return updatedUser
    }

    fun getCurrentSessionUser(): AppUser? {
        val loggedId = sessionPrefs.getString("logged_user_id", null) ?: return null
        val storedSessionId = sessionPrefs.getString("active_session_id", null) ?: return null

        val user = getAllUsers().find { it.id == loggedId && it.isActive } ?: return null

        // Verificar que la sesión no haya sido invalidada desde otro dispositivo (Single Session)
        if (user.activeSessionId.isNotBlank() && user.activeSessionId != storedSessionId) {
            // Sesión cerrada remotamente por nuevo login
            logout()
            return null
        }

        return user
    }

    fun logout() {
        sessionPrefs.edit().clear().apply()
    }

    fun setSuperadminAuditPermission(allowed: Boolean) {
        val current = getCurrentSessionUser() ?: return
        val updated = current.copy(allowSuperadminAudit = allowed)
        saveUser(updated)
    }

    private fun saveAllUsers(users: List<AppUser>) {
        try {
            val jsonArray = JSONArray()
            for (u in users) {
                val permObj = JSONObject().apply {
                    put("canEditClients", u.permissions.canEditClients)
                    put("canRecordPayments", u.permissions.canRecordPayments)
                    put("canDeleteRecords", u.permissions.canDeleteRecords)
                    put("canViewReceipts", u.permissions.canViewReceipts)
                    put("exportReports", u.permissions.canExportReports)
                    put("manageBackups", u.permissions.canManageBackups)
                }
                val obj = JSONObject().apply {
                    put("id", u.id)
                    put("username", u.username)
                    put("displayName", u.displayName)
                    put("role", u.role.name)
                    put("companyRucOrId", u.companyRucOrId)
                    put("companyName", u.companyName)
                    put("permissions", permObj)
                    put("pinHash", u.pinHash)
                    put("softTokenId", u.softTokenId)
                    put("activeSessionId", u.activeSessionId)
                    put("allowSuperadminAudit", u.allowSuperadminAudit)
                    put("isActive", u.isActive)
                    put("createdAt", u.createdAt)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private companion object {
        const val LEGACY_DEFAULT_MASTER_ID = "master_default"
        const val LEGACY_DEFAULT_PIN = "1234"
    }
}
