package com.fitnessclub.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.data.repository.PurchaseSubscriptionOutcome
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

data class SubscriptionPlansUiState(
    val isLoading: Boolean = true,
    val plans: List<SubscriptionPlan> = emptyList(),
    val error: String? = null,
    val appliedPromoCode: String? = null,
    val promoDiscountPercent: Double? = null,
    val promoDiscountAmount: Double? = null,
    val isApplyingPromo: Boolean = false,
    val purchaseSuccess: Boolean = false,
    val clubPurchaseContext: ClubPurchaseContext = ClubPurchaseContext(clubName = "Ваш клуб"),
    val isSavingPassport: Boolean = false,
)

/** Состояние шага паспорта перед согласием/оплатой. */
data class PurchasePassportGate(
    val plan: SubscriptionPlan,
    val needDateOfBirth: Boolean,
    val initialDobDisplay: String,
)

@HiltViewModel
class SubscriptionPlansViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionPlansUiState())
    val uiState: StateFlow<SubscriptionPlansUiState> = _uiState.asStateFlow()

    init {
        loadPlans()
        loadClubPurchaseContext()
    }

    fun refreshClubContext() {
        loadClubPurchaseContext()
    }

    private fun loadClubPurchaseContext() {
        viewModelScope.launch {
            val userClubId = authRepository.getCurrentUser().first()?.clubId
            val context = when (val result = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    val club = result.data
                    val idFromInfo = club.id?.toIntOrNull()
                    ClubPurchaseContext(
                        clubName = club.name.ifBlank { "Ваш клуб" },
                        clubId = idFromInfo ?: userClubId,
                        visitingRulesUrl = club.visitingRulesUrl,
                        safetyRulesUrl = club.safetyRulesUrl,
                    )
                }
                else -> ClubPurchaseContext(
                    clubName = "Ваш клуб",
                    clubId = userClubId,
                )
            }
            _uiState.update { it.copy(clubPurchaseContext = context) }
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching {
                when (val result = subscriptionRepository.getSubscriptionPlansSuspend()) {
                    is ApiResult.Success -> {
                        val validPlans = result.data.filter { it.safeId.isNotBlank() && it.safeName.isNotBlank() }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                plans = validPlans
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                    is ApiResult.Loading -> { /* handled by initial update */ }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Ошибка загрузки",
                        plans = emptyList()
                    )
                }
            }
        }
    }

    fun applyPromoCode(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingPromo = true, error = null) }

            val result = subscriptionRepository.validatePromoCode(code)

            if (result.isValid) {
                _uiState.update {
                    it.copy(
                        isApplyingPromo = false,
                        appliedPromoCode = result.code ?: code.uppercase(),
                        promoDiscountPercent = result.discountPercent,
                        promoDiscountAmount = result.discountAmount,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isApplyingPromo = false,
                        error = result.error ?: "Промокод недействителен"
                    )
                }
            }
        }
    }

    fun removePromoCode() {
        _uiState.update {
            it.copy(
                appliedPromoCode = null,
                promoDiscountPercent = null,
                promoDiscountAmount = null,
            )
        }
    }

    fun discountedPrice(plan: SubscriptionPlan): Double {
        val state = _uiState.value
        val base = plan.price
        if (state.appliedPromoCode == null) return base

        state.promoDiscountPercent?.let { percent ->
            return max(0.0, base - base * percent / 100.0)
        }
        state.promoDiscountAmount?.let { amount ->
            return max(0.0, base - min(amount, base))
        }
        return base
    }

    /** Каталожная цена для зачёркивания, если есть скидка группы и/или промокода. */
    fun strikeThroughPrice(plan: SubscriptionPlan): Double? {
        val final = discountedPrice(plan)
        val catalog = plan.catalogPrice
        return if (final < catalog) catalog else null
    }

    /** После подтверждения цены: паспорт (если нет) → согласие с документами → Альфа. */
    fun beginPurchaseAfterPriceConfirm(
        plan: SubscriptionPlan,
        onReadyForConsent: (SubscriptionPlan) -> Unit,
        onNeedPassport: (PurchasePassportGate) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val user = authRepository.refreshCurrentUser()
                ?: authRepository.getCurrentUser().first()
            if (user == null) {
                onError("Профиль не загружен. Войдите снова.")
                return@launch
            }
            if (user.isPassportCompleteForPurchase()) {
                onReadyForConsent(plan)
            } else {
                onNeedPassport(
                    PurchasePassportGate(
                        plan = plan,
                        needDateOfBirth = user.dateOfBirth.isNullOrBlank(),
                        initialDobDisplay = isoDateToDisplay(user.dateOfBirth),
                    ),
                )
            }
        }
    }

    fun savePassportThenContinue(
        result: PurchasePassportResult,
        onSaved: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_uiState.value.isSavingPassport) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPassport = true) }
            when (
                val saved = authRepository.savePassportForPurchase(
                    series = result.series,
                    number = result.number,
                    issuedBy = result.issuedBy,
                    issueDateIso = result.issueDateIso,
                    registrationAddress = result.registrationAddress,
                    dateOfBirthIso = result.dateOfBirthIso,
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSavingPassport = false) }
                    onSaved()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSavingPassport = false) }
                    onError(saved.message ?: "Не удалось сохранить паспортные данные")
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun purchasePlan(
        plan: SubscriptionPlan,
        onPaymentRequired: (paymentId: Int, paymentUrl: String) -> Unit,
        onVerificationRequired: (authorizeUrl: String, message: String) -> Unit,
        onPassportRequired: (String) -> Unit = {},
        onError: (String) -> Unit,
    ) {
        if (_uiState.value.isLoading) {
            return
        }
        val clubId = _uiState.value.clubPurchaseContext.clubId
        if (clubId == null || clubId <= 0) {
            onError("Сначала выберите клуб для покупки")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (
                val result = subscriptionRepository.purchaseSubscription(
                    planId = plan.safeId,
                    promoCode = _uiState.value.appliedPromoCode,
                    clubId = clubId,
                )
            ) {
                is PurchaseSubscriptionOutcome.PaymentRequired -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onPaymentRequired(result.paymentId, result.paymentUrl)
                }
                is PurchaseSubscriptionOutcome.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, purchaseSuccess = true)
                    }
                }
                is PurchaseSubscriptionOutcome.VerificationRequired -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onVerificationRequired(result.authorizeUrl, result.message)
                }
                is PurchaseSubscriptionOutcome.PassportRequired -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onPassportRequired(result.message)
                }
                is PurchaseSubscriptionOutcome.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onError(result.message)
                }
            }
        }
    }
}

private fun isoDateToDisplay(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val d = java.time.LocalDate.parse(iso.take(10))
        d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } catch (_: Exception) {
        iso
    }
}
