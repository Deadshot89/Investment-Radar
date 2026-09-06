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
        val dueExecutions = SavingsPlanStore.ensureDueExecutions(applicationContext, today)
        val savingsPlans = SavingsPlanStore.readPlans(applicationContext)
        val monthlySavings = SavingsPlanBudget.monthlyAmounts(savingsPlans)
        val positions = PortfolioStore.readPositions(applicationContext)
        val holdingIds = positions.keys
        val customItems = CustomInvestmentStore.read(applicationContext)

        val dashboard = runCatching { ApiClient.loadDashboard() }.getOrNull()
        val loaded = dashboard?.items.orEmpty().associateBy { it.id }.toMutableMap()

        val radarPage = runCatching {
            ApiClient.loadRadarPage(DailyCandidateUniverse.topRadarQuery())
        }.getOrNull()
        radarPage?.items.orEmpty().forEach { radarItem ->
            loaded.putIfAbsent(radarItem.id, radarItem.asInvestmentItem())
        }
        val candidateIds = radarPage?.let(DailyCandidateUniverse::candidateIds).orEmpty()

        loadMissingHoldings(holdingIds, loaded, customItems)

        val loadedHoldingIds = holdingIds.filterTo(mutableSetOf()) { it in loaded }
        if (holdingIds.isNotEmpty() && loadedHoldingIds.isEmpty()) return Result.retry()

        val analysisIds = loadedHoldingIds + candidateIds
        val analysisItems = analysisIds.mapNotNull { loaded[it] }
        if (analysisItems.isEmpty()) {
            AdvisorNotificationManager.publishDueSavingsPlans(
                applicationContext,
                dueExecutions,
                savingsPlans
            )
            return Result.success()
        }

        val previous = AdvisorStore.readAll(applicationContext)
        val output = DailyAnalysisCoordinator.analyze(
            analysisDay = today,
            holdingIds = loadedHoldingIds,
            candidateIds = candidateIds,
            items = analysisItems,
            previousSnapshots = previous
        )

        output.results.forEach { result ->
            AdvisorStore.record(applicationContext, result, today)
        }

        val currentValues = PortfolioAnalysis.values(
            items = analysisItems,
            positions = positions,
            customItems = customItems
        )
        val stableById = output.results.associateBy { it.instrumentId }
        val candidates = analysisItems.mapNotNull { item ->
            val stable = stableById[item.id] ?: return@mapNotNull null
            PortfolioAdvisorCandidate(
                itemId = item.id,
                isHolding = item.id in loadedHoldingIds,
                action = portfolioAction(stable, item.id in loadedHoldingIds),
                advisor = stable,
                currentValueEur = currentValues[item.id],
                monthlySavingsEur = monthlySavings[item.id] ?: 0
            )
        }
        val budget = dashboard?.budget?.coerceAtLeast(0) ?: 100
        val plan = PortfolioAdvisorEngine.allocate(candidates, budget)
        PortfolioAdvisorStore.save(applicationContext, today, plan)

        AdvisorNotificationManager.publishAdvisorEvents(applicationContext, output.events)
        AdvisorNotificationManager.publishDueSavingsPlans(
            applicationContext,
            dueExecutions,
            savingsPlans
        )

        return Result.success()
    }

    private suspend fun loadMissingHoldings(
        holdingIds: Set<String>,
        loaded: MutableMap<String, InvestmentItem>,
        customItems: List<CustomInvestment>
    ) {
        val customById = customItems.associateBy { it.id }
        holdingIds.filterNot { it in loaded }.forEach { itemId ->
            val item = customById[itemId]?.let { custom ->
                runCatching { ApiClient.loadCustomQuote(custom) }
                    .getOrElse { custom.fallbackItem(it.message ?: "Kursdaten fehlen", custom.manualPriceEur) }
            } ?: runCatching { ApiClient.loadRadarDetail(itemId).asInvestmentItem() }.getOrNull()
            if (item != null) loaded[itemId] = item
        }
    }

    private fun portfolioAction(
        advisor: AdvisorResult,
        isHolding: Boolean
    ): PortfolioAdvisorAction = when {
        !advisor.reliable -> PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG
        isHolding -> when (advisor.signal) {
            AdvisorSignal.NACHKAUFEN -> PortfolioAdvisorAction.NACHKAUFEN
            AdvisorSignal.HALTEN -> PortfolioAdvisorAction.HALTEN
            AdvisorSignal.REDUZIEREN -> PortfolioAdvisorAction.REDUZIEREN
            AdvisorSignal.VERKAUFEN -> PortfolioAdvisorAction.VERKAUFEN
            AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG
        }
        advisor.signal == AdvisorSignal.NACHKAUFEN -> PortfolioAdvisorAction.NEU_AUFNEHMEN
        else -> PortfolioAdvisorAction.NICHT_AUFNEHMEN
    }
}
