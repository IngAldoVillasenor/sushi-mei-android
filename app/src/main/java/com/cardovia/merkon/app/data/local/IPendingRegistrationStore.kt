package com.cardovia.merkon.app.data.local

import kotlinx.coroutines.flow.StateFlow

interface IPendingRegistrationStore {
    val pendingEmail: StateFlow<String?>
    fun saveEmail(email: String)
    fun clear()
}
