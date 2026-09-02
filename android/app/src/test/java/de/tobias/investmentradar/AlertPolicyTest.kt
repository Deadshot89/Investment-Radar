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
        assertTrue(AlertPolicy.shouldNotify(SignalAlert("1", "x", "REVIEW", "", "", ""), prefs))
        assertTrue(AlertPolicy.shouldNotify(SignalAlert("2", "x", "SELL", "", "", ""), prefs))
    }

    @Test fun thresholdPreferenceControlsThresholdNotification() {
        val alert = SignalAlert("3", "x", "THRESHOLD", "", "", "")
        assertTrue(AlertPolicy.shouldNotify(alert, AlertPreferences(thresholdEnabled = true)))
        assertFalse(AlertPolicy.shouldNotify(alert, AlertPreferences(thresholdEnabled = false)))
        assertTrue(AlertPolicy.shouldStore(alert, AlertPreferences(thresholdEnabled = false)))
    }
}
