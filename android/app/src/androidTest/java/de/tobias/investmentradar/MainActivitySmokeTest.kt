package de.tobias.investmentradar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rootNavigationAndMoneyScreenRender() {
        composeRule.onNodeWithText("Investment Radar").assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertIsDisplayed()
        composeRule.onNodeWithText("Radar").assertIsDisplayed()
        composeRule.onNodeWithText("Portfolio").assertIsDisplayed()
        composeRule.onNodeWithText("Alarme").assertIsDisplayed()
        composeRule.onNodeWithText("Geld").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText("GELDVERWALTUNG", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("GELDVERWALTUNG", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("moneyManagementList").performScrollToIndex(2)
        composeRule.onNodeWithText("ICH BRAUCHE GELD").assertIsDisplayed()
    }

    @Test
    fun germanThousandsSeparatorKeepsMonetaryValue() {
        // Regression: a German thousands separator must never turn 1.000,50 EUR into ~1 EUR.
        composeRule.onNodeWithText("Geld").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText("GELDVERWALTUNG", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("moneyManagementList").performScrollToIndex(2)

        val amountField = composeRule.onNodeWithText("Benötigter Betrag in €")
        amountField.performTextInput("1.000,50")
        amountField.assertTextContains("1000,50")
    }
}
