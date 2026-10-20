package de.tobias.investmentradar

/**
 * This classifies only source metadata already supplied by the backend or a user.
 * Missing delay flags or a known source do not prove that a quote is real-time.
 */
data class LiveQuoteProvenance(
    val label: String,
    val notice: String? = null
)

object LiveQuoteProvenanceResolver {
    fun resolve(
        marketPriceEur: Double?,
        manualPriceEur: Double?,
        marketDelayed: Boolean,
        fxDelayed: Boolean,
        dataError: String?,
        dataSource: String?
    ): LiveQuoteProvenance {
        val hasMarketPrice = marketPriceEur?.let { it.isFinite() && it > 0.0 } == true
        val hasManualPrice = manualPriceEur?.let { it.isFinite() && it > 0.0 } == true

        if (!hasMarketPrice) {
            return if (hasManualPrice) {
                LiveQuoteProvenance("Manueller Kurs", "Manuell hinterlegt · kein Live-Kurs")
            } else {
                LiveQuoteProvenance("Kurs nicht verfügbar")
            }
        }

        val warnings = buildList {
            if (marketDelayed) add("Marktdaten verzögert")
            if (fxDelayed) add("EUR-Umrechnung verzögert")
            if (!dataError.isNullOrBlank()) add("Datenwarnung vorhanden")
        }
        val source = dataSource?.trim()?.takeIf { it.isNotBlank() }
        return LiveQuoteProvenance(
            label = if (marketDelayed || fxDelayed) "Kurs (verzögert)" else "Kurs",
            notice = (warnings + listOfNotNull(source?.let { "Quelle: $it" }))
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" · ")
        )
    }
}
