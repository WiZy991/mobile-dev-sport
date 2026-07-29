package com.fitnessclub.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.fitnessclub.app.data.auth.PaymentDeepLinkBus
import com.fitnessclub.app.data.auth.SberAuthDeepLinkBus
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import com.fitnessclub.app.data.local.AppLanguage
import com.fitnessclub.app.data.local.AppSettingsStore
import com.fitnessclub.app.data.local.ThemeMode
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.push.PushTokenRegistrar
import com.fitnessclub.app.push.RequestNotificationPermission
import com.fitnessclub.app.ui.navigation.NavGraph
import com.fitnessclub.app.ui.theme.FitnessClubTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var appSettingsStore: AppSettingsStore

    @Inject
    lateinit var pushTokenRegistrar: PushTokenRegistrar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: на API 35+ система игнорирует decorFits=true; хэдер должен заходить под статус-бар.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        dispatchDeepLinks(intent)

        setContent {
            val themeMode by appSettingsStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val appLanguage by appSettingsStore.appLanguage.collectAsState(initial = AppLanguage.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(appLanguage) {
                val locales = if (appLanguage == AppLanguage.SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(appLanguage.tag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }

            FitnessClubTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val isLoggedIn by authRepository.isLoggedIn().collectAsState(initial = false)

                    RequestNotificationPermission(enabled = isLoggedIn)

                    LaunchedEffect(isLoggedIn) {
                        if (isLoggedIn) {
                            pushTokenRegistrar.register()
                        }
                    }
                    
                    NavGraph(
                        navController = navController,
                        isLoggedIn = isLoggedIn
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchDeepLinks(intent)
    }

    override fun onResume() {
        super.onResume()
        dispatchDeepLinks(intent)
    }

    private fun dispatchDeepLinks(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "worldfitness" && data.host == "auth" && data.path == "/callback") {
            SberAuthDeepLinkBus.publish(data)
        }
        if (data.scheme == "worldfitness" && data.host == "payment" && data.path == "/callback") {
            PaymentDeepLinkBus.publish(data)
        }
    }
}
