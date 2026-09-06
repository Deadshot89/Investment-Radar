package de.tobias.investmentradar

enum class PushNavigationTarget {
    HOME,
    ALERTS,
    ALERT_DETAIL,
    SAVINGS_PLANS;

    companion object {
        fun resolve(
            openSavingsPlans: Boolean,
            openAlerts: Boolean,
            itemId: String?
        ): PushNavigationTarget = when {
            !itemId.isNullOrBlank() -> ALERT_DETAIL
            openAlerts -> ALERTS
            openSavingsPlans -> SAVINGS_PLANS
            else -> HOME
        }
    }
}
