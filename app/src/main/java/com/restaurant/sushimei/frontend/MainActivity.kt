package com.restaurant.sushimei.frontend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.restaurant.sushimei.frontend.ui.theme.SushiMeiTheme
import com.restaurant.sushimei.frontend.data.local.provideAuthRepository
import com.restaurant.sushimei.frontend.ui.screens.AuthGateScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authRepository = provideAuthRepository(this)
        setContent {
            SushiMeiTheme {
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