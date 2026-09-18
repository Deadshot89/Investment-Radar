package de.tobias.investmentradar

data class AdvisorNotificationText(
    val title: String,
    val body: String,
    val level: String
)

object AdvisorNotificationCopy {
    fun format(
        event: AdvisorNotificationEvent,
        displayName: String,
        fromDisplayName: String? = null,
        toDisplayName: String? = null
    ): AdvisorNotificationText {
        val name = displayName.ifBlank { event.instrumentId }
        val reason = event.reasons.firstOrNull().orEmpty().trim()

        if (event.kind == AdvisorNotificationEventKind.RELIABILITY_LOST ||
            event.newSignal == AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG
        ) {
            return AdvisorNotificationText(
                title = "🟡 HALTEN – $name",
                body = body("HALTEN", "Datenwarnung: " + reason.ifBlank { "Die Datenbasis reicht aktuell nicht für eine belastbare Entscheidung." }),
                level = "WATCH"
            )
        }

        if (event.kind == AdvisorNotificationEventKind.PURCHASE_ALLOCATION) {
            val amount = event.amountEur?.coerceAtLeast(0) ?: 0
            val isExistingHolding = event.portfolioAction == PortfolioAdvisorAction.NACHKAUFEN
            val label = if (isExistingHolding) "NACHKAUFEN" else "KAUFEN"
            val detail = buildString {
                if (reason.isNotBlank()) append(reason)
            }
            return AdvisorNotificationText(
                title = "🟢 $label · $amount € – $name",
                body = body("$label · $amount €", detail),
                level = "BUY"
            )
        }

        if (event.kind == AdvisorNotificationEventKind.REALLOCATION) {
            val fromName = fromDisplayName?.takeIf { it.isNotBlank() } ?: name
            val toName = toDisplayName?.takeIf { it.isNotBlank() }
            val amount = event.amountEur?.takeIf { it > 0 }
            val detail = buildString {
                if (amount != null) append("$amount € ")
                append("$fromName teilweise verkaufen")
                if (toName != null) append(" und nach $toName umschichten")
                if (reason.isNotBlank()) append(". $reason")
            }
            return AdvisorNotificationText(
                title = "🔴 VERKAUFEN – $fromName",
                body = body("VERKAUFEN", detail),
                level = "SELL"
            )
        }

        val action = when (event.newSignal) {
            AdvisorSignal.NACHKAUFEN -> Action("🟢", "NACHKAUFEN", "BUY")
            AdvisorSignal.HALTEN -> Action("🟡", "HALTEN", "WATCH")
            AdvisorSignal.REDUZIEREN -> Action("🔴", "VERKAUFEN", "SELL")
            AdvisorSignal.VERKAUFEN -> Action("🔴", "VERKAUFEN", "SELL")
            AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> Action("🟡", "HALTEN", "WATCH")
        }

        val detail = when (event.kind) {
            AdvisorNotificationEventKind.NEW_STRONG_OPPORTUNITY -> {
                if (reason.isBlank()) "Neue starke Radar-Chance." else "Neue starke Radar-Chance. $reason"
            }
            else -> reason
        }

        return AdvisorNotificationText(
            title = "${action.emoji} ${action.label} – $name",
            body = body(action.label, detail),
            level = action.level
        )
    }

    private fun body(action: String, detail: String): String = buildString {
        append("Entscheidung: $action")
        if (detail.isNotBlank()) append(" · $detail")
    }

    private data class Action(
        val emoji: String,
        val label: String,
        val level: String
    )
}
