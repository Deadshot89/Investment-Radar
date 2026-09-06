package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyAnalysisSchedulerTest {
    @Test
    fun dailySchedulerUsesOneStableUniqueWorkIdentity() {
        assertEquals("investment-radar-daily-analysis", DailyAnalysisScheduler.WORK_NAME)
        assertEquals(24L, DailyAnalysisScheduler.REPEAT_HOURS)
    }
}
