#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
DETAIL="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"
VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
WORKER="android/app/src/main/java/de/tobias/investmentradar/ExitStrategyWorker.kt"
SCHEDULER="android/app/src/main/java/de/tobias/investmentradar/ExitStrategyScheduler.kt"
DAILY="android/app/src/main/java/de/tobias/investmentradar/DailyAnalysisWorker.kt"
ACTION="android/app/src/main/java/de/tobias/investmentradar/ActionPlanEngine.kt"
NOTIFY="android/app/src/main/java/de/tobias/investmentradar/AdvisorNotificationManager.kt"
COPY="android/app/src/main/java/de/tobias/investmentradar/AdvisorNotificationCopy.kt"
fail(){ echo "FAIL: $1" >&2; exit 1; }
grep -Fq 'DailyAnalysisScheduler.schedule(this)' "$MAIN" || fail daily
grep -Fq 'ExitStrategyScheduler.schedule(this)' "$MAIN" || fail scheduler
grep -Fq 'val exitStrategies by vm.exitStrategies.collectAsState()' "$MAIN" || fail state
grep -Fq 'ExitStrategyActionOverlay.apply(' "$MAIN" || fail overlay
grep -Fq 'Text("Ausstiegsplan"' "$DETAIL" || fail detail
grep -Fq 'Text("Gewinnziel %")' "$DETAIL" || fail profit
grep -Fq 'Text("Verlustgrenze %")' "$DETAIL" || fail stop
grep -Fq 'saveExitStrategy' "$VM" || fail save
grep -Fq 'REPEAT_HOURS=1L' "$SCHEDULER" || fail hourly
grep -Fq 'publishExitTriggers' "$WORKER" || fail publish
grep -Fq 'InvestmentBudgetStore.summary(applicationContext)' "$DAILY" || fail budget
grep -Fq 'PortfolioAdvisorAction.VERKAUFEN -> sourceValue' "$ACTION" || fail fullsell
grep -Fq 'AdvisorSignal.VERKAUFEN -> Action("🔴", "VERKAUFEN", "SELL")' "$COPY" || fail sellalert
grep -Fq 'val title="🔴 VERKAUFEN – $name"' "$NOTIFY" || fail exitsellcopy
echo "PASS exit strategy wiring"
