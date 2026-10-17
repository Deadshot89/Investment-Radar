package de.tobias.investmentradar

import android.content.Context
import android.content.SharedPreferences

object DepotImportPersistence {
    private const val PORTFOLIO_PREFS = "investment_radar_portfolio"
    private const val CUSTOM_PREFS = "investment_radar_custom_assets"

    fun commit(
        context: Context,
        result: PortfolioImportResult,
        metadata: PortfolioImportMetadata,
        customCommitEditor: (SharedPreferences.Editor) -> Boolean = { it.commit() },
        portfolioCommitEditor: (SharedPreferences.Editor) -> Boolean = { it.commit() }
    ): Boolean {
        val portfolioPrefs = context.getSharedPreferences(PORTFOLIO_PREFS, Context.MODE_PRIVATE)
        val customPrefs = context.getSharedPreferences(CUSTOM_PREFS, Context.MODE_PRIVATE)
        val portfolioBefore = snapshotPreferences(portfolioPrefs)
        val customBefore = snapshotPreferences(customPrefs)
        val customTarget = result.customAssets.ifEmpty { CustomInvestmentStore.read(context) }

        val customCommitted = runCatching {
            CustomInvestmentStore.replaceAllForImport(
                context = context,
                items = customTarget,
                commitEditor = customCommitEditor
            )
        }.getOrDefault(false)
        if (!customCommitted) {
            restoreBoth(portfolioPrefs, portfolioBefore, customPrefs, customBefore)
            return false
        }

        val portfolioCommitted = runCatching {
            PortfolioStore.commitImport(
                context = context,
                result = result,
                metadata = metadata,
                commitEditor = portfolioCommitEditor
            )
        }.getOrDefault(false)
        if (!portfolioCommitted) {
            restoreBoth(portfolioPrefs, portfolioBefore, customPrefs, customBefore)
            return false
        }
        return true
    }

    private fun restoreBoth(
        portfolioPrefs: SharedPreferences,
        portfolioBefore: Map<String, Any?>,
        customPrefs: SharedPreferences,
        customBefore: Map<String, Any?>
    ) {
        val portfolioRestored = restorePreferences(portfolioPrefs, portfolioBefore)
        val customRestored = restorePreferences(customPrefs, customBefore)
        check(portfolioRestored && customRestored) {
            "Depot-Import fehlgeschlagen und der vorherige Zustand konnte nicht vollständig wiederhergestellt werden."
        }
    }

    private fun snapshotPreferences(prefs: SharedPreferences): Map<String, Any?> =
        prefs.all.mapValues { (_, value) ->
            if (value is Set<*>) value.filterIsInstance<String>().toSet() else value
        }

    private fun restorePreferences(prefs: SharedPreferences, values: Map<String, Any?>): Boolean {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> Unit
            }
        }
        return editor.commit()
    }
}
