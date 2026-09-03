package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDiagnosticsTest {
    @Test
    fun readyOnlyWhenPermissionFirebaseTokenAndTopicAreAvailable() {
        val ready = PushDiagnostics(
            notificationsAllowed = true,
            firebaseConfigured = true,
            tokenAvailable = true,
            generalTopicSubscribed = true,
            holdingTopicsExpected = 7,
            holdingTopicsSubscribed = 7,
            lastPushAt = "2026-09-03T15:20:00Z"
        )
        assertTrue(ready.ready)
        assertEquals("Push bereit", ready.summary)
    }

    @Test
    fun missingPermissionIsReportedFirst() {
        val state = PushDiagnostics(
            notificationsAllowed = false,
            firebaseConfigured = true,
            tokenAvailable = true,
            generalTopicSubscribed = true,
            holdingTopicsExpected = 7,
            holdingTopicsSubscribed = 7
        )
        assertFalse(state.ready)
        assertEquals("Android-Benachrichtigungen nicht erlaubt", state.summary)
    }

    @Test
    fun incompleteHoldingSubscriptionsAreVisible() {
        val state = PushDiagnostics(
            notificationsAllowed = true,
            firebaseConfigured = true,
            tokenAvailable = true,
            generalTopicSubscribed = true,
            holdingTopicsExpected = 7,
            holdingTopicsSubscribed = 5
        )
        assertFalse(state.ready)
        assertEquals("Depot-Alarme 5/7 verbunden", state.summary)
    }
}
