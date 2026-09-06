package de.tobias.investmentradar

enum class AppOverlay {
    NONE,
    BUDGET,
    PURCHASE_HISTORY,
    CUSTOM_ASSET,
    EDIT_CUSTOM_ASSET,
    MISSING_ALERT_ITEM,
    UPDATE,
    UPDATE_STATUS
}

enum class AppChildScreen {
    NONE,
    SAVINGS_PLANS
}

data class AppNavigationState(
    val rootTab: Int = 0,
    val detailId: String? = null,
    val detailReturnTab: Int = 0,
    val child: AppChildScreen = AppChildScreen.NONE,
    val overlay: AppOverlay = AppOverlay.NONE
)

sealed interface BackResult {
    data class Consume(val next: AppNavigationState) : BackResult
    data object ExitActivity : BackResult
}

fun AppNavigationState.onBack(): BackResult = when {
    overlay != AppOverlay.NONE -> BackResult.Consume(copy(overlay = AppOverlay.NONE))
    detailId != null -> BackResult.Consume(
        AppNavigationState(rootTab = detailReturnTab.coerceIn(0, 3))
    )
    child != AppChildScreen.NONE -> BackResult.Consume(copy(child = AppChildScreen.NONE))
    rootTab != 0 -> BackResult.Consume(AppNavigationState(rootTab = 0))
    else -> BackResult.ExitActivity
}
