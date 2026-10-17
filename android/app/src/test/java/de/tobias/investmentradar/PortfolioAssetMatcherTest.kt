package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioAssetMatcherTest {
    private fun item(
        id: String,
        name: String,
        ticker: String,
        tradeRepublicName: String = name,
        type: String = "Aktie"
    ): InvestmentItem = testInvestmentItem(id = id, type = type).copy(
        name = name,
        ticker = ticker,
        tradeRepublicName = tradeRepublicName
    )

    private val candidates = listOf(
        item("meta", "Meta Platforms", "META", "Meta Platforms"),
        item("msft", "Microsoft", "MSFT", "Microsoft"),
        item("apple", "Apple", "AAPL", "Apple")
    )

    @Test
    fun exactNormalizedTradeRepublicNameIsMatchedExactly() {
        val match = PortfolioAssetMatcher.match(
            observedName = "  META   Platforms ",
            builtInItems = candidates,
            customItems = emptyList()
        )

        assertEquals(AssetMatch.Exact("meta"), match)
    }

    @Test
    fun exactTickerOrAppNameAliasIsMatchedExactly() {
        val tickerMatch = PortfolioAssetMatcher.match(
            observedName = "MSFT",
            builtInItems = candidates,
            customItems = emptyList()
        )
        val appNameMatch = PortfolioAssetMatcher.match(
            observedName = "Microsoft",
            builtInItems = candidates.map {
                if (it.id == "msft") it.copy(tradeRepublicName = "Microsoft Corp.") else it
            },
            customItems = emptyList()
        )

        assertEquals(AssetMatch.Exact("msft"), tickerMatch)
        assertEquals(AssetMatch.Exact("msft"), appNameMatch)
    }

    @Test
    fun oneConservativeFuzzyCandidateRequiresReviewInsteadOfAutomaticMatch() {
        val match = PortfolioAssetMatcher.match(
            observedName = "Meta Platform",
            builtInItems = candidates,
            customItems = emptyList()
        )

        assertEquals(AssetMatch.Review(listOf("meta")), match)
    }

    @Test
    fun ambiguousSimilarCandidatesRequireReviewWithBothCandidates() {
        val similar = listOf(
            item("alpha-a", "Alpha Technologies", "ALPA", "Alpha Technologies"),
            item("alpha-b", "Alpha Technology", "ALPB", "Alpha Technology")
        )

        val match = PortfolioAssetMatcher.match(
            observedName = "Alpha Technolog",
            builtInItems = similar,
            customItems = emptyList()
        )

        assertTrue(match is AssetMatch.Review)
        assertEquals(setOf("alpha-a", "alpha-b"), (match as AssetMatch.Review).candidateItemIds.toSet())
    }

    @Test
    fun unknownNameIsNeverAutomaticallyMapped() {
        val match = PortfolioAssetMatcher.match(
            observedName = "Completely Unknown Mining",
            builtInItems = candidates,
            customItems = emptyList()
        )

        assertEquals(AssetMatch.Unknown, match)
    }

    @Test
    fun exactCustomStockNameCanBeMatchedButFestzinsIsNeverACandidate() {
        val custom = listOf(
            CustomInvestment("custom-nvda", "NVIDIA", "NVDA", "US67066G1040", "Aktie"),
            CustomInvestment("fixed", "Festzins 2027", "FIXED27", "", "Festzins")
        )

        assertEquals(
            AssetMatch.Exact("custom-nvda"),
            PortfolioAssetMatcher.match("NVIDIA", emptyList(), custom)
        )
        assertEquals(
            AssetMatch.Unknown,
            PortfolioAssetMatcher.match("Festzins 2027", emptyList(), custom)
        )
    }
}
