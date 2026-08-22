package com.restaurant.sushimei.frontend.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberBluetoothPermissionGateway(
    checkPermission: (Context, String) -> Int = ContextCompat::checkSelfPermission
): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var showDeniedDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else null

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingAction?.invoke()
        } else {
            showDeniedDialog = true
        }
        pendingAction = null
    }

    if (showDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showDeniedDialog = false },
            title = { Text("Permiso Requerido") },
            text = { Text("Se necesita permiso para acceder a la impresora Bluetooth. Si el sistema ya no te lo pregunta, ve a Configuración -> Aplicaciones -> Sushi Mei -> Permisos y habilita 'Dispositivos cercanos'.") },
            confirmButton = {
                TextButton(onClick = { showDeniedDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }

    return { action ->
        if (permission == null) {
            action()
        } else {
            if (checkPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pendingAction = action
                launcher.launch(permission)
            }
        }
    }
}
