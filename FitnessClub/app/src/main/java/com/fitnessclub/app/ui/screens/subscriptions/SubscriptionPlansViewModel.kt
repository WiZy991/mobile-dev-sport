package com.fitnessclub.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.repository.PurchaseSubscriptionOutcome
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionPlansUiState(
    val isLoading: Boolean = true,
    val plans: List<SubscriptionPlan> = emptyList(),
    val error: String? = null,
    val appliedPromoCode: String? = null,
    val promoDiscount: Int = 0,
    val isApplyingPromo: Boolean = false,
    val purchaseSuccess: Boolean = false
)

@HiltViewModel
class SubscriptionPlansViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SubscriptionPlansUiState())
    val uiState: StateFlow<SubscriptionPlansUiState> = _uiState.asStateFlow()
    
    init {
        loadPlans()
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
            _uiState.update { it.copy(isApplyingPromo = true) }
            
            // Mock promo code validation
            kotlinx.coroutines.delay(500)
            
            val validCodes = mapOf(
                "WELCOME10" to 10,
                "FITNESS20" to 20,
                "NEWYEAR25" to 25,
                "VIP30" to 30
            )
            
            val discount = validCodes[code.uppercase()]
            
            if (discount != null) {
                _uiState.update {
                    it.copy(
                        isApplyingPromo = false,
                        appliedPromoCode = code.uppercase(),
                        promoDiscount = discount
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isApplyingPromo = false,
                        error = "Промокод недействителен"
                    )
                }
            }
        }
    }
    
    fun removePromoCode() {
        _uiState.update {
            it.copy(
                appliedPromoCode = null,
                promoDiscount = 0
            )
        }
    }
    
    fun purchasePlan(
        plan: SubscriptionPlan,
        onSuccess: () -> Unit,
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
                is PurchaseSubscriptionOutcome.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, purchaseSuccess = true)
                    }
                    onSuccess()
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
