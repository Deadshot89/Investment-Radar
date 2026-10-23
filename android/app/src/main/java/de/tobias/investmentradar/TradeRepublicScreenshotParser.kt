package de.tobias.investmentradar

import kotlin.math.abs
import kotlin.math.roundToInt

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
        "portfolio", "investments", "investment", "cash", "seit kauf", "seit beginn",
        "performance", "depot", "depotwert", "trade republic", "positionen",
        "alle positionen", "wertpapiere", "deine investments", "investiert",
        "übersicht", "suche", "suchen", "start", "profil", "märkte", "entdecken",
        "aktien", "etfs", "sparpläne", "wertentwicklung", "heute", "gesamt",
        "rendite", "gewinn", "verlust", "details", "anzeigen", "alle"
    )

    private val portfolioMarkers = listOf(
        "trade republic", "portfolio", "depot", "seit kauf", "seit beginn",
        "investments", "investiert", "positionen", "wertpapiere"
    )
    private val euroCurrency = Regex("""(?:€|\bEUR\b)""", RegexOption.IGNORE_CASE)
    private val combinedNameAndValue = Regex(
        """^(.+?)\s+((?:\d+(?:[.,]\d+)*)(?:\s*(?:€|EUR)))\s*$""",
        RegexOption.IGNORE_CASE
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
        val ordered = joinSplitEuroAmounts(blocks.flatMap(::splitNameAndValue))
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<RecognizedTextBlock>({ it.top }, { it.left }))
        val hasPortfolioHeader = ordered.any { block ->
            val normalized = block.text.trim().lowercase()
            portfolioMarkers.any { marker -> normalized == marker || normalized.startsWith("$marker ") }
        }

        // Combine wrapped asset names before pairing them with a nearby portfolio value.
        // Otherwise, the second line of an ETF name may steal the first line's value.
        val nameGroups = mutableListOf<MutableList<RecognizedTextBlock>>()
        ordered.filter(::isAssetNameBlock).forEach { name ->
            val group = nameGroups.lastOrNull()
            val previous = group?.lastOrNull()
            val nearby = previous != null &&
                name.top - previous.bottom in -5..28 &&
                abs(name.left - previous.left) <= 70
            val amountBeforeNextName = previous != null && ordered.any { block ->
                block.top >= previous.top && block.top < name.top &&
                    (isUnsignedEuroAmount(block.text) || isSignedEuroAmount(block.text))
            }
            if (nearby && !amountBeforeNextName) {
                group!!.add(name)
            } else {
                nameGroups.add(mutableListOf(name))
            }
        }

        val observations = buildList {
            nameGroups.forEachIndexed { index, group ->
                val firstName = group.first()
                val nextTop = nameGroups.getOrNull(index + 1)?.first()?.top ?: Int.MAX_VALUE
                val rowBlocks = ordered.filter { it.top >= firstName.top && it.top < nextTop }
                // Keep only amounts spatially associated with the asset name.
                val valueBlock = rowBlocks.firstOrNull {
                    isUnsignedEuroAmount(it.text) && it.left >= firstName.left
                } ?: return@forEachIndexed
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
                        name = group.joinToString(" ") { it.text.trim() },
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

        // A scrolled screenshot may not show the portfolio header. In that case,
        // require more than a generic name/amount pair to avoid reading receipts.
        val strongRows = observations.count {
            it.profitLossEur != null || it.profitLossPct != null
        }
        val headerlessPortfolio = (observations.size >= 2 && strongRows >= 2) ||
            (observations.size == 1 &&
                observations.single().profitLossEur != null &&
                observations.single().profitLossPct != null)
        val recognized = observations.isNotEmpty() && (hasPortfolioHeader || headerlessPortfolio)
        return TradeRepublicParseResult(
            recognizedAsTradeRepublic = recognized,
            observations = if (recognized) observations else emptyList()
        )
    }

    // ML Kit sometimes recognizes the amount and its currency as two separate
    // text blocks. Pair only bare numbers with a nearby currency token on the
    // same visual line. Never infer a currency from an unmarked number.
    private fun joinSplitEuroAmounts(blocks: List<RecognizedTextBlock>): List<RecognizedTextBlock> {
        val currencyIndices = blocks.indices.filter { index ->
            val text = blocks[index].text.trim()
            text == "€" || text.equals("EUR", ignoreCase = true)
        }.toSet()
        val usedCurrencyIndices = mutableSetOf<Int>()
        val bareNumber = Regex("""^[+−–-]?\s*\d[\d.,\s]*$""")
        return buildList {
            blocks.forEachIndexed { index, amount ->
                if (index in currencyIndices) return@forEachIndexed
                if (!bareNumber.matches(amount.text.trim()) || parseLocalizedNumber(amount.text) == null) {
                    add(amount)
                    return@forEachIndexed
                }

                val currencyIndex = currencyIndices.asSequence()
                    .filter { it !in usedCurrencyIndices }
                    .map { currencyIndex ->
                        val currency = blocks[currencyIndex]
                        val centerDelta = abs(
                            (amount.top + amount.bottom) / 2 -
                                (currency.top + currency.bottom) / 2
                        )
                        val horizontalGap = when {
                            amount.right < currency.left -> currency.left - amount.right
                            currency.right < amount.left -> amount.left - currency.right
                            else -> 0
                        }
                        Triple(currencyIndex, centerDelta, horizontalGap)
                    }
                    .filter { (_, centerDelta, horizontalGap) ->
                        centerDelta <= 12 && horizontalGap <= 32
                    }
                    .minWithOrNull(compareBy<Triple<Int, Int, Int>>({ it.third }, { it.second }))
                    ?.first

                if (currencyIndex == null) {
                    add(amount)
                } else {
                    usedCurrencyIndices += currencyIndex
                    val currency = blocks[currencyIndex]
                    add(
                        amount.copy(
                            text = "${amount.text.trim()} €",
                            left = minOf(amount.left, currency.left),
                            top = minOf(amount.top, currency.top),
                            right = maxOf(amount.right, currency.right),
                            bottom = maxOf(amount.bottom, currency.bottom)
                        )
                    )
                }
            }
        }
    }

    private fun splitNameAndValue(block: RecognizedTextBlock): List<RecognizedTextBlock> {
        val match = combinedNameAndValue.matchEntire(block.text.trim()) ?: return listOf(block)
        val label = match.groupValues[1].trim()
        val amount = match.groupValues[2].trim()
        if (!label.any(Char::isLetter) || label.lowercase() in ignoredLabels) return listOf(block)
        val splitPosition = match.groups[2]?.range?.first ?: return listOf(block)
        val estimatedX = block.left + (
            (block.right - block.left).toDouble() * splitPosition / block.text.length.coerceAtLeast(1)
        ).roundToInt()
        return listOf(
            block.copy(text = label, right = estimatedX),
            block.copy(text = amount, left = estimatedX)
        )
    }

    private fun isAssetNameBlock(block: RecognizedTextBlock): Boolean {
        val text = block.text.trim()
        val lower = text.lowercase()
        if (text.isBlank() || lower in ignoredLabels) return false
        if (!text.any(Char::isLetter)) return false
        if (Regex("^\\([^)]{1,8}\\)$").matches(text)) return false
        if (euroCurrency.containsMatchIn(text) || text.contains('%') || isSharesText(text)) return false
        if (parseLocalizedNumber(text) != null && text.none(Char::isLetter)) return false
        return true
    }

    private fun isUnsignedEuroAmount(text: String): Boolean {
        if (!euroCurrency.containsMatchIn(text)) return false
        val normalized = text.trim().replace('−', '-').replace('–', '-')
        if (normalized.startsWith('+') || normalized.startsWith('-')) return false
        return parseLocalizedNumber(normalized)?.let { it.isFinite() && it >= 0.0 } == true
    }

    private fun isSignedEuroAmount(text: String): Boolean {
        if (!euroCurrency.containsMatchIn(text)) return false
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
