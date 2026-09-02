package de.tobias.investmentradar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFilterTest {
    @Test fun reviewFilterAlsoContainsThresholdEvents() {
        assertTrue(AlertFilter.REVIEW.matches("REVIEW"))
        assertTrue(AlertFilter.REVIEW.matches("THRESHOLD"))
        assertFalse(AlertFilter.BUY.matches("THRESHOLD"))
    }
}
