package de.tobias.investmentradar

import kotlin.math.roundToInt

object AdvisorEngine {
    private const val MIN_COVERAGE = 60

    fun evaluate(input: AdvisorInput): AdvisorResult {
        val missing = missingRequiredMetrics(input)
        if (!input.isFresh || input.coveragePct < MIN_COVERAGE || missing.isNotEmpty()) {
            val reasons = buildList {
                if (!input.isFresh) add("Die Daten sind für eine neue Handlungsempfehlung zu alt.")
                if (input.coveragePct < MIN_COVERAGE) add("Die Datenabdeckung ist für eine belastbare Entscheidung zu gering.")
                if (missing.isNotEmpty()) add("Für die Bewertung fehlen Pflichtdaten: ${missing.joinToString(", ")}.")
            }
            return AdvisorResult(
                instrumentId = input.instrumentId,
                signal = AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG,
                score = null,
                reliable = false,
                reasons = reasons,
                risks = emptyList()
            )
        }

        val score = when (input.instrumentType) {
            AdvisorInstrumentType.STOCK -> stockScore(input)
            AdvisorInstrumentType.ETF -> etfScore(input)
            AdvisorInstrumentType.FIXED_INCOME -> fixedIncomeScore(input)
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
            reasons = positiveReasons(input, score),
            risks = riskReasons(input)
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
                if (forecast12m(input) == null) add("12M-Prognose")
            }
            AdvisorInstrumentType.ETF -> {
                if (input.valuation == null) add("Bewertung")
                if (input.momentum == null) add("Momentum")
                if (input.riskScore == null) add("Risiko")
                if (forecast12m(input) == null) add("12M-Prognose")
            }
            AdvisorInstrumentType.FIXED_INCOME -> {
                if (input.momentum == null) add("Momentum")
                if (input.riskScore == null) add("Risiko")
                if (forecast12m(input) == null) add("12M-Prognose")
            }
        }
    }

    private fun stockScore(input: AdvisorInput): Double =
        input.quality!!.bounded() * 0.25 +
            input.valuation!!.bounded() * 0.20 +
            input.growth!!.bounded() * 0.20 +
            input.momentum!!.bounded() * 0.15 +
            input.riskScore!!.bounded() * 0.10 +
            forecastScore(requireNotNull(forecast12m(input))) * 0.10

    private fun etfScore(input: AdvisorInput): Double =
        input.valuation!!.bounded() * 0.30 +
            input.momentum!!.bounded() * 0.25 +
            input.riskScore!!.bounded() * 0.25 +
            forecastScore(requireNotNull(forecast12m(input))) * 0.20

    private fun fixedIncomeScore(input: AdvisorInput): Double =
        input.momentum!!.bounded() * 0.20 +
            input.riskScore!!.bounded() * 0.50 +
            forecastScore(requireNotNull(forecast12m(input))) * 0.30

    private fun forecast12m(input: AdvisorInput): Double? =
        input.forecastRanges
            .firstOrNull { it.horizon == ForecastHorizon.TWELVE_MONTHS && it.reliable }
            ?.expectedChangePct

    private fun forecastScore(changePct: Double): Double =
        (50.0 + changePct.coerceIn(-20.0, 20.0) * 2.5).coerceIn(0.0, 100.0)

    private fun positiveReasons(input: AdvisorInput, score: Int): List<String> = buildList {
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
        forecast12m(input)?.let {
            if (it >= 8.0) add("Die 12-Monats-Prognose zeigt positives Potenzial.")
            else if (it <= -5.0) add("Die 12-Monats-Prognose zeigt Abwärtsrisiko.")
        }
        if (isEmpty()) add("Der kombinierte Advisor-Score liegt bei $score/100.")
    }.take(4)

    private fun riskReasons(input: AdvisorInput): List<String> = buildList {
        input.riskScore?.let {
            if (it <= 35) add("Das Risikoprofil ist aktuell ungünstig.")
        }
        if (input.coveragePct < 75) add("Die Datenabdeckung ist nur mittel und erhöht die Unsicherheit.")
        forecast12m(input)?.let {
            if (it < 0.0) add("Die Basisschätzung für zwölf Monate ist negativ.")
        }
    }.take(3)

    private fun Int.bounded(): Double = coerceIn(0, 100).toDouble()
}
