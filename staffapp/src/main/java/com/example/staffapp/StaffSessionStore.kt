package com.example.staffapp

import android.content.Context

class StaffSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("staff_session", Context.MODE_PRIVATE)

    fun saveSession(session: StaffSession) {
        prefs.edit()
            .putString("access", session.accessToken)
            .putString("refresh", session.refreshToken)
            .putString("email", session.userEmail)
            .apply()
    }

    fun loadSession(): StaffSession? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val email = prefs.getString("email", "") ?: ""
        return StaffSession(access, refresh, email)
    }

    fun saveConfig(config: RoleConfig) {
        prefs.edit()
            .putString("roles", config.roles.joinToString(","))
            .putString("app_sections", config.appSections.joinToString(","))
            .putString("admin_sections", config.adminSections.joinToString(","))
            .putString("admin_actions", config.adminActions.joinToString(","))
            .apply()
    }

    fun loadConfig(): RoleConfig? {
        val rolesRaw = prefs.getString("roles", null) ?: return null
        if (rolesRaw.isBlank()) return null
        return RoleConfig(
            roles = parseCsv(rolesRaw),
            appSections = parseCsv(prefs.getString("app_sections", "")),
            adminSections = parseCsv(prefs.getString("admin_sections", "")),
            adminActions = parseCsv(prefs.getString("admin_actions", "")),
            featureFlags = emptyMap(),
        )
    }

    fun clearConfig() {
        prefs.edit()
            .remove("roles")
            .remove("app_sections")
            .remove("admin_sections")
            .remove("admin_actions")
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun getLastUnreadNotificationCount(): Int = prefs.getInt("unread_notifications", -1)

    fun setLastUnreadNotificationCount(count: Int) {
        prefs.edit().putInt("unread_notifications", count).apply()
    }

    fun getOrCreatePushToken(): String {
        val existing = prefs.getString("push_token", null)
        if (!existing.isNullOrBlank()) return existing
        val token = "staff-local-${java.util.UUID.randomUUID()}"
        prefs.edit().putString("push_token", token).apply()
        return token
    }

    private fun parseCsv(raw: String?): List<String> {
        return raw.orEmpty().split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
}
