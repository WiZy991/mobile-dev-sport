package com.fitnessclub.app.payment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** Доброзал платит через Альфу. Этот экран не должен открываться. */
class YooKassaPaymentActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            Activity.RESULT_FIRST_USER,
            Intent().putExtra(
                YooKassaPaymentContract.EXTRA_MESSAGE,
                "Оплата ЮKassa доступна в приложении Академии борьбы",
            ),
        )
        finish()
    }
}
