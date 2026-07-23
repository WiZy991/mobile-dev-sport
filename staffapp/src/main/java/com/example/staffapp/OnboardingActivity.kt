package com.example.staffapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.onboarding.OnboardingScreen
import com.example.staffapp.ui.onboarding.OnboardingUiState
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class OnboardingActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null
    private var lastPaymentId: Int? = null

    private var uiState by mutableStateOf(OnboardingUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        if (session == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            StaffTheme {
                OnboardingScreen(
                    state = uiState,
                    onOfferAcceptedChange = { uiState = uiState.copy(offerAccepted = it) },
                    onPayClick = { startPayment() },
                    onRefresh = { refreshOnboarding() },
                    onLogout = { logout() },
                )
            }
        }
        refreshOnboarding()
    }

    override fun onResume() {
        super.onResume()
        val paymentId = lastPaymentId
        if (paymentId != null) {
            pollPayment(paymentId)
        } else {
            refreshOnboarding(silent = true)
        }
    }

    private fun refreshOnboarding(silent: Boolean = false) {
        runAsync(if (silent) null else "Обновляем статус...") {
            val onboarding = executeWithRefresh { apiClient.loadOnboarding(it) }
            applyOnboarding(onboarding)
            if (onboarding.status == "active") {
                openWork()
            }
            "Готово"
        }
    }

    private fun startPayment() {
        runAsync("Создаём платёж...") {
            val result = executeWithRefresh {
                apiClient.initRentalPayment(it, offerAccepted = uiState.offerAccepted)
            }
            lastPaymentId = result.paymentId
            val url = result.paymentUrl
            if (!url.isNullOrBlank()) {
                runOnUiThread {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            "Откройте страницу оплаты"
        }
    }

    private fun pollPayment(paymentId: Int) {
        runAsync("Проверяем оплату...") {
            val status = executeWithRefresh { apiClient.rentalPaymentStatus(it, paymentId) }
            applyOnboarding(status.onboarding)
            if (status.status == "paid" || status.onboarding.status == "active") {
                lastPaymentId = null
                openWork()
                "Оплата прошла"
            } else {
                "Статус: ${status.status}"
            }
        }
    }

    private fun applyOnboarding(onboarding: StaffOnboarding) {
        runOnUiThread {
            uiState = uiState.copy(
                status = onboarding.status,
                offerUrl = onboarding.offerUrl,
                amountRub = onboarding.rentalAmountRub,
                rentalPaidUntil = onboarding.rentalPaidUntil,
                errorMessage = null,
            )
        }
    }

    private fun openWork() {
        runOnUiThread {
            startActivity(Intent(this, WorkActivity::class.java))
            finish()
        }
    }

    private fun logout() {
        store.clearAll()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun runAsync(progressText: String?, action: () -> String) {
        if (progressText != null) {
            uiState = uiState.copy(isLoading = true, statusMessage = progressText, errorMessage = null)
        }
        thread {
            try {
                val msg = action()
                runOnUiThread {
                    uiState = uiState.copy(isLoading = false, statusMessage = msg)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun <T> executeWithRefresh(action: (token: String) -> T): T {
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
