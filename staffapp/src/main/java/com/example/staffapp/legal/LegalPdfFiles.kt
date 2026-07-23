package com.example.staffapp.legal

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object LegalPdfFiles {
    const val DOBROZAL_OFFER_ASSET = "legal/dobrozal_offer.pdf"
    const val DOBROZAL_OFFER_TITLE = "Публичная оферта"

    fun resolveDobrozalOffer(context: Context): File {
        val out = File(context.cacheDir, "legal_dobrozal_offer.pdf")
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(DOBROZAL_OFFER_ASSET).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        return out
    }
}
