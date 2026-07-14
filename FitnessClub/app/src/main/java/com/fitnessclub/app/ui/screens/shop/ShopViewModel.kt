package com.fitnessclub.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.data.repository.ProductRepository
import com.fitnessclub.app.data.repository.PurchaseSubscriptionOutcome
import com.fitnessclub.app.data.repository.SubscriptionRepository
import com.fitnessclub.app.ui.screens.subscriptions.ClubLegalLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val isLoading: Boolean = true,
    val items: List<ShopItem> = emptyList(),
    val subscriptionPlansByItemId: Map<String, SubscriptionPlan> = emptyMap(),
    val isPurchasing: Boolean = false,
    val error: String? = null,
    val purchaseMessage: String? = null,
    val clubLegalLinks: ClubLegalLinks? = null,
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val clubRepository: ClubRepository,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()
    
    init {
        loadItems()
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
    
    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = productRepository.getProducts()) {
                is ApiResult.Success -> {
                    val (subscriptionItems, plansByItemId) = getSubscriptionItems()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = result.data + subscriptionItems,
                            subscriptionPlansByItemId = plansByItemId,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    val (subscriptionItems, plansByItemId) = getSubscriptionItems()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = subscriptionItems,
                            subscriptionPlansByItemId = plansByItemId,
                            error = result.message,
                        )
                    }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    private suspend fun getSubscriptionItems(): Pair<List<ShopItem>, Map<String, SubscriptionPlan>> {
        val subscriptionPlans = when (val result = subscriptionRepository.getSubscriptionPlansSuspend()) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
        val subscriptionItems = subscriptionPlans.map { it.toShopSubscriptionItem() }
        val plansByItemId = subscriptionPlans.associateBy { "sub-${it.safeId}" }
        return subscriptionItems to plansByItemId
    }
    
    fun planForItem(itemId: String): SubscriptionPlan? = _uiState.value.subscriptionPlansByItemId[itemId]

    fun purchaseSubscriptionPlan(
        plan: SubscriptionPlan,
        onPaymentRequired: (paymentId: Int, paymentUrl: String) -> Unit,
        onVerificationRequired: (authorizeUrl: String, message: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPurchasing = true, error = null) }
            when (
                val result = subscriptionRepository.purchaseSubscription(
                    planId = plan.safeId,
                    promoCode = null,
                )
            ) {
                is PurchaseSubscriptionOutcome.PaymentRequired -> {
                    _uiState.update { it.copy(isPurchasing = false) }
                    onPaymentRequired(result.paymentId, result.paymentUrl)
                }
                is PurchaseSubscriptionOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isPurchasing = false,
                            purchaseMessage = "Абонемент «${plan.safeName}» оформлен",
                        )
                    }
                }
                is PurchaseSubscriptionOutcome.VerificationRequired -> {
                    _uiState.update { it.copy(isPurchasing = false) }
                    onVerificationRequired(result.authorizeUrl, result.message)
                }
                is PurchaseSubscriptionOutcome.Error -> {
                    _uiState.update { it.copy(isPurchasing = false) }
                    onError(result.message)
                }
            }
        }
    }

    fun buyItem(item: ShopItem) {
        viewModelScope.launch {
            when {
                item.id.startsWith("product-") -> {
                    when (val result = productRepository.purchaseProduct(item.id)) {
                        is ApiResult.Success -> {
                            _uiState.update {
                                it.copy(purchaseMessage = "Покупка оформлена: ${item.name}")
                            }
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(error = result.message ?: "Ошибка покупки")
                            }
                        }
                        is ApiResult.Loading -> { /* no-op */ }
                    }
                }
                item.category == ShopCategory.DEPOSITS || item.id.startsWith("dep-") -> {
                    _uiState.update {
                        it.copy(purchaseMessage = "Депозит пополняется на ресепшене клуба или через администратора.")
                    }
                }
                item.price == 0.0 -> {
                    _uiState.update {
                        it.copy(purchaseMessage = "Бесплатная услуга: запись в разделе «Расписание» или у администратора.")
                    }
                }
                item.category == ShopCategory.SERVICES -> {
                    when (val result = productRepository.purchaseProduct(item.id)) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(purchaseMessage = "Услуга оформлена: ${item.name}") }
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    purchaseMessage = "Услуга из каталога клуба оформляется после появления её в приложении. Запишитесь через «Расписание»."
                                )
                            }
                        }
                        is ApiResult.Loading -> {}
                    }
                }
                item.category == ShopCategory.GOODS -> {
                    when (val result = productRepository.purchaseProduct(item.id)) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(purchaseMessage = "Заказ оформлен: ${item.name}") }
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(purchaseMessage = "Товар получите в клубе — позиция пока не в онлайн-каталоге.")
                            }
                        }
                        is ApiResult.Loading -> {}
                    }
                }
                else -> {
                    when (val result = productRepository.purchaseProduct(item.id)) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(purchaseMessage = "Оформлено: ${item.name}") }
                        }
                        is ApiResult.Error -> {
                            _uiState.update {
                                it.copy(purchaseMessage = "Обратитесь в клуб для оформления этой позиции.")
                            }
                        }
                        is ApiResult.Loading -> {}
                    }
                }
            }
        }
    }
    
    fun clearPurchaseMessage() {
        _uiState.update { it.copy(purchaseMessage = null, error = null) }
    }
}

private fun SubscriptionPlan.toShopSubscriptionItem(): ShopItem {
    val details = buildString {
        if (safeDescription.isNotBlank()) append(safeDescription)
        if (safeDurationDays > 0) {
            if (isNotBlank()) append(" ")
            append("Срок: $safeDurationDays дней.")
        }
        visitsCount?.let {
            if (isNotBlank()) append(" ")
            append("Посещений: $it.")
        }
    }.ifBlank { "Оформление абонемента на следующем экране." }

    return ShopItem(
        id = "sub-${safeId}",
        name = safeName.ifBlank { "Абонемент" },
        description = details,
        price = price,
        category = ShopCategory.SUBSCRIPTIONS,
        isPromo = isPopular,
        promoText = if (isPopular) "Популярный выбор" else null
    )
}
