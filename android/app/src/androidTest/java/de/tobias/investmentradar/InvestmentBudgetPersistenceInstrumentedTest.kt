package de.tobias.investmentradar

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class InvestmentBudgetPersistenceInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("investment_radar_budget", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("investment_radar_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("investment_radar_budget", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("investment_radar_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun decimalMonthlyBudgetIsPreservedForNextMonth() {
        InvestmentBudgetStore.setMonthlyBudget(
            context = context,
            amountEur = 99.99,
            date = "2099-10-01"
        )

        InvestmentBudgetStore.ensureCurrentMonth(
            context = context,
            today = LocalDate.of(2099, 11, 1)
        )

        val novemberBudget = InvestmentBudgetStore.readEntries(context)
            .single {
                it.type == BudgetJournalType.MONTHLY_DEPOSIT &&
                    InvestmentBudgetDate.monthKey(it.date) == "2099-11"
            }

        assertEquals(99.99, novemberBudget.amountEur, 0.000001)
    }
}
