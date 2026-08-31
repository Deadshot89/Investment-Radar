package de.tobias.investmentradar

fun main() {
    val buy = PlannerItem("msft", "Aktie", "KAUFEN", 20, 2, 1.5)
    val watch = PlannerItem("abc", "Aktie", "BEOBACHTEN", 20, 3, -0.5)
    val sell = PlannerItem("xyz", "Aktie", "SELL", 20, 4, -6.0)

    check(InvestmentPlanner.actionHeadline(buy, 25) == "KAUFEN · 25 €")
    check(InvestmentPlanner.actionHeadline(watch, 0) == "BEOBACHTEN · 0 €")
    check(InvestmentPlanner.actionHeadline(sell, 0) == "VERKAUF PRÜFEN")

    check(InvestmentPlanner.confidenceLabel(90) == "SEHR STARK")
    check(InvestmentPlanner.confidenceLabel(75) == "STARK")
    check(InvestmentPlanner.confidenceLabel(60) == "MITTEL")
    check(InvestmentPlanner.confidenceLabel(40) == "SCHWACH")

    val allocations = InvestmentPlanner.plan(listOf(buy, buy.copy(id = "two", baseAllocation = 30)), 137)
    check(allocations.sumOf { it.amount } == 137)

    println("InvestmentPlanner tests passed")
}
