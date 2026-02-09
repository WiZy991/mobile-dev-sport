package com.fitnessclub.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.Subscription
import com.fitnessclub.app.data.model.User
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()
    
    init {
        loadProfile()
        loadSubscriptions()
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                _uiState.value = _uiState.value.copy(user = user)
            }
        }
    }
    
    private fun loadSubscriptions() {
        viewModelScope.launch {
            subscriptionRepository.getMySubscriptions().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoadingSubscriptions = true)
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingSubscriptions = false,
                            subscriptions = result.data
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingSubscriptions = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }
    
    fun freezeSubscription(subscriptionId: String, days: Int) {
        viewModelScope.launch {
            subscriptionRepository.freezeSubscription(subscriptionId, days).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        loadSubscriptions()
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(error = result.message)
                    }
                    is ApiResult.Loading -> {}
                }
            }
        }
    }
    
    fun unfreezeSubscription(subscriptionId: String) {
        viewModelScope.launch {
            subscriptionRepository.unfreezeSubscription(subscriptionId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        loadSubscriptions()
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(error = result.message)
                    }
                    is ApiResult.Loading -> {}
                }
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _events.emit(ProfileEvent.LoggedOut)
        }
    }
    
    fun refresh() {
        loadProfile()
        loadSubscriptions()
    }
}

data class ProfileUiState(
    val user: User? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val isLoadingSubscriptions: Boolean = false,
    val error: String? = null
)

sealed class ProfileEvent {
    data object LoggedOut : ProfileEvent()
}
