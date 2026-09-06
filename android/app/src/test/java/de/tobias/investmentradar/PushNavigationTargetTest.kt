package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class PushNavigationTargetTest {
    @Test
    fun savingsPlanPushOpensPortfolioSavingsChild() {
        assertEquals(
            PushNavigationTarget.SAVINGS_PLANS,
            PushNavigationTarget.resolve(openSavingsPlans = true, openAlerts = false, itemId = null)
        )
    }

    @Test
    fun itemPushKeepsAlertDetailPriority() {
        assertEquals(
            PushNavigationTarget.ALERT_DETAIL,
            PushNavigationTarget.resolve(openSavingsPlans = true, openAlerts = true, itemId = "meta")
        )
    }

    @Test
    fun alertsPushOpensAlertsWhenNoItemExists() {
        assertEquals(
            PushNavigationTarget.ALERTS,
            PushNavigationTarget.resolve(openSavingsPlans = false, openAlerts = true, itemId = null)
        )
    }

    @Test
    fun plainLaunchStaysAtHome() {
        assertEquals(
            PushNavigationTarget.HOME,
            PushNavigationTarget.resolve(openSavingsPlans = false, openAlerts = false, itemId = null)
        )
    }
}
