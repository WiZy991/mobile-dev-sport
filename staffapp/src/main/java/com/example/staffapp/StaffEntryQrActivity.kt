package com.example.staffapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.qr.StaffEntryQrScreen
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class StaffEntryQrActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STAFF_USER_ID = "extra_staff_user_id"
    }

    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null

    private var staffUserId by mutableStateOf(0)
    private var rentalActive by mutableStateOf(false)
    private var blockedMessage by mutableStateOf<String?>("Проверяем оплату аренды…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        if (session == null) {
            finish()
            return
        }

        staffUserId = intent.getIntExtra(EXTRA_STAFF_USER_ID, 0).coerceAtLeast(0)

        setContent {
            StaffTheme {
                StaffEntryQrScreen(
                    staffUserId = staffUserId,
                    rentalActive = rentalActive,
                    blockedMessage = blockedMessage,
                    onBack = { finish() },
                )
            }
        }
        loadAccess()
    }

    private fun loadAccess() {
        thread {
            try {
                val onboarding = withRefresh { apiClient.loadOnboarding(it) }
                var id = listOfNotNull(
                    onboarding.staffUserId?.takeIf { it > 0 },
                    intent.getIntExtra(EXTRA_STAFF_USER_ID, 0).takeIf { it > 0 },
                ).firstOrNull() ?: 0

                if (id <= 0) {
                    runCatching {
                        withRefresh { apiClient.loadAppData(it).employeeId }
                    }.getOrNull()?.takeIf { it > 0 }?.let { id = it }
                }

                val paidClubs = onboarding.rentalClubs.filter { it.rentalActive }
                val hasPaidButInactive = onboarding.requiresRental &&
                    paidClubs.isNotEmpty() &&
                    !onboarding.activeClubRentalOk
                val active = StaffRentalAccess.canShowEntryQr(
                    staffUserId = id,
                    status = onboarding.status,
                    requiresRental = onboarding.requiresRental,
                    rentalPaidUntilIso = onboarding.activeClubPaidUntil,
                    rentalActiveFromServer = onboarding.rentalActive,
                    activeClubRentalOk = onboarding.activeClubRentalOk,
                )

                runOnUiThread {
                    staffUserId = id
                    rentalActive = active
                    blockedMessage = StaffRentalAccess.entryQrBlockedMessage(
                        staffUserId = id,
                        status = onboarding.status,
                        requiresRental = onboarding.requiresRental,
                        rentalPaidUntilIso = onboarding.activeClubPaidUntil,
                        hasPaidClubsButWrongActive = hasPaidButInactive,
                    )
                }
            } catch (e: Exception) {
                val fallbackId = intent.getIntExtra(EXTRA_STAFF_USER_ID, 0)
                runOnUiThread {
                    if (fallbackId > 0) {
                        // Сеть/API недоступны, но id уже передали из профиля — не врём про «нет учётки».
                        staffUserId = fallbackId
                        rentalActive = false
                        blockedMessage = "Не удалось проверить аренду. Откройте экран ещё раз."
                    } else {
                        rentalActive = false
                        blockedMessage = UserFacingError.message(e)
                    }
                }
            }
        }
    }

    private fun <T> withRefresh(action: (token: String) -> T): T {
        val current = session ?: throw IllegalStateException("Нет сессии")
        return try {
            action(current.accessToken)
        } catch (e: IllegalStateException) {
            if (!e.message.orEmpty().contains("401")) throw e
            val refreshed = apiClient.refresh(current.refreshToken)
            session = refreshed
            store.saveSession(refreshed)
            action(refreshed.accessToken)
        }
    }
}
