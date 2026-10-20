package de.tobias.investmentradar

/**
 * An available price is not necessarily a real-time market quote. The API provides
 * delay/source/error flags but not a verified quote execution timestamp.
 */
object PriceLineQuoteStatus {
    fun render(
        hasPrice: Boolean,
        delayed: Boolean,
        fxDelayed: Boolean,
        source: String,
        dataError: String?
    ): String {
        if (!hasPrice) return ""
        val notes = buildList {
            add(if (delayed) "Kurs verzögert" else "Kurszeit nicht bestätigt")
            if (fxDelayed) add("EUR-Umrechnung verzögert")
            if (!dataError.isNullOrBlank()) add("Datenwarnung")
            source.trim().takeIf { it.isNotBlank() }?.let { add("Quelle: $it") }
        }
        return notes.joinToString(separator = " · ", prefix = " · ")
    }
}
