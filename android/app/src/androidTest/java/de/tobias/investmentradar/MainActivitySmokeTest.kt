package de.tobias.investmentradar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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

        openMoneyScreen()

        composeRule.onNodeWithText("GELDVERWALTUNG", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("ICH BRAUCHE GELD").assertIsDisplayed()
    }

    @Test
    fun liquidityNeedPresetAndCashFirstSwitchUpdatePlan() {
        openMoneyScreen()

        composeRule.onNodeWithText("100 €").performClick()
        composeRule.onNodeWithText("Benötigt:", substring = true).performScrollTo().assertIsDisplayed()

        composeRule.onNode(isToggleable()).assertIsOn().performClick().assertIsOff()
        composeRule.onNodeWithText("Der komplette Betrag wird über Verkäufe geplant.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Noch durch Verkäufe freizumachen:", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun openMoneyScreen() {
        composeRule.onNodeWithText("Geld").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText("GELDVERWALTUNG", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("moneyManagementList").performScrollToIndex(2)
        composeRule.onNodeWithText("ICH BRAUCHE GELD").assertIsDisplayed()
    }
}
