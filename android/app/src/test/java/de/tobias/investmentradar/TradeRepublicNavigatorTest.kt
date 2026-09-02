package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TradeRepublicNavigatorTest {
    @Test fun validIsinBuildsHttpsStockUrl() {
        assertEquals("https://app.traderepublic.com/stocks/US5949181045", TradeRepublicNavigator.stockUrl("us5949181045"))
    }

    @Test fun invalidIsinDoesNotBuildStockUrl() {
        assertNull(TradeRepublicNavigator.stockUrl("MSFT"))
    }
}
