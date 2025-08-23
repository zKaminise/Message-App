package com.example.messageapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messageapp.data.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _isLogged = MutableStateFlow(FirebaseAuth.getInstance().currentUser != null)
    val isLogged = _isLogged.asStateFlow()

    fun signInAnonymously() {
        viewModelScope.launch {
            runCatching { repo.signInAnonymouslyAndUpsert() }
                .onSuccess { _isLogged.value = true }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOutAndRemoveToken()
            _isLogged.value = false
        }
    }

    fun updatePresence(online: Boolean) {
        viewModelScope.launch { repo.updatePresence(online) }
    }
}
