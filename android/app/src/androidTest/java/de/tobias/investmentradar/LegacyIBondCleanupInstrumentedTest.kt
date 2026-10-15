package de.tobias.investmentradar

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyIBondCleanupInstrumentedTest {
    private lateinit var context: Context
    private lateinit var app: Application

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        app = context.applicationContext as Application
        clearLegacyStores()
    }

    @After
    fun tearDown() {
        clearLegacyStores()
    }

    @Test
    fun viewModelStartupRemovesRetiredIBondFromPersistedUserData() {
        val retiredId = LegacyPortfolioCleanup.RETIRED_IBOND_ID
        val customPrefs = context.getSharedPreferences("investment_radar_custom_assets", Context.MODE_PRIVATE)
        val portfolioPrefs = context.getSharedPreferences("investment_radar_portfolio", Context.MODE_PRIVATE)
        val watchlistPrefs = context.getSharedPreferences("investment_radar_watchlist", Context.MODE_PRIVATE)

        customPrefs.edit()
            .putString(
                "items",
                """[{"id":"$retiredId","name":"iBonds Dec 2026 USD","ticker":"IBONDDEC26","isin":"","type":"ETF"}]"""
            )
            .commit()
        portfolioPrefs.edit()
            .putStringSet("holding_ids", setOf(retiredId))
            .putStringSet("position_ids", setOf(retiredId))
            .putString("position.$retiredId.invested", "100.0")
            .putString("position.$retiredId.shares", "1.0")
            .commit()
        watchlistPrefs.edit()
            .putString("item_ids", "[\"$retiredId\"]")
            .commit()

        MainViewModel(app)

        assertFalse(CustomInvestmentStore.read(context).any { it.id == retiredId })
        assertFalse(PortfolioStore.readPositions(context).containsKey(retiredId))
        assertFalse(WatchlistStore.read(context).contains(retiredId))
        assertFalse(customPrefs.getString("items", "").orEmpty().contains(retiredId))
        assertFalse(portfolioPrefs.getStringSet("holding_ids", emptySet()).orEmpty().contains(retiredId))
        assertFalse(portfolioPrefs.getStringSet("position_ids", emptySet()).orEmpty().contains(retiredId))
        assertFalse(portfolioPrefs.contains("position.$retiredId.invested"))
        assertFalse(portfolioPrefs.contains("position.$retiredId.shares"))
        assertFalse(watchlistPrefs.getString("item_ids", "").orEmpty().contains(retiredId))
    }

    private fun clearLegacyStores() {
        context.getSharedPreferences("investment_radar_custom_assets", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("investment_radar_portfolio", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("investment_radar_watchlist", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
