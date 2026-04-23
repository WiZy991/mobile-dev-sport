package com.fitnessclub.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.repository.ProductRepository
import com.fitnessclub.app.data.repository.SubscriptionRepository
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
    val error: String? = null,
    val purchaseMessage: String? = null
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()
    
    init {
        loadItems()
    }
    
    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            runCatching {
                when (val result = productRepository.getProducts()) {
                    is ApiResult.Success -> {
                        val apiItems = result.data
                        val allItems = apiItems + getSubscriptionAndDepositItems()
                        _uiState.update {
                            it.copy(isLoading = false, items = allItems)
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = getFallbackItems(),
                                error = result.message
                            )
                        }
                    }
                    is ApiResult.Loading -> { /* no-op */ }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = getFallbackItems(),
                        error = e.message ?: "Ошибка загрузки"
                    )
                }
            }
        }
    }
    
    private suspend fun getSubscriptionAndDepositItems(): List<ShopItem> {
        val subscriptionItems = when (val result = subscriptionRepository.getSubscriptionPlansSuspend()) {
            is ApiResult.Success -> result.data.map { it.toShopSubscriptionItem() }
            else -> emptyList()
        }

        val depositItems = listOf(
            ShopItem(
                id = "dep-1",
                name = "Пополнение депозита",
                description = "Пополните депозит на ресепшене клуба.",
                price = 1000.0,
                category = ShopCategory.DEPOSITS
            )
        )

        return subscriptionItems + depositItems
    }

    private fun getFallbackItems(): List<ShopItem> = listOf(
        ShopItem(id = "srv-1", name = "Пробная тренировка", description = "Пробная тренировка — отличная возможность познакомиться с клубом.", price = 0.0, category = ShopCategory.SERVICES),
        ShopItem(id = "srv-2", name = "Йога", description = "Йога – отличный способ отвлечься от проблем.", price = 350.0, category = ShopCategory.SERVICES),
        ShopItem(id = "sub-1", name = "Абонемент", description = "Тарифы абонементов временно недоступны. Попробуйте позже.", price = 0.0, category = ShopCategory.SUBSCRIPTIONS),
        ShopItem(id = "good-1", name = "Полотенце с логотипом", description = "Мягкое хлопковое полотенце.", price = 800.0, category = ShopCategory.GOODS)
    ) + listOf(
        ShopItem(id = "dep-1", name = "Пополнение депозита", description = "Пополните депозит на ресепшене клуба.", price = 1000.0, category = ShopCategory.DEPOSITS)
    )
    
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
