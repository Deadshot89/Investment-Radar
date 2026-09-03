package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Test

class PromotedPortfolioAssetTest {
    @Test
    fun promotedCustomAssetsAreDetectedByBackendId() {
        val custom = listOf(
            CustomInvestment("custom-nel-asa", "Nel ASA", "NEL.OL", "NO0010081235", "Aktie"),
            CustomInvestment("custom-samsung-gdr", "Samsung (GDR)", "SMSN", "US7960508882", "Aktie"),
            CustomInvestment("custom-other", "Other", "OTH", "", "Aktie")
        )
        val builtInIds = setOf("meta", "custom-nel-asa", "custom-samsung-gdr")

        assertEquals(
            setOf("custom-nel-asa", "custom-samsung-gdr"),
            CustomInvestmentStore.promotedIds(custom, builtInIds)
        )
    }
}
