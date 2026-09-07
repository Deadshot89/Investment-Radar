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
        candidateIds: Set<String> = emptySet(),
        items: List<InvestmentItem>,
        previousSnapshots: Map<String, AdvisorSnapshot>,
        marketEventsByInstrument: Map<String, List<MarketEvent>> = emptyMap(),
        freshnessFor: (InvestmentItem) -> DataFreshnessSummary = { DataFreshness.summarize(it) }
    ): DailyAnalysisOutput {
        val byId = items.associateBy { it.id }
        val results = mutableListOf<AdvisorResult>()
        val events = mutableListOf<AdvisorNotificationEvent>()
        val snapshots = previousSnapshots.toMutableMap()
        val analysisIds = (holdingIds + candidateIds).sorted()

        analysisIds.forEach { itemId ->
            val item = byId[itemId] ?: return@forEach
            val freshness = freshnessFor(item)
            val forecast = ForecastEngine.forecast(item)
            val input = AdvisorInputFactory.from(item, forecast, freshness)
            val base = AdvisorEngine.evaluate(input)
            val eventContext = EventAwareAdvisorPolicy.context(
                marketEventsByInstrument[itemId].orEmpty()
            )
            val proposed = EventAwareAdvisorPolicy.apply(base, eventContext)
            val before = snapshots[itemId] ?: AdvisorSnapshot()
            val result = AdvisorStabilityPolicy.resolve(
                previous = before,
                proposed = proposed,
                analysisDay = analysisDay,
                criticalEvent = eventContext.criticalThesisBreak
            )
            val event = AdvisorChangePolicy.notificationEvent(
                previous = before.current?.result,
                current = result,
                analysisDay = analysisDay,
                isHolding = itemId in holdingIds
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
