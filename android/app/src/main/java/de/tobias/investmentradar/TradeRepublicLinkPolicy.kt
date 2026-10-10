package de.tobias.investmentradar

import java.net.URI

object TradeRepublicLinkPolicy {
    private const val ROOT_HOST = "traderepublic.com"

    fun sanitize(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isBlank()) return null

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null) return null
        if (uri.port != -1 && uri.port != 443) return null

        val host = uri.host?.trimEnd('.')?.lowercase() ?: return null
        val trustedHost = host == ROOT_HOST || host.endsWith(".$ROOT_HOST")
        if (!trustedHost) return null

        return normalized
    }

    fun sanitizeOrBlank(raw: String): String = sanitize(raw).orEmpty()
}
