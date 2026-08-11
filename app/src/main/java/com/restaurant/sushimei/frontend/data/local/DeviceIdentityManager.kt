package com.restaurant.sushimei.frontend.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

interface IDeviceIdentityProvider {
    val deviceId: String
}

/**
 * Manages a stable, non-secret device identifier (UUID).
 * This UUID is generated once per installation and persisted across normal app restarts.
 */
class DeviceIdentityManager(context: Context) : IDeviceIdentityProvider {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    companion object {
        private const val PREFS_NAME = "device_identity_prefs"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
