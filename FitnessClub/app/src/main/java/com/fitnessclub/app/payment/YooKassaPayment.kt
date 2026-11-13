package com.fitnessclub.app.payment

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.data.repository.PurchaseSubscriptionOutcome

data class YooKassaPaymentRequest(
    val paymentId: Int,
    val amount: Double,
    val title: String,
    val subtitle: String,
    val phone: String?,
    val confirmationUrl: String?,
    val paymentMethodType: String?,
)

sealed class YooKassaPaymentResult {
    data class Completed(val paymentId: Int) : YooKassaPaymentResult()
    data object Cancelled : YooKassaPaymentResult()
    data class Failed(val message: String) : YooKassaPaymentResult()
}

object YooKassaPaymentContract : ActivityResultContract<YooKassaPaymentRequest, YooKassaPaymentResult>() {
    const val EXTRA_PAYMENT_ID = "yookassa_payment_id"
    const val EXTRA_AMOUNT = "yookassa_amount"
    const val EXTRA_TITLE = "yookassa_title"
    const val EXTRA_SUBTITLE = "yookassa_subtitle"
    const val EXTRA_PHONE = "yookassa_phone"
    const val EXTRA_CONFIRMATION_URL = "yookassa_confirmation_url"
    const val EXTRA_METHOD = "yookassa_method"
    const val EXTRA_MESSAGE = "yookassa_message"

    override fun createIntent(context: Context, input: YooKassaPaymentRequest): Intent {
        return Intent(context, YooKassaPaymentActivity::class.java)
            .putExtra(EXTRA_PAYMENT_ID, input.paymentId)
            .putExtra(EXTRA_AMOUNT, input.amount)
            .putExtra(EXTRA_TITLE, input.title)
            .putExtra(EXTRA_SUBTITLE, input.subtitle)
            .putExtra(EXTRA_PHONE, input.phone)
            .putExtra(EXTRA_CONFIRMATION_URL, input.confirmationUrl)
            .putExtra(EXTRA_METHOD, input.paymentMethodType)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): YooKassaPaymentResult {
        return when (resultCode) {
            Activity.RESULT_OK -> YooKassaPaymentResult.Completed(
                intent?.getIntExtra(EXTRA_PAYMENT_ID, 0) ?: 0,
            )
            Activity.RESULT_CANCELED -> YooKassaPaymentResult.Cancelled
            else -> YooKassaPaymentResult.Failed(
                intent?.getStringExtra(EXTRA_MESSAGE) ?: "Оплата не прошла",
            )
        }
    }
}

@Composable
fun rememberYooKassaPaymentLauncher(
    onFinished: (paymentId: Int) -> Unit,
    onError: (String) -> Unit,
): ActivityResultLauncher<YooKassaPaymentRequest> {
    return rememberLauncherForActivityResult(YooKassaPaymentContract) { result ->
        when (result) {
            is YooKassaPaymentResult.Completed -> {
                if (result.paymentId > 0) onFinished(result.paymentId) else onError("Оплата не прошла")
            }
            YooKassaPaymentResult.Cancelled -> onError("Оплата отменена")
            is YooKassaPaymentResult.Failed -> onError(result.message)
        }
    }
}

fun startOnlinePayment(
    context: Context,
    payment: PurchaseSubscriptionOutcome.PaymentRequired,
    yooKassaLauncher: ActivityResultLauncher<YooKassaPaymentRequest>,
    onAlfaOpened: () -> Unit,
    onError: (String) -> Unit,
) {
    if (payment.provider == "yookassa") {
        Brand.yooKassaSetupError()?.let {
            onError(it)
            return
        }
        yooKassaLauncher.launch(
            YooKassaPaymentRequest(
                paymentId = payment.paymentId,
                amount = payment.amount,
                title = payment.title.ifBlank { "Абонемент" },
                subtitle = Brand.name,
                phone = payment.userPhone,
                confirmationUrl = payment.paymentUrl,
                paymentMethodType = payment.paymentMethodType,
            ),
        )
        return
    }

    val url = payment.paymentUrl
    if (url.isNullOrBlank()) {
        onError("Не получен URL оплаты")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onError("Не удалось открыть страницу оплаты")
        return
    }
    onAlfaOpened()
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
