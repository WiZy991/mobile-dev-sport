package com.fitnessclub.app.ui.screens.subscriptions

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalUriHandler
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.config.LegalLinks
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.ui.screens.legal.LegalPdfScreen
import com.fitnessclub.app.ui.theme.AccentOrange
import com.fitnessclub.app.ui.theme.AppShapes
import com.fitnessclub.app.ui.theme.Error
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPlansScreen(
    onNavigateBack: () -> Unit,
    onPromoCode: () -> Unit,
    onNavigateToPayment: (paymentId: Int) -> Unit = {},
    onPurchaseSuccess: () -> Unit = {},
    onOpenLegalDocument: (LegalDocumentType) -> Unit = {},
    onOpenLegalPdf: (LegalPdfAsset) -> Unit = {},
    viewModel: SubscriptionPlansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPromoDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var showLegalConsentDialog by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var pdfOverlay by remember { mutableStateOf<LegalPdfAsset?>(null) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Купить абонемент") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { showPromoDialog = true }) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Промокод")
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
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(uiState.error!!, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadPlans() }) {
                        Text("Повторить")
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                ) {
                    // Applied promo code
                    if (uiState.appliedPromoCode != null) {
                        item {
                            PromoCodeAppliedCard(
                                code = uiState.appliedPromoCode!!,
                                discountPercent = uiState.promoDiscountPercent,
                                discountAmount = uiState.promoDiscountAmount,
                                onRemove = { viewModel.removePromoCode() }
                            )
                        }
                    }
                    
                    // Plans
                    items(
                        items = uiState.plans,
                        key = { it.safeId }
                    ) { plan ->
                        val discountedPrice = viewModel.discountedPrice(plan)
                        val hasDiscount = uiState.appliedPromoCode != null && discountedPrice < plan.price
                        SubscriptionPlanCard(
                            plan = plan,
                            finalPrice = if (hasDiscount) discountedPrice else null,
                            onPurchase = { showPurchaseDialog = plan }
                        )
                    }
                    
                    // Info
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Информация",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "• Оплата онлайн или в клубе\n" +
                                    "• ИП Мацкова Александра Сергеевна — реквизиты в приложении\n" +
                                    "• Если клуб включил проверку — подтверждение через Сбер ID перед оплатой\n" +
                                    "• Заморозка: 1 мес — нет; 3 мес — 14 дн.; 6 мес — 20 дн.; 12 мес — 30 дн.\n" +
                                    "• Возврат в течение 14 дней",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        LegalLinks.open(LegalDocumentType.REQUISITES, onOpenLegalPdf, onOpenLegalDocument)
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Реквизиты")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Promo code dialog
    if (showPromoDialog) {
        PromoCodeDialog(
            onDismiss = { showPromoDialog = false },
            onApply = { code ->
                viewModel.applyPromoCode(code)
                showPromoDialog = false
            },
            isLoading = uiState.isApplyingPromo
        )
    }
    
    // Purchase dialog
    var purchaseError by remember { mutableStateOf<String?>(null) }
    if (pdfOverlay == null && showLegalConsentDialog == null) {
        showPurchaseDialog?.let { plan ->
            val discountedPrice = viewModel.discountedPrice(plan)
            SubscriptionPurchaseConfirmDialog(
                plan = plan,
                finalPrice = discountedPrice,
                hasDiscount = uiState.appliedPromoCode != null && discountedPrice < plan.price,
                isLoading = uiState.isLoading,
                error = purchaseError,
                onDismiss = { showPurchaseDialog = null; purchaseError = null },
                onOpenRequisites = {
                    LegalLinks.open(LegalDocumentType.REQUISITES, onOpenLegalPdf, onOpenLegalDocument)
                },
                onConfirm = {
                    showPurchaseDialog = null
                    showLegalConsentDialog = plan
                },
            )
        }
    }

    if (pdfOverlay == null) {
        showLegalConsentDialog?.let { plan ->
            ClubPurchaseConsentDialog(
                context = uiState.clubPurchaseContext,
                onDismiss = { showLegalConsentDialog = null },
                onOpenPdf = { pdfOverlay = it },
                onOpenExternalUrl = { openExternalUrl(context, it) },
                onConfirm = {
                    purchaseError = null
                    showLegalConsentDialog = null
                    viewModel.purchasePlan(
                        plan = plan,
                        onPaymentRequired = { paymentId, paymentUrl ->
                            showPurchaseDialog = null
                            onNavigateToPayment(paymentId)
                            openPaymentUrl(context, paymentUrl)
                        },
                        onVerificationRequired = { url, message ->
                            showPurchaseDialog = null
                            scope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                            openExternalUrl(context, url)
                        },
                        onError = { msg ->
                            purchaseError = msg
                        }
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

private fun openPaymentUrl(context: Context, url: String) {
    openExternalUrl(context, url)
}

private fun openExternalUrl(context: Context, url: String) {
    val activity = context.findActivity() ?: return
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .launchUrl(activity, Uri.parse(url))
}

private fun openSberVerification(context: Context, url: String) = openExternalUrl(context, url)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PromoCodeAppliedCard(
    code: String,
    discountPercent: Double?,
    discountAmount: Double?,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Success.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Промокод применён",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            discountPercent != null -> "$code — скидка ${discountPercent.toInt()}%"
                            discountAmount != null -> "$code — скидка ${discountAmount.toInt()} ₽"
                            else -> code
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Success
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Удалить")
            }
        }
    }
}

@Composable
private fun SubscriptionPlanCard(
    plan: SubscriptionPlan,
    finalPrice: Double?,
    onPurchase: () -> Unit
) {
    val isPopular = plan.isPopular
    val borderColor = if (isPopular) Primary else MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isPopular) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = if (isPopular) 4.dp else 1.dp),
    ) {
        Column {
            if (isPopular) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Primary, AccentOrange)
                            )
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ПОПУЛЯРНЫЙ ВЫБОР",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = plan.safeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                if (plan.safeDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = plan.safeDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                plan.safeFeatures.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Success,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (plan.safeDurationDays > 0 || plan.visitsCount != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (plan.safeDurationDays > 0) {
                            AssistChip(
                                onClick = { },
                                enabled = false,
                                label = { Text("${plan.safeDurationDays} дней") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                        plan.visitsCount?.let { visits ->
                            AssistChip(
                                onClick = { },
                                enabled = false,
                                label = { Text("$visits посещений") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ConfirmationNumber,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (finalPrice != null) {
                            Text(
                                text = "${plan.price.toInt()} ₽",
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${finalPrice.toInt()} ₽",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Success,
                            )
                        } else {
                            Text(
                                text = "${plan.price.toInt()} ₽",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Button(
                        onClick = onPurchase,
                        modifier = Modifier.padding(start = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPopular) Primary else MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Купить")
                    }
                }
            }
        }
    }
}

@Composable
private fun PromoCodeDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    isLoading: Boolean
) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Введите промокод") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Промокод") },
                shape = AppShapes.medium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onApply(code) },
                enabled = code.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Применить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
