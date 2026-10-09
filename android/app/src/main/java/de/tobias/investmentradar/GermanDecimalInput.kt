package de.tobias.investmentradar

object GermanDecimalInput {
    fun sanitize(value: String): String {
        val raw = value.filter { it.isDigit() || it == ',' || it == '.' }
        val lastComma = raw.lastIndexOf(',')
        val lastDot = raw.lastIndexOf('.')
        val decimalIndex = when {
            lastComma >= 0 && lastDot >= 0 -> maxOf(lastComma, lastDot)
            lastComma >= 0 -> lastComma
            lastDot >= 0 -> lastDot
            else -> -1
        }
        if (decimalIndex < 0) return raw.filter(Char::isDigit).take(12)

        val integerPart = raw.substring(0, decimalIndex).filter(Char::isDigit).take(12)
        val fractionalPart = raw.substring(decimalIndex + 1).filter(Char::isDigit).take(6)
        return "$integerPart,$fractionalPart".take(16)
    }

    fun parse(value: String): Double? =
        sanitize(value).replace(',', '.').toDoubleOrNull()
}
