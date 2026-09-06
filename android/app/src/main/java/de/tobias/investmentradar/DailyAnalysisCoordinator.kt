package de.tobias.investmentradar

data class DailyAnalysisOutput(
    val results: List<AdvisorResult>,
    val events: List<AdvisorNotificationEvent>,
    val snapshots: Map<String, AdvisorSnapshot>
)

object DailyAnalysisCoordinator {
    fun analyze(
        analysisDay: String,
        holdingIds: Set<String>,
        items: List<InvestmentItem>,
        previousSnapshots: Map<String, AdvisorSnapshot>,
        freshnessFor: (InvestmentItem) -> DataFreshnessSummary = DataFreshness::summarize
    ): DailyAnalysisOutput {
        val byId = items.associateBy { it.id }
        val results = mutableListOf<AdvisorResult>()
        val events = mutableListOf<AdvisorNotificationEvent>()
        val snapshots = previousSnapshots.toMutableMap()

        holdingIds.sorted().forEach { itemId ->
            val item = byId[itemId] ?: return@forEach
            val freshness = freshnessFor(item)
            val forecast = ForecastEngine.forecast(item)
            val input = AdvisorInputFactory.from(item, forecast, freshness)
            val result = AdvisorEngine.evaluate(input)
            val before = snapshots[itemId] ?: AdvisorSnapshot()
            val event = AdvisorChangePolicy.notificationEvent(
                previous = before.current?.result,
                current = result,
                analysisDay = analysisDay
            )
            val after = AdvisorHistoryState.record(before, result, analysisDay)

            results += result
            if (event != null) events += event
            snapshots[itemId] = after
        }

        return DailyAnalysisOutput(
            results = results,
            events = events.distinctBy { it.id },
            snapshots = snapshots
        )
    }
}
