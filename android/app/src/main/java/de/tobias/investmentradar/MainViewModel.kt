package de.tobias.investmentradar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
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

    private val _holdingIds = MutableStateFlow(PortfolioStore.read(app))
    val holdingIds: StateFlow<Set<String>> = _holdingIds.asStateFlow()

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
            val previousState = _state.value
            val hadReadyData = previousState is UiState.Ready
            if (!silent && !hadReadyData) _state.value = UiState.Loading
            runCatching { ApiClient.loadDashboard() }
                .onSuccess { _state.value = UiState.Ready(it) }
                .onFailure { e ->
                    if (!hadReadyData) {
                        _state.value = UiState.Error(e.message ?: "Verbindung zum Server fehlgeschlagen. Bitte erneut versuchen.")
                    }
                }
        }
    }

    fun markBought(itemId: String) {
        val app = getApplication<Application>()
        PortfolioStore.add(app, itemId)
        _holdingIds.value = PortfolioStore.read(app)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().subscribeToTopic(holdingTopic(itemId))
        }
    }

    fun removeHolding(itemId: String) {
        val app = getApplication<Application>()
        PortfolioStore.remove(app, itemId)
        _holdingIds.value = PortfolioStore.read(app)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(holdingTopic(itemId))
        }
    }

    fun localAlerts(): List<SignalAlert> = AlertStore.read(getApplication())

    companion object {
        fun holdingTopic(itemId: String): String = "holding-" + itemId
            .lowercase()
            .replace(Regex("[^a-z0-9._~%-]"), "-")
            .take(80)
    }
}
