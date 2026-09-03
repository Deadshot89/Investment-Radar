package de.tobias.investmentradar

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object TradeRepublicNavigator {
    private const val STOCK_BASE_URL = "https://app.traderepublic.com/stocks/"
    const val BROWSE_URL = "https://app.traderepublic.com/browse/stock"
    private const val PACKAGE_NAME = "de.traderepublic.app"
    private val ISIN_PATTERN = Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]")

    fun stockUrl(isin: String): String? {
        val normalized = isin.trim().uppercase()
        if (!ISIN_PATTERN.matches(normalized)) return null
        return STOCK_BASE_URL + normalized
    }

    fun open(context: Context, item: InvestmentItem) {
        val normalizedIsin = item.isin.trim().uppercase()
        val searchValue = normalizedIsin.ifBlank { item.ticker.trim().uppercase() }
        if (searchValue.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Trade Republic ISIN", searchValue))
        }

        val directUrl = stockUrl(normalizedIsin)

        // Zuerst gezielt die installierte Trade-Republic-App ansprechen.
        // Falls die App den Web-Deep-Link nicht direkt annimmt, öffnen wir ihren Launcher,
        // statt sofort in den Browser zurückzufallen. Die ISIN liegt dann bereits in der Zwischenablage.
        if (directUrl != null && openUrl(context, directUrl, PACKAGE_NAME)) return
        if (launchTradeRepublic(context)) {
            if (searchValue.isNotBlank()) {
                Toast.makeText(context, "$searchValue für Trade Republic kopiert.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Nur wenn Trade Republic nicht installiert/erreichbar ist, Browser-Fallback verwenden.
        val browserUrl = directUrl ?: BROWSE_URL
        if (openUrl(context, browserUrl, null)) return
        if (browserUrl != BROWSE_URL && openUrl(context, BROWSE_URL, null)) return

        Toast.makeText(
            context,
            if (searchValue.isBlank()) "Trade Republic konnte nicht geöffnet werden."
            else "Trade Republic konnte nicht geöffnet werden. $searchValue wurde für die Suche kopiert.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun launchTradeRepublic(context: Context): Boolean {
        val launcher = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) ?: return false
        return runCatching {
            context.startActivity(launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    private fun openUrl(context: Context, url: String, packageName: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != null) setPackage(packageName)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
