package de.tobias.investmentradar

enum class PushNavigationTarget {
    HOME,
    ALERTS,
    ALERT_DETAIL,
    SAVINGS_PLANS;

    companion object {
        const val SAVINGS_ITEM_ID = "__savings_plans__"

        fun resolve(
            openSavingsPlans: Boolean,
            openAlerts: Boolean,
            itemId: String?
        ): PushNavigationTarget = when {
            itemId == SAVINGS_ITEM_ID && openSavingsPlans -> SAVINGS_PLANS
            !itemId.isNullOrBlank() -> ALERT_DETAIL
            openAlerts -> ALERTS
            openSavingsPlans -> SAVINGS_PLANS
            else -> HOME
        }
    }
}
