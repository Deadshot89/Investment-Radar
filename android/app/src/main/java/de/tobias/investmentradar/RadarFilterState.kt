package de.tobias.investmentradar

enum class RadarRecommendationFilter { ALL, BUY, WATCH, NO_BUY, REVIEW }
enum class RadarTypeFilter { ALL, STOCK, ETF }
enum class RadarHoldingFilter { ALL, HELD, NOT_HELD }
enum class RadarDataQualityFilter { ALL, FULL, REDUCED, INSUFFICIENT }
enum class RadarRiskFilter { ALL, LOW, MEDIUM, HIGH }
enum class RadarSortMode { SCORE, ALLOCATION, MOMENTUM_6M, DAY_ASC, DAY_DESC, NAME }

data class RadarFilterState(
    val query: String = "",
    val recommendation: RadarRecommendationFilter = RadarRecommendationFilter.ALL,
    val type: RadarTypeFilter = RadarTypeFilter.ALL,
    val holding: RadarHoldingFilter = RadarHoldingFilter.ALL,
    val watchlistOnly: Boolean = false,
    val dataQuality: RadarDataQualityFilter = RadarDataQualityFilter.ALL,
    val risk: RadarRiskFilter = RadarRiskFilter.ALL,
    val sort: RadarSortMode = RadarSortMode.SCORE
)

data class RadarFilterResult(
    val items: List<InvestmentItem>,
    val buyFallbackActive: Boolean = false
)

object RadarFilterEngine {
    fun apply(
        items: List<InvestmentItem>,
        state: RadarFilterState,
        holdingIds: Set<String>,
        watchlistIds: Set<String>,
        allocationById: Map<String, Int>
    ): List<InvestmentItem> = evaluate(items, state, holdingIds, watchlistIds, allocationById).items

    fun evaluate(
        items: List<InvestmentItem>,
        state: RadarFilterState,
        holdingIds: Set<String>,
        watchlistIds: Set<String>,
        allocationById: Map<String, Int>
    ): RadarFilterResult {
        val exact = filterBase(items, state, holdingIds, watchlistIds)
            .filter { matchesRecommendation(it, state.recommendation) }
            .let { sort(it, state.sort, allocationById) }

        return RadarFilterResult(items = exact)
    }

    private fun filterBase(
        items: List<InvestmentItem>,
        state: RadarFilterState,
        holdingIds: Set<String>,
        watchlistIds: Set<String>
    ): List<InvestmentItem> = items.asSequence()
        .filter { !it.portfolioOnly }
        .filter { matchesQuery(it, state.query) }
        .filter { matchesType(it, state.type) }
        .filter { matchesHolding(it.id, state.holding, holdingIds) }
        .filter { !state.watchlistOnly || it.id in watchlistIds }
        .filter { matchesCoverage(it.coverage, state.dataQuality) }
        .filter { matchesRisk(it.risk, state.risk) }
        .toList()

    private fun sort(
        filtered: List<InvestmentItem>,
        mode: RadarSortMode,
        allocationById: Map<String, Int>
    ): List<InvestmentItem> = when (mode) {
        RadarSortMode.SCORE -> filtered.sortedWith(
            compareByDescending<InvestmentItem> { it.scoreTotal ?: Int.MIN_VALUE }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        RadarSortMode.ALLOCATION -> filtered.sortedWith(
            compareByDescending<InvestmentItem> { allocationById[it.id] ?: 0 }
                .thenByDescending { it.scoreTotal ?: Int.MIN_VALUE }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        RadarSortMode.MOMENTUM_6M -> filtered.sortedWith(
            nullableNumberComparator({ it.momentum?.m6 }, ascending = false)
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        RadarSortMode.DAY_ASC -> filtered.sortedWith(
            nullableNumberComparator({ it.percentChange }, ascending = true)
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        RadarSortMode.DAY_DESC -> filtered.sortedWith(
            nullableNumberComparator({ it.percentChange }, ascending = false)
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
        )
        RadarSortMode.NAME -> filtered.sortedWith(
            compareBy<InvestmentItem> { it.name.lowercase() }
                .thenBy { it.ticker.lowercase() }
                .thenBy { it.id }
        )
    }

    private fun matchesQuery(item: InvestmentItem, rawQuery: String): Boolean {
        val query = rawQuery.trim()
        if (query.isEmpty()) return true
        return item.name.contains(query, ignoreCase = true) ||
            item.ticker.contains(query, ignoreCase = true) ||
            item.isin.contains(query, ignoreCase = true) ||
            item.type.contains(query, ignoreCase = true)
    }

    private fun matchesRecommendation(item: InvestmentItem, filter: RadarRecommendationFilter): Boolean {
        if (filter == RadarRecommendationFilter.ALL) return true
        val recommendation = RecommendationPresentation.effectiveRecommendation(item)
        return when (filter) {
            RadarRecommendationFilter.ALL -> true
            RadarRecommendationFilter.BUY -> recommendation == "BUY"
            RadarRecommendationFilter.WATCH -> recommendation == "WATCH"
            RadarRecommendationFilter.NO_BUY -> recommendation == "NO_BUY"
            RadarRecommendationFilter.REVIEW -> recommendation == "REVIEW"
        }
    }

    private fun matchesType(item: InvestmentItem, filter: RadarTypeFilter): Boolean {
        if (filter == RadarTypeFilter.ALL) return true
        val isEtf = item.type.equals("ETF", ignoreCase = true)
        return when (filter) {
            RadarTypeFilter.ALL -> true
            RadarTypeFilter.STOCK -> !isEtf
            RadarTypeFilter.ETF -> isEtf
        }
    }

    private fun matchesHolding(itemId: String, filter: RadarHoldingFilter, holdingIds: Set<String>): Boolean =
        when (filter) {
            RadarHoldingFilter.ALL -> true
            RadarHoldingFilter.HELD -> itemId in holdingIds
            RadarHoldingFilter.NOT_HELD -> itemId !in holdingIds
        }

    private fun matchesCoverage(coverage: Int?, filter: RadarDataQualityFilter): Boolean =
        when (filter) {
            RadarDataQualityFilter.ALL -> true
            RadarDataQualityFilter.FULL -> coverage != null && coverage >= 70
            RadarDataQualityFilter.REDUCED -> coverage != null && coverage in 50..69
            RadarDataQualityFilter.INSUFFICIENT -> coverage == null || coverage < 50
        }

    private fun matchesRisk(risk: Int, filter: RadarRiskFilter): Boolean =
        when (filter) {
            RadarRiskFilter.ALL -> true
            RadarRiskFilter.LOW -> risk in 1..2
            RadarRiskFilter.MEDIUM -> risk == 3
            RadarRiskFilter.HIGH -> risk in 4..5
        }

    private fun nullableNumberComparator(
        selector: (InvestmentItem) -> Double?,
        ascending: Boolean
    ): Comparator<InvestmentItem> = Comparator { left, right ->
        val a = selector(left)?.takeIf { it.isFinite() }
        val b = selector(right)?.takeIf { it.isFinite() }
        when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            ascending -> a.compareTo(b)
            else -> b.compareTo(a)
        }
    }
}