package com.fitnessclub.app.ui.screens.shop

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalUriHandler
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.config.LegalLinks
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.ui.screens.legal.LegalPdfScreen
import com.fitnessclub.app.ui.screens.subscriptions.ClubPurchaseConsentDialog
import com.fitnessclub.app.ui.screens.subscriptions.SubscriptionPurchaseConfirmDialog
import com.fitnessclub.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ShopCategory(val title: String) {
    SERVICES("Услуги"),
    SUBSCRIPTIONS("Абонементы"),
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
    val promoText: String? = null,
    val durationDays: Int? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    onNavigateBack: () -> Unit,
    onOpenPurchaseHistory: () -> Unit = {},
    onNavigateToPayment: (paymentId: Int) -> Unit = {},
    onOpenLegalDocument: (LegalDocumentType) -> Unit = {},
    onOpenLegalPdf: (LegalPdfAsset) -> Unit = {},
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory = uiState.selectedCategory
    var showPurchasePlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var showLegalConsentForPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var purchaseError by remember { mutableStateOf<String?>(null) }
    var pdfOverlay by remember { mutableStateOf<LegalPdfAsset?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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
            if (uiState.visibleCategories.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.visibleCategories.indexOf(selectedCategory).coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 16.dp
                ) {
                    uiState.visibleCategories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            text = { Text(category.title) }
                        )
                    }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
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

    if (pdfOverlay == null && showLegalConsentForPlan == null) {
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
                onOpenRequisites = {
                    LegalLinks.open(LegalDocumentType.REQUISITES, onOpenLegalPdf, onOpenLegalDocument)
                },
                onConfirm = {
                    showPurchasePlan = null
                    showLegalConsentForPlan = plan
                },
            )
        }
    }

    if (pdfOverlay == null) {
        showLegalConsentForPlan?.let { plan ->
            ClubPurchaseConsentDialog(
                context = uiState.clubPurchaseContext,
                onDismiss = { showLegalConsentForPlan = null },
                onOpenPdf = { pdfOverlay = it },
                onOpenExternalUrl = { openExternalUrl(context, it) },
                onConfirm = {
                    purchaseError = null
                    showLegalConsentForPlan = null
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
                }
            )
        }
    }

    pdfOverlay?.let { asset ->
        LegalPdfScreen(
            asset = asset,
            onNavigateBack = { pdfOverlay = null },
        )
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    onBuy: () -> Unit
) {
    val isSubscription = item.category == ShopCategory.SUBSCRIPTIONS
    val borderColor = when {
        item.isPromo && isSubscription -> Primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (item.isPromo && isSubscription) 2.dp else 1.dp,
            color = borderColor,
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.outlinedCardElevation(
            defaultElevation = if (item.isPromo && isSubscription) 4.dp else 1.dp,
        ),
    ) {
        Column {
            if (item.isPromo && isSubscription) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Primary, AccentOrange),
                            ),
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "АКЦИЯ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (item.isPromo && item.promoText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.promoText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Success,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (item.isPromo && !isSubscription) {
                        Surface(
                            color = AccentOrange,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "АКЦИЯ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item.durationDays?.takeIf { it > 0 }?.let { days ->
                    Spacer(modifier = Modifier.height(12.dp))
                    AssistChip(
                        onClick = { },
                        enabled = false,
                        label = { Text("$days дней") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (item.oldPrice != null) {
                            Text(
                                text = "${item.oldPrice.toInt()} ₽",
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "−${(item.oldPrice - item.price).toInt()} ₽",
                                style = MaterialTheme.typography.bodySmall,
                                color = Success,
                            )
                        }
                        Text(
                            text = when {
                                item.price == 0.0 -> "Бесплатно"
                                else -> "${item.price.toInt()} ₽"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (item.price == 0.0) Success else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (isSubscription) {
                        Button(
                            onClick = onBuy,
                            modifier = Modifier.padding(start = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.isPromo) Primary else MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Купить")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onBuy,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Text(if (item.price == 0.0) "Получить" else "Купить")
                        }
                    }
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
