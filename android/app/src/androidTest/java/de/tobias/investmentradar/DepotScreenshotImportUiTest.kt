package de.tobias.investmentradar

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DepotScreenshotImportUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun dashboardItem(
        id: String,
        name: String,
        priceEur: Double?
    ) = InvestmentItem(
        id = id,
        type = "Aktie",
        name = name,
        ticker = id.uppercase(),
        isin = "",
        tradeRepublicName = name,
        status = "OK",
        allocation = 0,
        risk = 3,
        price = priceEur,
        priceEur = priceEur,
        currency = "EUR",
        fxRateToEur = 1.0,
        fxSource = "test",
        fxDelayed = false,
        fxAsOf = null,
        percentChange = null,
        marketOpen = true,
        dataSource = "test",
        dataDelayed = false,
        dataError = null
    )

    private fun emptyAdvisorPlan() = PortfolioAdvisorPlan(
        budgetEur = 0,
        allocations = emptyList(),
        cashEur = 0,
        reallocations = emptyList(),
        savingsPlanConflicts = emptyList(),
        candidates = emptyList()
    )

    private fun renderPortfolioDashboard(
        items: List<InvestmentItem>,
        positions: Map<String, PortfolioPosition>,
        onUpdateDepot: () -> Unit = {}
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        composeRule.setContent {
            MaterialTheme {
                PortfolioDashboard(
                    items = items,
                    positions = positions,
                    customItems = emptyList(),
                    advisorPlan = emptyAdvisorPlan(),
                    showSavingsPlans = false,
                    onShowSavingsPlansChange = {},
                    onOpenDetail = {},
                    onEdit = {},
                    onRemove = {},
                    onUpdateDepot = onUpdateDepot,
                    onAddCustom = {},
                    onEditCustom = {},
                    onRemoveCustom = {},
                    vm = vm
                )
            }
        }
    }

    @Test
    fun portfolioDashboardExposesDepotUpdateEntry() {
        var opened = false
        renderPortfolioDashboard(emptyList(), emptyMap()) { opened = true }

        composeRule.onNodeWithText("Depot aktualisieren").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun soldMarkedPositionIsSeparatedAndShowsMarketPriceNotSalePrice() {
        val item = dashboardItem("meta", "Meta Platforms", 123.45)
        val sold = PortfolioPosition(
            itemId = "meta",
            trackedShares = 2.0,
            snapshotValueEur = 240.0,
            snapshotCostBasisEur = 200.0,
            lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED
        )
        renderPortfolioDashboard(listOf(item), mapOf("meta" to sold))

        val list = composeRule.onNodeWithTag("portfolioDashboardList")
        list.performScrollToIndex(4)
        composeRule.onNodeWithText("Verkaufte Positionen").performScrollTo().assertIsDisplayed()

        list.performScrollToIndex(5)
        composeRule.onNodeWithText("Aktueller Marktpreis").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("123,45 €").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Verkaufspreis", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun soldMarkedPositionWithoutQuoteShowsUnavailableMarketPrice() {
        val item = dashboardItem("meta", "Meta Platforms", null)
        val sold = PortfolioPosition(
            itemId = "meta",
            lifecycleStatus = PortfolioLifecycleStatus.SOLD_MARKED
        )
        renderPortfolioDashboard(listOf(item), mapOf("meta" to sold))

        composeRule.onNodeWithTag("portfolioDashboardList").performScrollToIndex(5)
        composeRule.onNodeWithText("Aktueller Marktpreis").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kurs nicht verfügbar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun snapshotWithoutSharesShowsConfirmedTradeRepublicValueAndSeparateMarketPrice() {
        val item = dashboardItem("meta", "Meta Platforms", 150.0)
        val snapshot = PortfolioPosition(
            itemId = "meta",
            snapshotValueEur = 1_675.88,
            snapshotCostBasisEur = 1_500.0,
            snapshotCapturedAt = "2026-10-15T10:00:00Z"
        )
        renderPortfolioDashboard(listOf(item), mapOf("meta" to snapshot))

        composeRule.onNodeWithTag("portfolioDashboardList").performScrollToIndex(4)
        composeRule.onNodeWithText("Letzter bestätigter Trade-Republic-Wert").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1675,88 €", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("15.10.2026", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Aktueller Marktpreis").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("150,00 €").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Aktueller Wert").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun selectingShowsSelectedScreenshotCount() {
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Selecting(
                        listOf(
                            ScreenshotImageRef("one", "content://one"),
                            ScreenshotImageRef("two", "content://two")
                        )
                    ),
                    itemNames = emptyMap(),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("1 von 4 · Screenshots auswählen").assertIsDisplayed()
        composeRule.onNodeWithText("2 Screenshots ausgewählt").assertIsDisplayed()
        composeRule.onNodeWithText("Screenshots analysieren").assertIsEnabled()
    }

    @Test
    fun recognitionShowsProgress() {
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Recognizing(
                        images = List(5) { index -> ScreenshotImageRef("$index", "content://$index") },
                        completed = 3,
                        total = 5
                    ),
                    itemNames = emptyMap(),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("2 von 4 · Lokal erkennen").assertIsDisplayed()
        composeRule.onNodeWithText("3 von 5 Screenshots analysiert").assertIsDisplayed()
    }

    @Test
    fun conflictReviewIsVisible() {
        val observation = TradeRepublicScreenshotObservation(
            name = "Meta Platforms",
            valueEur = 812.40,
            profitLossEur = null,
            profitLossPct = null,
            derivedCostBasisEur = 760.0,
            shares = null,
            sourceIndex = 0
        )
        val row = DepotScreenshotImportReviewRow(
            key = "conflict",
            itemId = "meta",
            observation = observation,
            status = ScreenshotReviewStatus.CONFLICT,
            reason = "Widersprüchliche Werte",
            kind = DepotScreenshotImportRowKind.UPDATE,
            resolved = false
        )

        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Reviewing(
                        images = listOf(ScreenshotImageRef("one", "content://one")),
                        rows = listOf(row),
                        failedImages = emptyList(),
                        summary = PortfolioImportSummary(),
                        canApply = false
                    ),
                    itemNames = mapOf("meta" to "Meta Platforms"),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("3 von 4 · Prüfen & korrigieren").assertIsDisplayed()
        composeRule.onNodeWithText("Konflikt · bitte prüfen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missingPositionShowsKeepSoldDeleteActions() {
        val row = DepotScreenshotImportReviewRow(
            key = "missing",
            itemId = "msft",
            observation = null,
            status = ScreenshotReviewStatus.NOT_FOUND,
            reason = "Nicht gefunden",
            kind = DepotScreenshotImportRowKind.MISSING,
            resolved = false
        )

        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Reviewing(
                        images = listOf(ScreenshotImageRef("one", "content://one")),
                        rows = listOf(row),
                        failedImages = emptyList(),
                        summary = PortfolioImportSummary(),
                        canApply = false
                    ),
                    itemNames = mapOf("msft" to "Microsoft"),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("Behalten").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Als verkauft markieren").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Löschen").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unresolvedReviewBlocksApply() {
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Reviewing(
                        images = listOf(ScreenshotImageRef("one", "content://one")),
                        rows = emptyList(),
                        failedImages = emptyList(),
                        summary = PortfolioImportSummary(),
                        canApply = false
                    ),
                    itemNames = emptyMap(),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("Änderungen übernehmen").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun resolvedReviewEnablesApply() {
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Reviewing(
                        images = listOf(ScreenshotImageRef("one", "content://one")),
                        rows = emptyList(),
                        failedImages = emptyList(),
                        summary = PortfolioImportSummary(updatedCount = 1),
                        canApply = true
                    ),
                    itemNames = emptyMap(),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("Änderungen übernehmen").performScrollTo().assertIsEnabled()
    }

    @Test
    fun completedShowsSummary() {
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Completed(
                        PortfolioImportSummary(
                            updatedCount = 6,
                            addedCount = 1,
                            soldCount = 1,
                            deletedCount = 0,
                            keptCount = 1
                        )
                    ),
                    itemNames = emptyMap(),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("4 von 4 · Übernommen").assertIsDisplayed()
        composeRule.onNodeWithText("6 aktualisiert · 1 neu · 1 verkauft · 0 gelöscht · 1 behalten").assertIsDisplayed()
    }
}
