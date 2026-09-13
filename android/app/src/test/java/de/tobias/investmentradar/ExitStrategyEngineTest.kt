package de.tobias.investmentradar

import org.junit.Assert.*
import org.junit.Test

class ExitStrategyEngineTest {
    private fun position(cost:Double=100.0,shares:Double=1.0)=
        PortfolioPosition("asset").upsertPurchaseIfValid(PortfolioPurchase("buy","2026-09-01",cost,shares))!!

    @Test fun disabledStrategyNeverTriggers(){
        val r=ExitStrategyEngine.evaluate(position(),130.0,ExitStrategy("asset",false,15.0,10.0))
        assertFalse(r.triggered); assertNull(r.kind)
    }

    @Test fun takeProfitTriggersAtConfiguredThreshold(){
        val r=ExitStrategyEngine.evaluate(position(),116.0,ExitStrategy("asset",true,15.0,10.0))
        assertEquals(ExitTriggerKind.TAKE_PROFIT,r.kind); assertEquals(16.0,r.currentProfitLossPct!!,0.0001)
    }

    @Test fun stopLossTriggersAtConfiguredThreshold(){
        val r=ExitStrategyEngine.evaluate(position(),88.0,ExitStrategy("asset",true,15.0,10.0))
        assertEquals(ExitTriggerKind.STOP_LOSS,r.kind); assertEquals(-12.0,r.currentProfitLossPct!!,0.0001)
    }

    @Test fun triggeredExitOverridesPartialAdvisorAction(){
        val pos=position(100.0,2.0)
        val base=ActionPlan("plan","2026-09-13",0.0,0.0,listOf(
            ActionPlanAction("reduce",ActionType.REDUCE,"asset",25.0,reason="reduce",priority=70)
        ))
        val plan=ExitStrategyActionOverlay.apply(
            base,mapOf("asset" to pos),mapOf("asset" to 60.0),mapOf("asset" to ExitStrategy("asset",true,15.0,null))
        )
        val sell=plan.actions.single()
        assertEquals(ActionType.SELL,sell.type); assertEquals(120.0,sell.amountEur,0.0001); assertEquals(2.0,sell.plannedShares!!,0.0001)
    }
}
