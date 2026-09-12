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
    private val _deepLinkEvent = MutableStateFlow<com.cardovia.merkon.app.data.model.DeepLinkEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authRepository = provideAuthRepository(this)

        handleIntent(intent)

        setContent {
            val deepLinkEvent by _deepLinkEvent.collectAsState()

            MerkonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthGateScreen(
                        authRepository = authRepository,
                        deepLinkEvent = deepLinkEvent,
                        onDeepLinkEventConsumed = { _deepLinkEvent.value = null }
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

            val verificationToken = com.cardovia.merkon.app.util.VerificationTokenParser.parse(urlStr, BuildConfig.VERIFICATION_HOST)
            if (verificationToken != null) {
                _deepLinkEvent.value = com.cardovia.merkon.app.data.model.DeepLinkEvent.Verification(verificationToken)
            } else {
                val resetToken = com.cardovia.merkon.app.util.PasswordResetTokenParser.parse(urlStr, BuildConfig.PASSWORD_RESET_HOST)
                if (resetToken != null) {
                    _deepLinkEvent.value = com.cardovia.merkon.app.data.model.DeepLinkEvent.PasswordReset(resetToken)
                }
            }
            // Scrub intent to prevent reuse on rotation/recreation
            intent.data = null
        }
    }
}
