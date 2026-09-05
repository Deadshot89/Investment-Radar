package de.tobias.investmentradar

enum class DataUiState {
    NO_CURRENT_DATA,
    NO_ANALYSIS,
    NOT_IN_RADAR,
    NO_VERIFIED_TR_MAPPING,
    CONNECTION_FAILED
}

fun DataUiState.userMessage(): String = when (this) {
    DataUiState.NO_CURRENT_DATA -> "Keine aktuellen Daten"
    DataUiState.NO_ANALYSIS -> "Noch keine Analyse"
    DataUiState.NOT_IN_RADAR -> "Nicht im aktuellen Radar enthalten"
    DataUiState.NO_VERIFIED_TR_MAPPING -> "Keine verifizierte Trade-Republic-Zuordnung"
    DataUiState.CONNECTION_FAILED -> "Verbindung konnte nicht hergestellt werden"
}
