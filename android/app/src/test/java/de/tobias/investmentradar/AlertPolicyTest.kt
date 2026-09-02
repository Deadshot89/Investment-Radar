package de.tobias.investmentradar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    @Test fun disabledBuyAlertsAreStoredButNotNotified() {
        val prefs = AlertPreferences(buyEnabled = false)
        val alert = SignalAlert("1", "msft", "BUY", "Kaufchance", "Text", "2026-09-02T08:00:00Z")
        assertFalse(AlertPolicy.shouldNotify(alert, prefs))
        assertTrue(AlertPolicy.shouldStore(alert, prefs))
    }

    @Test fun reviewAndSellDefaultToEnabled() {
        val prefs = AlertPreferences()
        assertTrue(AlertPolicy.shouldNotify(SignalAlert("1", "x", "REVIEW", "", "", ""), prefs, isHeld = true))
        assertTrue(AlertPolicy.shouldNotify(SignalAlert("2", "x", "SELL", "", "", ""), prefs, isHeld = true))
    }

    @Test fun heldOnlyReviewSuppressesReviewForNonHoldings() {
        val prefs = AlertPreferences(heldOnlyForReview = true)
        val alert = SignalAlert("1", "watch-only", "REVIEW", "Prüfen", "Text", "")
        assertFalse(AlertPolicy.shouldNotify(alert, prefs, isHeld = false))
        assertTrue(AlertPolicy.shouldNotify(alert, prefs, isHeld = true))
    }
}
