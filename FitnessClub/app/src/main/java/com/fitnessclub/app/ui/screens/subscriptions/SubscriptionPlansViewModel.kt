package com.fitnessclub.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.data.repository.PurchaseSubscriptionOutcome
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val clubLegalLinks: ClubLegalLinks? = null,
)

@HiltViewModel
class SubscriptionPlansViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val clubRepository: ClubRepository,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SubscriptionPlansUiState())
    val uiState: StateFlow<SubscriptionPlansUiState> = _uiState.asStateFlow()
    
    init {
        loadPlans()
        loadClubLegalLinks()
    }

    private fun loadClubLegalLinks() {
        viewModelScope.launch {
            when (val result = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    val club = result.data
                    _uiState.update {
                        it.copy(
                            clubLegalLinks = ClubLegalLinks(
                                clubName = club.name,
                                offerUrl = club.offerUrl ?: AppConfig.TERMS_URL,
                                privacyUrl = club.privacyUrl ?: AppConfig.PRIVACY_URL,
                                visitingRulesUrl = club.visitingRulesUrl,
                                safetyRulesUrl = club.safetyRulesUrl,
                            )
                        )
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            clubLegalLinks = ClubLegalLinks(
                                clubName = "Ваш клуб",
                                offerUrl = AppConfig.TERMS_URL,
                                privacyUrl = AppConfig.PRIVACY_URL,
                            )
                        )
                    }
                }
            }
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
    
    fun purchasePlan(
        plan: SubscriptionPlan,
        onPaymentRequired: (paymentId: Int, paymentUrl: String) -> Unit,
        onVerificationRequired: (authorizeUrl: String, message: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (
                val result = subscriptionRepository.purchaseSubscription(
                    planId = plan.safeId,
                    promoCode = _uiState.value.appliedPromoCode,
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
                is PurchaseSubscriptionOutcome.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onError(result.message)
                }
            }
        }
    }
}
