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
                title = "⚠️ NICHT HANDELN – $name",
                body = body("NICHT HANDELN", reason.ifBlank { "Die Datenbasis reicht aktuell nicht für eine belastbare Entscheidung." }),
                level = "REVIEW"
            )
        }

        if (event.kind == AdvisorNotificationEventKind.REALLOCATION) {
            val fromName = fromDisplayName?.takeIf { it.isNotBlank() } ?: name
            val toName = toDisplayName?.takeIf { it.isNotBlank() }
            val amount = event.amountEur?.takeIf { it > 0 }
            val detail = buildString {
                if (amount != null) append("$amount € ")
                append("$fromName reduzieren")
                if (toName != null) append(" und nach $toName umschichten")
                if (reason.isNotBlank()) append(". $reason")
            }
            return AdvisorNotificationText(
                title = "🟠 REDUZIEREN – $fromName",
                body = body("REDUZIEREN", detail),
                level = "REVIEW"
            )
        }

        val action = when (event.newSignal) {
            AdvisorSignal.NACHKAUFEN -> Action("🟢", "NACHKAUFEN", "BUY")
            AdvisorSignal.HALTEN -> Action("🟡", "HALTEN", "WATCH")
            AdvisorSignal.REDUZIEREN -> Action("🟠", "REDUZIEREN", "REVIEW")
            AdvisorSignal.VERKAUFEN -> Action("🔴", "VERKAUFEN", "SELL")
            AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> Action("⚠️", "NICHT HANDELN", "REVIEW")
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
