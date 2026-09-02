package de.tobias.investmentradar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    private val _customItems = MutableStateFlow(CustomInvestmentStore.read(app))
    val customItems: StateFlow<List<CustomInvestment>> = _customItems.asStateFlow()

    private val _watchlistIds = MutableStateFlow(WatchlistStore.read(app))
    val watchlistIds: StateFlow<Set<String>> = _watchlistIds.asStateFlow()

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
            runCatching {
                val dashboard = ApiClient.loadDashboard()
                val customQuotes = _customItems.value.map { custom ->
                    async {
                        runCatching { ApiClient.loadCustomQuote(custom) }
                            .getOrElse { custom.fallbackItem(it.message ?: "Kursdaten fehlen", custom.manualPriceEur) }
                    }
                }.awaitAll()
                dashboard.copy(items = (dashboard.items + customQuotes).distinctBy { it.id })
            }
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

    fun addCustomInvestment(item: CustomInvestment, initialPurchase: PortfolioPurchase?) {
        val app = getApplication<Application>()
        CustomInvestmentStore.save(app, item)
        _customItems.value = CustomInvestmentStore.read(app)
        val current = _positions.value[item.id] ?: PortfolioPosition(item.id)
        val next = if (initialPurchase != null) current.upsertPurchaseIfValid(initialPurchase) ?: current else current
        savePosition(next)
        refresh(silent = true)
    }

    fun updateCustomInvestment(item: CustomInvestment) {
        val app = getApplication<Application>()
        CustomInvestmentStore.save(app, item)
        _customItems.value = CustomInvestmentStore.read(app)
        refresh(silent = true)
    }

    fun removeCustomInvestment(itemId: String) {
        val app = getApplication<Application>()
        CustomInvestmentStore.remove(app, itemId)
        _customItems.value = CustomInvestmentStore.read(app)
        _watchlistIds.value = WatchlistStore.remove(app, itemId)
        removeHolding(itemId)
        refresh(silent = true)
    }

    fun removeHolding(itemId: String) {
        val app = getApplication<Application>()
        PortfolioStore.remove(app, itemId)
        reloadPortfolio(app)
        if (FirebaseBootstrap.isConfigured()) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(holdingTopic(itemId))
        }
    }


    fun toggleWatchlist(itemId: String) {
        val app = getApplication<Application>()
        _watchlistIds.value = WatchlistStore.toggle(app, itemId)
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
