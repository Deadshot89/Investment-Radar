package de.tobias.investmentradar

import android.content.Context

/**
 * Legacy compatibility shim.
 *
 * Older releases imported user-provided Trade Republic snapshots from source code.
 * That behavior is intentionally retired: production code must never create portfolio
 * holdings, market values, cost bases, identifiers or prices from compiled-in fixtures.
 *
 * Existing values already stored on the device are preserved by PortfolioStore.
 */
object UserPortfolioSeed {
    @Deprecated("Automatic portfolio seeding is retired. Use explicit user input/import instead.")
    fun ensureSeeded(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

    fun canRefreshSnapshot(position: PortfolioPosition): Boolean =
        position.purchases.isEmpty() && position.sales.isEmpty() && position.trackedShares == null

    fun canApplyImportedCostBasis(position: PortfolioPosition): Boolean =
        position.purchases.isEmpty() && position.sales.isEmpty()
}
