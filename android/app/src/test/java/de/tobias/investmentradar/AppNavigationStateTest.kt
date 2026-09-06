package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test fun closesOverlayBeforeAnythingElse() {
        val state = AppNavigationState(rootTab = 2, detailId = "meta", overlay = AppOverlay.BUDGET)
        val result = state.onBack()
        assertTrue(result is BackResult.Consume)
        assertEquals(state.copy(overlay = AppOverlay.NONE), (result as BackResult.Consume).next)
    }

    @Test fun closesDetailAndReturnsToCallingTab() {
        val state = AppNavigationState(rootTab = 3, detailId = "meta", detailReturnTab = 1)
        val result = state.onBack() as BackResult.Consume
        assertEquals(AppNavigationState(rootTab = 1), result.next)
    }

    @Test fun closesChildScreenBeforeLeavingApp() {
        val state = AppNavigationState(rootTab = 2, child = AppChildScreen.SAVINGS_PLANS)
        val result = state.onBack() as BackResult.Consume
        assertEquals(AppNavigationState(rootTab = 2), result.next)
    }

    @Test fun returnsNonRootTabsToLiveBeforeExit() {
        val state = AppNavigationState(rootTab = 3)
        val result = state.onBack() as BackResult.Consume
        assertEquals(AppNavigationState(rootTab = 0), result.next)
    }

    @Test fun exitsOnlyFromTrueRootState() {
        assertEquals(BackResult.ExitActivity, AppNavigationState(rootTab = 0).onBack())
    }

    @Test fun oneBackPressRemovesExactlyOneLayer() {
        val state = AppNavigationState(
            rootTab = 2,
            detailId = "meta",
            detailReturnTab = 2,
            child = AppChildScreen.SAVINGS_PLANS,
            overlay = AppOverlay.UPDATE
        )
        val result = state.onBack() as BackResult.Consume
        assertEquals(state.copy(overlay = AppOverlay.NONE), result.next)
    }
}
