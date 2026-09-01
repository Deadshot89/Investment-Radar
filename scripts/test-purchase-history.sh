#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
cat > "$TEST_DIR/TestPurchaseHistory.kt" <<'KOTLIN'
import de.tobias.investmentradar.PortfolioPosition
import de.tobias.investmentradar.PortfolioPurchase

fun assertNear(actual: Double?, expected: Double, label: String) {
    check(actual != null) { "$label was null" }
    check(kotlin.math.abs(actual - expected) < 0.0001) { "$label expected $expected but was $actual" }
}

fun main() {
    val first = PortfolioPurchase(id = "a", date = "2026-08-01", investedAmount = 100.0, shares = 2.0)
    val second = PortfolioPurchase(id = "b", date = "2026-09-01", investedAmount = 60.0, shares = 1.0)
    assertNear(first.buyPrice(), 50.0, "first buy price")

    val position = PortfolioPosition(itemId = "msft", purchases = listOf(first, second))
    assertNear(position.investedAmount, 160.0, "total invested")
    assertNear(position.shares, 3.0, "total shares")
    assertNear(position.averageBuyPrice(), 160.0 / 3.0, "weighted average")
    assertNear(position.currentValue(70.0), 210.0, "current value")
    assertNear(position.profitLoss(70.0), 50.0, "profit")

    val updated = position.upsertPurchase(second.copy(investedAmount = 90.0, shares = 1.5))
    check(updated.purchases.size == 2)
    assertNear(updated.investedAmount, 190.0, "updated invested")
    assertNear(updated.shares, 3.5, "updated shares")

    val removed = updated.removePurchase("a")
    check(removed.purchases.map { it.id } == listOf("b"))
    assertNear(removed.investedAmount, 90.0, "removed invested")
}
KOTLIN
kotlinc "$SRC" "$TEST_DIR/TestPurchaseHistory.kt" -include-runtime -d "$TEST_DIR/test.jar"
java -jar "$TEST_DIR/test.jar"
