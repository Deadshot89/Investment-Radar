# Investment Radar 1.2.0 Alerts and UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local alert preferences, a real alert center, data-focused Firebase push transport, robust Trade Republic navigation and explicit manual update-check feedback without breaking existing updater behavior.

**Architecture:** Backend sends alert data rather than an unconditional Firebase notification payload; Android decides locally whether to store/show the notification. Alert persistence gains read/tombstone state, alert Compose UI moves into its own file, Trade Republic navigation becomes a focused navigator, and update checking returns a typed current/available/error result.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Firebase Messaging/Admin, SharedPreferences/JSON, Android intents, JUnit 4, Node `node:test`, existing GitHub Releases updater.

**Spec:** `docs/superpowers/specs/2026-09-02-investment-radar-1.2.0-design.md`

## Global Constraints

- Existing Firebase topic names remain compatible.
- Holding-specific REVIEW/SELL events remain scoped to holding topics.
- BUY opportunity events use the general investment topic.
- User alert preferences are local and do not upload portfolio/settings.
- Startup update checks stay silent when current or failed.
- Manual update checks distinguish current from network/API failure.
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
- `AlertPreferencesStore.read(context)` / `save(context, prefs)`.
- `AlertPolicy.shouldNotify(alert, prefs)` and `shouldStore(alert, prefs)`.

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

- [ ] **Step 2: Run RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*AlertPolicyTest*'
```

- [ ] **Step 3: Implement exact defaults and persistence**

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

Persist in `investment_radar_alert_preferences`. BUY/REVIEW/SELL/THRESHOLD map to their explicit toggles. Unknown informational levels may be stored but should not bypass minimum-severity filtering.

- [ ] **Step 4: Run GREEN and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AlertPreferencesStore.kt app/src/main/java/de/tobias/investmentradar/AlertPolicy.kt app/src/test/java/de/tobias/investmentradar/AlertPolicyTest.kt
git commit -m "feat: add local alert preferences"
```

---

### Task 2: Read/unread/delete/tombstone alert-center state

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertStore.kt`
- Create: `android/app/src/main/java/de/tobias/investmentradar/AlertCenterState.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AlertCenterStateTest.kt`

**Interfaces:**
- `StoredAlert(alert: SignalAlert, isRead: Boolean)`.
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
```

- [ ] **Step 2: Run RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*AlertCenterStateTest*'
```

- [ ] **Step 3: Implement backward-compatible storage migration**

Existing `history` JSON entries without `isRead` load as unread. Persist `isRead`. Add a `tombstones` JSON object `id -> deletionEpochMs`; purge entries older than 30 days.

- [ ] **Step 4: Implement deterministic merge**

Deduplicate by ID, preserve local read state, sort newest first, and ignore active tombstones. A new backend fingerprint/new ID appears normally.

- [ ] **Step 5: Run GREEN and commit**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AlertStore.kt app/src/main/java/de/tobias/investmentradar/AlertCenterState.kt app/src/test/java/de/tobias/investmentradar/AlertCenterStateTest.kt
git commit -m "feat: add alert center state"
```

---

### Task 3: Convert backend Firebase transport to data-only alert messages

**Files:**
- Modify: `backend/src/lib/push.mjs`
- Create: `backend/test/pushPayload.test.mjs`

**Interfaces:**
- `buildPushMessage(alert) -> Firebase message object` contains `topic`, `data` and Android priority/channel metadata, but no top-level `notification` payload.
- `sendAlert(alert)` calls `getMessaging().send(buildPushMessage(alert))`.

- [ ] **Step 1: Write failing payload test**

```js
import test from "node:test";
import assert from "node:assert/strict";
import { buildPushMessage } from "../src/lib/push.mjs";

test("push is data-focused so Android can apply local policy", () => {
  const message = buildPushMessage({ id: "1", itemId: "msft", level: "REVIEW", title: "Prüfen", message: "Grund", createdAt: "2026-09-02T08:00:00Z" });
  assert.equal(message.notification, undefined);
  assert.equal(message.data.level, "REVIEW");
  assert.equal(message.data.itemId, "msft");
});
```

- [ ] **Step 2: Run RED**

```bash
cd backend
node --test test/pushPayload.test.mjs
```

- [ ] **Step 3: Extract and use data-only builder**

Build:

```js
export function buildPushMessage(alert) {
  return {
    topic: targetTopic(alert),
    data: {
      alertId: String(alert.id),
      itemId: String(alert.itemId ?? ""),
      level: String(alert.level ?? "INFO"),
      title: String(alert.title ?? "Investment Radar"),
      message: String(alert.message ?? ""),
      createdAt: String(alert.createdAt ?? new Date().toISOString())
    },
    android: { priority: "high" }
  };
}
```

Do not include a Firebase `notification` object, because background notification payloads could bypass Android local preference policy.

- [ ] **Step 4: Run backend tests/checks and commit**

```bash
cd backend
npm test
npm run check
git add src/lib/push.mjs test/pushPayload.test.mjs
git commit -m "feat: send policy-aware data push events"
```

---

### Task 4: Apply local policy in Firebase messaging service

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt`
- Create: `android/tests/test-alert-policy-wiring.sh`

**Interfaces:**
- Incoming data becomes `SignalAlert` first.
- `AlertPolicy.shouldStore` controls history.
- `AlertPolicy.shouldNotify` controls system notification.

- [ ] **Step 1: Write failing wiring regression**

```bash
#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt"
grep -q 'AlertPreferencesStore.read' "$SRC"
grep -q 'AlertPolicy.shouldStore' "$SRC"
grep -q 'AlertPolicy.shouldNotify' "$SRC"
```

- [ ] **Step 2: Run RED**

```bash
bash android/tests/test-alert-policy-wiring.sh
```

- [ ] **Step 3: Wire policy before notification**

```kotlin
val alert = SignalAlert(id, itemId, level, title, body, createdAt)
val prefs = AlertPreferencesStore.read(this)
if (AlertPolicy.shouldStore(alert, prefs)) AlertStore.add(this, alert)
if (AlertPolicy.shouldNotify(alert, prefs)) showNotification(title, body, level, id.hashCode())
```

Keep channel creation and notification tap behavior intact.

- [ ] **Step 4: Run GREEN and commit**

```bash
bash android/tests/test-alert-policy-wiring.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt ../android/tests/test-alert-policy-wiring.sh
git commit -m "feat: apply alert notification preferences"
```

---

### Task 5: Dedicated Alert Center Compose screen and settings

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt`
- Create: `android/tests/test-alert-center-ui.sh`

**Interfaces:**
- `AlertsScreen(alerts, preferences, onPreferenceChange, onOpenAlert, onMarkAllRead, onDelete, onClear)`.
- Filters: ALL, BUY, REVIEW, SELL.

- [ ] **Step 1: Write failing UI regression**

Script asserts `AlertsScreen.kt` contains `Alle`, `Kauf`, `Prüfen`, `Verkauf`, `Alle gelesen`, `Alarmeinstellungen`, delete and clear actions.

- [ ] **Step 2: Run RED**

```bash
bash android/tests/test-alert-center-ui.sh
```

- [ ] **Step 3: Move alert UI out of MainActivity**

Render unread count/marker, filter chips, alert cards and settings controls in the dedicated file. Keep app colors through MaterialTheme/explicit existing palette inputs rather than duplicating unrelated screen logic.

- [ ] **Step 4: Add ViewModel operations**

Reload local state after mark-read/delete/clear. Merge local/backend alerts through `AlertCenterState.merge` so tombstones suppress refresh resurrection.

- [ ] **Step 5: Open related investment in-app**

When `itemId` resolves, mark read, switch to Radar and focus/open that asset's detail context. If unresolved, mark read and remain in Alert Center.

- [ ] **Step 6: Run GREEN and commit**

```bash
bash android/tests/test-alert-center-ui.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt app/src/main/java/de/tobias/investmentradar/MainActivity.kt app/src/main/java/de/tobias/investmentradar/MainViewModel.kt ../android/tests/test-alert-center-ui.sh
git commit -m "feat: add interactive alert center"
```

---

### Task 6: Extract robust Trade Republic navigator

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/TradeRepublicNavigatorTest.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/tests/test-trade-republic-links.sh`

**Interfaces:**
- `TradeRepublicNavigator.stockUrl(isin) -> String?`.
- `TradeRepublicNavigator.open(context, item)` tries package-targeted HTTPS, normal HTTPS, then browse/clipboard fallback.

- [ ] **Step 1: Write failing URL tests**

```kotlin
@Test
fun validIsinBuildsHttpsStockUrl() {
    assertEquals("https://app.traderepublic.com/stocks/US5949181045", TradeRepublicNavigator.stockUrl("us5949181045"))
}

@Test
fun invalidIsinDoesNotBuildStockUrl() {
    assertNull(TradeRepublicNavigator.stockUrl("MSFT"))
}
```

- [ ] **Step 2: Run RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*TradeRepublicNavigatorTest*'
```

- [ ] **Step 3: Implement targeted HTTPS launch**

Build normal `ACTION_VIEW` HTTPS intent, then target a copy to package `de.traderepublic.app` only when resolvable. Catch launch failures. Always copy ISIN/ticker first. Use `https://app.traderepublic.com/browse/stock` for invalid/missing ISIN. Do not use `traderepublic://`.

- [ ] **Step 4: Replace old MainActivity helper and strengthen regression**

All `Trade Republic öffnen` actions call `TradeRepublicNavigator.open`. Regression asserts stock URL, browse URL, package name, `ACTION_VIEW` and MainActivity navigator reference.

- [ ] **Step 5: Run GREEN and commit**

```bash
bash android/tests/test-trade-republic-links.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt app/src/test/java/de/tobias/investmentradar/TradeRepublicNavigatorTest.kt app/src/main/java/de/tobias/investmentradar/MainActivity.kt ../android/tests/test-trade-republic-links.sh
git commit -m "refactor: harden Trade Republic navigation"
```

---

### Task 7: Typed manual update-check result

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt`
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/AppUpdateResultTest.kt`
- Modify: `android/tests/test-update-resume.sh`

**Interfaces:**
- `sealed interface UpdateCheckResult { data class Available(val info: AppUpdateInfo); data class Current(val versionName: String); data class Error(val message: String) }`.
- `AppUpdateManager.checkDetailed(context) -> UpdateCheckResult`.
- Startup wrapper only surfaces `Available`.

- [ ] **Step 1: Write failing pure version/result test**

```kotlin
@Test
fun semanticVersionComparisonTreats120AsNewerThan1129() {
    assertTrue(AppUpdateManager.isNewerVersionForTest("1.2.0", "1.1.29"))
}
```

- [ ] **Step 2: Run RED**

```bash
cd android
gradle --no-daemon :app:testDebugUnitTest --tests '*AppUpdateResultTest*'
```

- [ ] **Step 3: Add typed result without changing permission-resume flow**

Expose an internal `isNewerVersionForTest` wrapper around the existing pure comparator. `checkDetailed` returns Available for a newer valid APK release, Current for non-newer latest release, and Error for HTTP/network/parse/missing-asset failure. Existing `check(context)` may translate only Available to `AppUpdateInfo?` for silent startup use.

- [ ] **Step 4: Add manual feedback**

Manual Update tap shows `Du nutzt bereits die aktuelle Version ${BuildConfig.VERSION_NAME}.` for Current and `Update-Prüfung fehlgeschlagen. Bitte erneut versuchen.` for Error. Never report Current after a network failure.

- [ ] **Step 5: Preserve updater resume regression**

Extend `test-update-resume.sh` only as needed; lifecycle callbacks and automatic post-settings download remain present.

- [ ] **Step 6: Run GREEN and commit**

```bash
bash android/tests/test-update-resume.sh
cd android
gradle --no-daemon :app:testDebugUnitTest
git add app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt app/src/main/java/de/tobias/investmentradar/MainActivity.kt app/src/test/java/de/tobias/investmentradar/AppUpdateResultTest.kt ../android/tests/test-update-resume.sh
git commit -m "feat: add explicit manual update feedback"
```
