package com.fitnessclub.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.AppUpdateInfo
import com.fitnessclub.app.data.config.AppDistribution
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Полноэкранный баннер обновления при входе.
 * Soft: крестик закрывает до следующего запуска. Force: закрыть нельзя.
 * Включается в CRM: android_min_version_code / android_force_update / android_update_message.
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
    val storeOptions = AppDistribution.updateStoreOptions(context)
    val message = required.message?.takeIf { it.isNotBlank() }
        ?: "Доступна новая версия. Обновите приложение, чтобы всё работало стабильно."

    BackHandler {
        if (!required.force) dismissedSoft = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Primary)
            .systemBarsPadding(),
    ) {
        if (!required.force) {
            IconButton(
                onClick = { dismissedSoft = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1C1B1F),
                ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 72.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = Brand.name,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (required.force) "Нужно обновить приложение" else "Обновите приложение",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Сейчас ${BuildConfig.VERSION_NAME}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.weight(1f))
            storeOptions.forEachIndexed { index, option ->
                if (index == 0) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(option.url)))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Primary,
                        ),
                    ) {
                        Text(option.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(option.url)))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.5.dp, Color.White),
                    ) {
                        Text(option.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
