package com.example.staffapp.ui.work

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.ui.graphics.vector.ImageVector

object SectionIcons {
    fun forSection(key: String): ImageVector = when (key) {
        "dashboard" -> Icons.Default.Dashboard
        "tasks" -> Icons.Default.Task
        "clients" -> Icons.Default.People
        "schedule" -> Icons.Default.CalendarMonth
        "bookings" -> Icons.Default.EventNote
        "subscriptions" -> Icons.Default.FitnessCenter
        "visits" -> Icons.Default.EventNote
        "analytics" -> Icons.Default.Analytics
        "finance" -> Icons.Default.Payments
        "app_support" -> Icons.Default.SupportAgent
        "trainers" -> Icons.Default.Group
        "documents" -> Icons.Default.Folder
        "settings" -> Icons.Default.Settings
        else -> Icons.Default.Dashboard
    }
}
