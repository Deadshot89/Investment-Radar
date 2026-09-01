#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT
cat > "$TEST_DIR/TestPortfolio.kt" <<'KOTLIN'
import de.tobias.investmentradar.PortfolioPosition

fun assertNear(actual: Double?, expected: Double, label: String) {
    check(actual != null) { "$label was null" }
    check(kotlin.math.abs(actual - expected) < 0.0001) { "$label expected $expected but was $actual" }
}

fun main() {
    val p = PortfolioPosition(itemId = "msft", investedAmount = 250.0, shares = 2.5)
    assertNear(p.averageBuyPrice(), 100.0, "averageBuyPrice")
    assertNear(p.currentValue(120.0), 300.0, "currentValue")
    assertNear(p.profitLoss(120.0), 50.0, "profitLoss")
    assertNear(p.profitLossPercent(120.0), 20.0, "profitLossPercent")

    val zeroShares = PortfolioPosition(itemId = "spyi", investedAmount = 100.0, shares = 0.0)
    check(zeroShares.averageBuyPrice() == null)
    check(zeroShares.currentValue(50.0) == null)

    val missingPrice = PortfolioPosition(itemId = "googl", investedAmount = 100.0, shares = 1.0)
    check(missingPrice.currentValue(null) == null)
    check(missingPrice.profitLoss(null) == null)
}
KOTLIN
kotlinc "$SRC" "$TEST_DIR/TestPortfolio.kt" -include-runtime -d "$TEST_DIR/test.jar"
java -jar "$TEST_DIR/test.jar"
