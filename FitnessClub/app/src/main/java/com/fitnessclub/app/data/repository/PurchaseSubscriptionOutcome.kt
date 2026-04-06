package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.model.Subscription

sealed class PurchaseSubscriptionOutcome {
    data class Success(val subscription: Subscription) : PurchaseSubscriptionOutcome()

    /** Сервер вернул 403 — нужно пройти Сбер ID, затем повторить покупку. */
    data class VerificationRequired(val authorizeUrl: String, val message: String) : PurchaseSubscriptionOutcome()

    data class Error(val message: String, val httpCode: Int? = null) : PurchaseSubscriptionOutcome()
}
