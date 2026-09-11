package com.cardovia.merkon.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cardovia.merkon.app.ui.theme.MerkonTheme
import com.cardovia.merkon.app.data.local.provideAuthRepository
import com.cardovia.merkon.app.ui.screens.AuthGateScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val _deepLinkToken = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authRepository = provideAuthRepository(this)

        handleIntent(intent)

        setContent {
            val deepLinkToken by _deepLinkToken.collectAsState()

            MerkonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthGateScreen(
                        authRepository = authRepository,
                        deepLinkToken = deepLinkToken,
                        onDeepLinkTokenConsumed = { _deepLinkToken.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val urlStr = intent.dataString ?: return
            val token = com.cardovia.merkon.app.util.VerificationTokenParser.parse(urlStr, BuildConfig.VERIFICATION_HOST)
            if (token != null) {
                _deepLinkToken.value = token
            }
            // Scrub intent to prevent reuse on rotation/recreation
            intent.data = null
        }
    }
}
