package com.fitnessclub.app

import android.app.Application
import com.fitnessclub.app.push.PushChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FitnessClubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushChannels.ensureCreated(this)
    }
}
