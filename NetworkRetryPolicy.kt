package de.tobias.investmentradar

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay

class DashboardTimeoutException(message: String, cause: Throwable) : IOException(message, cause)

object NetworkRetryPolicy {
    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 45_000
    const val MAX_ATTEMPTS = 2
    private const val DEFAULT_RETRY_DELAY_MS = 750L

    suspend fun <T> execute(
        retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MS,
        block: suspend () -> T
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (timeout: SocketTimeoutException) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw DashboardTimeoutException(
                        "Der Server braucht zu lange für eine Antwort. Bitte erneut versuchen.",
                        timeout
                    )
                }
                attempt++
                if (retryDelayMillis > 0) delay(retryDelayMillis)
            }
        }
    }
}
