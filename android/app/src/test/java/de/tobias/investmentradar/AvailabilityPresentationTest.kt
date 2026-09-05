package de.tobias.investmentradar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityPresentationTest {
    @Test fun everyDataStateExplainsTheRealCauseWithoutGenericUnavailableCopy() {
        val expectedFragments = mapOf(
            DataUiState.NO_CURRENT_DATA to "Keine aktuellen Daten",
            DataUiState.NO_ANALYSIS to "Noch keine Analyse",
            DataUiState.NOT_IN_RADAR to "Nicht im aktuellen Radar enthalten",
            DataUiState.NO_VERIFIED_TR_MAPPING to "Keine verifizierte Trade-Republic-Zuordnung",
            DataUiState.CONNECTION_FAILED to "Verbindung konnte nicht hergestellt werden"
        )

        expectedFragments.forEach { (state, fragment) ->
            val message = state.userMessage()
            assertTrue("$state should explain its cause", message.contains(fragment))
            assertFalse("$state must not use generic unavailable copy", message.contains("nicht verfügbar", ignoreCase = true))
        }
    }
}
