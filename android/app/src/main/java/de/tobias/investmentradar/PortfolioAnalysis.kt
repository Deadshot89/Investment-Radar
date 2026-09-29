package de.tobias.investmentradar

object PortfolioAnalysis {
    fun values(
        items: List<InvestmentItem>,
        positions: Map<String, PortfolioPosition>,
        customItems: List<CustomInvestment>
    ): Map<String, Double> {
        val itemById = items.associateBy { it.id }
        val customById = customItems.associateBy { it.id }
        return positions.mapNotNull { (itemId, position) ->
            val item = itemById[itemId]
            val custom = customById[itemId]
            val fixedIncomePrincipal = custom
                ?.takeIf { it.type.equals("Festzins", ignoreCase = true) }
                ?.fixedPrincipalEur
                ?.takeIf { it.isFinite() && it > 0.0 }
            val comparablePrice = item?.priceEur?.takeIf { it.isFinite() && it >= 0.0 }
                ?: custom?.manualPriceEur?.takeIf { it.isFinite() && it >= 0.0 }
            val value = fixedIncomePrincipal
                ?: position.currentValue(comparablePrice)
                ?: return@mapNotNull null
            itemId to value
        }.toMap()
    }
}
