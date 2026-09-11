package de.tobias.investmentradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object InvestmentBudgetCodec {
    fun encodeEntries(entries: List<BudgetJournalEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("type", entry.type.name)
                    .put("amountEur", entry.amountEur)
                    .put("date", entry.date)
                    .put("itemId", entry.itemId)
                    .put("source", entry.source.name)
                    .put("note", entry.note)
            )
        }
        return array.toString()
    }

    fun decodeEntries(raw: String): List<BudgetJournalEntry> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val type = runCatching { BudgetJournalType.valueOf(item.optString("type")) }.getOrNull() ?: continue
                val amount = item.optDouble("amountEur", Double.NaN)
                val date = item.optString("date").trim()
                val source = runCatching {
                    BudgetJournalSource.valueOf(item.optString("source", BudgetJournalSource.MANUAL.name))
                }.getOrDefault(BudgetJournalSource.MANUAL)
                if (id.isBlank() || !amount.isFinite() || amount < 0.0 || date.isBlank()) continue
                add(
                    BudgetJournalEntry(
                        id = id,
                        type = type,
                        amountEur = amount,
                        date = date,
                        itemId = item.optString("itemId").trim(),
                        source = source,
                        note = item.optString("note").trim()
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encodeReservations(reservations: List<BudgetReservation>): String {
        val array = JSONArray()
        reservations.forEach { reservation ->
            array.put(
                JSONObject()
                    .put("id", reservation.id)
                    .put("itemId", reservation.itemId)
                    .put("amountEur", reservation.amountEur)
                    .put("note", reservation.note)
            )
        }
        return array.toString()
    }

    fun decodeReservations(raw: String): List<BudgetReservation> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val itemId = item.optString("itemId").trim()
                val amount = item.optDouble("amountEur", Double.NaN)
                if (id.isBlank() || !amount.isFinite() || amount < 0.0) continue
                add(
                    BudgetReservation(
                        id = id,
                        itemId = itemId,
                        amountEur = amount,
                        note = item.optString("note").trim()
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

object InvestmentBudgetStore {
    private const val PREFS = "investment_radar_budget"
    private const val ENTRIES_KEY = "entries_v1"
    private const val RESERVATIONS_KEY = "reservations_v1"

    fun readEntries(context: Context): List<BudgetJournalEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ENTRIES_KEY, null) ?: return emptyList()
        return InvestmentBudgetCodec.decodeEntries(raw)
    }

    fun saveEntries(context: Context, entries: List<BudgetJournalEntry>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ENTRIES_KEY, InvestmentBudgetCodec.encodeEntries(entries))
            .apply()
        refreshRuntime(context)
    }

    fun upsertEntry(context: Context, entry: BudgetJournalEntry) {
        saveEntries(context, InvestmentBudgetJournalEngine.upsert(readEntries(context), entry))
    }

    fun readReservations(context: Context): List<BudgetReservation> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RESERVATIONS_KEY, null) ?: return emptyList()
        return InvestmentBudgetCodec.decodeReservations(raw)
    }

    fun saveReservations(context: Context, reservations: List<BudgetReservation>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(RESERVATIONS_KEY, InvestmentBudgetCodec.encodeReservations(reservations))
            .apply()
        refreshRuntime(context)
    }

    fun upsertReservation(context: Context, reservation: BudgetReservation) {
        saveReservations(
            context,
            InvestmentBudgetJournalEngine.upsertReservation(readReservations(context), reservation)
        )
    }

    fun removeReservation(context: Context, reservationId: String) {
        saveReservations(
            context,
            InvestmentBudgetJournalEngine.removeReservation(readReservations(context), reservationId)
        )
    }

    fun summary(context: Context): InvestmentBudgetSummary =
        InvestmentBudgetJournalEngine.summarize(readEntries(context), readReservations(context))

    fun refreshRuntime(context: Context) {
        InvestmentBudgetRuntime.refresh(summary(context))
    }
}
