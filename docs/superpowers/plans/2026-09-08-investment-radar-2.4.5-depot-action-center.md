# Investment Radar 2.4.5 – Depot-Aktionscenter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine zentrale depotweite Arbeitsliste im Alarmcenter bauen, die den vorhandenen `ActionPlanEngine` als einzige Entscheidungsquelle nutzt, Maßnahmen priorisiert, Datenqualität berücksichtigt und den Nutzer direkt in bestehende Kauf-/Verkaufs-/Sparplan-Flows führt.

**Architecture:** Die bestehende `ActionPlanEngine`-Berechnung bleibt unverändert die Quelle aller Euro-Beträge. Eine neue reine Kotlin-Schicht `DepotActionCenterMapper` übersetzt `ActionPlan`, `PortfolioAdvisorCandidate`, `InvestmentItem` und `PortfolioPosition` in stabile UI-View-States, sortiert sie gemäß 2.4.5 und blockiert Kaufdarstellung bei unzuverlässiger Datenbasis. `AlertsScreen` rendert diese View-States oberhalb der Einzelalarme; `MainActivity` verbindet die Karten ausschließlich mit bereits vorhandenen Depot-, Transaktions- und Sparplan-Flows.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, JUnit 4 JVM Tests, Bash Source-Contract Tests, GitHub Actions Android Build/Signing/Release.

**Spec:** `docs/superpowers/specs/2026-09-08-investment-radar-2.4.5-depot-action-center-design.md`

## Global Constraints

- Android-Release: `versionName = "2.4.5"`, `versionCode = 65`.
- Backend-Vertrag bleibt `2.1.0`; kein neuer Backend-Endpunkt für 2.4.5.
- Umsetzung direkt auf `main`.
- `ActionPlanEngine` bleibt einzige Quelle für Kauf-, Verkauf-, Reduzierungs-, Sparplan- und Cash-Beträge.
- Keine automatische Trade-Republic-Order und keine automatische Ausführungsbestätigung.
- BUY/WATCH darf bei unvollständiger, veralteter oder unzuverlässiger Datenbasis nicht als ausführbare Kaufempfehlung erscheinen.
- Priorität: `SELL > REDUCE > REVIEW_SAVINGS_PLAN > BUY_MORE > OPEN_POSITION > KEEP_SAVINGS_PLAN > HOLD_CASH`.
- Innerhalb derselben Klasse: bestehende Depotposition vor externer Position, dann `priority` absteigend, danach `instrumentId`, danach `actionId`.
- Bestehende Release-Tags bleiben unveränderlich; Veröffentlichung als neuer Tag `v2.4.5`.
- Vor Veröffentlichung müssen Contract Tests, JVM Tests, signed APK, Signaturprüfung, Live-Backend-Gate und In-App-Publish auf demselben finalen `main`-SHA grün sein.

---

## File Structure

- **Create:** `android/app/src/main/java/de/tobias/investmentradar/DepotActionCenterMapper.kt` — reine Kotlin-Mapping-, Sperr-, Sortier- und Zusammenfassungslogik; keine Compose-Abhängigkeit.
- **Create:** `android/app/src/test/java/de/tobias/investmentradar/DepotActionCenterMapperTest.kt` — direkte JVM-Tests für Reihenfolge, Depotvorrang, Datenqualität, Betragsübernahme, Leerzustand und Cash.
- **Modify:** `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt` — depotweite Aktionssektion oberhalb der Einzelalarme; keine neue Budgetberechnung.
- **Modify:** `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt` — bestehende Kauf-/Verkaufs-/Sparplannavigation an die Aktionskarten anbinden.
- **Modify:** `android/app/src/test/java/de/tobias/investmentradar/ActionPlanEngineTest.kt` — Budgetinvarianten für 2.4.5 explizit absichern.
- **Modify:** `android/tests/test-alert-center-ui.sh` — Source-Vertrag für Überschrift, Zusammenfassung, Karten und Callbacks.
- **Modify:** `android/tests/test-version-contract.sh` — 2.4.5 / Code 65.
- **Modify:** `android/tests/test-history-card-glare.sh` — Release-Version auf 2.4.5 / 65 anheben, bestehende Glow-Regel unverändert lassen.
- **Modify:** `android/tests/test-release-backend-gate.sh` — Android 2.4.5 / Code 65 bei Backend 2.1.0.
- **Modify:** `android/app/build.gradle.kts` — Release 2.4.5 / Code 65.

---

### Task 1: RED-Vertrag für das depotweite Aktionscenter

**Files:**
- Modify: `android/tests/test-alert-center-ui.sh`

**Interfaces:**
- Consumes: bestehendes `AlertsScreen.kt` aus 2.4.4.
- Produces: ein absichtlich roter Source-Vertrag, der erst nach Tasks 2–4 grün werden darf.

- [ ] **Step 1: Ergänze den Source-Vertrag um die 2.4.5-Anforderungen**

Am Ende vor dem finalen `echo` folgende Prüfungen ergänzen:

```bash
# 2.4.5: depotweite Arbeitsliste oberhalb der Einzelalarme.
require_literal 'DepotActionCenterMapper.build(' "$UI" 'Depot-Aktionscenter-Mapping'
require_literal 'Text("Das solltest du jetzt mit deinem Depot machen"' "$UI" 'Depot-Aktionscenter-Überschrift'
require_literal 'Text("Dringende Aktionen"' "$UI" 'Aktionscenter-Zusammenfassung'
require_literal 'Text("Geplantes Kaufbudget"' "$UI" 'Kaufbudget-Zusammenfassung'
require_literal 'Text("Cash halten"' "$UI" 'Cash-Zusammenfassung'
require_literal 'private fun DepotActionCenterSection' "$UI" 'Aktionscenter-Komponente'
require_literal 'onExecuteAction: (DepotActionCenterItem) -> Unit' "$UI" 'Aktionscenter-Navigation'
require_literal '"Noch kein belastbarer Depot-Aktionsplan verfügbar."' "$UI" 'Aktionscenter-Leerzustand'
require_literal '"Nicht kaufen – Datenbasis unvollständig"' "$UI" 'Aktionscenter-Kaufsperre'
require_literal '"WATCH · NICHT BESTÄTIGT"' "$UI" 'Aktionscenter-WATCH-Sperre'
```

Den finalen Erfolgsstring ersetzen durch:

```bash
echo 'PASS Alarmcenter mit depotweitem 2.4.5-Aktionscenter, Depotfilter, Prognose und Datenqualität'
```

- [ ] **Step 2: Führe den Source-Vertrag aus und bestätige RED**

Run:

```bash
bash android/tests/test-alert-center-ui.sh
```

Expected: FAIL bei `DepotActionCenterMapper.build(` oder der neuen Überschrift, weil 2.4.5 noch nicht implementiert ist.

- [ ] **Step 3: Committe ausschließlich den roten Test**

```bash
git add android/tests/test-alert-center-ui.sh
git commit -m "test: Depot-Aktionscenter 2.4.5 verlangen"
```

---

### Task 2: Reine Mapping- und Priorisierungslogik

**Files:**
- Create: `android/app/src/main/java/de/tobias/investmentradar/DepotActionCenterMapper.kt`
- Create: `android/app/src/test/java/de/tobias/investmentradar/DepotActionCenterMapperTest.kt`

**Interfaces:**
- Consumes: `ActionPlan`, `ActionPlanAction`, `ActionType`, `PortfolioAdvisorCandidate`, `InvestmentItem`, `PortfolioPosition`.
- Produces:
  - `data class DepotActionCenterItem(...)`
  - `data class DepotActionCenterSummary(...)`
  - `data class DepotActionCenterState(...)`
  - `object DepotActionCenterMapper { fun build(...): DepotActionCenterState }`

- [ ] **Step 1: Schreibe JVM-Tests für feste Reihenfolge und Depotvorrang**

`DepotActionCenterMapperTest.kt` anlegen mit mindestens diesen Tests:

```kotlin
package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepotActionCenterMapperTest {
    @Test
    fun actionsFollowDepotWidePriorityOrder() {
        val plan = ActionPlan(
            planId = "plan",
            analysisDay = "2026-09-08",
            availableBudgetEur = 100.0,
            cashEur = 10.0,
            actions = listOf(
                action("cash", ActionType.HOLD_CASH, 10.0),
                action("keep", ActionType.KEEP_SAVINGS_PLAN, 20.0),
                action("open", ActionType.OPEN_POSITION, 30.0),
                action("buy", ActionType.BUY_MORE, 40.0),
                action("review", ActionType.REVIEW_SAVINGS_PLAN, 25.0),
                action("reduce", ActionType.REDUCE, 50.0),
                action("sell", ActionType.SELL, 60.0)
            )
        )

        val state = DepotActionCenterMapper.build(plan, emptyMap(), emptyMap(), emptyMap())

        assertEquals(
            listOf(
                ActionType.SELL,
                ActionType.REDUCE,
                ActionType.REVIEW_SAVINGS_PLAN,
                ActionType.BUY_MORE,
                ActionType.OPEN_POSITION,
                ActionType.KEEP_SAVINGS_PLAN,
                ActionType.HOLD_CASH
            ),
            state.items.map { it.type }
        )
    }

    @Test
    fun holdingComesFirstInsideSameActionType() {
        val plan = ActionPlan(
            "plan",
            "2026-09-08",
            100.0,
            0.0,
            listOf(
                action("external", ActionType.BUY_MORE, 40.0, priority = 50),
                action("held", ActionType.BUY_MORE, 40.0, priority = 50)
            )
        )
        val positions = mapOf("held" to PortfolioPosition("held"))

        val state = DepotActionCenterMapper.build(plan, emptyMap(), emptyMap(), positions)

        assertEquals(listOf("held", "external"), state.items.map { it.instrumentId })
    }

    @Test
    fun unreliableBuyIsVisibleButBlockedWithoutExecutableAmount() {
        val plan = ActionPlan("plan", "2026-09-08", 100.0, 0.0, listOf(action("weak", ActionType.OPEN_POSITION, 75.0)))
        val candidate = candidate("weak", reliable = false, coverage = 35)

        val state = DepotActionCenterMapper.build(plan, mapOf("weak" to candidate), emptyMap(), emptyMap())
        val item = state.items.single()

        assertTrue(item.buyBlocked)
        assertFalse(item.executable)
        assertEquals(null, item.displayAmountEur)
        assertEquals("Nicht kaufen – Datenbasis unvollständig", item.actionText)
    }

    @Test
    fun reliableAmountComesUnchangedFromActionPlan() {
        val plan = ActionPlan("plan", "2026-09-08", 100.0, 0.0, listOf(action("buy", ActionType.BUY_MORE, 37.0)))
        val candidate = candidate("buy", reliable = true, coverage = 90)

        val item = DepotActionCenterMapper.build(plan, mapOf("buy" to candidate), emptyMap(), emptyMap()).items.single()

        assertEquals(37.0, item.displayAmountEur!!, 0.001)
        assertEquals("Position um ca. 37 € erhöhen", item.actionText)
        assertTrue(item.executable)
    }

    @Test
    fun summaryUsesPlanAmountsWithoutRecalculatingAllocations() {
        val plan = ActionPlan(
            "plan",
            "2026-09-08",
            100.0,
            15.0,
            listOf(
                action("a", ActionType.BUY_MORE, 35.0),
                action("b", ActionType.OPEN_POSITION, 50.0),
                action("cash", ActionType.HOLD_CASH, 15.0)
            )
        )
        val candidates = mapOf(
            "a" to candidate("a", true, 90),
            "b" to candidate("b", true, 90)
        )

        val state = DepotActionCenterMapper.build(plan, candidates, emptyMap(), emptyMap())

        assertEquals(85.0, state.summary.plannedBuyEur, 0.001)
        assertEquals(15.0, state.summary.cashEur, 0.001)
        assertEquals(0, state.summary.urgentActions)
    }

    @Test
    fun emptyPlanReturnsExplicitEmptyState() {
        val state = DepotActionCenterMapper.build(ActionPlan("", "", 0.0, 0.0, emptyList()), emptyMap(), emptyMap(), emptyMap())

        assertTrue(state.items.isEmpty())
        assertTrue(state.isEmpty)
    }

    private fun action(
        id: String,
        type: ActionType,
        amount: Double,
        priority: Int = 0
    ) = ActionPlanAction(
        actionId = id,
        type = type,
        instrumentId = id,
        amountEur = amount,
        reason = "Testgrund",
        priority = priority
    )

    private fun candidate(itemId: String, reliable: Boolean, coverage: Int) = PortfolioAdvisorCandidate(
        itemId = itemId,
        isHolding = false,
        action = if (reliable) PortfolioAdvisorAction.NACHKAUFEN else PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG,
        advisor = AdvisorResult(
            instrumentId = itemId,
            signal = if (reliable) AdvisorSignal.NACHKAUFEN else AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG,
            score = if (reliable) 82 else 40,
            reliable = reliable,
            reasons = listOf("Testgrund"),
            risks = emptyList(),
            confidencePct = if (reliable) 85 else 30,
            timingFactor = 1.0
        ),
        currentValueEur = 0.0,
        monthlySavingsEur = 0,
        riskScore = 2,
        coveragePct = coverage,
        forecastDirection = "AUFWÄRTS"
    )
}
```

- [ ] **Step 2: Führe den neuen JVM-Test aus und bestätige RED**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests de.tobias.investmentradar.DepotActionCenterMapperTest
```

Expected: Compile/FAIL, weil `DepotActionCenterMapper` und seine View-State-Typen noch nicht existieren.

- [ ] **Step 3: Implementiere die reine Mapping-Schicht**

`DepotActionCenterMapper.kt` mit folgenden öffentlichen Typen und exakt dieser zentralen Signatur anlegen:

```kotlin
package de.tobias.investmentradar

import java.util.Locale

data class DepotActionCenterItem(
    val actionId: String,
    val instrumentId: String,
    val type: ActionType,
    val instrumentName: String,
    val actionText: String,
    val reason: String,
    val priority: Int,
    val isHolding: Boolean,
    val displayAmountEur: Double?,
    val executable: Boolean,
    val buyBlocked: Boolean,
    val dataQualityLabel: String,
    val currentValueEur: Double?,
    val costBasisEur: Double?,
    val profitLossEur: Double?,
    val score: Int?,
    val riskScore: Int?,
    val coveragePct: Int?,
    val forecastDirection: String?
)

data class DepotActionCenterSummary(
    val urgentActions: Int,
    val plannedBuyEur: Double,
    val cashEur: Double
)

data class DepotActionCenterState(
    val items: List<DepotActionCenterItem>,
    val summary: DepotActionCenterSummary,
    val isEmpty: Boolean
)

object DepotActionCenterMapper {
    fun build(
        actionPlan: ActionPlan,
        advisorById: Map<String, PortfolioAdvisorCandidate>,
        itemsById: Map<String, InvestmentItem>,
        positions: Map<String, PortfolioPosition>
    ): DepotActionCenterState {
        val mapped = actionPlan.actions.map { action ->
            val candidate = advisorById[action.instrumentId]
            val investment = itemsById[action.instrumentId]
            val position = positions[action.instrumentId]
            val isHolding = position != null
            val buyType = action.type == ActionType.BUY_MORE || action.type == ActionType.OPEN_POSITION
            val coverage = candidate?.coveragePct ?: investment?.coverage
            val unreliable = candidate?.advisor?.reliable == false || (coverage != null && coverage < 50)
            val buyBlocked = buyType && unreliable
            val currentValue = candidate?.currentValueEur?.takeIf { isHolding }
            val basis = position?.activeCostBasis?.takeIf { position.performanceCostBasisKnown }
            val profitLoss = if (basis != null && currentValue != null) {
                currentValue - basis + position.realizedProfitLoss()
            } else null
            val amount = action.amountEur.takeIf { it.isFinite() && it > 0.0 && !buyBlocked }
            DepotActionCenterItem(
                actionId = action.actionId,
                instrumentId = action.instrumentId,
                type = action.type,
                instrumentName = investment?.name?.takeIf { it.isNotBlank() } ?: action.instrumentId,
                actionText = actionText(action, amount, buyBlocked),
                reason = action.reason,
                priority = action.priority,
                isHolding = isHolding,
                displayAmountEur = amount,
                executable = action.type != ActionType.HOLD_CASH && !buyBlocked,
                buyBlocked = buyBlocked,
                dataQualityLabel = if (buyBlocked) "UNVOLLSTÄNDIG" else "AUSREICHEND",
                currentValueEur = currentValue,
                costBasisEur = basis,
                profitLossEur = profitLoss,
                score = candidate?.advisor?.score ?: investment?.scoreTotal,
                riskScore = candidate?.riskScore ?: investment?.risk?.takeIf { it > 0 },
                coveragePct = coverage,
                forecastDirection = candidate?.forecastDirection
            )
        }.sortedWith(
            compareBy<DepotActionCenterItem> { typeRank(it.type) }
                .thenByDescending { it.isHolding }
                .thenByDescending { it.priority }
                .thenBy { it.instrumentId }
                .thenBy { it.actionId }
        )

        val plannedBuy = mapped
            .filter { !it.buyBlocked && (it.type == ActionType.BUY_MORE || it.type == ActionType.OPEN_POSITION) }
            .sumOf { it.displayAmountEur ?: 0.0 }
        val urgent = mapped.count { it.type == ActionType.SELL || it.type == ActionType.REDUCE || it.type == ActionType.REVIEW_SAVINGS_PLAN }

        return DepotActionCenterState(
            items = mapped,
            summary = DepotActionCenterSummary(
                urgentActions = urgent,
                plannedBuyEur = plannedBuy,
                cashEur = actionPlan.cashEur.coerceAtLeast(0.0)
            ),
            isEmpty = mapped.isEmpty()
        )
    }

    private fun actionText(action: ActionPlanAction, amount: Double?, buyBlocked: Boolean): String {
        if (buyBlocked) return "Nicht kaufen – Datenbasis unvollständig"
        val formatted = amount?.let { String.format(Locale.GERMANY, "%.0f €", it) }
        return when (action.type) {
            ActionType.SELL -> formatted?.let { "Verkauf von ca. $it prüfen" } ?: "Position verkaufen"
            ActionType.REDUCE -> formatted?.let { "Reduzierung um ca. $it prüfen" } ?: "Position reduzieren"
            ActionType.REVIEW_SAVINGS_PLAN -> formatted?.let { "Sparplan $it prüfen" } ?: "Sparplan prüfen"
            ActionType.BUY_MORE -> formatted?.let { "Position um ca. $it erhöhen" } ?: "Position erhöhen"
            ActionType.OPEN_POSITION -> formatted?.let { "Neue Position mit ca. $it eröffnen" } ?: "Neue Position eröffnen"
            ActionType.KEEP_SAVINGS_PLAN -> formatted?.let { "Sparplan $it beibehalten" } ?: "Sparplan beibehalten"
            ActionType.HOLD_CASH -> formatted?.let { "$it als Cash halten" } ?: "Cash halten"
        }
    }

    private fun typeRank(type: ActionType): Int = when (type) {
        ActionType.SELL -> 0
        ActionType.REDUCE -> 1
        ActionType.REVIEW_SAVINGS_PLAN -> 2
        ActionType.BUY_MORE -> 3
        ActionType.OPEN_POSITION -> 4
        ActionType.KEEP_SAVINGS_PLAN -> 5
        ActionType.HOLD_CASH -> 6
    }
}
```

- [ ] **Step 4: Führe den Mapper-Test erneut aus**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests de.tobias.investmentradar.DepotActionCenterMapperTest
```

Expected: PASS.

- [ ] **Step 5: Committe Mapper und Tests**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/DepotActionCenterMapper.kt android/app/src/test/java/de/tobias/investmentradar/DepotActionCenterMapperTest.kt
git commit -m "feat: depotweiten Aktionsplan für die UI abbilden"
```

---

### Task 3: Budgetinvarianten des ActionPlanEngine explizit absichern

**Files:**
- Modify: `android/app/src/test/java/de/tobias/investmentradar/ActionPlanEngineTest.kt`

**Interfaces:**
- Consumes: bestehendes `ActionPlanEngine.build(...)`.
- Produces: Regressionstest, dass Sparplan, freie Käufe und Cash gemeinsam das Monatsbudget nicht überschreiten und Umschichtungsziele nicht als freies Monatsbudget gezählt werden.

- [ ] **Step 1: Ergänze einen 2.4.5-Budgettest**

Vor den privaten Helpern folgenden Test ergänzen:

```kotlin
@Test
fun monthlyBudgetCountsSavingsAndFreshAllocationsExactlyOnce() {
    val saving = candidate(
        itemId = "saving",
        action = PortfolioAdvisorAction.HALTEN,
        monthlySavingsEur = 25,
        score = 70
    )
    val buy = candidate(
        itemId = "buy",
        action = PortfolioAdvisorAction.NEU_AUFNEHMEN,
        monthlySavingsEur = 0,
        score = 88,
        isHolding = false
    )
    val advisorPlan = plan(
        budgetEur = 100,
        allocations = listOf(
            PortfolioAllocation("buy", 90, PortfolioAdvisorAction.NEU_AUFNEHMEN, "Beste neue Chance")
        ),
        candidates = listOf(saving, buy)
    )

    val actionPlan = ActionPlanEngine.build("2026-09-08", advisorPlan)

    val savings = actionPlan.actions.single { it.type == ActionType.KEEP_SAVINGS_PLAN }.amountEur
    val purchases = actionPlan.actions
        .filter { it.type == ActionType.BUY_MORE || it.type == ActionType.OPEN_POSITION }
        .filter { it.fromInstrumentId == null }
        .sumOf { it.amountEur }
    assertEquals(25.0, savings, 0.001)
    assertEquals(75.0, purchases, 0.001)
    assertEquals(0.0, actionPlan.cashEur, 0.001)
    assertEquals(actionPlan.availableBudgetEur, savings + purchases + actionPlan.cashEur, 0.001)
}
```

- [ ] **Step 2: Führe `ActionPlanEngineTest` aus**

Run:

```bash
cd android && ./gradlew testDebugUnitTest --tests de.tobias.investmentradar.ActionPlanEngineTest
```

Expected: PASS mit der bestehenden Engine. Falls der Test rot wird, nur die nachgewiesene Budgetursache in `ActionPlanEngine.kt` korrigieren und anschließend den gesamten Test erneut grün ausführen.

- [ ] **Step 3: Committe den Budgetvertrag**

```bash
git add android/app/src/test/java/de/tobias/investmentradar/ActionPlanEngineTest.kt android/app/src/main/java/de/tobias/investmentradar/ActionPlanEngine.kt
git commit -m "test: Monatsbudget im Aktionsplan eindeutig absichern"
```

Hinweis: `ActionPlanEngine.kt` nur stagen, falls Step 2 tatsächlich eine produktive Korrektur erfordert; andernfalls nur die Testdatei committen.

---

### Task 4: Depot-Aktionscenter in `AlertsScreen` rendern

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt`
- Test: `android/tests/test-alert-center-ui.sh`

**Interfaces:**
- Consumes: `DepotActionCenterMapper.build(actionPlan, advisorById, itemsById, positions)`.
- Produces: `onExecuteAction: (DepotActionCenterItem) -> Unit` im `AlertsScreen`-Interface sowie Compose-Funktion `DepotActionCenterSection(...)`.

- [ ] **Step 1: Erweitere die `AlertsScreen`-Signatur**

Direkt vor `onOpen` ergänzen:

```kotlin
onExecuteAction: (DepotActionCenterItem) -> Unit = {},
```

- [ ] **Step 2: Erzeuge den depotweiten View-State einmal aus den bereits aufgelösten Daten**

Nach `resolvedPositions` ergänzen:

```kotlin
val depotActionCenter = DepotActionCenterMapper.build(
    actionPlan = resolvedActionPlan,
    advisorById = resolvedAdvisorById,
    itemsById = itemsById,
    positions = resolvedPositions
)
```

- [ ] **Step 3: Rendere die neue Sektion vor den Einzelalarmen**

Nach dem bisherigen Header-`item { ... }` und vor `if (visible.isEmpty())` einfügen:

```kotlin
item {
    DepotActionCenterSection(
        state = depotActionCenter,
        onExecuteAction = onExecuteAction
    )
}
```

- [ ] **Step 4: Implementiere die Compose-Sektion ohne eigene Empfehlungslogik**

Vor `AlertCard` folgende Komponente ergänzen:

```kotlin
@Composable
private fun DepotActionCenterSection(
    state: DepotActionCenterState,
    onExecuteAction: (DepotActionCenterItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Das solltest du jetzt mit deinem Depot machen",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        if (state.isEmpty) {
            Text(
                "Noch kein belastbarer Depot-Aktionsplan verfügbar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionSummaryMetric("Dringende Aktionen", state.summary.urgentActions.toString(), Modifier.weight(1f))
            ActionSummaryMetric("Geplantes Kaufbudget", formatEur(state.summary.plannedBuyEur), Modifier.weight(1f))
            ActionSummaryMetric("Cash halten", formatEur(state.summary.cashEur), Modifier.weight(1f))
        }
        state.items.forEach { item ->
            val accent = when (item.type) {
                ActionType.SELL -> Color(0xFFFF6577)
                ActionType.REDUCE, ActionType.REVIEW_SAVINGS_PLAN -> Color(0xFFFFC857)
                ActionType.BUY_MORE, ActionType.OPEN_POSITION -> MaterialTheme.colorScheme.primary
                ActionType.KEEP_SAVINGS_PLAN, ActionType.HOLD_CASH -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.actionText, fontWeight = FontWeight.Black, color = accent)
                        if (item.isHolding) Text("IM DEPOT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(item.instrumentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (item.buyBlocked) {
                        Text("WATCH · NICHT BESTÄTIGT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFFFFC857))
                    }
                    if (item.reason.isNotBlank()) Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MetricRow("Depotwert", item.currentValueEur?.let(::formatEur) ?: "–")
                    MetricRow("Einstand / G/V", item.costBasisEur?.let { "${formatEur(it)} / ${formatSignedEur(item.profitLossEur)}" } ?: "–")
                    MetricRow("Score", item.score?.toString() ?: "–")
                    MetricRow("Risiko", item.riskScore?.let { "$it/5" } ?: "–")
                    MetricRow("Datenabdeckung", item.coveragePct?.let { "$it %" } ?: "–")
                    MetricRow("Prognose", item.forecastDirection ?: "–")
                    if (item.executable) {
                        Button(onClick = { onExecuteAction(item) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when (item.type) {
                                    ActionType.BUY_MORE, ActionType.OPEN_POSITION -> "Depotbuchung öffnen"
                                    ActionType.SELL, ActionType.REDUCE -> "Verkaufsbuchung öffnen"
                                    ActionType.REVIEW_SAVINGS_PLAN, ActionType.KEEP_SAVINGS_PLAN -> "Sparplan öffnen"
                                    ActionType.HOLD_CASH -> "Ansehen"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
    }
}
```

- [ ] **Step 5: Führe Source-Vertrag und Kotlin-Kompilation aus**

Run:

```bash
bash android/tests/test-alert-center-ui.sh
cd android && ./gradlew testDebugUnitTest --tests de.tobias.investmentradar.DepotActionCenterMapperTest
```

Expected: Source-Vertrag kann jetzt bis zum Navigationswiring grün werden; JVM-Mappingtest PASS.

- [ ] **Step 6: Committe die UI-Schicht**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt android/tests/test-alert-center-ui.sh
git commit -m "feat: zentrales Depot-Aktionscenter anzeigen"
```

---

### Task 5: Bestehende Buchungs- und Sparplan-Flows anbinden

**Files:**
- Modify: `android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt`
- Modify: `android/tests/test-alert-center-ui.sh`

**Interfaces:**
- Consumes: `onExecuteAction: (DepotActionCenterItem) -> Unit` aus Task 4.
- Produces: BUY/OPEN/SELL/REDUCE öffnen `PurchaseHistoryDialog`; Sparplanaktionen öffnen vorhandene Sparplanverwaltung; Cash führt keine Ausführung aus.

- [ ] **Step 1: Übergib aktuelle Investment-, Advisor- und Positionsdaten an `AlertsScreen`**

Im vorhandenen `else -> AlertsScreen(` Block direkt nach `preferences = alertPreferences,` ergänzen:

```kotlin
actionPlan = ActionPlanEngine.build(
    analysisDay = s.data.generatedAt.take(10).takeIf { it.length == 10 } ?: java.time.LocalDate.now().toString(),
    advisorPlan = advisorPlan
),
advisorById = advisorById,
itemsById = s.data.items.associateBy { it.id },
positions = positions,
```

- [ ] **Step 2: Verbinde die Aktionskarten mit bestehenden Flows**

Direkt vor `onOpen = { stored ->` ergänzen:

```kotlin
onExecuteAction = { action ->
    when (action.type) {
        ActionType.BUY_MORE,
        ActionType.OPEN_POSITION,
        ActionType.SELL,
        ActionType.REDUCE -> {
            val item = s.data.items.firstOrNull { it.id == action.instrumentId }
            if (item != null) {
                investmentDialogItem = item
            } else {
                missingAlertItemMessage = "Das Wertpapier ist nicht im aktuellen Radar enthalten."
            }
        }
        ActionType.REVIEW_SAVINGS_PLAN,
        ActionType.KEEP_SAVINGS_PLAN -> {
            selectedDetailId = null
            tab = 2
            showSavingsPlans = true
        }
        ActionType.HOLD_CASH -> Unit
    }
},
```

Damit bleibt der vorhandene `PurchaseHistoryDialog` die einzige Transaktionsoberfläche für Kauf und Verkauf. Kein Klick markiert einen Trade als ausgeführt.

- [ ] **Step 3: Ergänze Navigation-Wiring im Source-Vertrag**

In `android/tests/test-alert-center-ui.sh` zusätzlich `MAIN` definieren:

```bash
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
```

Und im 2.4.5-Block ergänzen:

```bash
require_literal 'onExecuteAction = { action ->' "$MAIN" 'Aktionscenter-Main-Wiring'
require_literal 'investmentDialogItem = item' "$MAIN" 'Kauf-Verkauf-Buchungsnavigation'
require_literal 'showSavingsPlans = true' "$MAIN" 'Sparplan-Navigation'
require_literal 'ActionType.HOLD_CASH -> Unit' "$MAIN" 'Cash ohne Auto-Ausführung'
```

- [ ] **Step 4: Führe Source-Vertrag und JVM-Tests aus**

Run:

```bash
bash android/tests/test-alert-center-ui.sh
cd android && ./gradlew testDebugUnitTest --tests de.tobias.investmentradar.DepotActionCenterMapperTest --tests de.tobias.investmentradar.ActionPlanEngineTest
```

Expected: PASS.

- [ ] **Step 5: Committe Navigation und Vertragsprüfung**

```bash
git add android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt android/tests/test-alert-center-ui.sh
git commit -m "feat: Depot-Aktionen mit bestehenden Buchungen verbinden"
```

---

### Task 6: Version 2.4.5 / Code 65 und Release-Verträge

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/tests/test-version-contract.sh`
- Modify: `android/tests/test-history-card-glare.sh`
- Modify: `android/tests/test-release-backend-gate.sh`

**Interfaces:**
- Consumes: fertig implementierter 2.4.5-App-Tree.
- Produces: eindeutig versionierter Android-App-Tree, der vom bestehenden immutable Release-Workflow als `v2.4.5` veröffentlicht werden kann.

- [ ] **Step 1: Bumpe Android auf 2.4.5 / Code 65**

In `android/app/build.gradle.kts` setzen:

```kotlin
versionCode = 65
versionName = "2.4.5"
```

Den Release-Kommentar auf 2.4.5 aktualisieren.

- [ ] **Step 2: Aktualisiere Versionsvertrag**

In `android/tests/test-version-contract.sh`:

```bash
require_literal 'versionCode = 65' "$APP_BUILD" 'Android versionCode 65'
require_literal 'versionName = "2.4.5"' "$APP_BUILD" 'Android versionName 2.4.5'
forbid_literal 'versionCode = 64' "$APP_BUILD" 'veralteter Android versionCode 64'
forbid_literal 'versionName = "2.4.4"' "$APP_BUILD" 'veraltete Android versionName 2.4.4'
```

Backend-Erwartung unverändert `2.1.0` lassen.

- [ ] **Step 3: Aktualisiere Glare- und Release-Gate-Verträge**

In `android/tests/test-history-card-glare.sh` ausschließlich die Release-Version auf `2.4.5` / `65` anheben; `NeonPanelGlowAlpha = 0.04f` unverändert lassen.

In `android/tests/test-release-backend-gate.sh` Android-Erwartung auf `2.4.5` / `65` ändern; Backend `2.1.0`, main-only Publish, Candidate/Main-Live-Gate, App-Tree-Immutable-Check, dynamische Release Notes und `--clobber`-Verbot unverändert lassen.

- [ ] **Step 4: Führe alle lokalen Source-Verträge aus**

Run:

```bash
bash android/tests/test-version-contract.sh
bash android/tests/test-history-card-glare.sh
bash android/tests/test-release-backend-gate.sh
bash android/tests/test-alert-center-ui.sh
```

Expected: alle PASS.

- [ ] **Step 5: Führe die vollständigen JVM-Tests aus**

Run:

```bash
cd android && ./gradlew testDebugUnitTest
```

Expected: PASS ohne Regression.

- [ ] **Step 6: Committe den Release-Kandidaten**

```bash
git add android/app/build.gradle.kts android/tests/test-version-contract.sh android/tests/test-history-card-glare.sh android/tests/test-release-backend-gate.sh
git commit -m "release: Android 2.4.5 vorbereiten"
```

---

### Task 7: Finalen `main`-SHA verifizieren und veröffentlichen

**Files:**
- No source-file changes expected.
- Verify GitHub Actions and Release metadata only.

**Interfaces:**
- Consumes: finalen `main`-SHA aus Task 6.
- Produces: grünen Contract/JVM/APK-Nachweis und neuen unveränderlichen Release `v2.4.5`.

- [ ] **Step 1: Lies den exakten aktuellen `main`-SHA**

Run via GitHub repository/branch lookup and record the exact SHA. Alle folgenden Runs müssen genau diesen SHA referenzieren.

- [ ] **Step 2: Prüfe Android Contract Tests**

Für den finalen SHA den zugehörigen `Android Contract Tests` Run öffnen.

Expected: `completed / success`, einschließlich `test-alert-center-ui.sh`, Versions-, Glare- und Release-Gate-Vertrag.

- [ ] **Step 3: Prüfe Android JVM Tests**

Für denselben SHA den `Android JVM Tests` Run öffnen.

Expected: `completed / success`, inklusive `DepotActionCenterMapperTest` und `ActionPlanEngineTest`.

- [ ] **Step 4: Prüfe Build Android APK**

Für denselben SHA den Build-Run öffnen und folgende Schritte einzeln als `success` verifizieren:

```text
Build signed release APK
Verify APK signature
Verify live backend before Android publish
Publish APK for in-app updates
Upload APK
```

- [ ] **Step 5: Prüfe Release `v2.4.5`**

Release-Metadaten abrufen und verifizieren:

```text
tag_name = v2.4.5
name = Investment Radar 2.4.5
target_commitish = main
asset name = investment-radar.apk
asset digest = sha256:<GitHub-verifizierter Digest>
```

Den tatsächlichen Digest aus GitHub berichten; keinen Wert vorab annehmen.

- [ ] **Step 6: Abschlussmeldung nur mit frischer Evidenz**

Berichte final:

- finalen `main`-SHA,
- Contract-Run-ID + success,
- JVM-Run-ID + success,
- APK-Build-Run-ID + success,
- `v2.4.5` Release-ID,
- APK Asset-ID, Größe und SHA-256-Digest,
- Hinweis, dass Nutzer über die bestehende In-App-Update-Funktion aktualisieren kann und keine Neuinstallation nötig ist.

---

## Self-Review

### Spec coverage

- Zentrale depotweite Arbeitsliste: Tasks 2 und 4.
- Klare deutsche Aktionsformulierungen und echte Planbeträge: Task 2.
- Priorisierung und Depotvorrang: Task 2 JVM-Test.
- Budgetkonsistenz und keine doppelte Sparplanverplanung: Task 3 sowie bestehender `ActionPlanEngine`.
- BUY/WATCH-Sperre bei schlechter Datenbasis: Task 2 und Task 4.
- Direkte Navigation zu Kauf/Verkauf/Sparplan ohne Auto-Ausführung: Task 5.
- Leerzustand ohne erfundene Empfehlung: Task 2 und Task 4.
- Version 2.4.5 / Code 65: Task 6.
- Finaler Release-Nachweis: Task 7.

### Placeholder scan

Der Plan enthält keine `TBD`, keine offenen Implementierungsmarker und keine nicht definierten Funktionsnamen. Jeder neu verwendete Typ wird in Task 2 definiert; die späteren Tasks verwenden exakt dieselben Namen.

### Type consistency

- `DepotActionCenterMapper.build(ActionPlan, Map<String, PortfolioAdvisorCandidate>, Map<String, InvestmentItem>, Map<String, PortfolioPosition>): DepotActionCenterState` wird in Tasks 2 und 4 identisch verwendet.
- `onExecuteAction: (DepotActionCenterItem) -> Unit` wird in Tasks 4 und 5 identisch verwendet.
- `ActionType` bleibt die bestehende Enum aus `ActionPlanEngine.kt`; es wird keine konkurrierende Aktions-Enum eingeführt.
- Alle Euro-Beträge stammen aus `ActionPlanAction.amountEur` oder `ActionPlan.cashEur`; das UI erzeugt keine Allokation.