package de.tobias.investmentradar

import kotlin.math.floor
import kotlin.math.min

object ReallocationPolicy {
    fun suggest(candidates: List<PortfolioAdvisorCandidate>): List<ReallocationSuggestion> {
        val targets = candidates.filter { candidate ->
            candidate.advisor.reliable &&
                candidate.advisor.score != null &&
                (candidate.action == PortfolioAdvisorAction.NACHKAUFEN ||
                    candidate.action == PortfolioAdvisorAction.NEU_AUFNEHMEN)
        }

        return candidates.mapNotNull { source ->
            if (!source.isHolding) return@mapNotNull null
            if (source.action != PortfolioAdvisorAction.REDUZIEREN &&
                source.action != PortfolioAdvisorAction.VERKAUFEN
            ) return@mapNotNull null

            val sourceScore = source.advisor.score ?: return@mapNotNull null
            val sourceValue = source.currentValueEur?.takeIf { it > 0.0 } ?: return@mapNotNull null
            val target = targets
                .filter { it.itemId != source.itemId }
                .filter { (it.advisor.score ?: Int.MIN_VALUE) - sourceScore >= 15 }
                .maxByOrNull { it.advisor.score ?: Int.MIN_VALUE }
                ?: return@mapNotNull null

            val amount = when (source.action) {
                PortfolioAdvisorAction.REDUZIEREN -> floor(min(sourceValue * 0.25, 50.0)).toInt()
                PortfolioAdvisorAction.VERKAUFEN -> floor(min(sourceValue * 0.50, 100.0)).toInt()
                else -> 0
            }
            if (amount <= 0) return@mapNotNull null

            ReallocationSuggestion(
                fromItemId = source.itemId,
                toItemId = target.itemId,
                amountEur = amount,
                reason = "${target.itemId} ist im Advisor mindestens 15 Punkte stärker"
            )
        }
    }
}
