package de.tobias.investmentradar

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DailyAnalysisScheduler {
    const val WORK_NAME = "investment-radar-daily-analysis"
    const val REPEAT_HOURS = 24L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyAnalysisWorker>(REPEAT_HOURS, TimeUnit.HOURS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
