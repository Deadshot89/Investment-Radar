package de.tobias.investmentradar

import java.util.Locale

data class DepotActionCenterSummary(
    val urgentActions: Int,
    val plannedBuyEur: Double,
    val cashEur: Double
)

data class DepotActionCenterItem(
    val actionId: String,
    val type: ActionType,
    val instrumentId: String,
    val instrumentName: String,
    val actionText: String,
    val reason: String,
    val priority: Int,
    val isHolding: Boolean,
    val displayAmountEur: Double?,
    val executable: Boolean,
    val buyBlocked: Boolean,
    val depotValueEur: Double?,
    val costBasisEur: Double?,
    val profitLossEur: Double?,
    val score: Int?,
    val riskScore: Int?,
    val coveragePct: Int?,
    val forecastDirection: String?,
    val dataQualityLabel: String
)

data class DepotActionCenterState(
    val summary: DepotActionCenterSummary,
    val items: List<DepotActionCenterItem>
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

object DepotActionCenterMapper {
    private val typeRank = mapOf(
        ActionType.SELL to 0,
        ActionType.REDUCE to 1,
        ActionType.REVIEW_SAVINGS_PLAN to 2,
        ActionType.BUY_MORE to 3,
        ActionType.OPEN_POSITION to 4,
        ActionType.KEEP_SAVINGS_PLAN to 5,
        ActionType.HOLD_CASH to 6
    )

    fun build(
        actionPlan: ActionPlan,
        advisorById: Map<String, PortfolioAdvisorCandidate>,
        itemsById: Map<String, InvestmentItem>,
        positions: Map<String, PortfolioPosition>
    ): DepotActionCenterState {
        val mapped = actionPlan.actions.map { action ->
            val candidate = advisorById[action.instrumentId]
            val item = itemsById[action.instrumentId]
            val position = positions[action.instrumentId]
            val isHolding = position != null || candidate?.isHolding == true
            val currentQualityBlocked = currentAnalysisInsufficient(item)
            val backendBlocked = currentQualityBlocked ||
                item?.forecast?.quality.equals("NICHT_BELASTBAR", ignoreCase = true) ||
                item?.dataQuality?.missingBlocks.orEmpty().any { it.equals("forecast", ignoreCase = true) }
            val analysisIncomplete = candidate?.advisor?.reliable == false ||
                backendBlocked ||
                (candidate?.coveragePct != null && candidate.coveragePct < 60)
            val effectiveType = if (action.type == ActionType.KEEP_SAVINGS_PLAN && analysisIncomplete) {
                ActionType.REVIEW_SAVINGS_PLAN
            } else {
                action.type
            }
            val buyBlocked = effectiveType in setOf(ActionType.BUY_MORE, ActionType.OPEN_POSITION) &&
                (candidate == null || candidate.advisor.reliable.not() || (candidate.coveragePct ?: 0) < 60 || backendBlocked)
            val currentValue = candidate?.currentValueEur
            val basis = position?.activeCostBasis?.takeIf { position.performanceCostBasisKnown }
            val profitLoss = if (basis != null && currentValue != null) {
                currentValue - basis + position.realizedProfitLoss()
            } else {
                null
            }
            val executable = when (effectiveType) {
                ActionType.HOLD_CASH -> false
                ActionType.BUY_MORE, ActionType.OPEN_POSITION -> !buyBlocked
                else -> true
            }
            val displayAmount = if (buyBlocked) null else action.amountEur.takeIf { it.isFinite() && it >= 0.0 }

            DepotActionCenterItem(
                actionId = action.actionId,
                type = effectiveType,
                instrumentId = action.instrumentId,
                instrumentName = item?.name ?: if (action.instrumentId == "cash") "Cash" else action.instrumentId,
                actionText = actionText(action, effectiveType, buyBlocked, analysisIncomplete),
                reason = action.reason,
                priority = action.priority,
                isHolding = isHolding,
                displayAmountEur = displayAmount,
                executable = executable,
                buyBlocked = buyBlocked,
                depotValueEur = currentValue,
                costBasisEur = basis,
                profitLossEur = profitLoss,
                score = candidate?.advisor?.score ?: item?.scoreTotal,
                riskScore = candidate?.riskScore ?: item?.risk?.takeIf { it > 0 },
                coveragePct = item?.dataQuality?.overallCoverage ?: candidate?.coveragePct ?: item?.coverage,
                forecastDirection = if (backendBlocked) "Nicht belastbar" else candidate?.forecastDirection ?: item?.forecast?.direction,
                dataQualityLabel = if (analysisIncomplete || buyBlocked) "UNVOLLSTÄNDIG" else "AUSREICHEND"
            )
        }.sortedWith(
            compareBy<DepotActionCenterItem> { typeRank[it.type] ?: Int.MAX_VALUE }
                .thenByDescending { it.isHolding }
                .thenByDescending { it.priority }
                .thenBy { it.instrumentId }
                .thenBy { it.actionId }
        )

        val plannedBuy = mapped
            .filter { it.type in setOf(ActionType.BUY_MORE, ActionType.OPEN_POSITION) && !it.buyBlocked }
            .sumOf { it.displayAmountEur ?: 0.0 }
        val urgent = mapped.count {
            it.type in setOf(ActionType.SELL, ActionType.REDUCE, ActionType.REVIEW_SAVINGS_PLAN)
        }

        return DepotActionCenterState(
            summary = DepotActionCenterSummary(
                urgentActions = urgent,
                plannedBuyEur = plannedBuy,
                cashEur = actionPlan.cashEur.coerceAtLeast(0.0)
            ),
            items = mapped
        )
    }

    private fun currentAnalysisInsufficient(item: InvestmentItem?): Boolean {
        val quality = item?.dataQuality ?: return false
        val isEtf = item.type.equals("ETF", ignoreCase = true)
        val missing = quality.missingBlocks.map { it.lowercase() }.toSet()
        val criticalMissing = "quote" in missing ||
            "history" in missing ||
            "forecast" in missing ||
            (!isEtf && "fundamentals" in missing)

        return (quality.quoteCoverage ?: 0) < 100 ||
            (quality.historyCoverage ?: 0) < 70 ||
            (!isEtf && (quality.fundamentalCoverage ?: 0) < 60) ||
            (quality.forecastInputCoverage ?: 0) < 70 ||
            (quality.overallCoverage ?: 0) < 70 ||
            quality.criticalConflicts.isNotEmpty() ||
            criticalMissing
    }

    private fun actionText(action: ActionPlanAction, effectiveType: ActionType, buyBlocked: Boolean, analysisIncomplete: Boolean): String {
        if (buyBlocked) return "Nicht kaufen – Datenbasis unvollständig"
        val amount = formatEur(action.amountEur)
        return when (effectiveType) {
            ActionType.SELL -> "Verkauf von ca. $amount prüfen"
            ActionType.REDUCE -> "Reduzierung um ca. $amount prüfen"
            ActionType.REVIEW_SAVINGS_PLAN -> if (analysisIncomplete && action.type == ActionType.KEEP_SAVINGS_PLAN) {
                "Sparplan $amount prüfen – Datenbasis unvollständig"
            } else {
                "Sparplan $amount prüfen"
            }
            ActionType.BUY_MORE -> "Position um ca. $amount erhöhen"
            ActionType.OPEN_POSITION -> "Neue Position mit ca. $amount eröffnen"
            ActionType.KEEP_SAVINGS_PLAN -> "Sparplan $amount beibehalten"
            ActionType.HOLD_CASH -> "$amount als Cash halten"
        }
    }

    private fun formatEur(value: Double): String = String.format(Locale.GERMANY, "%.0f €", value)
}
