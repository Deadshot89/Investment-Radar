package de.tobias.investmentradar

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ExitStrategyScheduler {
    const val WORK_NAME="investment-radar-exit-strategy"
    const val REPEAT_HOURS=1L
    fun schedule(context:Context){
        val request=PeriodicWorkRequestBuilder<ExitStrategyWorker>(REPEAT_HOURS,TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_NAME).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}
