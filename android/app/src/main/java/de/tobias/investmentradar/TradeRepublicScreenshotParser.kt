package de.tobias.investmentradar

data class RecognizedTextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class TradeRepublicScreenshotObservation(
    val name: String,
    val valueEur: Double?,
    val profitLossEur: Double?,
    val profitLossPct: Double?,
    val shares: Double?,
    val derivedCostBasisEur: Double?,
    val sourceIndex: Int
)

data class TradeRepublicParseResult(
    val recognizedAsTradeRepublic: Boolean,
    val observations: List<TradeRepublicScreenshotObservation>
)

object TradeRepublicScreenshotParser {
    private val ignoredLabels = setOf(
        "portfolio",
        "investments",
        "investment",
        "cash",
        "seit kauf",
        "seit beginn",
        "performance",
        "depot",
        "depotwert"
    )

    private val germanThousandsOnly = Regex("^[1-9]\\d{0,2}(\\.\\d{3})+$")

    fun parseLocalizedNumber(text: String): Double? {
        var raw = text
            .replace('−', '-')
            .replace('–', '-')
            .replace('\u00A0', ' ')
            .trim()
        val match = Regex("[+-]?\\s*\\d[\\d.,\\s]*").find(raw) ?: return null
        raw = match.value.replace(" ", "")
        if (raw.none(Char::isDigit)) return null

        val sign = when {
            raw.startsWith('-') -> -1.0
            else -> 1.0
        }
        val unsigned = raw.removePrefix("+").removePrefix("-")
        if (unsigned.isBlank()) return null

        val lastComma = unsigned.lastIndexOf(',')
        val lastDot = unsigned.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 -> {
                val decimal = maxOf(lastComma, lastDot)
                val integer = unsigned.substring(0, decimal).filter(Char::isDigit)
                val fraction = unsigned.substring(decimal + 1).filter(Char::isDigit)
                if (integer.isBlank() || fraction.isBlank()) return null
                "$integer.$fraction"
            }
            lastComma >= 0 -> {
                val integer = unsigned.substring(0, lastComma).filter(Char::isDigit)
                val fraction = unsigned.substring(lastComma + 1).filter(Char::isDigit)
                if (integer.isBlank() || fraction.isBlank()) return null
                "$integer.$fraction"
            }
            // German grouping without cents, e.g. "1.234 €", must not shrink by a factor of 1000.
            germanThousandsOnly.matches(unsigned) -> unsigned.filter(Char::isDigit)
            lastDot >= 0 -> {
                val integer = unsigned.substring(0, lastDot).filter(Char::isDigit)
                val fraction = unsigned.substring(lastDot + 1).filter(Char::isDigit)
                if (integer.isBlank() || fraction.isBlank()) return null
                "$integer.$fraction"
            }
            else -> unsigned.filter(Char::isDigit)
        }
        val value = normalized.toDoubleOrNull() ?: return null
        return (value * sign).takeIf(Double::isFinite)
    }

    fun parse(
        blocks: List<RecognizedTextBlock>,
        sourceIndex: Int
    ): TradeRepublicParseResult {
        val ordered = blocks
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<RecognizedTextBlock>({ it.top }, { it.left }))
        val hasTradeRepublicMarker = ordered.any { block ->
            val normalized = block.text.trim().lowercase()
            normalized == "seit kauf" || normalized == "portfolio" || normalized == "depot"
        }
        if (!hasTradeRepublicMarker) return TradeRepublicParseResult(false, emptyList())

        val nameBlocks = ordered.filter(::isAssetNameBlock)
        val observations = buildList {
            nameBlocks.forEachIndexed { index, nameBlock ->
                val nextTop = nameBlocks.getOrNull(index + 1)?.top ?: Int.MAX_VALUE
                val rowBlocks = ordered.filter { it.top >= nameBlock.top && it.top < nextTop }
                val valueBlock = rowBlocks.firstOrNull { isUnsignedEuroAmount(it.text) } ?: return@forEachIndexed
                val value = parseLocalizedNumber(valueBlock.text) ?: return@forEachIndexed
                if (!value.isFinite() || value < 0.0) return@forEachIndexed

                val pnl = rowBlocks
                    .firstOrNull { isSignedEuroAmount(it.text) }
                    ?.text
                    ?.let(::parseLocalizedNumber)
                val pnlPct = rowBlocks
                    .firstOrNull { it.text.contains('%') }
                    ?.text
                    ?.let(::parseLocalizedNumber)
                val shares = rowBlocks
                    .firstOrNull { isSharesText(it.text) }
                    ?.text
                    ?.let(::parseLocalizedNumber)
                    ?.takeIf { it.isFinite() && it > 0.0 }
                val derivedCostBasis = pnl?.let { profitLoss ->
                    (value - profitLoss).takeIf { it.isFinite() && it >= 0.0 }
                }

                add(
                    TradeRepublicScreenshotObservation(
                        name = nameBlock.text.trim(),
                        valueEur = value,
                        profitLossEur = pnl,
                        profitLossPct = pnlPct,
                        shares = shares,
                        derivedCostBasisEur = derivedCostBasis,
                        sourceIndex = sourceIndex
                    )
                )
            }
        }
        return TradeRepublicParseResult(
            recognizedAsTradeRepublic = observations.isNotEmpty(),
            observations = observations
        )
    }

    private fun isAssetNameBlock(block: RecognizedTextBlock): Boolean {
        val text = block.text.trim()
        val lower = text.lowercase()
        if (text.isBlank() || lower in ignoredLabels) return false
        if (!text.any(Char::isLetter)) return false
        if (Regex("^\\([^)]{1,8}\\)$").matches(text)) return false
        if (text.contains('€') || text.contains('%') || isSharesText(text)) return false
        if (parseLocalizedNumber(text) != null && text.none(Char::isLetter)) return false
        return true
    }

    private fun isUnsignedEuroAmount(text: String): Boolean {
        if (!text.contains('€')) return false
        val normalized = text.trim().replace('−', '-').replace('–', '-')
        if (normalized.startsWith('+') || normalized.startsWith('-')) return false
        return parseLocalizedNumber(normalized)?.let { it.isFinite() && it >= 0.0 } == true
    }

    private fun isSignedEuroAmount(text: String): Boolean {
        if (!text.contains('€')) return false
        val normalized = text.trim().replace('−', '-').replace('–', '-')
        if (!normalized.startsWith('+') && !normalized.startsWith('-')) return false
        return parseLocalizedNumber(normalized) != null
    }

    private fun isSharesText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("stück") ||
            Regex("(^|\\s)stk\\.?($|\\s)").containsMatchIn(lower) ||
            lower.contains("anteile") ||
            lower.contains("anteil ")
    }
}
