package de.tobias.investmentradar

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object InvestmentBudgetDate {
    private val german = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(value: String): LocalDate? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        return runCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(raw, german) }.getOrNull()
    }

    fun monthKey(value: String): String? =
        parse(value)?.let { YearMonth.from(it).toString() }

    fun monthKey(date: LocalDate): String = YearMonth.from(date).toString()

    fun monthLabel(monthKey: String): String {
        val month = runCatching { YearMonth.parse(monthKey) }.getOrNull() ?: return monthKey
        return "%02d/%04d".format(month.monthValue, month.year)
    }
}
