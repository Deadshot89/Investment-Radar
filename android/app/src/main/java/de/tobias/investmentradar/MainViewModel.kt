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

    private val initialPositions = PortfolioStore.readPositions(app)
    private val _holdingIds = MutableStateFlow(initialPositions.keys)
    val holdingIds: StateFlow<Set<String>> = _holdingIds.asStateFlow()

    private val _positions = MutableStateFlow(initialPositions)
    val positions: StateFlow<Map<String, PortfolioPosition>> = _positions.asStateFlow()

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
        savePosition(PortfolioPosition(itemId = itemId))
    }

    fun savePosition(position: PortfolioPosition) {
        val app = getApplication<Application>()
        val isNew = position.itemId !in _holdingIds.value
        PortfolioStore.save(app, position)
        reloadPortfolio(app)
        if (isNew && FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().subscribeToTopic(holdingTopic(position.itemId))
        }
    }

    fun upsertPurchase(itemId: String, purchase: PortfolioPurchase): Boolean {
        val current = _positions.value[itemId] ?: PortfolioPosition(itemId)
        val next = current.upsertPurchaseIfValid(purchase) ?: return false
        savePosition(next)
        return true
    }

    fun removePurchase(itemId: String, purchaseId: String): Boolean {
        val current = _positions.value[itemId] ?: return false
        val next = current.removePurchaseIfValid(purchaseId) ?: return false
        savePosition(next)
        return true
    }

    fun upsertSale(itemId: String, sale: PortfolioSale): Boolean {
        val current = _positions.value[itemId] ?: PortfolioPosition(itemId)
        val next = current.upsertSale(sale) ?: return false
        savePosition(next)
        return true
    }

    fun removeSale(itemId: String, saleId: String): Boolean {
        val current = _positions.value[itemId] ?: return false
        savePosition(current.removeSale(saleId))
        return true
    }

    fun removeHolding(itemId: String) {
        val app = getApplication<Application>()
        PortfolioStore.remove(app, itemId)
        reloadPortfolio(app)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(holdingTopic(itemId))
        }
    }

    fun localAlerts(): List<SignalAlert> = AlertStore.read(getApplication())

    private fun reloadPortfolio(app: Application) {
        val next = PortfolioStore.readPositions(app)
        _positions.value = next
        _holdingIds.value = next.keys
    }

    companion object {
        fun holdingTopic(itemId: String): String = "holding-" + itemId
            .lowercase()
            .replace(Regex("[^a-z0-9._~%-]"), "-")
            .take(80)
    }
}
