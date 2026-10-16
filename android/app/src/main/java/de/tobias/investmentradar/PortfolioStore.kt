package de.tobias.investmentradar

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object PortfolioStore {
    private const val PREFS = "investment_radar_portfolio"
    private const val LEGACY_KEY = "holding_ids"
    private const val POSITION_IDS_KEY = "position_ids"
    private const val IMPORT_HISTORY_KEY = "import_history_v1"
    private const val MAX_IMPORT_HISTORY = 50

    fun read(context: Context): Set<String> = readPositions(context).keys

    fun readPositions(context: Context): Map<String, PortfolioPosition> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedIds = buildSet {
            addAll(prefs.getStringSet(LEGACY_KEY, emptySet()).orEmpty())
            addAll(prefs.getStringSet(POSITION_IDS_KEY, emptySet()).orEmpty())
        }
        val ids = storedIds.filterNot { it == LegacyPortfolioCleanup.RETIRED_IBOND_ID }.toSet()
        if (ids.size != storedIds.size) {
            val retiredId = LegacyPortfolioCleanup.RETIRED_IBOND_ID
            prefs.edit()
                .putStringSet(LEGACY_KEY, ids)
                .putStringSet(POSITION_IDS_KEY, ids)
                .remove(investedKey(retiredId))
                .remove(sharesKey(retiredId))
                .remove(snapshotKey(retiredId))
                .remove(snapshotCostBasisKey(retiredId))
                .remove(trackedSharesKey(retiredId))
                .remove(purchasesKey(retiredId))
                .remove(salesKey(retiredId))
                .remove(lifecycleStatusKey(retiredId))
                .remove(snapshotCapturedAtKey(retiredId))
                .apply()
        }
        return ids.associateWith { itemId ->
            val snapshotValue = prefs.getString(snapshotKey(itemId), null)
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val snapshotCostBasis = prefs.getString(snapshotCostBasisKey(itemId), null)
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val trackedShares = prefs.getString(trackedSharesKey(itemId), null)
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
            val lifecycleStatus = prefs.getString(lifecycleStatusKey(itemId), null)
                ?.let { stored -> runCatching { PortfolioLifecycleStatus.valueOf(stored) }.getOrNull() }
                ?: PortfolioLifecycleStatus.ACTIVE
            val snapshotCapturedAt = prefs.getString(snapshotCapturedAtKey(itemId), null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val storedPurchases = prefs.getString(purchasesKey(itemId), null)
                ?.let(::decodePurchases)
                .orEmpty()
            val storedSales = prefs.getString(salesKey(itemId), null)
                ?.let(::decodeSales)
                .orEmpty()
            if (storedPurchases.isNotEmpty() || storedSales.isNotEmpty()) {
                PortfolioPosition(
                    itemId = itemId,
                    snapshotValueEur = snapshotValue,
                    snapshotCostBasisEur = snapshotCostBasis,
                    trackedShares = trackedShares,
                    purchases = storedPurchases,
                    sales = storedSales,
                    lifecycleStatus = lifecycleStatus,
                    snapshotCapturedAt = snapshotCapturedAt
                )
            } else {
                val invested = prefs.getString(investedKey(itemId), null)?.toDoubleOrNull() ?: 0.0
                val shares = prefs.getString(sharesKey(itemId), null)?.toDoubleOrNull() ?: 0.0
                val migrated = if (invested > 0.0 && shares > 0.0) {
                    listOf(
                        PortfolioPurchase(
                            id = "legacy-$itemId",
                            date = "",
                            investedAmount = invested,
                            shares = shares
                        )
                    )
                } else {
                    emptyList()
                }
                PortfolioPosition(
                    itemId = itemId,
                    snapshotValueEur = snapshotValue,
                    snapshotCostBasisEur = snapshotCostBasis,
                    trackedShares = trackedShares,
                    purchases = migrated,
                    lifecycleStatus = lifecycleStatus,
                    snapshotCapturedAt = snapshotCapturedAt
                )
            }
        }
    }

    fun add(context: Context, itemId: String) {
        if (itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return
        val existing = readPositions(context)[itemId] ?: PortfolioPosition(itemId)
        save(context, existing)
    }

    fun save(context: Context, position: PortfolioPosition) {
        if (position.itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = readPositions(context).keys.toMutableSet().apply { add(position.itemId) }
        val editor = prefs.edit()
            .putStringSet(LEGACY_KEY, ids)
            .putStringSet(POSITION_IDS_KEY, ids)
        writePosition(editor, position)
        editor.apply()
    }

    fun remove(context: Context, itemId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = readPositions(context).keys.toMutableSet().apply { remove(itemId) }
        prefs.edit()
            .putStringSet(LEGACY_KEY, ids)
            .putStringSet(POSITION_IDS_KEY, ids)
            .remove(investedKey(itemId))
            .remove(sharesKey(itemId))
            .remove(snapshotKey(itemId))
            .remove(snapshotCostBasisKey(itemId))
            .remove(trackedSharesKey(itemId))
            .remove(purchasesKey(itemId))
            .remove(salesKey(itemId))
            .remove(lifecycleStatusKey(itemId))
            .remove(snapshotCapturedAtKey(itemId))
            .apply()
    }

    fun commitImport(
        context: Context,
        result: PortfolioImportResult,
        metadata: PortfolioImportMetadata,
        commitEditor: (SharedPreferences.Editor) -> Boolean = { it.commit() }
    ): Boolean {
        require(metadata.importId.isNotBlank()) { "Import-ID fehlt." }
        require(metadata.capturedAt.isNotBlank()) { "Import-Zeitpunkt fehlt." }
        require(metadata.screenshotCount > 0) { "Mindestens ein Screenshot ist erforderlich." }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = snapshotPreferences(prefs)
        val previousHistory = decodeImportHistory(previous[IMPORT_HISTORY_KEY] as? String)
        val nextHistory = (previousHistory + metadata).takeLast(MAX_IMPORT_HISTORY)

        val editor = prefs.edit().clear()
            .putStringSet(LEGACY_KEY, result.positions.keys)
            .putStringSet(POSITION_IDS_KEY, result.positions.keys)
            .putString(IMPORT_HISTORY_KEY, encodeImportHistory(nextHistory))
        result.positions.values.sortedBy { it.itemId }.forEach { position ->
            writePosition(editor, position)
        }

        val committed = runCatching { commitEditor(editor) }.getOrDefault(false)
        if (committed) return true

        check(restorePreferences(prefs, previous)) {
            "Depot-Import fehlgeschlagen und der vorherige Zustand konnte nicht wiederhergestellt werden."
        }
        return false
    }

    private fun writePosition(editor: SharedPreferences.Editor, position: PortfolioPosition) {
        editor
            .putString(investedKey(position.itemId), position.investedAmount.toString())
            .putString(sharesKey(position.itemId), position.shares.toString())
            .putString(purchasesKey(position.itemId), encodePurchases(position.purchases))
            .putString(salesKey(position.itemId), encodeSales(position.sales))
            .putString(lifecycleStatusKey(position.itemId), position.lifecycleStatus.name)
        position.snapshotValueEur?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            editor.putString(snapshotKey(position.itemId), it.toString())
        } ?: editor.remove(snapshotKey(position.itemId))
        position.snapshotCostBasisEur?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            editor.putString(snapshotCostBasisKey(position.itemId), it.toString())
        } ?: editor.remove(snapshotCostBasisKey(position.itemId))
        position.trackedShares?.takeIf { it.isFinite() && it > 0.0 }?.let {
            editor.putString(trackedSharesKey(position.itemId), it.toString())
        } ?: editor.remove(trackedSharesKey(position.itemId))
        position.snapshotCapturedAt?.trim()?.takeIf { it.isNotBlank() }?.let {
            editor.putString(snapshotCapturedAtKey(position.itemId), it)
        } ?: editor.remove(snapshotCapturedAtKey(position.itemId))
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

    private fun encodeImportHistory(history: List<PortfolioImportMetadata>): String {
        val array = JSONArray()
        history.forEach { metadata ->
            array.put(
                JSONObject()
                    .put("importId", metadata.importId)
                    .put("capturedAt", metadata.capturedAt)
                    .put("screenshotCount", metadata.screenshotCount)
                    .put("updatedCount", metadata.updatedCount)
                    .put("addedCount", metadata.addedCount)
                    .put("soldCount", metadata.soldCount)
                    .put("deletedCount", metadata.deletedCount)
            )
        }
        return array.toString()
    }

    private fun decodeImportHistory(raw: String?): List<PortfolioImportMetadata> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val importId = item.optString("importId").trim()
                val capturedAt = item.optString("capturedAt").trim()
                val screenshotCount = item.optInt("screenshotCount", 0)
                if (importId.isBlank() || capturedAt.isBlank() || screenshotCount <= 0) continue
                add(
                    PortfolioImportMetadata(
                        importId = importId,
                        capturedAt = capturedAt,
                        screenshotCount = screenshotCount,
                        updatedCount = item.optInt("updatedCount", 0).coerceAtLeast(0),
                        addedCount = item.optInt("addedCount", 0).coerceAtLeast(0),
                        soldCount = item.optInt("soldCount", 0).coerceAtLeast(0),
                        deletedCount = item.optInt("deletedCount", 0).coerceAtLeast(0)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodePurchases(purchases: List<PortfolioPurchase>): String {
        val array = JSONArray()
        purchases.forEach { purchase ->
            array.put(
                JSONObject()
                    .put("id", purchase.id)
                    .put("date", purchase.date)
                    .put("investedAmount", purchase.investedAmount.coerceAtLeast(0.0))
                    .put("shares", purchase.shares.coerceAtLeast(0.0))
            )
        }
        return array.toString()
    }

    private fun decodePurchases(raw: String): List<PortfolioPurchase> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val amount = item.optDouble("investedAmount", Double.NaN)
                val shares = item.optDouble("shares", Double.NaN)
                if (id.isBlank() || !amount.isFinite() || !shares.isFinite() || amount < 0.0 || shares < 0.0) continue
                add(
                    PortfolioPurchase(
                        id = id,
                        date = item.optString("date").trim(),
                        investedAmount = amount,
                        shares = shares
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeSales(sales: List<PortfolioSale>): String {
        val array = JSONArray()
        sales.forEach { sale ->
            array.put(
                JSONObject()
                    .put("id", sale.id)
                    .put("date", sale.date)
                    .put("proceeds", sale.proceeds.coerceAtLeast(0.0))
                    .put("shares", sale.shares.coerceAtLeast(0.0))
            )
        }
        return array.toString()
    }

    private fun decodeSales(raw: String): List<PortfolioSale> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val proceeds = item.optDouble("proceeds", Double.NaN)
                val shares = item.optDouble("shares", Double.NaN)
                if (id.isBlank() || !proceeds.isFinite() || !shares.isFinite() || proceeds < 0.0 || shares <= 0.0) continue
                add(
                    PortfolioSale(
                        id = id,
                        date = item.optString("date").trim(),
                        proceeds = proceeds,
                        shares = shares
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun investedKey(itemId: String) = "position.$itemId.invested"
    private fun sharesKey(itemId: String) = "position.$itemId.shares"
    private fun snapshotKey(itemId: String) = "position.$itemId.snapshotValueEur"
    private fun snapshotCostBasisKey(itemId: String) = "position.$itemId.snapshotCostBasisEur"
    private fun trackedSharesKey(itemId: String) = "position.$itemId.trackedShares"
    private fun purchasesKey(itemId: String) = "position.$itemId.purchases"
    private fun salesKey(itemId: String) = "position.$itemId.sales"
    private fun lifecycleStatusKey(itemId: String) = "position.$itemId.lifecycleStatus"
    private fun snapshotCapturedAtKey(itemId: String) = "position.$itemId.snapshotCapturedAt"
}
