package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorNotificationPolicyTest {
    @Test
    fun onlyUnseenStableIdsAreEmitted() {
        val existing = setOf("meta|HALTEN|NACHKAUFEN|2026-09-06")
        val candidates = listOf(
            "meta|HALTEN|NACHKAUFEN|2026-09-06",
            "msft|HALTEN|REDUZIEREN|2026-09-06",
            "msft|HALTEN|REDUZIEREN|2026-09-06"
        )

        assertEquals(
            listOf("msft|HALTEN|REDUZIEREN|2026-09-06"),
            NotificationEventLedger.unseen(existing, candidates)
        )
    }

    @Test
    fun savingsExecutionIdentityPreventsRepeatedDueNotifications() {
        val executionId = "tr-meta-twice-monthly|2026-09-15"
        val emitted = NotificationEventLedger.unseen(emptySet(), listOf(executionId, executionId))
        assertEquals(listOf(executionId), emitted)
        assertTrue(NotificationEventLedger.unseen(emitted.toSet(), listOf(executionId)).isEmpty())
    }
}
