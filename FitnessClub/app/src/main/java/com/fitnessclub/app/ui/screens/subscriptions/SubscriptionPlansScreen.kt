package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPlansScreen(
    onNavigateBack: () -> Unit,
    onPromoCode: () -> Unit,
    viewModel: SubscriptionPlansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPromoDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf<SubscriptionPlan?>(null) }
    
    Scaffold(
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Applied promo code
                    if (uiState.appliedPromoCode != null) {
                        item {
                            PromoCodeAppliedCard(
                                code = uiState.appliedPromoCode!!,
                                discount = uiState.promoDiscount,
                                onRemove = { viewModel.removePromoCode() }
                            )
                        }
                    }
                    
                    // Plans
                    items(uiState.plans) { plan ->
                        SubscriptionPlanCard(
                            plan = plan,
                            discount = if (uiState.appliedPromoCode != null) uiState.promoDiscount else 0,
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
                                    "• Заморозка до 14 дней\n" +
                                    "• Возврат в течение 14 дней",
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
    showPurchaseDialog?.let { plan ->
        PurchaseConfirmDialog(
            plan = plan,
            discount = if (uiState.appliedPromoCode != null) uiState.promoDiscount else 0,
            onDismiss = { showPurchaseDialog = null },
            onConfirm = {
                viewModel.purchasePlan(plan)
                showPurchaseDialog = null
            }
        )
    }
}

@Composable
private fun PromoCodeAppliedCard(
    code: String,
    discount: Int,
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
                        "$code — скидка $discount%",
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
    discount: Int,
    onPurchase: () -> Unit
) {
    val isPopular = plan.isPopular
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPopular) {
                    Modifier.border(2.dp, Primary, RoundedCornerShape(16.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // Popular badge
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
            
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Features
                plan.features.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Success
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(feature, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Duration and visits
                Row {
                    if (plan.durationDays > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${plan.durationDays} дней") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    plan.visitsCount?.let { visits ->
                        AssistChip(
                            onClick = {},
                            label = { Text("$visits посещений") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (discount > 0) {
                            Text(
                                text = "${plan.price.toInt()} ₽",
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(plan.price * (100 - discount) / 100).toInt()} ₽",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Success
                            )
                        } else {
                            Text(
                                text = "${plan.price.toInt()} ₽",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Button(
                        onClick = onPurchase,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPopular) Primary else MaterialTheme.colorScheme.primary
                        )
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

@Composable
private fun PurchaseConfirmDialog(
    plan: SubscriptionPlan,
    discount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val finalPrice = if (discount > 0) {
        (plan.price * (100 - discount) / 100).toInt()
    } else {
        plan.price.toInt()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверждение покупки") },
        text = {
            Column {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Срок: ${plan.durationDays} дней",
                    style = MaterialTheme.typography.bodyMedium
                )
                plan.visitsCount?.let {
                    Text(
                        text = "Посещений: $it",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "К оплате: $finalPrice ₽",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Оплатить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
