package de.tobias.investmentradar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ExitStrategyWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext,params){
    override suspend fun doWork(): Result {
        val strategies=ExitStrategyStore.readAll(applicationContext).filterValues{it.enabled}
        if(strategies.isEmpty()) return Result.success()
        val positions=PortfolioStore.readPositions(applicationContext)
        val active=strategies.filterKeys{positions[it]?.isActiveHolding()==true}
        if(active.isEmpty()) return Result.success()
        val custom=CustomInvestmentStore.read(applicationContext).associateBy{it.id}
        val evaluations=mutableMapOf<String,ExitStrategyEvaluation>()
        val names=mutableMapOf<String,String>()
        active.forEach{(id,strategy)->
            val item=custom[id]?.let{runCatching{ApiClient.loadCustomQuote(it)}.getOrNull()}
                ?:runCatching{ApiClient.loadRadarDetail(id).asInvestmentItem()}.getOrNull()
                ?:return@forEach
            val price=item.priceEur ?: item.price?.takeIf{item.currency.isBlank()||item.currency.equals("EUR",true)}
            evaluations[id]=ExitStrategyEngine.evaluate(positions[id]?:return@forEach,price,strategy)
            names[id]=item.name
        }
        if(evaluations.isEmpty()) return Result.retry()
        AdvisorNotificationManager.publishExitTriggers(
            applicationContext,
            ExitStrategyStore.applyEvaluations(applicationContext,evaluations),
            names
        )
        return Result.success()
    }
}
