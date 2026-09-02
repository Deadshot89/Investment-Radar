package de.tobias.investmentradar

object RecommendationPresentation {
    fun label(item: InvestmentItem): String = when (effectiveRecommendation(item)) {
        "BUY" -> "KAUFEN"
        "WATCH" -> "BEOBACHTEN"
        "NO_BUY" -> "NICHT KAUFEN"
        "REVIEW" -> "VERKAUF PRÜFEN"
        else -> "BEOBACHTEN"
    }

    fun confidence(item: InvestmentItem): String {
        val coverage = item.coverage
        val score = item.scoreTotal
        if (coverage != null && coverage < 50) return "ZU WENIG DATEN"
        if (coverage != null && coverage < 70) return "REDUZIERTE DATEN"
        return when {
            score == null -> "DATEN AUSSTEHEND"
            score >= 85 -> "SEHR STARK"
            score >= 75 -> "STARK"
            score >= 55 -> "MITTEL"
            else -> "SCHWACH"
        }
    }

    fun scoreText(score: Int?): String = score?.let { "$it/100" } ?: "—"

    fun topReasons(item: InvestmentItem): List<String> = item.recommendationReasons.filter { it.isNotBlank() }.take(3)

    fun effectiveRecommendation(item: InvestmentItem): String = recommendationFallback(item.recommendation, item.status)
}
