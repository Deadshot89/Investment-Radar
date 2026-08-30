package de.tobias.investmentradar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data class Ready(val data: DashboardData) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = UiState.Loading
            runCatching { ApiClient.loadDashboard() }
                .onSuccess { _state.value = UiState.Ready(it) }
                .onFailure { e ->
                    if (!silent || _state.value !is UiState.Ready) {
                        _state.value = UiState.Error(e.message ?: "Unbekannter Fehler")
                    }
                }
        }
    }

    fun localAlerts(): List<SignalAlert> = AlertStore.read(getApplication())
}
