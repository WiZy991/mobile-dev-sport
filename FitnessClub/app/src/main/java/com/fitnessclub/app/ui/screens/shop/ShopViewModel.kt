package com.fitnessclub.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShopUiState(
    val isLoading: Boolean = true,
    val items: List<ShopItem> = emptyList()
)

@HiltViewModel
class ShopViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()
    
    init {
        loadItems()
    }
    
    private fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val items = listOf(
                // Services
                ShopItem(
                    id = "srv-1",
                    name = "Пробная тренировка",
                    description = "Пробная тренировка — это отличная возможность познакомиться с клубом, получить консультацию тренера и пройти исследование состава тела.",
                    price = 0.0,
                    category = ShopCategory.SERVICES
                ),
                ShopItem(
                    id = "srv-2",
                    name = "Йога",
                    description = "Йога – это отличный способ отвлечься от проблем и избавиться от ненужных мыслей.",
                    price = 350.0,
                    oldPrice = 500.0,
                    category = ShopCategory.SERVICES,
                    isPromo = true,
                    promoText = "действует персональное предложение"
                ),
                ShopItem(
                    id = "srv-3",
                    name = "Пилатес",
                    description = "Укрепление мышц кора, улучшение осанки и гибкости.",
                    price = 500.0,
                    category = ShopCategory.SERVICES
                ),
                ShopItem(
                    id = "srv-4",
                    name = "Персональная тренировка",
                    description = "Индивидуальное занятие с персональным тренером.",
                    price = 2000.0,
                    category = ShopCategory.SERVICES
                ),
                
                // Subscriptions
                ShopItem(
                    id = "sub-1",
                    name = "Безлимит на месяц",
                    description = "Неограниченное посещение всех групповых занятий в течение месяца.",
                    price = 4500.0,
                    oldPrice = 5000.0,
                    category = ShopCategory.SUBSCRIPTIONS,
                    isPromo = true,
                    promoText = "скидка 10% при онлайн оплате"
                ),
                ShopItem(
                    id = "sub-2",
                    name = "Безлимит на 3 месяца",
                    description = "Неограниченное посещение на 3 месяца + заморозка 14 дней.",
                    price = 12000.0,
                    category = ShopCategory.SUBSCRIPTIONS
                ),
                ShopItem(
                    id = "sub-3",
                    name = "Безлимит на год",
                    description = "Максимальная выгода! Безлимит на год + заморозка 30 дней.",
                    price = 36000.0,
                    oldPrice = 48000.0,
                    category = ShopCategory.SUBSCRIPTIONS,
                    isPromo = true,
                    promoText = "скидка 25%!"
                ),
                ShopItem(
                    id = "sub-4",
                    name = "8 занятий",
                    description = "8 групповых занятий на выбор в течение месяца.",
                    price = 3500.0,
                    category = ShopCategory.SUBSCRIPTIONS
                ),
                
                // Deposits
                ShopItem(
                    id = "dep-1",
                    name = "Пополнение 1000 руб.",
                    description = "Пополните депозит и оплачивайте услуги со скидкой 5%.",
                    price = 1000.0,
                    category = ShopCategory.DEPOSITS
                ),
                ShopItem(
                    id = "dep-2",
                    name = "Пополнение 5000 руб.",
                    description = "Пополните депозит и получите бонус 500 руб.",
                    price = 5000.0,
                    category = ShopCategory.DEPOSITS,
                    isPromo = true,
                    promoText = "+500 руб. бонусом"
                ),
                ShopItem(
                    id = "dep-3",
                    name = "Пополнение 10000 руб.",
                    description = "Пополните депозит и получите бонус 1500 руб.",
                    price = 10000.0,
                    category = ShopCategory.DEPOSITS,
                    isPromo = true,
                    promoText = "+1500 руб. бонусом"
                ),
                
                // Goods
                ShopItem(
                    id = "good-1",
                    name = "Полотенце с логотипом",
                    description = "Мягкое хлопковое полотенце с логотипом клуба.",
                    price = 800.0,
                    category = ShopCategory.GOODS
                ),
                ShopItem(
                    id = "good-2",
                    name = "Бутылка для воды",
                    description = "Спортивная бутылка 750 мл.",
                    price = 600.0,
                    category = ShopCategory.GOODS
                ),
                ShopItem(
                    id = "good-3",
                    name = "Перчатки для фитнеса",
                    description = "Профессиональные перчатки для тренировок.",
                    price = 1200.0,
                    category = ShopCategory.GOODS
                )
            )
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    items = items
                )
            }
        }
    }
    
    fun buyItem(item: ShopItem) {
        // TODO: Implement purchase flow
    }
}
