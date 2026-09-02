#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/android/app/src/main/java/de/tobias/investmentradar"
ENGINE="$SRC/ForecastEngine.kt"

if [[ ! -f "$ENGINE" ]]; then
  echo "FAIL: ForecastEngine.kt fehlt"
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/ForecastEngineSpec.kt" <<'KOTLIN'
package de.tobias.investmentradar

fun main() {
    val strong = InvestmentItem(
        id = "msft", type = "Aktie", name = "Microsoft", ticker = "MSFT", isin = "US5949181045",
        tradeRepublicName = "Microsoft", status = "LIVE", allocation = 0, risk = 2,
        price = 100.0, priceEur = 100.0, currency = "EUR", fxRateToEur = 1.0, fxSource = "test",
        fxDelayed = false, fxAsOf = null, percentChange = 1.0, marketOpen = true, dataSource = "test",
        dataDelayed = false, dataError = null, scoreTotal = 84, scoreQuality = 90, scoreValuation = 68,
        scoreGrowth = 88, scoreMomentum = 78, scoreRisk = 82, coverage = 95, recommendation = "BUY",
        recommendationReasons = listOf("Starkes Wachstum"),
        momentum = MomentumSnapshot(m1 = 4.0, m3 = 9.0, m6 = 14.0, m12 = 24.0),
        fundamentals = FundamentalSnapshot(revenueGrowth = 0.15, epsGrowth = 0.18, freeCashFlowYield = 0.04, debtToEquity = 0.5)
    )

    val forecast = ForecastEngine.forecast(strong)
    check(forecast.points.map { it.horizon.months } == listOf(1, 3, 6, 12)) { "Horizonte müssen 1/3/6/12 Monate sein" }
    check(forecast.points.all { it.bearChangePct < it.expectedChangePct && it.expectedChangePct < it.bullChangePct }) { "Bear < Basis < Bull erwartet" }
    check(forecast.points.all { it.targetPriceEur != null && it.bearTargetPriceEur != null && it.bullTargetPriceEur != null }) { "Zielpreise fehlen" }
    check(forecast.points.last().expectedChangePct > 0.0) { "Starke Aktie sollte positive 12M-Basisprognose haben" }
    check(forecast.points.last().reasons.isNotEmpty()) { "Begründung fehlt" }

    val noPrice = strong.copy(price = null, priceEur = null)
    val noPriceForecast = ForecastEngine.forecast(noPrice)
    check(noPriceForecast.points.all { it.targetPriceEur == null }) { "Ohne EUR-Kurs darf kein Zielpreis erfunden werden" }

    println("PASS forecast engine")
}
KOTLIN

kotlinc "$SRC/Models.kt" "$ENGINE" "$TMP/ForecastEngineSpec.kt" -include-runtime -d "$TMP/forecast-test.jar"
java -jar "$TMP/forecast-test.jar"
