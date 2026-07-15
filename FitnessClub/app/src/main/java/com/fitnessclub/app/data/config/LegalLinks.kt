package com.fitnessclub.app.data.config

/** Открытие правовых документов: встроенный PDF-экран или реквизиты в приложении. */
object LegalLinks {
    fun pdfFor(type: LegalDocumentType): LegalPdfAsset? = when (type) {
        LegalDocumentType.REQUISITES -> null
        LegalDocumentType.TERMS,
        LegalDocumentType.CLIENT_AGREEMENT,
        LegalDocumentType.TRAINER_AGREEMENT -> LegalPdfAsset.DOBROZAL_OFFER
        LegalDocumentType.PRIVACY -> LegalPdfAsset.PRIVACY_POLICY
        LegalDocumentType.PERSONAL_DATA_CONSENT -> LegalPdfAsset.CONSENT_USER
    }

    fun open(
        type: LegalDocumentType,
        onOpenPdf: (LegalPdfAsset) -> Unit,
        openInApp: (LegalDocumentType) -> Unit,
    ) {
        val pdf = pdfFor(type)
        if (pdf != null) {
            onOpenPdf(pdf)
        } else {
            openInApp(type)
        }
    }
}
