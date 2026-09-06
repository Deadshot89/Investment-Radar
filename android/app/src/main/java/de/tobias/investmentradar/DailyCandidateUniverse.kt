package de.tobias.investmentradar

object DailyCandidateUniverse {
    fun topRadarQuery(): RadarQuery = RadarQuery(
        recommendation = "BUY",
        sort = "SCORE_DESC",
        page = 1,
        pageSize = 20,
        tradeRepublicVerified = true,
        includeCounts = false
    )

    fun candidateIds(page: RadarPage): Set<String> = page.items
        .asSequence()
        .filter { it.id.isNotBlank() }
        .filter { it.recommendation == "BUY" }
        .filter { it.tradeRepublicEligible == true && it.purchaseEligible }
        .map { it.id }
        .toSet()
}
