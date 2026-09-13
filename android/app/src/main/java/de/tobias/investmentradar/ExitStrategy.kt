package de.tobias.investmentradar

import java.util.Locale

enum class ExitTriggerKind { TAKE_PROFIT, STOP_LOSS }

data class ExitStrategy(
    val itemId: String,
    val enabled: Boolean = false,
    val takeProfitPct: Double? = null,
    val stopLossPct: Double? = null
) {
    fun normalized(): ExitStrategy {
        val take = takeProfitPct?.takeIf { it.isFinite() && it > 0.0 }
        val stop = stopLossPct?.takeIf { it.isFinite() && it > 0.0 }
        return copy(
            itemId = itemId.trim(),
            enabled = enabled && (take != null || stop != null),
            takeProfitPct = take,
            stopLossPct = stop
        )
    }
}

data class ExitStrategyEvaluation(
    val itemId: String,
    val kind: ExitTriggerKind?,
    val currentProfitLossPct: Double?,
    val thresholdPct: Double?,
    val currentValueEur: Double?,
    val shares: Double?,
    val reason: String
) { val triggered: Boolean get() = kind != null }

data class ExitStrategyTrigger(
    val eventId: String,
    val itemId: String,
    val kind: ExitTriggerKind,
    val currentProfitLossPct: Double,
    val thresholdPct: Double,
    val currentValueEur: Double?,
    val shares: Double?,
    val reason: String
)

object ExitStrategyEngine {
    fun evaluate(position: PortfolioPosition, currentPriceEur: Double?, strategy: ExitStrategy): ExitStrategyEvaluation {
        val s = strategy.normalized()
        val currentValue = position.currentValue(currentPriceEur)
        val shares = position.shares.takeIf { it.isFinite() && it > 0.0000001 }
            ?: position.trackedShares?.takeIf { it.isFinite() && it > 0.0000001 }
        val pnl = position.unrealizedProfitLossPercent(currentPriceEur)
        if (!s.enabled || pnl == null || !pnl.isFinite()) {
            return ExitStrategyEvaluation(s.itemId, null, pnl, null, currentValue, shares,
                if (!s.enabled) "Ausstiegsplan nicht aktiv" else "Einstand oder aktueller Kurs fehlt")
        }
        s.stopLossPct?.let { stop ->
            if (pnl <= -stop) return ExitStrategyEvaluation(
                s.itemId, ExitTriggerKind.STOP_LOSS, pnl, stop, currentValue, shares,
                "Verlustgrenze ${fmt(-stop)} erreicht · aktuell ${fmt(pnl)}"
            )
        }
        s.takeProfitPct?.let { take ->
            if (pnl >= take) return ExitStrategyEvaluation(
                s.itemId, ExitTriggerKind.TAKE_PROFIT, pnl, take, currentValue, shares,
                "Gewinnziel ${fmt(take)} erreicht · aktuell ${fmt(pnl)}"
            )
        }
        return ExitStrategyEvaluation(s.itemId, null, pnl, null, currentValue, shares,
            "Ausstiegsplan aktiv · aktuell ${fmt(pnl)}")
    }
    private fun fmt(v: Double): String = String.format(Locale.GERMANY, "%+.1f %%", v)
}

object ExitStrategyActionOverlay {
    fun apply(
        plan: ActionPlan,
        positions: Map<String, PortfolioPosition>,
        currentPricesEur: Map<String, Double?>,
        strategies: Map<String, ExitStrategy>
    ): ActionPlan {
        val exits = strategies.values.mapNotNull { strategy ->
            val pos = positions[strategy.itemId]?.takeIf { it.isActiveHolding() } ?: return@mapNotNull null
            val e = ExitStrategyEngine.evaluate(pos, currentPricesEur[strategy.itemId], strategy)
            val kind = e.kind ?: return@mapNotNull null
            val value = e.currentValueEur?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            ActionPlanAction(
                actionId = "exit-${kind.name.lowercase(Locale.ROOT)}-${strategy.itemId}-${e.thresholdPct}",
                type = ActionType.SELL,
                instrumentId = strategy.itemId,
                amountEur = value,
                plannedShares = e.shares,
                fromInstrumentId = strategy.itemId,
                reason = e.reason,
                priority = if (kind == ExitTriggerKind.STOP_LOSS) 220 else 210
            )
        }
        if (exits.isEmpty()) return plan
        val ids = exits.mapTo(mutableSetOf()) { it.instrumentId }
        val rest = plan.actions.filterNot { it.instrumentId in ids && (it.type == ActionType.SELL || it.type == ActionType.REDUCE) }
        return plan.copy(actions=(exits+rest).sortedWith(
            compareByDescending<ActionPlanAction>{it.priority}.thenBy{it.type.name}.thenBy{it.instrumentId}.thenBy{it.actionId}
        ))
    }
}
