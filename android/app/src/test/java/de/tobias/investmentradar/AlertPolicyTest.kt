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

    @Test fun customDailyDropThresholdSuppressesSmallerDropButKeepsPriceThreshold() {
        val prefs = AlertPreferences(localDailyDropThresholdPct = 10.0)
        val smallerDrop = SignalAlert("d1", "x", "THRESHOLD", "", "", "", triggerValuePct = -8.0)
        val largeDrop = SignalAlert("d2", "x", "THRESHOLD", "", "", "", triggerValuePct = -11.0)
        val priceThreshold = SignalAlert("p", "x", "THRESHOLD", "", "", "")
        assertFalse(AlertPolicy.shouldNotify(smallerDrop, prefs))
        assertTrue(AlertPolicy.shouldNotify(largeDrop, prefs))
        assertTrue(AlertPolicy.shouldNotify(priceThreshold, prefs))
        assertTrue(AlertPolicy.shouldStore(smallerDrop, prefs))
    }

    @Test fun portfolioSpecificAlertsOnlyBelongToHeldAssets() {
        val empty = emptySet<String>()
        val held = setOf("msft")
        assertTrue(AlertPolicy.isRelevantForPortfolio(SignalAlert("b", "x", "BUY", "", "", ""), empty))
        assertFalse(AlertPolicy.isRelevantForPortfolio(SignalAlert("r", "msft", "REVIEW", "", "", ""), empty))
        assertFalse(AlertPolicy.isRelevantForPortfolio(SignalAlert("s", "msft", "SELL", "", "", ""), empty))
        assertFalse(AlertPolicy.isRelevantForPortfolio(SignalAlert("t", "msft", "THRESHOLD", "", "", ""), empty))
        assertTrue(AlertPolicy.isRelevantForPortfolio(SignalAlert("r2", "msft", "REVIEW", "", "", ""), held))
        assertTrue(AlertPolicy.isRelevantForPortfolio(SignalAlert("t2", "msft", "THRESHOLD", "", "", ""), held))
    }
}
