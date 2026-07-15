package com.fitnessclub.app.data.config

/**
 * Документы клуба при покупке абонемента — всегда открываются в приложении.
 * Не зависят от настроек CRM и не подменяются документами WorldCashFit.
 */
object ClubPurchaseDocuments {
    /** Договор с клиентом (ИП Мацкова / оферта клуба) */
    val offer = LegalDocumentType.CLIENT_AGREEMENT

    /** Политика по обработке персональных данных */
    val privacy = LegalDocumentType.PRIVACY
}
