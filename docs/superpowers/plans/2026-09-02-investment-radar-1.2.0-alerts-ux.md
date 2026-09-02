# Investment Radar 1.2.0 Alerts and UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local alert preferences, a real alert center, robust Trade Republic navigation and explicit manual update-check feedback without breaking existing push delivery or updater behavior.

**Architecture:** Keep Firebase transport and backend alert generation separate from local display policy. Introduce pure/testable alert policy and navigation helpers, evolve local alert persistence to read/tombstone state, move alert Compose UI into its own file, and expose a typed update-check result so startup can remain silent while manual checks are explicit.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Firebase Messaging, SharedPreferences/JSON, Android intents, JUnit 4, existing GitHub Releases updater.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`

## Global Constraints

- Existing Firebase topic names remain compatible.
- Holding-specific REVIEW/SELL events remain scoped to holding topics.
- BUY opportunity events use the general investment topic.
- User notification preferences are local and do not upload portfolio/settings.
- Startup update checks remain silent when current or when the check fails.
- Manual update checks show current-version success or a concise error.
- Trade Republic uses HTTPS only; no undocumented custom URI scheme.
- Existing 1.1.28 unknown-source permission resume flow remains unchanged.

---

### Task 1: Alert preference model, storage and display policy

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AlertPreferencesStore.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AlertPolicy.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AlertPolicyTest.kt`

**Interfaces:**
- `AlertPreferences(buyEnabled, reviewEnabled, sellEnabled, thresholdEnabled, minimumSeverity, localDailyDropThresholdPct)`.
- `AlertPreferencesStore.read(context)` and `.save(context, prefs)`.
- `AlertPolicy.shouldNotify(alert, prefs) -> Boolean`.
- `AlertPolicy.shouldStore(alert, prefs) -> Boolean`.

- [ ] **Step 1: Write failing policy tests**

```kotlin
@Test
fun disabledBuyAlertsAreStoredButNotNotified() {
    val prefs = AlertPreferences(buyEnabled = false)
    val alert = SignalAlert("1", "msft", "BUY", "Kaufchance", "Text", "2026-09-02T08:00:00Z")
    assertFalse(AlertPolicy.shouldNotify(alert, prefs))
    assertTrue(AlertPolicy.shouldStore(alert, prefs))
}

@Test
fun reviewAndSellDefaultToEnabled() {
    val prefs = AlertPreferences()
    assertTrue(AlertPolicy.shouldNotify(SignalAlert("1", "x", "REVIEW", "", "", ""), prefs))
    assertTrue(AlertPolicy.shouldNotify(SignalAlert("2", "x", "SELL", "", "", ""), prefs))
}
```

- [ ] **Step 2: Run and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*AlertPolicyTest*'
```

- [ ] **Step 3: Implement exact defaults**

```kotlin
data class AlertPreferences(
    val buyEnabled: Boolean = true,
    val reviewEnabled: Boolean = true,
    val sellEnabled: Boolean = true,
    val thresholdEnabled: Boolean = true,
    val minimumSeverity: String = "NORMAL",
    val localDailyDropThresholdPct: Double? = null
)
```

Persist each field in `investment_radar_alert_preferences`. Unknown alert levels default to storage but notification only when severity policy allows.

- [ ] **Step 4: Run tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AlertPreferencesStore.kt app/src/main/java/de/tobias/investmentradar/AlertPolicy.kt app/src/test/java/de/tobias/investmentradar/AlertPolicyTest.kt
git commit -m "feat: add local alert preferences"
```

---

### Task 2: Evolve AlertStore into read/unread/delete/tombstone center state

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertStore.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AlertCenterState.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AlertCenterStateTest.kt`

**Interfaces:**
- `StoredAlert(alert, isRead)`.
- `AlertCenterState.merge(local, remote, tombstones, nowEpochMs) -> List<StoredAlert>`.
- Store operations: `markRead`, `markAllRead`, `delete`, `clear`, `readTombstones`.
- Tombstones retain deleted IDs for 30 days.

- [ ] **Step 1: Write failing pure-state tests**

```kotlin
@Test
fun deletedRemoteAlertDoesNotImmediatelyReappear() {
    val remote = SignalAlert("same-id", "msft", "BUY", "", "", "2026-09-02T08:00:00Z")
    val merged = AlertCenterState.merge(
        local = emptyList(),
        remote = listOf(remote),
        tombstones = mapOf("same-id" to 1_788_336_000_000L),
        nowEpochMs = 1_788_336_100_000L
    )
    assertTrue(merged.isEmpty())
}

@Test
fun openingAnAlertCanMarkItReadWithoutChangingPayload() {
    val stored = StoredAlert(SignalAlert("1", "x", "REVIEW", "T", "M", "D"), isRead = false)
    assertTrue(stored.copy(isRead = true).isRead)
    assertEquals("1", stored.alert.id)
}
```

- [ ] **Step 2: Run and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*AlertCenterStateTest*'
```

- [ ] **Step 3: Implement storage migration**

Read existing `history` JSON entries as unread `StoredAlert` when `isRead` is absent. Persist `isRead`. Add a `tombstones` JSON object mapping alert IDs to deletion epoch milliseconds. Purge tombstones older than 30 days during reads/writes.

- [ ] **Step 4: Implement merge semantics**

Deduplicate by alert ID, prefer local read state, sort by `createdAt` descending where parseable, and ignore active tombstones. A genuinely new backend fingerprint has a new ID and appears normally.

- [ ] **Step 5: Run tests and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AlertStore.kt app/src/main/java/de/tobias/investmentradar/AlertCenterState.kt app/src/test/java/de/tobias/investmentradar/AlertCenterStateTest.kt
git commit -m "feat: add alert center state"
```

---

### Task 3: Apply local alert policy in Firebase messaging service

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt`
- Create: `android/tests/test-alert-policy-wiring.sh`

**Interfaces:**
- Incoming Firebase data is converted to `SignalAlert` first.
- `AlertPolicy.shouldStore` controls local history storage.
- `AlertPolicy.shouldNotify` controls system notification display.

- [ ] **Step 1: Write failing wiring regression**

```bash
#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt"
grep -q 'AlertPreferencesStore.read' "$SRC"
grep -q 'AlertPolicy.shouldStore' "$SRC"
grep -q 'AlertPolicy.shouldNotify' "$SRC"
```

- [ ] **Step 2: Run and verify RED**

```bash
bash android/tests/test-alert-policy-wiring.sh
```

- [ ] **Step 3: Wire policy before notification**

Use:

```kotlin
val alert = SignalAlert(id, itemId, level, title, body, createdAt)
val prefs = AlertPreferencesStore.read(this)
if (AlertPolicy.shouldStore(alert, prefs)) AlertStore.add(this, alert)
if (AlertPolicy.shouldNotify(alert, prefs)) showNotification(title, body, level, id.hashCode())
```

Keep channel creation and notification tap behavior intact.

- [ ] **Step 4: Run tests and commit**

```bash
bash android/tests/test-alert-policy-wiring.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt ../android/tests/test-alert-policy-wiring.sh
git commit -m "feat: apply alert notification preferences"
```

---

### Task 4: Dedicated Alert Center Compose screen and settings

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Create: `android/tests/test-alert-center-ui.sh`

**Interfaces:**
- `AlertsScreen(alerts, preferences, onPreferenceChange, onOpenAlert, onMarkAllRead, onDelete, onClear)`.
- Filters: ALL, BUY, REVIEW, SELL.

- [ ] **Step 1: Write failing UI source regression**

Assert the new file contains `Alle`, `Kauf`, `Prüfen`, `Verkauf`, `Alle gelesen`, `Alarmeinstellungen` and delete/clear actions.

- [ ] **Step 2: Run and verify RED**

```bash
bash android/tests/test-alert-center-ui.sh
```

- [ ] **Step 3: Move alert UI out of MainActivity**

Create a focused Compose file that renders unread marker/count, filter chips, alert cards and settings controls. Keep app colors passed through MaterialTheme rather than duplicating unrelated screen logic.

- [ ] **Step 4: Add ViewModel operations backed by AlertStore**

Expose functions that reload local alert state after mark-read/delete/clear. Merge local and backend alerts through `AlertCenterState.merge` so deleted remote alerts respect tombstones.

- [ ] **Step 5: Open related investment in-app**

When an alert's `itemId` resolves to a current investment, set an app-level selected asset and navigate to Radar/detail context; if not resolvable, simply mark the alert read and leave the alert visible.

- [ ] **Step 6: Run tests and commit**

```bash
bash android/tests/test-alert-center-ui.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt app/src/main/java/de/tobias/investmentradar/MainActivity.kt app/src/main/java/de/tobias/investmentradar/MainViewModel.kt ../android/tests/test-alert-center-ui.sh
git commit -m "feat: add interactive alert center"
```

---

### Task 5: Extract robust Trade Republic navigator

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicNavigatorTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/tests/test-trade-republic-links.sh`

**Interfaces:**
- `TradeRepublicNavigator.stockUrl(isin) -> String?`.
- `TradeRepublicNavigator.open(context, item)` tries package-targeted HTTPS, then normal HTTPS, then browse URL/clipboard fallback.

- [ ] **Step 1: Write failing pure URL tests**

```kotlin
@Test
fun validIsinBuildsHttpsStockUrl() {
    assertEquals(
        "https://app.traderepublic.com/stocks/US5949181045",
        TradeRepublicNavigator.stockUrl("us5949181045")
    )
}

@Test
fun invalidIsinDoesNotBuildStockUrl() {
    assertNull(TradeRepublicNavigator.stockUrl("MSFT"))
}
```

- [ ] **Step 2: Run and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*TradeRepublicNavigatorTest*'
```

- [ ] **Step 3: Implement targeted HTTPS launch**

Create the normal HTTPS intent first, clone/target it to `de.traderepublic.app` only when package resolution succeeds, and catch `ActivityNotFoundException`. Always copy ISIN/ticker before navigation. Use browse URL for invalid/missing ISIN. Do not use `traderepublic://`.

- [ ] **Step 4: Replace old MainActivity helper**

All existing `Trade Republic öffnen` buttons call `TradeRepublicNavigator.open(context, item)`. Remove duplicated URL constants and old launcher fallback code from MainActivity.

- [ ] **Step 5: Strengthen existing shell regression and commit**

The shell test must assert the navigator contains stock base URL, browse URL, package name and `ACTION_VIEW`, and MainActivity references `TradeRepublicNavigator`.

```bash
bash android/tests/test-trade-republic-links.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt app/src/test/java/de/tobias/investmentradar/TradeRepublicNavigatorTest.kt app/src/main/java/de/tobias/investmentradar/MainActivity.kt ../android/tests/test-trade-republic-links.sh
git commit -m "refactor: harden Trade Republic navigation"
```

---

### Task 6: Typed manual update-check result

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AppUpdateResultTest.kt`
- Modify: `android/tests/test-update-resume.sh`

**Interfaces:**
- `sealed interface UpdateCheckResult { data class Available(...); data class Current(...); data class Error(...) }`.
- `AppUpdateManager.checkDetailed(context) -> UpdateCheckResult`.
- `AppUpdateManager.check(context) -> AppUpdateInfo?` remains as startup-compatible wrapper if useful.

- [ ] **Step 1: Write failing result-model tests**

```kotlin
@Test
fun semanticVersionComparisonTreats120AsNewerThan1129() {
    assertTrue(AppUpdateManager.isNewerVersionForTest("1.2.0", "1.1.29"))
}
```

Expose only an internal pure comparison wrapper for tests; do not expose network internals.

- [ ] **Step 2: Run and verify RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*AppUpdateResultTest*'
```

- [ ] **Step 3: Distinguish current from error**

`checkDetailed` returns:
- `Available(info)` when newer APK release exists,
- `Current(BuildConfig.VERSION_NAME)` when latest release is not newer,
- `Error("Update-Prüfung fehlgeschlagen. Bitte erneut versuchen.")` for HTTP/parse/network/missing-asset failures relevant to manual checks.

Startup can continue to translate only `Available` into a dialog and suppress `Current/Error`.

- [ ] **Step 4: Add manual feedback in MainActivity**

Manual Update tap shows:

```text
Du nutzt bereits die aktuelle Version 1.2.0.
```

when current, or the concise retry message on error. It must not create a false current-version message after a failed network request.

- [ ] **Step 5: Preserve permission-resume regression**

Keep lifecycle callback behavior and extend `test-update-resume.sh` to assert the existing resume strings/callbacks still exist after the refactor.

- [ ] **Step 6: Run tests and commit**

```bash
bash android/tests/test-update-resume.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt app/src/main/java/de/tobias/investmentradar/MainActivity.kt app/src/test/java/de/tobias/investmentradar/AppUpdateResultTest.kt ../android/tests/test-update-resume.sh
git commit -m "feat: add explicit manual update feedback"
```
