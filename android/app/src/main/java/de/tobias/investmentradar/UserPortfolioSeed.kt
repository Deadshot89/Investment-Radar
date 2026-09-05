package de.tobias.investmentradar

import android.content.Context

/** One-time imports of portfolio snapshots supplied by the user. */
object UserPortfolioSeed {
    private const val PREFS = "investment_radar_portfolio_seed"
    private const val KEY_INITIAL = "trade_republic_snapshot_2026_09_03_v1"
    private const val KEY_CURRENT = "trade_republic_snapshot_2026_09_03_v2_current"
    private const val KEY_COST_BASIS = "trade_republic_snapshot_2026_09_03_v3_cost_basis"
    private const val KEY_FULL_CURRENT = "trade_republic_snapshot_2026_09_05_v4_full"

    data class ImportedPosition(val marketValue: Double, val shares: Double, val buyIn: Double)

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

    private val currentSnapshotCostBasis = linkedMapOf(
        "meta" to 1716.25,
        "custom-nel-asa" to 131.80,
        "spyi" to 51.00,
        "custom-samsung-gdr" to 43.61,
        "msft" to 28.83,
        "is3s" to 21.00,
        "googl" to 16.00
    )

    /** Authoritative Trade Republic depot supplied on 05.09.2026. */
    private val fullCurrentSnapshot = linkedMapOf(
        "meta" to ImportedPosition(1213.11, 2.291921, 519.04),
        "custom-nel-asa" to ImportedPosition(113.45, 582.392344, 0.226),
        "spyi" to ImportedPosition(50.25, 4.339524, 11.75),
        "custom-samsung-gdr" to ImportedPosition(45.45, 0.010822, 4029.32),
        "msft" to ImportedPosition(31.31, 0.072857, 395.68),
        "is3s" to ImportedPosition(20.30, 0.284292, 73.87),
        "googl" to ImportedPosition(15.00, 0.051449, 310.99),
        "custom-ibonds-dec-2026-usd" to ImportedPosition(3.02, 0.688382, 5.81)
    )

    fun latestSnapshotValues(): Map<String, Double> = fullCurrentSnapshot.mapValues { it.value.marketValue }
    fun latestSnapshotCostBasis(): Map<String, Double> = fullCurrentSnapshot.mapValues { (_, imported) -> imported.shares * imported.buyIn }

    fun canRefreshSnapshot(position: PortfolioPosition): Boolean =
        position.purchases.isEmpty() && position.sales.isEmpty() && position.trackedShares == null

    fun canApplyImportedCostBasis(position: PortfolioPosition): Boolean =
        position.purchases.isEmpty() && position.sales.isEmpty()

    fun ensureSeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_INITIAL, false)) {
            val existing = PortfolioStore.readPositions(context)
            initialSnapshotValues.forEach { (id, value) ->
                if (id !in existing) PortfolioStore.save(context, PortfolioPosition(itemId = id, snapshotValueEur = value))
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

        if (!prefs.getBoolean(KEY_COST_BASIS, false)) {
            val existing = PortfolioStore.readPositions(context)
            currentSnapshotCostBasis.forEach { (id, costBasis) ->
                val current = existing[id] ?: return@forEach
                if (canApplyImportedCostBasis(current)) PortfolioStore.save(context, current.copy(snapshotCostBasisEur = costBasis))
            }
            prefs.edit().putBoolean(KEY_COST_BASIS, true).apply()
        }

        if (!prefs.getBoolean(KEY_FULL_CURRENT, false)) {
            fullCurrentSnapshot.forEach { (id, imported) ->
                PortfolioStore.save(
                    context,
                    PortfolioPosition(
                        itemId = id,
                        snapshotValueEur = imported.marketValue,
                        snapshotCostBasisEur = imported.shares * imported.buyIn,
                        trackedShares = imported.shares
                    )
                )
            }
            prefs.edit().putBoolean(KEY_FULL_CURRENT, true).apply()
        }

        ensureCustomAsset(context, CustomInvestment("custom-nel-asa", "Nel ASA", "NEL.OL", "NO0010081235", "Aktie", 5))
        ensureCustomAsset(context, CustomInvestment("custom-samsung-gdr", "Samsung (GDR)", "SMSN", "US7960508882", "Aktie", 3))
        ensureCustomAsset(
            context,
            CustomInvestment(
                id = "custom-ibonds-dec-2026-usd",
                name = "iBonds Dec 2026 USD (Dist)",
                ticker = "IB26",
                isin = "",
                type = "Anleihen-ETF",
                risk = 2
            )
        )
    }

    private fun ensureCustomAsset(context: Context, asset: CustomInvestment) {
        if (CustomInvestmentStore.read(context).none { it.id == asset.id }) CustomInvestmentStore.save(context, asset)
    }
}
