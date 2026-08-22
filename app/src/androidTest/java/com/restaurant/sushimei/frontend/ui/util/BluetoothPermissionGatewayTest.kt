package com.restaurant.sushimei.frontend.ui.util

import android.content.Context
import android.content.pm.PackageManager

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import com.restaurant.sushimei.frontend.ui.theme.SushiMeiTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.restaurant.sushimei.frontend.TestHostActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BluetoothPermissionGatewayTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestHostActivity>()

    @Test
    fun permissionAlreadyGranted_printProceeds() {
        var printCalled = false

        composeTestRule.setContent {
            SushiMeiTheme {
            val gateway = rememberBluetoothPermissionGateway(
                checkPermission = { _, _ -> PackageManager.PERMISSION_GRANTED }
            )
            Button(onClick = { gateway { printCalled = true } }) {
                Text("Imprimir cierre")
            }
            }
        }

        composeTestRule.onNodeWithText("Imprimir cierre").performClick()
        
        assertTrue(printCalled)
        composeTestRule.onNodeWithText("Permiso Requerido").assertDoesNotExist()
    }

    @Test
    fun permissionNotGranted_requestFlow_granted_printProceeds() {
        var printCalled = false
        var launcherCalled = false

        val fakeRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                launcherCalled = true
                dispatchResult(requestCode, true as O)
            }
        }

        composeTestRule.setContent {
            SushiMeiTheme {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = fakeRegistry
                }
            ) {
                val gateway = rememberBluetoothPermissionGateway(
                    checkPermission = { _, _ -> PackageManager.PERMISSION_DENIED }
                )
                Button(onClick = { gateway { printCalled = true } }) {
                    Text("Imprimir cierre")
                }
            }
            }
        }

        composeTestRule.onNodeWithText("Imprimir cierre").performClick()
        
        assertTrue(launcherCalled)
        assertTrue(printCalled)
        composeTestRule.onNodeWithText("Permiso Requerido").assertDoesNotExist()
    }

    @Test
    fun permissionNotGranted_requestFlow_denied_showsDialog() {
        var printCalled = false
        var launcherCalled = false

        val fakeRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                launcherCalled = true
                dispatchResult(requestCode, false as O)
            }
        }

        composeTestRule.setContent {
            SushiMeiTheme {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = fakeRegistry
                }
            ) {
                val gateway = rememberBluetoothPermissionGateway(
                    checkPermission = { _, _ -> PackageManager.PERMISSION_DENIED }
                )
                Button(onClick = { gateway { printCalled = true } }) {
                    Text("Imprimir cierre")
                }
            }
            }
        }

        composeTestRule.onNodeWithText("Imprimir cierre").performClick()
        
        assertTrue(launcherCalled)
        assertFalse(printCalled)
        composeTestRule.onNodeWithText("Permiso Requerido").assertExists()
    }
}
