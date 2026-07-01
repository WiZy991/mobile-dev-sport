package com.fitnessclub.app

import android.content.Intent
import android.os.Bundle
import com.fitnessclub.app.data.auth.PaymentDeepLinkBus
import com.fitnessclub.app.data.auth.SberAuthDeepLinkBus
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.ui.navigation.NavGraph
import com.fitnessclub.app.ui.theme.FitnessClubTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        dispatchDeepLinks(intent)

        setContent {
            FitnessClubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val isLoggedIn by authRepository.isLoggedIn().collectAsState(initial = false)
                    
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
