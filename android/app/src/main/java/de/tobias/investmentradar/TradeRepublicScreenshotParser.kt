package de.tobias.investmentradar

import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.Normalizer
import java.util.Locale

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
    val sourceIndex: Int,
    val requiresConfirmation: Boolean = false
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
        "rendite", "gewinn", "verlust", "details", "anzeigen", "alle",
        "kurs", "preis", "einstand", "buy in", "buy-in", "marktwert",
        "gesamtwert", "aktuell", "tagesgewinn", "summe"
    )

    private val portfolioMarkers = listOf(
        "trade republic", "portfolio", "depot", "seit kauf", "seit beginn",
        "investments", "investiert", "positionen", "wertpapiere"
    )
    private val euroCurrency = Regex("""(?:€|\bEUR\b)""", RegexOption.IGNORE_CASE)
    // OCR can put the name, current value and P/L on the same line. Split each
    // explicit currency/percent token instead of dropping the entire name row.
    private val monetaryToken = Regex("""[+−–-]?\s*\d[\d.,]*\s*(?:€|\bEUR\b)""", RegexOption.IGNORE_CASE)
    private val percentToken = Regex("""[+−–-]?\s*\d[\d.,]*\s*%""")

    private val germanThousandsOnly = Regex("^[1-9]\\d{0,2}(\\.\\d{3})+$")

    // ML Kit preserves several Unicode spacing characters used as thousands
    // separators. Normalize them before token detection, so 1 217,07 € can
    // never silently become 1 € or a rejected asset row.
    private fun normalizeOcrWhitespace(text: String): String =
        text.replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u2009', ' ')
            .replace('\u2007', ' ')
            .replace('\u200A', ' ')

    fun parseLocalizedNumber(text: String): Double? {
        var raw = normalizeOcrWhitespace(text)
            .replace('−', '-')
            .replace('–', '-')
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
        sourceIndex: Int,
        trustedAssetNames: Set<String> = emptySet()
    ): TradeRepublicParseResult {
        val ordered = joinSplitEuroAmounts(
            blocks.map { it.copy(text = normalizeOcrWhitespace(it.text)) }
                .flatMap(::splitNameAndValue)
        )
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<RecognizedTextBlock>({ it.top }, { it.left }))
        val hasPortfolioHeader = ordered.any { block ->
            val normalized = block.text.trim().lowercase()
            portfolioMarkers.any { marker ->
                normalized == marker || normalized.startsWith("$marker ") ||
                    (normalized.length <= 42 && normalized.endsWith(" $marker"))
            }
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
        // Scrolled portfolio screenshots can contain a single known holding and
        // only its EUR value. They have no title or P/L; rejecting them created
        // the user-visible '0 detected stocks' failure. Trust names from the
        // local asset catalog, never an arbitrary name/amount pair from a receipt.
        val trusted = observations.filter { observation ->
            matchesTrustedName(observation.name, trustedAssetNames)
        }
        val recognized = observations.isNotEmpty() &&
            (hasPortfolioHeader || headerlessPortfolio || trusted.isNotEmpty())
        val accepted = when {
            !recognized -> emptyList()
            hasPortfolioHeader || headerlessPortfolio -> observations
            // Headerless fallback uses only trusted rows and requires explicit
            // confirmation so an accidental receipt match cannot update a holding.
            else -> trusted.map { it.copy(requiresConfirmation = true) }
        }
        return TradeRepublicParseResult(
            recognizedAsTradeRepublic = recognized,
            observations = accepted
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
        val line = block.text.trim()
        val tokens = (monetaryToken.findAll(line).toList() +
            percentToken.findAll(line).toList())
            .sortedBy { it.range.first }
        if (tokens.isEmpty()) return listOf(block)
        if (tokens.zipWithNext().any { (first, second) ->
                first.range.last >= second.range.first
            }) return listOf(block)

        val trailing = line.substring(tokens.last().range.last + 1).trim()
        if (trailing.isNotEmpty()) return listOf(block)
        val prefix = line.substring(0, tokens.first().range.first).trim()
        if (prefix.isNotEmpty() && (
                !prefix.any(Char::isLetter) ||
                prefix.lowercase() in ignoredLabels ||
                isSharesText(prefix)
            )) return listOf(block)

        val spans = buildList {
            if (prefix.isNotBlank()) {
                add(0 to tokens.first().range.first)
            }
            tokens.forEach { add(it.range.first to (it.range.last + 1)) }
        }
        if (spans.size <= 1) return listOf(block)
        val width = (block.right - block.left).coerceAtLeast(0)
        return spans.mapNotNull { (from, until) ->
            val value = line.substring(from, until).trim()
            if (value.isEmpty()) null else {
                block.copy(
                    text = value,
                    left = block.left + (width.toDouble() * from / line.length).roundToInt(),
                    right = block.left + (width.toDouble() * until / line.length).roundToInt()
                )
            }
        }
    }

    private fun matchesTrustedName(name: String, trustedNames: Set<String>): Boolean {
        val normalized = normalizeName(name)
        if (normalized.length < 5) return false
        return trustedNames.any { candidate ->
            val known = normalizeName(candidate)
            normalized == known ||
                (minOf(normalized.length, known.length) >= 12 &&
                    (normalized.startsWith(known) || known.startsWith(normalized)))
        }
    }

    private fun normalizeName(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "")

    private fun isAssetNameBlock(block: RecognizedTextBlock): Boolean {
        val text = block.text.trim()
        val lower = text.lowercase()
        if (text.isBlank() || lower in ignoredLabels) return false
        if (!text.any(Char::isLetter)) return false
        if (Regex("^\\([^)]{1,8}\\)$").matches(text)) {
            return Regex("""^\((?:A|B|ADR|GDR|Acc|Dist)\)$""", RegexOption.IGNORE_CASE).matches(text)
        }
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
