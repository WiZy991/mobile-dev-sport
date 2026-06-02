package com.example.staffapp

class AuthorizationGuard(private val config: RoleConfig) {
    fun canOpenAppSection(section: String): Boolean = config.appSections.contains(section)

    fun canOpenAdminSection(section: String): Boolean = config.adminSections.contains(section)

    fun canRunAdminAction(action: String): Boolean = config.adminActions.contains(action)
}
