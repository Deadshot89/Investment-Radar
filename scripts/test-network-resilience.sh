#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POLICY="$ROOT/android/app/src/main/java/de/tobias/investmentradar/NetworkRetryPolicy.kt"
API="$ROOT/android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
VM="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
BUILD="$ROOT/android/app/build.gradle.kts"

if [ ! -f "$POLICY" ]; then
  echo "FAIL: NetworkRetryPolicy.kt fehlt – Timeout-Retry ist noch nicht implementiert."
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
COROUTINES_JAR="/root/.sdkman/candidates/kotlin/current/lib/kotlinx-coroutines-core-jvm.jar"
if [ ! -f "$COROUTINES_JAR" ]; then
  echo "SKIP: lokale Kotlin-Coroutines-Testbibliothek fehlt"
  exit 0
fi

cat > "$TMP/NetworkRetryPolicyTest.kt" <<'KOTLIN'
package de.tobias.investmentradar

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking

private fun checkThat(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun main() = runBlocking {
    checkThat(NetworkRetryPolicy.CONNECT_TIMEOUT_MS >= 30_000, "Connect-Timeout muss mindestens 30 Sekunden sein")
    checkThat(NetworkRetryPolicy.READ_TIMEOUT_MS >= 45_000, "Read-Timeout muss mindestens 45 Sekunden sein")
    checkThat(NetworkRetryPolicy.MAX_ATTEMPTS == 2, "Es muss genau einen automatischen Retry geben")

    var attempts = 0
    val result = NetworkRetryPolicy.execute(retryDelayMillis = 0) {
        attempts++
        if (attempts == 1) throw SocketTimeoutException("cold start")
        "ok"
    }
    checkThat(result == "ok", "Zweiter Versuch muss Ergebnis liefern")
    checkThat(attempts == 2, "Nach einem Timeout muss genau einmal wiederholt werden")

    var nonTimeoutAttempts = 0
    try {
        NetworkRetryPolicy.execute<String>(retryDelayMillis = 0) {
            nonTimeoutAttempts++
            throw IllegalStateException("server error")
        }
        throw AssertionError("Nicht-Timeout-Fehler muss weitergereicht werden")
    } catch (e: IllegalStateException) {
        checkThat(e.message == "server error", "Originalfehler muss erhalten bleiben")
    }
    checkThat(nonTimeoutAttempts == 1, "Nicht-Timeout-Fehler darf nicht wiederholt werden")

    var timeoutAttempts = 0
    try {
        NetworkRetryPolicy.execute<String>(retryDelayMillis = 0) {
            timeoutAttempts++
            throw SocketTimeoutException("still sleeping")
        }
        throw AssertionError("Zwei Timeouts müssen verständlichen Fehler liefern")
    } catch (e: DashboardTimeoutException) {
        checkThat(e.message?.contains("zu lange", ignoreCase = true) == true, "Timeout-Meldung muss verständlich sein")
    }
    checkThat(timeoutAttempts == 2, "Nach zweitem Timeout muss abgebrochen werden")

    println("PASS: NetworkRetryPolicy")
}
KOTLIN

kotlinc "$POLICY" "$TMP/NetworkRetryPolicyTest.kt" \
  -cp "$COROUTINES_JAR" -include-runtime -d "$TMP/network-policy-test.jar"
java -cp "$TMP/network-policy-test.jar:$COROUTINES_JAR" de.tobias.investmentradar.NetworkRetryPolicyTestKt

grep -q 'NetworkRetryPolicy.execute' "$API" || { echo "FAIL: ApiClient verwendet Retry-Policy nicht"; exit 1; }
grep -q 'NetworkRetryPolicy.CONNECT_TIMEOUT_MS' "$API" || { echo "FAIL: ApiClient verwendet neuen Connect-Timeout nicht"; exit 1; }
grep -q 'NetworkRetryPolicy.READ_TIMEOUT_MS' "$API" || { echo "FAIL: ApiClient verwendet neuen Read-Timeout nicht"; exit 1; }
grep -q 'previousState is UiState.Ready' "$VM" || { echo "FAIL: MainViewModel bewahrt vorhandene Daten beim Refresh nicht"; exit 1; }
grep -q 'versionCode = 16' "$BUILD" || { echo "FAIL: versionCode 16 fehlt"; exit 1; }
grep -q 'versionName = "1.1.15"' "$BUILD" || { echo "FAIL: versionName 1.1.15 fehlt"; exit 1; }

echo "PASS: Network resilience wiring"
