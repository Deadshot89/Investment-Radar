package de.tobias.investmentradar

import android.content.Context

/** One-time import of the portfolio snapshot supplied by the user on 03.09.2026. */
object UserPortfolioSeed {
    private const val PREFS = "investment_radar_portfolio_seed"
    private const val KEY = "trade_republic_snapshot_2026_09_03_v1"

    private val snapshotValues = linkedMapOf(
        "meta" to 1675.88,
        "custom-nel-asa" to 113.80,
        "spyi" to 49.93,
        "custom-samsung-gdr" to 42.64,
        "msft" to 31.38,
        "is3s" to 20.11,
        "googl" to 15.06
    )

    fun ensureSeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return

        val existing = PortfolioStore.readPositions(context)
        snapshotValues.forEach { (id, value) ->
            if (id !in existing) {
                PortfolioStore.save(context, PortfolioPosition(itemId = id, snapshotValueEur = value))
            }
        }

        ensureCustomAsset(
            context,
            CustomInvestment(
                id = "custom-nel-asa",
                name = "Nel ASA",
                ticker = "NEL.OL",
                isin = "NO0010081235",
                type = "Aktie",
                risk = 5
            )
        )
        ensureCustomAsset(
            context,
            CustomInvestment(
                id = "custom-samsung-gdr",
                name = "Samsung (GDR)",
                ticker = "SMSN",
                isin = "US7960508882",
                type = "Aktie",
                risk = 3
            )
        )

        prefs.edit().putBoolean(KEY, true).apply()
    }

    private fun ensureCustomAsset(context: Context, asset: CustomInvestment) {
        if (CustomInvestmentStore.read(context).none { it.id == asset.id }) {
            CustomInvestmentStore.save(context, asset)
        }
    }
}
