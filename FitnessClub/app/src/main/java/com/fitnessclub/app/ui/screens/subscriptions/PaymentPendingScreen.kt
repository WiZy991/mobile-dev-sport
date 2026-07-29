package com.fitnessclub.app.ui.screens.subscriptions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fitnessclub.app.data.auth.PaymentDeepLinkBus
import com.fitnessclub.app.ui.theme.Error
import com.fitnessclub.app.ui.theme.Primary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentPendingScreen(
    paymentId: Int,
    onNavigateBack: () -> Unit,
    onPaymentSuccess: () -> Unit,
    viewModel: PaymentPendingViewModel = hiltViewModel(),
) {
    var statusMessage by remember { mutableStateOf("Ожидаем подтверждение оплаты…") }
    var isFailed by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current

    val leaveScreen: () -> Unit = {
        if (isFailed) {
            onNavigateBack()
        } else if (!leaving) {
            leaving = true
            scope.launch {
                val handled = viewModel.leaveAndCheckOnce(paymentId)
                // Success/Failed обработает collectLatest; если статус ещё pending — уходим.
                if (!handled) {
                    onNavigateBack()
                } else {
                    leaving = false
                }
            }
        }
    }

    BackHandler(onBack = leaveScreen)

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelPolling() }
    }

    LaunchedEffect(paymentId) {
        viewModel.pollPaymentStatus(paymentId)
    }

    LaunchedEffect(paymentId) {
        PaymentDeepLinkBus.events.collect { uri ->
            val returnedId = uri.getQueryParameter("payment_id")?.toIntOrNull()
            if (returnedId == paymentId) {
                viewModel.resumePolling(paymentId)
            }
        }
    }

    DisposableEffect(lifecycleOwner, paymentId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resumePolling(paymentId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PaymentPendingEvent.Success -> {
                    statusMessage = "Оплата прошла успешно"
                    onPaymentSuccess()
                }
                is PaymentPendingEvent.Failed -> {
                    isFailed = true
                    leaving = false
                    statusMessage = event.message
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оплата") },
                navigationIcon = {
                    IconButton(onClick = leaveScreen) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                if (isFailed) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Error,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                if (!isFailed) {
                    Text(
                        text = "Если вы закрыли страницу банка без оплаты — нажмите «Вернуться».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedButton(onClick = leaveScreen, enabled = !leaving || isFailed) {
                    Text(if (isFailed) "Закрыть" else "Вернуться")
                }
            }
        }
    }
}
