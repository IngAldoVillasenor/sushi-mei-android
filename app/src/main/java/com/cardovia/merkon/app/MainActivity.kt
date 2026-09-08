package com.cardovia.merkon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cardovia.merkon.app.ui.theme.MerkonTheme
import com.cardovia.merkon.app.data.local.provideAuthRepository
import com.cardovia.merkon.app.ui.screens.AuthGateScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authRepository = provideAuthRepository(this)
        setContent {
            MerkonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthGateScreen(authRepository = authRepository)
                }
            }
        }
    }
}