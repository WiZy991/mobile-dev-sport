package com.fitnessclub.app.data.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SberAuthDeepLinkBus {
    private val _events = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun publish(uri: Uri) {
        _events.tryEmit(uri)
    }
}
