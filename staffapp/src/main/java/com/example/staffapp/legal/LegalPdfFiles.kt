package com.example.staffapp.legal

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Встроенные PDF правовых документов (assets/legal/). */
enum class StaffLegalPdf(
    val assetPath: String,
    val cacheFileName: String,
    val title: String,
) {
    USER_AGREEMENT(
        assetPath = "legal/user_agreement.pdf",
        cacheFileName = "user_agreement.pdf",
        title = "Пользовательское соглашение",
    ),
    PRIVACY(
        assetPath = "legal/privacy.pdf",
        cacheFileName = "privacy.pdf",
        title = "Политика конфиденциальности",
    ),
    PRO_OFFER(
        assetPath = "legal/pro_offer.pdf",
        cacheFileName = "pro_offer.pdf",
        title = "Оферта для специалистов",
    ),
    DOBROZAL_PRIVACY(
        assetPath = "legal/dobrozal_privacy.pdf",
        cacheFileName = "dobrozal_privacy.pdf",
        title = "Политика обработки и защиты персональных данных Клуба",
    ),
}

object LegalPdfFiles {
    const val CLUB_DOCS_URL = "https://dobrozal.ru/doc"

    fun resolve(context: Context, doc: StaffLegalPdf): File {
        val out = File(context.cacheDir, "legal_${doc.cacheFileName}")
        context.assets.open(doc.assetPath).use { input ->
            val bytes = input.readBytes()
            if (!out.exists() || out.length() != bytes.size.toLong()) {
                FileOutputStream(out).use { it.write(bytes) }
            }
        }
        return out
    }
}
