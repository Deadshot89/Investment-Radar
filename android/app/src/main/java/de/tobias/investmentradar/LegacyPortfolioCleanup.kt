package de.tobias.investmentradar

import android.content.Context

object LegacyPortfolioCleanup {
    const val RETIRED_IBOND_ID = "custom-ibonds-dec-2026-usd"

    fun apply(context: Context): Boolean {
        var changed = false

        if (CustomInvestmentStore.read(context).any { it.id == RETIRED_IBOND_ID }) {
            CustomInvestmentStore.remove(context, RETIRED_IBOND_ID)
            changed = true
        }

        if (PortfolioStore.readPositions(context).containsKey(RETIRED_IBOND_ID)) {
            PortfolioStore.remove(context, RETIRED_IBOND_ID)
            changed = true
        }

        if (WatchlistStore.read(context).contains(RETIRED_IBOND_ID)) {
            WatchlistStore.remove(context, RETIRED_IBOND_ID)
            changed = true
        }

        return changed
    }
}
