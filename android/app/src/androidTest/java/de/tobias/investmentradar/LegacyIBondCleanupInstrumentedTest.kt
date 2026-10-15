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
    fun viewModelStartupRemovesRetiredIBondFromPersistedPortfolio() {
        val retiredId = "custom-ibonds-dec-2026-usd"
        CustomInvestmentStore.save(
            context,
            CustomInvestment(
                id = retiredId,
                name = "iBonds Dec 2026 USD",
                ticker = "IBONDDEC26",
                isin = "",
                type = "ETF"
            )
        )
        PortfolioStore.save(
            context,
            PortfolioPosition(
                itemId = retiredId,
                purchases = listOf(
                    PortfolioPurchase(
                        id = "legacy-ibond-purchase",
                        date = "2026-09-01",
                        investedAmount = 100.0,
                        shares = 1.0
                    )
                )
            )
        )

        MainViewModel(app)

        assertFalse(CustomInvestmentStore.read(context).any { it.id == retiredId })
        assertFalse(PortfolioStore.readPositions(context).containsKey(retiredId))
    }

    private fun clearLegacyStores() {
        context.getSharedPreferences("investment_radar_custom_assets", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("investment_radar_portfolio", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
