package com.fitnessclub.app.data.config

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Копирует встроенный PDF из assets в кэш для просмотра в приложении. */
object LegalPdfFiles {
    fun resolve(context: Context, asset: LegalPdfAsset): File {
        val out = File(context.cacheDir, "legal_${asset.cacheFileName}")
        context.assets.open(asset.assetPath).use { input ->
            val bytes = input.readBytes()
            // Обновляем кэш, если APK принёс новую версию документа (другой размер).
            if (!out.exists() || out.length() != bytes.size.toLong()) {
                FileOutputStream(out).use { it.write(bytes) }
            }
        }
        return out
    }
}
