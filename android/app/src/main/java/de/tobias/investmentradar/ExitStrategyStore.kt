package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ExitStrategyStore {
    private const val PREFS="investment_radar_exit_strategy"
    private const val STRATEGIES="strategies_v1"
    private const val ACTIVE="active_triggers_v1"

    fun readAll(context: Context): Map<String, ExitStrategy> {
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(STRATEGIES,null) ?: return emptyMap()
        return runCatching {
            val a=JSONArray(raw)
            buildMap {
                for(i in 0 until a.length()){
                    val o=a.optJSONObject(i) ?: continue
                    val id=o.optString("itemId").trim()
                    if(id.isBlank()) continue
                    val take=o.optDouble("takeProfitPct",Double.NaN).takeIf{it.isFinite()&&it>0}
                    val stop=o.optDouble("stopLossPct",Double.NaN).takeIf{it.isFinite()&&it>0}
                    put(id,ExitStrategy(id,o.optBoolean("enabled",false),take,stop).normalized())
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun save(context: Context, strategy: ExitStrategy){
        val s=strategy.normalized()
        if(s.itemId.isBlank()) return
        val next=readAll(context).toMutableMap().apply{put(s.itemId,s)}
        saveStrategies(context,next)
        clearActive(context,s.itemId)
    }

    fun remove(context: Context,itemId:String){
        val next=readAll(context).toMutableMap().apply{remove(itemId)}
        saveStrategies(context,next)
        clearActive(context,itemId)
    }

    fun applyEvaluations(context: Context, evaluations: Map<String, ExitStrategyEvaluation>): List<ExitStrategyTrigger>{
        if(evaluations.isEmpty()) return emptyList()
        val active=readActive(context).toMutableMap()
        val out=mutableListOf<ExitStrategyTrigger>()
        evaluations.forEach{(id,e)->
            val next=e.kind
            val previous=active[id]
            if(next==null) active.remove(id) else {
                if(previous!=next && e.currentProfitLossPct!=null && e.thresholdPct!=null){
                    out+=ExitStrategyTrigger(
                        "exit-$id-${next.name.lowercase()}-${System.currentTimeMillis()}",
                        id,next,e.currentProfitLossPct,e.thresholdPct,e.currentValueEur,e.shares,e.reason
                    )
                }
                active[id]=next
            }
        }
        saveActive(context,active)
        return out
    }

    private fun saveStrategies(context:Context, values:Map<String,ExitStrategy>){
        val a=JSONArray()
        values.toSortedMap().values.forEach{s->
            a.put(JSONObject().put("itemId",s.itemId).put("enabled",s.enabled)
                .put("takeProfitPct",s.takeProfitPct?:JSONObject.NULL)
                .put("stopLossPct",s.stopLossPct?:JSONObject.NULL))
        }
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(STRATEGIES,a.toString()).apply()
    }
    private fun readActive(context:Context):Map<String,ExitTriggerKind>{
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(ACTIVE,null)?:return emptyMap()
        return runCatching{
            val a=JSONArray(raw)
            buildMap{
                for(i in 0 until a.length()){
                    val o=a.optJSONObject(i)?:continue
                    val id=o.optString("itemId").trim()
                    val kind=runCatching{ExitTriggerKind.valueOf(o.optString("kind"))}.getOrNull()
                    if(id.isNotBlank()&&kind!=null) put(id,kind)
                }
            }
        }.getOrDefault(emptyMap())
    }
    private fun saveActive(context:Context,values:Map<String,ExitTriggerKind>){
        val a=JSONArray()
        values.toSortedMap().forEach{(id,k)->a.put(JSONObject().put("itemId",id).put("kind",k.name))}
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(ACTIVE,a.toString()).apply()
    }
    private fun clearActive(context:Context,id:String){
        val next=readActive(context).toMutableMap().apply{remove(id)}
        saveActive(context,next)
    }
}
