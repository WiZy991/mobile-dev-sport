package com.fitnessclub.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.api.AppUpdateInfo
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.data.config.AppDistribution
import com.fitnessclub.app.data.repository.ClubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Добровольно-принудительное обновление: если на сервере задан
 * android_min_version_code выше текущего versionCode — показываем диалог.
 * При force=true закрыть нельзя (назад тоже блокируется).
 *
 * Управление без новой сборки: в настройках клуба CRM
 * android_min_version_code / android_force_update / android_update_message.
 */
@Composable
fun ForceUpdateGate(
    clubRepository: ClubRepository,
) {
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var dismissedSoft by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { clubRepository.getClubInfo() }.getOrNull()
        }
        if (result is ApiResult.Success) {
            val info = result.data.appUpdate ?: return@LaunchedEffect
            if (info.androidMinVersionCode > BuildConfig.VERSION_CODE) {
                update = info
            }
        }
    }

    val required = update ?: return
    if (!required.force && dismissedSoft) return

    val context = LocalContext.current
    val message = required.message?.takeIf { it.isNotBlank() }
        ?: "Доступна новая версия приложения. Обновите её, чтобы продолжить пользоваться сервисом."

    BackHandler(enabled = required.force) { /* блокируем выход */ }

    AlertDialog(
        onDismissRequest = {
            if (!required.force) dismissedSoft = true
        },
        title = { Text(if (required.force) "Требуется обновление" else "Доступно обновление") },
        text = { Text(message) },
        confirmButton = {
            val options = AppDistribution.storeRatingOptions(context)
            if (options.size <= 1) {
                TextButton(
                    onClick = {
                        val url = options.firstOrNull()?.url ?: AppConfig.PLAY_STORE_URL
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                ) {
                    Text("Обновить")
                }
            } else {
                options.forEach { option ->
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(option.url)))
                        },
                    ) {
                        Text(option.label.replace("Оценить", "Обновить"))
                    }
                }
            }
        },
        dismissButton = if (!required.force) {
            {
                TextButton(onClick = { dismissedSoft = true }) {
                    Text("Позже")
                }
            }
        } else {
            null
        },
    )
}
