package com.statushub

import android.content.Intent
import com.statushub.ads.ConsentManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.statushub.navigation.NavGraph
import com.statushub.ui.theme.StatusHubTheme
import com.statushub.viewmodel.MainViewModel
import com.statushub.viewmodel.SplashViewModel

/**
 * Main Activity - Entry point of the application
 * Sets up navigation and ViewModels
 */
class MainActivity : ComponentActivity() {

    /** Text shared to the app (e.g. Instagram → Share → SaveVibe), consumed by the Downloader tab */
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShareIntent(intent)
        // Consent (EEA/UK) first, then ads
        ConsentManager.gatherConsentAndStartAds(this)
        
        setContent {
            StatusHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Initialize ViewModels
                    val splashViewModel: SplashViewModel = viewModel()
                    val mainViewModel: MainViewModel = viewModel()
                    
                    // Navigation controller
                    val navController = rememberNavController()
                    
                    // Navigation graph
                    NavGraph(
                        navController = navController,
                        splashViewModel = splashViewModel,
                        mainViewModel = mainViewModel,
                        sharedText = sharedText.value,
                        onSharedTextConsumed = { sharedText.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it }
        }
    }
}
