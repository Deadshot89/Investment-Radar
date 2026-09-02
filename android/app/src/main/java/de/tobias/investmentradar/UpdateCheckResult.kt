package de.tobias.investmentradar

sealed interface UpdateCheckResult {
    data class Available(val update: AppUpdateInfo) : UpdateCheckResult
    data class Current(val versionName: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}
