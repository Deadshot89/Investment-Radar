package de.tobias.investmentradar

import android.content.Context

/** One-time imports of portfolio snapshots supplied by the user on 03.09.2026. */
object UserPortfolioSeed {
    private const val PREFS = "investment_radar_portfolio_seed"
    private const val KEY_INITIAL = "trade_republic_snapshot_2026_09_03_v1"
    private const val KEY_CURRENT = "trade_republic_snapshot_2026_09_03_v2_current"

    private val initialSnapshotValues = linkedMapOf(
        "meta" to 1675.88,
        "custom-nel-asa" to 113.80,
        "spyi" to 49.93,
        "custom-samsung-gdr" to 42.64,
        "msft" to 31.38,
        "is3s" to 20.11,
        "googl" to 15.06
    )

    private val currentSnapshotValues = linkedMapOf(
        "meta" to 1714.83,
        "custom-nel-asa" to 110.89,
        "spyi" to 50.15,
        "custom-samsung-gdr" to 42.96,
        "msft" to 32.08,
        "is3s" to 20.19,
        "googl" to 15.14
    )

    fun latestSnapshotValues(): Map<String, Double> = currentSnapshotValues.toMap()

    fun canRefreshSnapshot(position: PortfolioPosition): Boolean =
        position.purchases.isEmpty() && position.sales.isEmpty() && position.trackedShares == null

    fun ensureSeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_INITIAL, false)) {
            val existing = PortfolioStore.readPositions(context)
            initialSnapshotValues.forEach { (id, value) ->
                if (id !in existing) {
                    PortfolioStore.save(context, PortfolioPosition(itemId = id, snapshotValueEur = value))
                }
            }
            prefs.edit().putBoolean(KEY_INITIAL, true).apply()
        }

        if (!prefs.getBoolean(KEY_CURRENT, false)) {
            val existing = PortfolioStore.readPositions(context)
            currentSnapshotValues.forEach { (id, value) ->
                val current = existing[id]
                when {
                    current == null -> PortfolioStore.save(context, PortfolioPosition(itemId = id, snapshotValueEur = value))
                    canRefreshSnapshot(current) -> PortfolioStore.save(context, current.copy(snapshotValueEur = value))
                }
            }
            prefs.edit().putBoolean(KEY_CURRENT, true).apply()
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
    }

    private fun ensureCustomAsset(context: Context, asset: CustomInvestment) {
        if (CustomInvestmentStore.read(context).none { it.id == asset.id }) {
            CustomInvestmentStore.save(context, asset)
        }
    }
}
