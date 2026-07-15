package com.fitnessclub.app.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.ClubShopConfig
import com.fitnessclub.app.data.model.ClubShopCounts
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.data.repository.ProductRepository
import com.fitnessclub.app.data.repository.PurchaseSubscriptionOutcome
import com.fitnessclub.app.data.repository.SubscriptionRepository
import com.fitnessclub.app.ui.screens.subscriptions.ClubPurchaseContext
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
    val visibleCategories: List<ShopCategory> = ShopCategory.entries.toList(),
    val selectedCategory: ShopCategory = ShopCategory.SUBSCRIPTIONS,
    val isPurchasing: Boolean = false,
    val error: String? = null,
    val purchaseMessage: String? = null,
    val clubPurchaseContext: ClubPurchaseContext = ClubPurchaseContext(clubName = "Ваш клуб"),
)

@HiltViewModel
class ShopViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val clubRepository: ClubRepository,
) : ViewModel() {

    private var shopConfig: ClubShopConfig? = null

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    init {
        loadClubPurchaseContext()
        loadItems()
    }

    private fun loadClubPurchaseContext() {
        viewModelScope.launch {
            when (val result = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    val club = result.data
                    shopConfig = club.shopConfig
                    val context = ClubPurchaseContext(
                        clubName = club.name.ifBlank { "Ваш клуб" },
                        visitingRulesUrl = club.visitingRulesUrl,
                        safetyRulesUrl = club.safetyRulesUrl,
                    )
                    _uiState.update { state ->
                        val tabs = resolveVisibleCategories(state.items, shopConfig)
                        state.copy(
                            clubPurchaseContext = context,
                            visibleCategories = tabs,
                            selectedCategory = resolveDefaultCategory(tabs, shopConfig),
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = productRepository.getProducts()) {
                is ApiResult.Success -> {
                    val (subscriptionItems, plansByItemId) = getSubscriptionItems()
                    val allItems = result.data + subscriptionItems
                    val tabs = resolveVisibleCategories(allItems, shopConfig)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = allItems,
                            subscriptionPlansByItemId = plansByItemId,
                            visibleCategories = tabs,
                            selectedCategory = if (it.selectedCategory in tabs) {
                                it.selectedCategory
                            } else {
                                resolveDefaultCategory(tabs, shopConfig)
                            },
                            error = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    val (subscriptionItems, plansByItemId) = getSubscriptionItems()
                    val allItems = subscriptionItems
                    val tabs = resolveVisibleCategories(allItems, shopConfig)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = allItems,
                            subscriptionPlansByItemId = plansByItemId,
                            visibleCategories = tabs,
                            selectedCategory = resolveDefaultCategory(tabs, shopConfig),
                            error = result.message,
                        )
                    }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun selectCategory(category: ShopCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun resolveVisibleCategories(
        items: List<ShopItem>,
        config: ClubShopConfig?,
    ): List<ShopCategory> {
        val counts = config?.counts ?: ClubShopCounts()
        val hideEmpty = config?.hideEmptyTabs ?: true
        val order = config?.tabOrder?.takeIf { it.isNotEmpty() }
            ?: listOf("subscriptions", "services", "goods")

        val mapped = order.mapNotNull { key -> categoryFromApiKey(key) }
        val categories = mapped.mapNotNull { category ->
            val count = categoryItemCount(category, items, counts)
            if (hideEmpty && count == 0) null else category
        }

        return categories.ifEmpty { listOf(ShopCategory.SUBSCRIPTIONS) }
    }

    private fun resolveDefaultCategory(
        visible: List<ShopCategory>,
        config: ClubShopConfig?,
    ): ShopCategory {
        if (visible.isEmpty()) return ShopCategory.SUBSCRIPTIONS
        val preferred = categoryFromApiKey(config?.defaultTab ?: "subscriptions")
        return if (preferred != null && preferred in visible) preferred else visible.first()
    }

    private fun categoryFromApiKey(key: String): ShopCategory? = when (key) {
        "subscriptions" -> ShopCategory.SUBSCRIPTIONS
        "services" -> ShopCategory.SERVICES
        "goods" -> ShopCategory.GOODS
        else -> null
    }

    private fun categoryItemCount(
        category: ShopCategory,
        items: List<ShopItem>,
        counts: ClubShopCounts,
    ): Int {
        val fromItems = items.count { it.category == category }
        val fromApi = when (category) {
            ShopCategory.SUBSCRIPTIONS -> counts.subscriptions
            ShopCategory.SERVICES -> counts.services
            ShopCategory.GOODS -> counts.goods
        }
        return maxOf(fromItems, fromApi)
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
                        is ApiResult.Loading -> Unit
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
                                    purchaseMessage = "Услуга из каталога клуба оформляется после появления её в приложении. Запишитесь через «Расписание».",
                                )
                            }
                        }
                        is ApiResult.Loading -> Unit
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
                        is ApiResult.Loading -> Unit
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
                        is ApiResult.Loading -> Unit
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
        promoText = if (isPopular) "Популярный выбор" else null,
        durationDays = safeDurationDays.takeIf { it > 0 },
    )
}
