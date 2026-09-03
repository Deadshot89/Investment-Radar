package de.tobias.investmentradar

/**
 * Snapshot of the user's Trade Republic portfolio imported on 2026-09-03.
 * Only values visible in the supplied portfolio screenshot are seeded here.
 * No quantities or purchase prices are invented.
 */
data class UserPortfolioSeedPosition(
    val name: String,
    val symbol: String,
    val isin: String? = null,
    val valueEur: Double,
)

object UserPortfolioSeed {
    val positions = listOf(
        UserPortfolioSeedPosition("Meta Platforms (A)", "META", "US30303M1027", 1675.88),
        UserPortfolioSeedPosition("Nel ASA", "NEL.OL", "NO0010081235", 113.80),
        UserPortfolioSeedPosition("MSCI All Country World Investable Market", "ACWI", null, 49.93),
        UserPortfolioSeedPosition("Samsung (GDR)", "SMSN.L", "US7960508882", 42.64),
        UserPortfolioSeedPosition("Microsoft", "MSFT", "US5949181045", 31.38),
        UserPortfolioSeedPosition("Edge World Value USD (Acc)", "EDGE_WORLD_VALUE", null, 20.11),
        UserPortfolioSeedPosition("Alphabet (A)", "GOOGL", "US02079K3059", 15.06),
    )

    val totalValueEur: Double
        get() = positions.sumOf { it.valueEur }
}
