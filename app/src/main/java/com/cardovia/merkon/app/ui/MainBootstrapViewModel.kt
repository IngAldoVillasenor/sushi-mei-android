package com.cardovia.merkon.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardovia.merkon.app.data.model.ApplicationRole
import com.cardovia.merkon.app.data.model.AuthenticatedUserDto
import com.cardovia.merkon.app.data.repository.IMenuRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class BootstrapState {
    NotRequired,
    Loading,
    Empty,
    Configured,
    Error
}

class MainBootstrapViewModel(
    private val menuRepository: IMenuRepository,
    private val user: AuthenticatedUserDto
) : ViewModel() {

    var bootstrapState by mutableStateOf(BootstrapState.Loading)
        private set

    init {
        if (user.role == ApplicationRole.OWNER) {
            checkOwnerCatalog()
        } else {
            bootstrapState = BootstrapState.NotRequired
        }
    }

    fun retry() {
        if (user.role == ApplicationRole.OWNER) {
            checkOwnerCatalog()
        }
    }

    private fun checkOwnerCatalog() {
        viewModelScope.launch {
            bootstrapState = BootstrapState.Loading
            try {
                menuRepository.refreshCatalog(includeInactive = true)
                val products = menuRepository.observeAll().first()
                if (products.isEmpty()) {
                    bootstrapState = BootstrapState.Empty
                } else {
                    bootstrapState = BootstrapState.Configured
                }
            } catch (e: Exception) {
                bootstrapState = BootstrapState.Error
            }
        }
    }
}
