package de.tobias.investmentradar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class DepotScreenshotImportUiTest {
    @get:Rule
    val composeRule = createComposeRule()

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
    fun unresolvedReviewBlocksApplyAndShowsMissingActions() {
        val observation = TradeRepublicScreenshotObservation(
            name = "Meta Platforms",
            valueEur = 812.40,
            profitLossEur = null,
            profitLossPct = null,
            derivedCostBasisEur = 760.0,
            shares = null,
            sourceIndex = 0
        )
        val rows = listOf(
            DepotScreenshotImportReviewRow(
                key = "conflict",
                itemId = "meta",
                observation = observation,
                status = ScreenshotReviewStatus.CONFLICT,
                reason = "Widersprüchliche Werte",
                kind = DepotScreenshotImportRowKind.UPDATE,
                resolved = false
            ),
            DepotScreenshotImportReviewRow(
                key = "missing",
                itemId = "msft",
                observation = null,
                status = ScreenshotReviewStatus.NOT_FOUND,
                reason = "Nicht gefunden",
                kind = DepotScreenshotImportRowKind.MISSING,
                resolved = false
            )
        )
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Reviewing(
                        images = listOf(ScreenshotImageRef("one", "content://one")),
                        rows = rows,
                        failedImages = emptyList(),
                        summary = PortfolioImportSummary(),
                        canApply = false
                    ),
                    itemNames = mapOf("meta" to "Meta Platforms", "msft" to "Microsoft"),
                    actions = DepotScreenshotImportUiActions()
                )
            }
        }

        composeRule.onNodeWithText("3 von 4 · Prüfen & korrigieren").assertIsDisplayed()
        composeRule.onNodeWithText("Konflikt · bitte prüfen").assertIsDisplayed()
        composeRule.onNodeWithText("Behalten").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Als verkauft markieren").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Löschen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Änderungen übernehmen").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun resolvedReviewEnablesApply() {
        val row = DepotScreenshotImportReviewRow(
            key = "ready",
            itemId = "meta",
            observation = TradeRepublicScreenshotObservation(
                name = "Meta Platforms",
                valueEur = 812.40,
                profitLossEur = null,
                profitLossPct = null,
                derivedCostBasisEur = 760.0,
                shares = null,
                sourceIndex = 0
            ),
            status = ScreenshotReviewStatus.READY,
            kind = DepotScreenshotImportRowKind.UPDATE,
            resolved = true
        )
        composeRule.setContent {
            MaterialTheme {
                DepotScreenshotImportContent(
                    state = DepotScreenshotImportState.Reviewing(
                        images = listOf(ScreenshotImageRef("one", "content://one")),
                        rows = listOf(row),
                        failedImages = emptyList(),
                        summary = PortfolioImportSummary(updatedCount = 1),
                        canApply = true
                    ),
                    itemNames = mapOf("meta" to "Meta Platforms"),
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
