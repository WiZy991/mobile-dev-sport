package com.fitnessclub.app.payment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.SubscriptionRepository
import com.fitnessclub.app.ui.theme.FitnessClubTheme
import com.fitnessclub.app.ui.theme.Primary
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.yoomoney.sdk.kassa.payments.Checkout
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.Amount
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.PaymentMethodType
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.PaymentParameters
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.SavePaymentMethod
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.TestParameters
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

private sealed class YooKassaStep {
    data object Idle : YooKassaStep()
    data class Confirm(val url: String) : YooKassaStep()
    data object Finished : YooKassaStep()
    data class Failed(val message: String) : YooKassaStep()
}

@HiltViewModel
class YooKassaPaymentViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<YooKassaStep>(YooKassaStep.Idle)
    val step = _step.asStateFlow()

    var confirmationLaunched: Boolean = false

    private var submitting = false

    fun confirm(url: String) {
        _step.value = YooKassaStep.Confirm(url)
    }

    fun submit(paymentId: Int, token: String) {
        if (submitting) return
        submitting = true
        viewModelScope.launch {
            when (val response = subscriptionRepository.submitYooKassaToken(paymentId, token)) {
                is ApiResult.Success -> {
                    val data = response.data
                    val url = data.confirmationUrl?.takeIf { it.isNotBlank() }
                        ?: data.paymentUrl?.takeIf { it.isNotBlank() }
                    when {
                        data.status == "failed" || data.status == "expired" || data.status == "cancelled" -> {
                            _step.value = YooKassaStep.Failed(data.failureReason ?: "Оплата не прошла")
                        }
                        !url.isNullOrBlank() && data.status != "paid" -> {
                            _step.value = YooKassaStep.Confirm(url)
                        }
                        else -> _step.value = YooKassaStep.Finished
                    }
                }
                is ApiResult.Error -> _step.value = YooKassaStep.Failed(response.message)
                is ApiResult.Loading -> Unit
            }
        }
    }
}

@AndroidEntryPoint
class YooKassaPaymentActivity : FragmentActivity() {

    private val paymentViewModel: YooKassaPaymentViewModel by viewModels()

    private var paymentMethod: PaymentMethodType = PaymentMethodType.BANK_CARD

    private val tokenizeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            finishCancelled()
            return@registerForActivityResult
        }
        val tokenization = runCatching { Checkout.createTokenizationResult(result.data!!) }.getOrNull()
        if (tokenization == null || tokenization.paymentToken.isBlank()) {
            finishFailed("Не удалось получить платёжный токен")
            return@registerForActivityResult
        }
        paymentMethod = tokenization.paymentMethodType
        paymentViewModel.submit(paymentId(), tokenization.paymentToken)
    }

    private val confirmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        finishCompleted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessClubTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Открываем оплату…")
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                paymentViewModel.step.collect { step ->
                    when (step) {
                        is YooKassaStep.Confirm -> launchConfirmation(step.url)
                        YooKassaStep.Finished -> finishCompleted()
                        is YooKassaStep.Failed -> finishFailed(step.message)
                        YooKassaStep.Idle -> Unit
                    }
                }
            }
        }

        if (savedInstanceState != null) return

        val setupError = com.fitnessclub.app.data.config.Brand.yooKassaSetupError()
        if (setupError != null) {
            finishFailed(setupError)
            return
        }

        val confirmationUrl = intent.getStringExtra(YooKassaPaymentContract.EXTRA_CONFIRMATION_URL)
        if (!confirmationUrl.isNullOrBlank()) {
            paymentMethod = parseMethod(intent.getStringExtra(YooKassaPaymentContract.EXTRA_METHOD))
            paymentViewModel.confirm(confirmationUrl)
        } else {
            startTokenize()
        }
    }

    private fun startTokenize() {
        val amount = intent.getDoubleExtra(YooKassaPaymentContract.EXTRA_AMOUNT, 0.0)
        if (amount <= 0.0) {
            finishFailed("Некорректная сумма оплаты")
            return
        }
        val parameters = PaymentParameters(
            amount = Amount(
                BigDecimal(String.format(Locale.US, "%.2f", amount)),
                Currency.getInstance("RUB"),
            ),
            title = intent.getStringExtra(YooKassaPaymentContract.EXTRA_TITLE) ?: "Абонемент",
            subtitle = intent.getStringExtra(YooKassaPaymentContract.EXTRA_SUBTITLE) ?: "Академия Борьбы",
            clientApplicationKey = BuildConfig.YOOKASSA_CLIENT_KEY,
            shopId = BuildConfig.YOOKASSA_SHOP_ID,
            savePaymentMethod = SavePaymentMethod.OFF,
            paymentMethodTypes = setOf(
                PaymentMethodType.BANK_CARD,
                PaymentMethodType.SBERBANK,
                PaymentMethodType.SBP,
                PaymentMethodType.YOO_MONEY,
            ),
            userPhoneNumber = normalizeRuPhone(intent.getStringExtra(YooKassaPaymentContract.EXTRA_PHONE)),
        )
        val intent = Checkout.createTokenizeIntent(
            this,
            parameters,
            TestParameters(showLogs = BuildConfig.DEBUG),
        )
        tokenizeLauncher.launch(intent)
    }

    private fun launchConfirmation(url: String) {
        if (paymentViewModel.confirmationLaunched) return
        paymentViewModel.confirmationLaunched = true
        val intent = runCatching {
            Checkout.createConfirmationIntent(
                context = this,
                confirmationUrl = url,
                paymentMethodType = paymentMethod,
                clientApplicationKey = BuildConfig.YOOKASSA_CLIENT_KEY,
                shopId = BuildConfig.YOOKASSA_SHOP_ID,
                testParameters = TestParameters(showLogs = BuildConfig.DEBUG),
            )
        }.getOrNull()
        if (intent == null) {
            finishCompleted()
            return
        }
        confirmLauncher.launch(intent)
    }

    private fun paymentId(): Int = intent.getIntExtra(YooKassaPaymentContract.EXTRA_PAYMENT_ID, 0)

    private fun finishCompleted() {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(YooKassaPaymentContract.EXTRA_PAYMENT_ID, paymentId()),
        )
        finish()
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun finishFailed(message: String) {
        setResult(
            Activity.RESULT_FIRST_USER,
            Intent().putExtra(YooKassaPaymentContract.EXTRA_MESSAGE, message),
        )
        finish()
    }

    private fun parseMethod(raw: String?): PaymentMethodType = when (raw?.lowercase()) {
        "yoo_money", "yoomoney" -> PaymentMethodType.YOO_MONEY
        "sberbank", "sberpay" -> PaymentMethodType.SBERBANK
        "sbp" -> PaymentMethodType.SBP
        else -> PaymentMethodType.BANK_CARD
    }

    private fun normalizeRuPhone(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        val normalized = when {
            digits.length == 11 && digits.startsWith("8") -> "7" + digits.drop(1)
            digits.length == 11 && digits.startsWith("7") -> digits
            digits.length == 10 -> "7$digits"
            else -> return null
        }
        return "+$normalized"
    }
}
