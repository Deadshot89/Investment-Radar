package de.tobias.investmentradar

object UpdatePolicy {
    fun isNewer(installedCode: Long, latestCode: Long): Boolean = latestCode > installedCode

    fun displayVersion(raw: String): String = raw.trim().removePrefix("v").ifBlank { "unbekannt" }
}
