package de.tobias.investmentradar

import java.util.Locale
import kotlin.math.min

data class RecommendationTradePrefillResult(
    val amountEur: Double,
    val shares: Double?,
    val message: String
)

object RecommendationTradePrefill {
    fun calculate(
        type: ActionType,
        amountEur: Double?,
        priceEur: Double?,
        heldShares: Double?
    ): RecommendationTradePrefillResult {
        val amount = amountEur?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val price = priceEur?.takeIf { it.isFinite() && it > 0.0 }
        val holding = heldShares?.takeIf { it.isFinite() && it > 0.0 }

        val shares = when (type) {
            ActionType.SELL -> holding
            ActionType.REDUCE -> if (price != null && amount > 0.0) {
                val calculated = amount / price
                holding?.let { min(calculated, it) } ?: calculated
            } else null
            ActionType.BUY_MORE, ActionType.OPEN_POSITION ->
                if (price != null && amount > 0.0) amount / price else null
            else -> null
        }

        val message = when {
            type == ActionType.SELL && shares != null ->
                "Vorschlag für vollständigen Verkauf: ${formatShares(shares)} Anteile."
            type == ActionType.SELL ->
                "Bestand nicht eindeutig – verkaufte Anteile bitte manuell prüfen."
            type in setOf(ActionType.BUY_MORE, ActionType.OPEN_POSITION, ActionType.REDUCE) && price == null ->
                "Kein belastbarer EUR-Kurs – Stückzahl / Anteile bitte manuell erfassen."
            shares != null && type == ActionType.REDUCE ->
                "Vorschlag: ca. ${formatShares(shares)} Anteile reduzieren."
            shares != null ->
                "Vorschlag: ca. ${formatShares(shares)} Anteile zum aktuellen EUR-Kurs."
            else -> "Keine automatische Stückzahl-Vorbelegung für diese Aktion."
        }

        return RecommendationTradePrefillResult(
            amountEur = amount,
            shares = shares,
            message = message
        )
    }

    private fun formatShares(value: Double): String =
        String.format(Locale.GERMANY, "%.6f", value).trimEnd('0').trimEnd(',')
}
