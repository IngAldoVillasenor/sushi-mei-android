package com.cardovia.merkon.app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PendingRegistrationStore(private val prefs: SharedPreferences) : IPendingRegistrationStore {
    constructor(context: Context) : this(context.getSharedPreferences("merkon_pending_registration", Context.MODE_PRIVATE))

    private val _pendingEmail = MutableStateFlow<String?>(prefs.getString(KEY_EMAIL, null))
    override val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()

    override fun saveEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
        _pendingEmail.value = email
    }

    override fun clear() {
        prefs.edit().remove(KEY_EMAIL).apply()
        _pendingEmail.value = null
    }

    companion object {
        private const val KEY_EMAIL = "pending_email"
    }
}
