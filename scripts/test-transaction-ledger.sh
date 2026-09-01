#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
cat > "$TEST_DIR/TestTransactionLedger.kt" <<'KOTLIN'
import de.tobias.investmentradar.PortfolioPosition
import de.tobias.investmentradar.PortfolioPurchase
import de.tobias.investmentradar.PortfolioSale

fun assertNear(actual: Double?, expected: Double, label: String) {
    check(actual != null) { "$label was null" }
    check(kotlin.math.abs(actual - expected) < 0.0001) { "$label expected $expected but was $actual" }
}

fun main() {
    val buys = listOf(
        PortfolioPurchase("b1", "01.08.2026", 100.0, 2.0),
        PortfolioPurchase("b2", "02.08.2026", 60.0, 1.0)
    )
    val sale = PortfolioSale("s1", "03.08.2026", proceeds = 70.0, shares = 1.0)
    val position = PortfolioPosition("msft", purchases = buys).upsertSale(sale)
        ?: error("valid sale rejected")

    assertNear(position.shares, 2.0, "remaining shares")
    assertNear(position.investedAmount, 160.0 - (160.0 / 3.0), "remaining cost basis")
    assertNear(position.realizedProfitLoss(), 70.0 - (160.0 / 3.0), "realized profit")
    assertNear(position.currentValue(80.0), 160.0, "current value")
    assertNear(position.unrealizedProfitLoss(80.0), 160.0 - (160.0 - (160.0 / 3.0)), "unrealized profit")
    assertNear(position.totalProfitLoss(80.0), 70.0, "total profit")

    val laterBuy = PortfolioPurchase("b3", "04.08.2026", 90.0, 1.0)
    val withLaterBuy = position.upsertPurchase(laterBuy)
    assertNear(withLaterBuy.shares, 3.0, "shares after later buy")
    assertNear(withLaterBuy.investedAmount, (160.0 - 160.0 / 3.0) + 90.0, "cost basis after later buy")
    assertNear(withLaterBuy.realizedProfitLoss(), 70.0 - 160.0 / 3.0, "realized unchanged by later buy")

    check(withLaterBuy.upsertSale(PortfolioSale("too-much", "05.08.2026", 999.0, 3.1)) == null) {
        "oversell must be rejected"
    }
    check(PortfolioPosition("msft", purchases = buys).upsertSale(PortfolioSale("too-early", "31.07.2026", 50.0, 1.0)) == null) {
        "sale before any holding must be rejected"
    }
}
KOTLIN
kotlinc "$SRC" "$TEST_DIR/TestTransactionLedger.kt" -include-runtime -d "$TEST_DIR/test.jar"
java -jar "$TEST_DIR/test.jar"
