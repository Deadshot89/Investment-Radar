package de.tobias.investmentradar

import kotlin.math.min

data class LiquidityHolding(
    val itemId: String,
    val instrumentName: String,
    val currentValueEur: Double,
    val shares: Double?,
    val advisorAction: PortfolioAdvisorAction?,
    val advisorScore: Int?,
    val dataReliable: Boolean,
    val forecastDirection: String?,
    val profitLossPct: Double?
)

data class LiquiditySaleSuggestion(
    val itemId: String,
    val instrumentName: String,
    val amountEur: Double,
    val shares: Double?,
    val fullExit: Boolean,
    val currentValueEur: Double,
    val remainingValueEur: Double,
    val advisorAction: PortfolioAdvisorAction?,
    val advisorScore: Int?,
    val reason: String
)

data class LiquidityNeedPlan(
    val requestedEur: Double,
    val cashUsedEur: Double,
    val saleNeededEur: Double,
    val suggestions: List<LiquiditySaleSuggestion>,
    val uncoveredEur: Double
) {
    val coveredEur: Double get() = (requestedEur - uncoveredEur).coerceAtLeast(0.0)
    val noSaleNeeded: Boolean get() = saleNeededEur <= 0.01
}

data class LiquidityNeedProgress(
    val requestedEur: Double,
    val privateSaleCoveredEur: Double,
    val withdrawnCashEur: Double
) {
    val coveredEur: Double get() = (privateSaleCoveredEur + withdrawnCashEur).coerceAtMost(requestedEur)
    val remainingEur: Double get() = (requestedEur - coveredEur).coerceAtLeast(0.0)
    val complete: Boolean get() = remainingEur <= 0.01
}

object LiquidityNeedEngine {
    fun progress(
        requestedEur: Double,
        history: List<BudgetHistoryItem>,
        flowTag: String
    ): LiquidityNeedProgress {
        val requested = requestedEur.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        if (flowTag.isBlank()) return LiquidityNeedProgress(requested, 0.0, 0.0)

        val tagged = history.filter { item ->
            item.note.contains(flowTag, ignoreCase = false)
        }
        val privateSales = tagged
            .filter { it.isCredit }
            .sumOf { it.amountEur.coerceAtLeast(0.0) }
        val withdrawals = tagged
            .filterNot { it.isCredit }
            .sumOf { kotlin.math.abs(it.amountEur) }

        return LiquidityNeedProgress(
            requestedEur = requested,
            privateSaleCoveredEur = privateSales.coerceAtMost(requested),
            withdrawnCashEur = withdrawals.coerceAtMost(requested)
        )
    }

    fun plan(
        requestedEur: Double,
        availableCashEur: Double,
        holdings: List<LiquidityHolding>
    ): LiquidityNeedPlan {
        val requested = requestedEur.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val cash = availableCashEur.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val cashUsed = min(requested, cash)
        val saleNeed = (requested - cashUsed).coerceAtLeast(0.0)
        var remaining = saleNeed

        val suggestions = holdings
            .filter { it.currentValueEur.isFinite() && it.currentValueEur > 0.01 }
            .sortedWith(
                compareByDescending<LiquidityHolding> { sellPriority(it) }
                    .thenBy { it.advisorScore ?: 101 }
                    .thenByDescending { it.currentValueEur }
                    .thenBy { it.itemId }
            )
            .mapNotNull { holding ->
                if (remaining <= 0.01) return@mapNotNull null
                val amount = min(remaining, holding.currentValueEur)
                if (amount <= 0.01) return@mapNotNull null
                val fullExit = amount >= holding.currentValueEur - 0.01
                val shares = holding.shares
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { total -> if (fullExit) total else total * amount / holding.currentValueEur }
                remaining = (remaining - amount).coerceAtLeast(0.0)
                LiquiditySaleSuggestion(
                    itemId = holding.itemId,
                    instrumentName = holding.instrumentName,
                    amountEur = amount,
                    shares = shares,
                    fullExit = fullExit,
                    currentValueEur = holding.currentValueEur,
                    remainingValueEur = (holding.currentValueEur - amount).coerceAtLeast(0.0),
                    advisorAction = holding.advisorAction,
                    advisorScore = holding.advisorScore,
                    reason = reason(holding)
                )
            }

        return LiquidityNeedPlan(
            requestedEur = requested,
            cashUsedEur = cashUsed,
            saleNeededEur = saleNeed,
            suggestions = suggestions,
            uncoveredEur = remaining.coerceAtLeast(0.0)
        )
    }

    private fun sellPriority(holding: LiquidityHolding): Int {
        val action = when (holding.advisorAction) {
            PortfolioAdvisorAction.VERKAUFEN -> 1000
            PortfolioAdvisorAction.REDUZIEREN -> 800
            PortfolioAdvisorAction.HALTEN -> 500
            PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> 140
            PortfolioAdvisorAction.NACHKAUFEN -> 50
            PortfolioAdvisorAction.NEU_AUFNEHMEN,
            PortfolioAdvisorAction.NICHT_AUFNEHMEN,
            null -> 120
        }
        val score = holding.advisorScore?.let { 100 - it.coerceIn(0, 100) } ?: 0
        val forecast = holding.forecastDirection.orEmpty().uppercase()
        val trend = when {
            "ABWÄRTS" in forecast || "DOWN" in forecast || "NEGATIV" in forecast -> 50
            "AUFWÄRTS" in forecast || "UP" in forecast || "POSITIV" in forecast -> -35
            else -> 0
        }
        val reliability = if (holding.dataReliable) 0 else -40
        return action + score + trend + reliability
    }

    private fun reason(holding: LiquidityHolding): String {
        val base = when (holding.advisorAction) {
            PortfolioAdvisorAction.VERKAUFEN -> "Advisor empfiehlt bereits Verkauf"
            PortfolioAdvisorAction.REDUZIEREN -> "Advisor empfiehlt bereits Reduzierung"
            PortfolioAdvisorAction.HALTEN -> "Halteposition – erst nach schwächeren Positionen verkaufen"
            PortfolioAdvisorAction.NACHKAUFEN -> "Starke Position mit Nachkaufsignal – möglichst zuletzt verkaufen"
            PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "Datenlage nicht belastbar – nur als Reserve verkaufen"
            PortfolioAdvisorAction.NEU_AUFNEHMEN,
            PortfolioAdvisorAction.NICHT_AUFNEHMEN,
            null -> "Keine belastbare Depot-Einordnung – nur verkaufen, wenn nötig"
        }
        val details = buildList {
            holding.advisorScore?.let { add("Score $it") }
            holding.profitLossPct?.takeIf { it.isFinite() }?.let {
                add("G/V ${if (it >= 0) "+" else ""}${String.format(java.util.Locale.GERMANY, "%.1f", it)} %")
            }
            holding.forecastDirection?.takeIf { it.isNotBlank() }?.let { add("Prognose $it") }
        }
        return if (details.isEmpty()) base else "$base · ${details.joinToString(" · ")}"
    }
}
