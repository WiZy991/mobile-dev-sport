package com.example.staffapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.legal.StaffLegalPdf
import com.example.staffapp.ui.legal.LegalPdfScreen
import com.example.staffapp.ui.rental.RentalScreen
import com.example.staffapp.ui.rental.RentalScreenState
import com.example.staffapp.ui.rental.SpecialistPurchaseConsentDialog
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class RentalActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null
    private var lastPaymentId: Int? = null

    private var uiState by mutableStateOf(RentalScreenState())
    private var showConsentDialog by mutableStateOf(false)
    private var openLegalPdf by mutableStateOf<StaffLegalPdf?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        if (session == null) {
            finish()
            return
        }

        setContent {
            StaffTheme {
                val pdf = openLegalPdf
                when {
                    pdf != null -> LegalPdfScreen(doc = pdf, onNavigateBack = { openLegalPdf = null })
                    else -> {
                        if (showConsentDialog) {
                            SpecialistPurchaseConsentDialog(
                                onDismiss = { showConsentDialog = false },
                                onConfirm = {
                                    showConsentDialog = false
                                    startPayment()
                                },
                                onOpenPdf = { openLegalPdf = it },
                                isLoading = uiState.paying,
                            )
                        }
                        RentalScreen(
                            state = uiState,
                            onBack = { finish() },
                            onPlanSelected = { uiState = uiState.copy(selectedMonths = it) },
                            onPayClick = { showConsentDialog = true },
                            onRefresh = { load() },
                        )
                    }
                }
            }
        }
        handlePaymentDeepLink(intent)
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        val paymentId = lastPaymentId
        if (paymentId != null) {
            pollPayment(paymentId)
        }
    }

    private fun handlePaymentDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "staffapp" || data.host != "payment") return
        val paymentId = data.getQueryParameter("payment_id")?.toIntOrNull()
        if (paymentId != null && paymentId > 0) {
            lastPaymentId = paymentId
            pollPayment(paymentId)
        }
    }

    private fun load() {
        uiState = uiState.copy(loading = true, errorMessage = null)
        thread {
            try {
                val onboarding = withRefresh { apiClient.loadOnboarding(it) }
                val payments = try {
                    withRefresh { apiClient.loadRentalPayments(it) }
                } catch (e: Exception) {
                    if (!isMissingApi(e)) throw e
                    emptyList()
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        rentalPaidUntilLabel = formatRentalUntil(onboarding.rentalPaidUntil),
                        plans = onboarding.rentalPlans.ifEmpty {
                            listOf(
                                RentalPlan(
                                    months = 1,
                                    label = "1 месяц",
                                    amountKopecks = onboarding.rentalAmountKopecks,
                                    amountRub = onboarding.rentalAmountRub,
                                ),
                            )
                        },
                        selectedMonths = when {
                            onboarding.rentalPlans.any { it.months == uiState.selectedMonths } ->
                                uiState.selectedMonths
                            onboarding.rentalPlans.isNotEmpty() -> onboarding.rentalPlans.first().months
                            else -> 1
                        },
                        payments = payments,
                        loading = false,
                        statusMessage = uiState.statusMessage,
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        loading = false,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun isMissingApi(e: Exception): Boolean {
        val msg = e.message.orEmpty().lowercase()
        return msg.contains("404") || msg.contains("no route found")
    }

    private fun startPayment() {
        uiState = uiState.copy(paying = true, errorMessage = null, statusMessage = "Создаём платёж...")
        thread {
            try {
                val result = withRefresh {
                    apiClient.initRentalPayment(
                        it,
                        offerAccepted = true,
                        months = uiState.selectedMonths,
                    )
                }
                val url = result.paymentUrl
                if (url.isNullOrBlank()) {
                    throw IllegalStateException("Не получен URL оплаты Альфа-Банка.")
                }
                lastPaymentId = result.paymentId
                runOnUiThread {
                    uiState = uiState.copy(paying = false, statusMessage = "Откройте страницу оплаты")
                    openPaymentUrl(url)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        paying = false,
                        statusMessage = null,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun pollPayment(paymentId: Int) {
        uiState = uiState.copy(statusMessage = "Проверяем оплату...")
        thread {
            try {
                val status = withRefresh { apiClient.rentalPaymentStatus(it, paymentId) }
                runOnUiThread {
                    if (status.status == "paid") {
                        lastPaymentId = null
                        uiState = uiState.copy(statusMessage = "Оплата прошла")
                        load()
                    } else {
                        uiState = uiState.copy(statusMessage = "Статус: ${status.status}")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(errorMessage = UserFacingError.message(e))
                }
            }
        }
    }

    private fun openPaymentUrl(url: String) {
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun formatRentalUntil(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val date = iso.take(10)
        return "Аренда оплачена до $date"
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
