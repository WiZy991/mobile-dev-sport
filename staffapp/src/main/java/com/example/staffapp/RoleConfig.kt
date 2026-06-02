package com.example.staffapp

data class RoleConfig(
    val roles: List<String>,
    val appSections: List<String>,
    val adminSections: List<String>,
    val adminActions: List<String>,
    val featureFlags: Map<String, Boolean>,
)
