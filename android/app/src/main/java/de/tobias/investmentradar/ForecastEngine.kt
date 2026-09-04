package de.tobias.investmentradar

import kotlin.math.sqrt

enum class ForecastHorizon(val months: Int, val label: String) {
    ONE_MONTH(1, "1 Monat"),
    THREE_MONTHS(3, "3 Monate"),
    SIX_MONTHS(6, "6 Monate"),
    TWELVE_MONTHS(12, "12 Monate")
}

data class ForecastPoint(
    val horizon: ForecastHorizon,
    val expectedChangePct: Double,
    val bearChangePct: Double,
    val bullChangePct: Double,
    val targetPriceEur: Double?,
    val bearTargetPriceEur: Double?,
    val bullTargetPriceEur: Double?,
    val direction: String,
    val reasons: List<String>
)

data class InvestmentForecast(
    val points: List<ForecastPoint>,
    val coveragePct: Int?,
    val modelLabel: String = "Modellbasierte Einschätzung"
)

/**
 * Transparentes Szenario-Modell auf Basis der bereits vorhandenen Radar-Daten.
 * Es ist kein Analystenkursziel und keine Garantie für zukünftige Kurse.
 */
object ForecastEngine {
    fun forecast(item: InvestmentItem): InvestmentForecast {
        val basePrice = item.priceEur
            ?: item.price?.takeIf { item.currency.isBlank() || item.currency.equals("EUR", ignoreCase = true) }
        val fundamentalAnnual = fundamentalAnnualDrift(item)

        val points = ForecastHorizon.entries.map { horizon ->
            val momentum = momentumFor(item.momentum, horizon)
            val horizonScale = horizon.months / 12.0
            val momentumWeight = when (horizon) {
                ForecastHorizon.ONE_MONTH -> 0.58
                ForecastHorizon.THREE_MONTHS -> 0.50
                ForecastHorizon.SIX_MONTHS -> 0.42
                ForecastHorizon.TWELVE_MONTHS -> 0.34
            }
            val rawExpected = fundamentalAnnual * horizonScale + (momentum ?: 0.0) * momentumWeight
            val cap = when (horizon) {
                ForecastHorizon.ONE_MONTH -> 14.0
                ForecastHorizon.THREE_MONTHS -> 28.0
                ForecastHorizon.SIX_MONTHS -> 42.0
                ForecastHorizon.TWELVE_MONTHS -> 65.0
            }
            val expected = rawExpected.coerceIn(-cap, cap)
            val uncertainty = uncertaintyPct(item, horizon)
            val bear = (expected - uncertainty).coerceAtLeast(-80.0)
            val bull = (expected + uncertainty).coerceAtMost(120.0)

            ForecastPoint(
                horizon = horizon,
                expectedChangePct = expected,
                bearChangePct = bear,
                bullChangePct = bull,
                targetPriceEur = basePrice?.targetFrom(expected),
                bearTargetPriceEur = basePrice?.targetFrom(bear),
                bullTargetPriceEur = basePrice?.targetFrom(bull),
                direction = direction(expected),
                reasons = reasons(item, horizon, momentum).take(4)
            )
        }

        return InvestmentForecast(points = points, coveragePct = item.coverage)
    }

    private fun fundamentalAnnualDrift(item: InvestmentItem): Double {
        fun centered(score: Int?, maxImpact: Double): Double =
            score?.let { ((it.coerceIn(0, 100) - 50) / 50.0) * maxImpact } ?: 0.0

        var drift = 0.0
        drift += centered(item.scoreQuality, 5.0)
        drift += centered(item.scoreValuation, 5.0)
        drift += centered(item.scoreGrowth, 8.0)
        drift += centered(item.scoreRisk, 3.0)

        item.fundamentals?.revenueGrowth?.let { drift += (it * 100.0 * 0.12).coerceIn(-4.0, 4.0) }
        item.fundamentals?.epsGrowth?.let { drift += (it * 100.0 * 0.14).coerceIn(-5.0, 5.0) }
        item.fundamentals?.freeCashFlowYield?.let { drift += ((it * 100.0) - 3.0).coerceIn(-2.0, 3.0) * 0.5 }
        item.fundamentals?.debtToEquity?.let { debt ->
            if (debt > 2.0) drift -= ((debt - 2.0) * 1.2).coerceAtMost(4.0)
        }
        return drift.coerceIn(-30.0, 35.0)
    }

    private fun momentumFor(momentum: MomentumSnapshot?, horizon: ForecastHorizon): Double? = when (horizon) {
        ForecastHorizon.ONE_MONTH -> momentum?.m1
        ForecastHorizon.THREE_MONTHS -> momentum?.m3
        ForecastHorizon.SIX_MONTHS -> momentum?.m6
        ForecastHorizon.TWELVE_MONTHS -> momentum?.m12
    }

    private fun uncertaintyPct(item: InvestmentItem, horizon: ForecastHorizon): Double {
        val risk = item.risk.coerceIn(1, 5)
        val coveragePenalty = when {
            item.coverage == null -> 4.0
            item.coverage < 50 -> 6.0
            item.coverage < 70 -> 3.5
            item.coverage < 85 -> 1.5
            else -> 0.0
        }
        return ((5.0 + risk * 2.4 + coveragePenalty) * sqrt(horizon.months / 12.0)).coerceAtLeast(3.0)
    }

    private fun direction(changePct: Double): String = when {
        changePct >= 2.5 -> "↗ Aufwärts"
        changePct <= -2.5 -> "↘ Abwärts"
        else -> "→ Seitwärts"
    }

    private fun reasons(item: InvestmentItem, horizon: ForecastHorizon, momentum: Double?): List<String> {
        val result = mutableListOf<String>()
        momentum?.let {
            when {
                it >= 5.0 -> result += "Positives ${horizon.label}-Momentum stützt das Basisszenario."
                it <= -5.0 -> result += "Negatives ${horizon.label}-Momentum belastet das Basisszenario."
                else -> result += "Das Momentum ist aktuell gemischt und liefert nur wenig Richtung."
            }
        }
        item.scoreGrowth?.let {
            when {
                it >= 70 -> result += "Der Wachstumsscore ist stark und erhöht das mittelfristige Potenzial."
                it <= 40 -> result += "Der schwache Wachstumsscore begrenzt das Kurspotenzial."
            }
        }
        item.scoreValuation?.let {
            when {
                it >= 70 -> result += "Die Bewertung ist im Radar attraktiv und gibt Spielraum nach oben."
                it <= 40 -> result += "Die Bewertung ist anspruchsvoll und erhöht das Rückschlagrisiko."
            }
        }
        item.scoreQuality?.let {
            if (it >= 75) result += "Hohe Unternehmensqualität stabilisiert das Basisszenario."
            else if (it <= 40) result += "Die Qualitätskennzahlen erhöhen die Unsicherheit."
        }
        item.fundamentals?.epsGrowth?.let {
            if (it >= 0.10) result += "Zweistelliges Gewinnwachstum unterstützt höhere zukünftige Bewertungen."
            else if (it < 0.0) result += "Sinkende Gewinne sprechen gegen eine aggressive Kursprognose."
        }
        item.fundamentals?.debtToEquity?.let {
            if (it > 2.0) result += "Die erhöhte Verschuldung verbreitert das schwache Szenario."
        }
        if (item.risk >= 4) result += "Das hohe Risikoprofil sorgt für eine breite Prognosespanne."
        if ((item.coverage ?: 0) < 70) result += "Die reduzierte Datenabdeckung erhöht die Prognoseunsicherheit."
        if (result.isEmpty()) result += "Die Prognose wird überwiegend aus Score, Risiko und vorhandenen Kursdaten abgeleitet."
        return result.distinct()
    }

    private fun Double.targetFrom(changePct: Double): Double = this * (1.0 + changePct / 100.0)
}
