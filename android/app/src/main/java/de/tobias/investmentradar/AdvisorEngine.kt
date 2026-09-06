package de.tobias.investmentradar

import kotlin.math.roundToInt

object AdvisorEngine {
    private const val MIN_COVERAGE = 60

    fun evaluate(input: AdvisorInput): AdvisorResult {
        val missing = missingRequiredMetrics(input)
        val strategicRanges = strategicRanges(input)
        if (!input.isFresh || input.coveragePct < MIN_COVERAGE || missing.isNotEmpty() || strategicRanges == null) {
            val reasons = buildList {
                if (!input.isFresh) add("Die Daten sind für eine neue Handlungsempfehlung zu alt.")
                if (input.coveragePct < MIN_COVERAGE) add("Die Datenabdeckung ist für eine belastbare Entscheidung zu gering.")
                if (missing.isNotEmpty()) add("Für die Bewertung fehlen Pflichtdaten: ${missing.joinToString(", ")}.")
                if (strategicRanges == null && input.forecastRanges.isNotEmpty()) add("Für die strategische Bewertung fehlen belastbare 6M-/12M-Prognosen.")
            }
            return AdvisorResult(
                instrumentId = input.instrumentId,
                signal = AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG,
                score = null,
                reliable = false,
                reasons = reasons,
                risks = emptyList(),
                confidencePct = 0,
                timingFactor = 1.0
            )
        }

        val strategicForecast = strategicForecastPct(strategicRanges)
        val score = when (input.instrumentType) {
            AdvisorInstrumentType.STOCK -> stockScore(input, strategicForecast)
            AdvisorInstrumentType.ETF -> etfScore(input, strategicForecast)
            AdvisorInstrumentType.FIXED_INCOME -> fixedIncomeScore(input, strategicForecast)
        }.roundToInt().coerceIn(0, 100)

        val signal = when {
            score >= 72 -> AdvisorSignal.NACHKAUFEN
            score >= 52 -> AdvisorSignal.HALTEN
            score >= 35 -> AdvisorSignal.REDUZIEREN
            else -> AdvisorSignal.VERKAUFEN
        }

        return AdvisorResult(
            instrumentId = input.instrumentId,
            signal = signal,
            score = score,
            reliable = true,
            reasons = positiveReasons(input, score, strategicForecast),
            risks = riskReasons(input, strategicForecast),
            confidencePct = strategicConfidence(strategicRanges),
            timingFactor = timingFactor(input)
        )
    }

    private fun missingRequiredMetrics(input: AdvisorInput): List<String> = buildList {
        when (input.instrumentType) {
            AdvisorInstrumentType.STOCK -> {
                if (input.quality == null) add("Qualität")
                if (input.valuation == null) add("Bewertung")
                if (input.growth == null) add("Wachstum")
                if (input.momentum == null) add("Momentum")
                if (input.riskScore == null) add("Risiko")
            }
            AdvisorInstrumentType.ETF -> {
                if (input.valuation == null) add("Bewertung")
                if (input.momentum == null) add("Momentum")
                if (input.riskScore == null) add("Risiko")
            }
            AdvisorInstrumentType.FIXED_INCOME -> {
                if (input.momentum == null) add("Momentum")
                if (input.riskScore == null) add("Risiko")
            }
        }
    }

    private fun strategicRanges(input: AdvisorInput): Pair<AdvisorForecastRange, AdvisorForecastRange>? {
        val six = input.forecastRanges.firstOrNull {
            it.horizon == ForecastHorizon.SIX_MONTHS && it.reliable
        } ?: return null
        val twelve = input.forecastRanges.firstOrNull {
            it.horizon == ForecastHorizon.TWELVE_MONTHS && it.reliable
        } ?: return null
        return six to twelve
    }

    private fun strategicForecastPct(ranges: Pair<AdvisorForecastRange, AdvisorForecastRange>): Double =
        ranges.first.expectedChangePct * 0.40 + ranges.second.expectedChangePct * 0.60

    private fun strategicConfidence(ranges: Pair<AdvisorForecastRange, AdvisorForecastRange>): Int =
        (ranges.first.confidencePct * 0.40 + ranges.second.confidencePct * 0.60)
            .roundToInt()
            .coerceIn(0, 100)

    private fun timingFactor(input: AdvisorInput): Double {
        val one = input.forecastRanges.firstOrNull {
            it.horizon == ForecastHorizon.ONE_MONTH && it.reliable
        }?.expectedChangePct
        val three = input.forecastRanges.firstOrNull {
            it.horizon == ForecastHorizon.THREE_MONTHS && it.reliable
        }?.expectedChangePct
        if (one == null || three == null) return 1.0
        val shortTermPct = one * 0.40 + three * 0.60
        return (1.0 + shortTermPct / 100.0).coerceIn(0.80, 1.10)
    }

    private fun stockScore(input: AdvisorInput, strategicForecast: Double): Double =
        input.quality!!.bounded() * 0.25 +
            input.valuation!!.bounded() * 0.20 +
            input.growth!!.bounded() * 0.20 +
            input.momentum!!.bounded() * 0.15 +
            input.riskScore!!.bounded() * 0.10 +
            forecastScore(strategicForecast) * 0.10

    private fun etfScore(input: AdvisorInput, strategicForecast: Double): Double =
        input.valuation!!.bounded() * 0.30 +
            input.momentum!!.bounded() * 0.25 +
            input.riskScore!!.bounded() * 0.25 +
            forecastScore(strategicForecast) * 0.20

    private fun fixedIncomeScore(input: AdvisorInput, strategicForecast: Double): Double =
        input.momentum!!.bounded() * 0.20 +
            input.riskScore!!.bounded() * 0.50 +
            forecastScore(strategicForecast) * 0.30

    private fun forecastScore(changePct: Double): Double =
        (50.0 + changePct.coerceIn(-20.0, 20.0) * 2.5).coerceIn(0.0, 100.0)

    private fun positiveReasons(input: AdvisorInput, score: Int, strategicForecast: Double): List<String> = buildList {
        input.quality?.let {
            if (it >= 75) add("Hohe Qualität stützt die Bewertung.")
            else if (it <= 40) add("Die Qualitätskennzahlen sind schwach.")
        }
        input.valuation?.let {
            if (it >= 70) add("Die Bewertung ist im Radar attraktiv.")
            else if (it <= 40) add("Die Bewertung ist anspruchsvoll.")
        }
        input.growth?.let {
            if (it >= 70) add("Starkes Wachstum verbessert das Chance-Risiko-Verhältnis.")
            else if (it <= 40) add("Schwaches Wachstum begrenzt das Potenzial.")
        }
        input.momentum?.let {
            if (it >= 70) add("Positives Momentum unterstützt das aktuelle Signal.")
            else if (it <= 40) add("Negatives Momentum belastet das Signal.")
        }
        if (strategicForecast >= 8.0) add("Die 6-/12-Monats-Prognose zeigt positives Potenzial.")
        else if (strategicForecast <= -5.0) add("Die 6-/12-Monats-Prognose zeigt Abwärtsrisiko.")
        if (isEmpty()) add("Der kombinierte Advisor-Score liegt bei $score/100.")
    }.distinct().take(4)

    private fun riskReasons(input: AdvisorInput, strategicForecast: Double): List<String> = buildList {
        input.riskScore?.let {
            if (it <= 35) add("Das Risikoprofil ist aktuell ungünstig.")
        }
        if (input.coveragePct < 75) add("Die Datenabdeckung ist nur mittel und erhöht die Unsicherheit.")
        if (strategicForecast < 0.0) add("Die strategische 6-/12-Monats-Schätzung ist negativ.")
    }.take(3)

    private fun Int.bounded(): Double = coerceIn(0, 100).toDouble()
}
