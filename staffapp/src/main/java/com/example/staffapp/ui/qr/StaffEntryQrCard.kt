package com.example.staffapp.ui.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.RentalClubOption
import com.example.staffapp.ui.components.StaffInfoBanner
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Живой QR прохода тренера (ротация 15 с) — для главной и полноэкранного экрана. */
@Composable
fun StaffEntryQrCard(
    staffUserId: Int,
    rentalActive: Boolean,
    blockedMessage: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    entryQrFormat: String? = null,
    hallLabel: String? = null,
    paidRentalClubs: List<RentalClubOption> = emptyList(),
    activeClubId: Int? = null,
    onSelectClub: ((Int) -> Unit)? = null,
) {
    var secondsLeft by remember { mutableIntStateOf(15) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(staffUserId, rentalActive, entryQrFormat) {
        if (!rentalActive || staffUserId <= 0) {
            bitmap = null
            return@LaunchedEffect
        }
        while (isActive) {
            val payload = buildStaffEntryQr(staffUserId, System.currentTimeMillis(), entryQrFormat)
            bitmap = encodeStaffQrBitmap(payload, if (compact) 512 else 720)
            secondsLeft = 15
            repeat(15) {
                delay(1_000)
                if (!isActive) return@LaunchedEffect
                secondsLeft = maxOf(0, secondsLeft - 1)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 16.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Проход в зал",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!hallLabel.isNullOrBlank()) {
                Text(
                    "Зал: $hallLabel",
                    color = StaffOnSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (paidRentalClubs.size > 1 && onSelectClub != null) {
                Text(
                    "На какой зал QR",
                    color = StaffOnSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    paidRentalClubs.forEach { club ->
                        val selected = club.clubId == activeClubId
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectClub(club.clubId) },
                            label = {
                                Text(
                                    club.name.ifBlank { club.title },
                                    maxLines = 1,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StaffPrimary.copy(alpha = 0.18f),
                                selectedLabelColor = StaffPrimary,
                            ),
                        )
                    }
                }
            }
            if (!rentalActive || staffUserId <= 0) {
                StaffInfoBanner(
                    blockedMessage
                        ?: "Оплатите аренду клуба, чтобы пройти в зал по QR.",
                )
                return@Column
            }
            Text(
                "Покажите код на турникете",
                color = StaffOnSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "QR прохода",
                    modifier = Modifier.size(if (compact) 200.dp else 260.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(if (compact) 200.dp else 260.dp))
            }
            Text(
                "$secondsLeft с",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StaffPrimary,
            )
            Text(
                "Код обновляется каждые 15 секунд",
                color = StaffOnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

fun buildStaffEntryQr(
    staffUserId: Int,
    timestampMs: Long,
    entryQrFormat: String? = null,
): String {
    if (WiegandEntryQrCodec.usesWiegandNumeric(entryQrFormat)) {
        return WiegandEntryQrCodec.encode(staffUserId, timestampMs)
    }
    return "FITNESSCLUB:STAFF:$staffUserId:${encodeTimestampBase62(timestampMs)}"
}

fun encodeStaffQrBitmap(payload: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

private fun encodeTimestampBase62(ms: Long): String {
    val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    var v = ms.coerceAtLeast(0L)
    val sb = StringBuilder(7)
    repeat(7) {
        val idx = (v % 62L).toInt()
        sb.insert(0, alphabet[idx])
        v /= 62L
    }
    return sb.toString()
}
