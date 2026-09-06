package de.tobias.investmentradar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class DailyAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now().toString()

        // Fällige Sparpläne müssen unabhängig davon entstehen, ob der Sparplan-Screen geöffnet wurde.
        SavingsPlanStore.ensureDueExecutions(applicationContext, today)

        val holdingIds = PortfolioStore.read(applicationContext)
        if (holdingIds.isEmpty()) return Result.success()

        val items = runCatching { loadHoldingItems(holdingIds) }
            .getOrElse { return Result.retry() }
        if (items.isEmpty()) return Result.retry()

        val previous = AdvisorStore.readAll(applicationContext)
        val output = DailyAnalysisCoordinator.analyze(
            analysisDay = today,
            holdingIds = holdingIds,
            items = items,
            previousSnapshots = previous
        )

        output.results.forEach { result ->
            AdvisorStore.record(applicationContext, result, today)
        }
        AdvisorNotificationManager.publishAdvisorEvents(applicationContext, output.events)
        AdvisorNotificationManager.publishDueSavingsPlans(
            applicationContext,
            SavingsPlanStore.ensureDueExecutions(applicationContext, today),
            SavingsPlanStore.readPlans(applicationContext)
        )

        return Result.success()
    }

    private suspend fun loadHoldingItems(holdingIds: Set<String>): List<InvestmentItem> {
        val dashboard = ApiClient.loadDashboard()
        val loaded = dashboard.items.associateBy { it.id }.toMutableMap()
        val customById = CustomInvestmentStore.read(applicationContext).associateBy { it.id }

        holdingIds.filterNot { it in loaded }.forEach { itemId ->
            val item = customById[itemId]?.let { custom ->
                runCatching { ApiClient.loadCustomQuote(custom) }
                    .getOrElse { custom.fallbackItem(it.message ?: "Kursdaten fehlen", custom.manualPriceEur) }
            } ?: runCatching { ApiClient.loadRadarDetail(itemId).asInvestmentItem() }.getOrNull()
            if (item != null) loaded[itemId] = item
        }

        return holdingIds.mapNotNull { loaded[it] }
    }
}
