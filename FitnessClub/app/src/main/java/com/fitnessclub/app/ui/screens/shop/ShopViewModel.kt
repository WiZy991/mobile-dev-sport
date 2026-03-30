package com.fitnessclub.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.ProductRepository
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
    private val productRepository: ProductRepository
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
                        val subDepItems = getMockSubscriptionAndDepositItems()
                        val allItems = if (apiItems.isNotEmpty()) apiItems + subDepItems else getMockItems()
                        _uiState.update {
                            it.copy(isLoading = false, items = allItems)
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                items = getMockItems(),
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
                        items = getMockItems(),
                        error = e.message ?: "Ошибка загрузки"
                    )
                }
            }
        }
    }
    
    private fun getMockSubscriptionAndDepositItems(): List<ShopItem> = listOf(
        ShopItem(id = "sub-1", name = "Безлимит на месяц", description = "Неограниченное посещение всех занятий. Купите в разделе Абонементы.", price = 4500.0, category = ShopCategory.SUBSCRIPTIONS),
        ShopItem(id = "sub-2", name = "Безлимит на 3 месяца", description = "Неограниченное посещение на 3 месяца + заморозка.", price = 12000.0, category = ShopCategory.SUBSCRIPTIONS),
        ShopItem(id = "dep-1", name = "Пополнение депозита", description = "Пополните депозит в клубе.", price = 1000.0, category = ShopCategory.DEPOSITS)
    )

    private fun getMockItems(): List<ShopItem> = listOf(
        ShopItem(id = "srv-1", name = "Пробная тренировка", description = "Пробная тренировка — отличная возможность познакомиться с клубом.", price = 0.0, category = ShopCategory.SERVICES),
        ShopItem(id = "srv-2", name = "Йога", description = "Йога – отличный способ отвлечься от проблем.", price = 350.0, category = ShopCategory.SERVICES),
        ShopItem(id = "sub-1", name = "Безлимит на месяц", description = "Неограниченное посещение всех занятий.", price = 4500.0, category = ShopCategory.SUBSCRIPTIONS),
        ShopItem(id = "good-1", name = "Полотенце с логотипом", description = "Мягкое хлопковое полотенце.", price = 800.0, category = ShopCategory.GOODS)
    ) + getMockSubscriptionAndDepositItems()
    
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
                item.id.startsWith("sub-") -> {
                    _uiState.update {
                        it.copy(purchaseMessage = "Абонементы — в разделе «Абонементы»")
                    }
                }
                item.price == 0.0 -> {
                    _uiState.update {
                        it.copy(purchaseMessage = "Оформите услугу в клубе")
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(purchaseMessage = "Оформите покупку в клубе")
                    }
                }
            }
        }
    }
    
    fun clearPurchaseMessage() {
        _uiState.update { it.copy(purchaseMessage = null, error = null) }
    }
}
