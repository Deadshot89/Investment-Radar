#!/usr/bin/env bash
set -euo pipefail

NAV="android/app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
STORE="android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"
VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
POLICY="android/app/src/main/java/de/tobias/investmentradar/TradeRepublicLinkPolicy.kt"
MANIFEST="android/app/src/main/AndroidManifest.xml"

fail() { echo "FAIL: $1" >&2; exit 1; }

grep -Fq 'fun open(context: Context, item: InvestmentItem)' "$NAV" || fail "Navigator entry point missing"
grep -Fq 'private const val PACKAGE_NAME = "de.traderepublic.app"' "$NAV" || fail "Trade Republic package constant missing"
grep -Fq 'openUrl(context, directUrl, PACKAGE_NAME)' "$NAV" || fail "Installed Trade Republic app is not tried first for the stock link"
grep -Fq 'if (launchTradeRepublic(context))' "$NAV" || fail "Trade Republic launcher fallback missing"
grep -Fq 'getLaunchIntentForPackage(PACKAGE_NAME)' "$NAV" || fail "Installed Trade Republic launcher is not resolved"
grep -Fq 'val browserUrl = directUrl ?: BROWSE_URL' "$NAV" || fail "Browser fallback missing"
grep -Fq 'openUrl(context, browserUrl, null)' "$NAV" || fail "Browser fallback is not used after app attempts"
grep -Fq 'Trade Republic ISIN' "$NAV" || fail "ISIN clipboard handoff missing"
grep -Fq '<package android:name="de.traderepublic.app" />' "$MANIFEST" || fail "Android package visibility for Trade Republic missing"
grep -Fq 'TradeRepublicNavigator.open(context, item)' "$MAIN" || fail "Live UI is not wired to TradeRepublicNavigator"
if grep -Fq 'TRADE_REPUBLIC_STOCK_BASE_URL' "$MAIN"; then
  fail "Legacy Trade Republic navigation still lives in MainActivity"
fi

# Manually stored broker URLs remain backward-compatible data, but must be sanitized
# before they can reach the UI. Only HTTPS on traderepublic.com or its real subdomains is trusted.
grep -Fq 'object TradeRepublicLinkPolicy' "$POLICY" || fail "Trusted Trade Republic URL policy missing"
grep -Fq 'TradeRepublicLinkPolicy.sanitizeOrBlank(o.optString("tradeRepublicUrl"))' "$STORE" || fail "Stored Trade Republic URLs are not sanitized on read"
grep -Fq 'TradeRepublicLinkPolicy.sanitizeOrBlank(item.tradeRepublicUrl)' "$STORE" || fail "Trade Republic URLs are not sanitized before persistence"

# New and edited custom investments must be reloaded from the sanitized store before
# they are exposed to Compose state. This keeps the existing opener behind a trusted-data invariant.
RELOAD_COUNT=$(grep -Fc '_customItems.value = CustomInvestmentStore.read(app)' "$VM" || true)
if [ "$RELOAD_COUNT" -lt 2 ]; then
  fail "Custom investment UI state is not reloaded from the sanitized store after add/update"
fi

APP_LINE=$(grep -n -F 'if (launchTradeRepublic(context))' "$NAV" | head -n1 | cut -d: -f1)
BROWSER_LINE=$(grep -n -F 'val browserUrl = directUrl ?: BROWSE_URL' "$NAV" | head -n1 | cut -d: -f1)
if [ -z "$APP_LINE" ] || [ -z "$BROWSER_LINE" ] || [ "$APP_LINE" -ge "$BROWSER_LINE" ]; then
  fail "Browser fallback occurs before the Trade Republic app launcher"
fi

echo "PASS: Trade Republic app-first navigation and trusted persisted-link state invariant"
