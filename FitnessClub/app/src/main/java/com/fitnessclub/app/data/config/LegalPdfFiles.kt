package com.fitnessclub.app.data.config

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Копирует встроенный PDF из assets в кэш для просмотра в приложении. */
object LegalPdfFiles {
    fun resolve(context: Context, asset: LegalPdfAsset): File {
        val out = File(context.cacheDir, "legal_${asset.cacheFileName}")
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(asset.assetPath).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        return out
    }
}
