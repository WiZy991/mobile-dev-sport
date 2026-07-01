package com.fitnessclub.app.ui.screens.shop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.ui.screens.subscriptions.SubscriptionPurchaseConfirmDialog
import com.fitnessclub.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ShopCategory(val title: String) {
    SERVICES("Услуги"),
    SUBSCRIPTIONS("Абонементы"),
    DEPOSITS("Депозиты"),
    GOODS("Товары")
}

data class ShopItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val oldPrice: Double? = null,
    val category: ShopCategory,
    val isPromo: Boolean = false,
    val promoText: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    onNavigateBack: () -> Unit,
    onOpenPurchaseHistory: () -> Unit = {},
    onNavigateToPayment: (paymentId: Int) -> Unit = {},
    onOpenLegalDocument: (LegalDocumentType) -> Unit = {},
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf(ShopCategory.SERVICES) }
    var showPurchasePlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var purchaseError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(uiState.purchaseMessage, uiState.error) {
        uiState.purchaseMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearPurchaseMessage()
        }
        uiState.error?.let { err ->
            snackbarHostState.showSnackbar(err, withDismissAction = true)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Магазин") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPurchaseHistory) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Мои покупки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = ShopCategory.entries.indexOf(selectedCategory),
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp
            ) {
                ShopCategory.entries.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = { Text(category.title) }
                    )
                }
            }
            
            // Items list
            val filteredItems = uiState.items.filter { it.category == selectedCategory }
            
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Товары скоро появятся",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems) { item ->
                        ShopItemCard(
                            item = item,
                            onBuy = {
                                val plan = viewModel.planForItem(item.id)
                                if (plan != null) {
                                    purchaseError = null
                                    showPurchasePlan = plan
                                } else {
                                    viewModel.buyItem(item)
                                }
                            },
                        )
                    }
                }
            }
        }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    showPurchasePlan?.let { plan ->
        SubscriptionPurchaseConfirmDialog(
            plan = plan,
            finalPrice = plan.price,
            hasDiscount = false,
            isLoading = uiState.isPurchasing,
            error = purchaseError,
            onDismiss = {
                showPurchasePlan = null
                purchaseError = null
            },
            onOpenRequisites = { onOpenLegalDocument(LegalDocumentType.REQUISITES) },
            onConfirm = {
                purchaseError = null
                viewModel.purchaseSubscriptionPlan(
                    plan = plan,
                    onPaymentRequired = { paymentId, paymentUrl ->
                        showPurchasePlan = null
                        onNavigateToPayment(paymentId)
                        openPaymentUrl(context, paymentUrl)
                    },
                    onVerificationRequired = { url, message ->
                        showPurchasePlan = null
                        scope.launch { snackbarHostState.showSnackbar(message) }
                        openExternalUrl(context, url)
                    },
                    onError = { msg -> purchaseError = msg },
                )
            },
        )
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (item.isPromo && item.promoText != null) {
                        Text(
                            text = item.promoText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Success,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                if (item.isPromo) {
                    Surface(
                        color = AccentOrange,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "АКЦИЯ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (item.oldPrice != null) {
                        Text(
                            text = "ЦЕНА: ${item.oldPrice.toInt()} руб.",
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "СКИДКА: ${(item.oldPrice - item.price).toInt()} руб.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Error
                        )
                    }
                    Text(
                        text = if (item.price == 0.0) "БЕСПЛАТНО" else "ИТОГО: ${item.price.toInt()} руб.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.price == 0.0) Success else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                OutlinedButton(onClick = onBuy) {
                    Text(if (item.price == 0.0) "ПОЛУЧИТЬ" else "КУПИТЬ")
                }
            }
        }
    }
}

private fun openPaymentUrl(context: Context, url: String) = openExternalUrl(context, url)

private fun openExternalUrl(context: Context, url: String) {
    val activity = context.findActivity() ?: return
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(activity, Uri.parse(url))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
