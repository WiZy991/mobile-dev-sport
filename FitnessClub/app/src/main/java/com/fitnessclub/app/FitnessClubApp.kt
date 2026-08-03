package com.fitnessclub.app

import android.app.Application
import com.fitnessclub.app.push.PushChannels
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class FitnessClubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushChannels.ensureCreated(this)
        // osmdroid: кэш в sandbox приложения + обязательный User-Agent
        val base = File(cacheDir, "osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            load(this@FitnessClubApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
    }
}
