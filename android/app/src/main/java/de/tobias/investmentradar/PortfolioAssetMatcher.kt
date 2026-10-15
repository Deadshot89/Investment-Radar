package de.tobias.investmentradar

import java.text.Normalizer
import java.util.Locale

sealed interface AssetMatch {
    data class Exact(val itemId: String) : AssetMatch
    data class Review(val candidateItemIds: List<String>) : AssetMatch
    data object Unknown : AssetMatch
}

object PortfolioAssetMatcher {
    private const val FUZZY_REVIEW_THRESHOLD = 0.80
    private const val MIN_FUZZY_LENGTH = 5

    fun match(
        observedName: String,
        builtInItems: List<InvestmentItem>,
        customItems: List<CustomInvestment>
    ): AssetMatch {
        val observed = normalize(observedName)
        if (observed.isBlank()) return AssetMatch.Unknown

        val candidates = buildList {
            builtInItems
                .filter(::isTradeRepublicCandidate)
                .forEach { item ->
                    add(
                        Candidate(
                            itemId = item.id,
                            exactAliases = listOf(item.tradeRepublicName, item.name, item.ticker),
                            fuzzyAliases = listOf(item.tradeRepublicName, item.name)
                        )
                    )
                }
            customItems
                .filter(::isTradeRepublicCandidate)
                .forEach { item ->
                    add(
                        Candidate(
                            itemId = item.id,
                            exactAliases = listOf(item.name, item.ticker),
                            fuzzyAliases = listOf(item.name)
                        )
                    )
                }
        }

        val exactIds = candidates
            .filter { candidate -> candidate.exactAliases.any { normalize(it) == observed } }
            .map { it.itemId }
            .distinct()
            .sorted()
        if (exactIds.size == 1) return AssetMatch.Exact(exactIds.single())
        if (exactIds.size > 1) return AssetMatch.Review(exactIds)

        if (observed.length < MIN_FUZZY_LENGTH) return AssetMatch.Unknown
        val fuzzy = candidates
            .mapNotNull { candidate ->
                val score = candidate.fuzzyAliases
                    .asSequence()
                    .map(::normalize)
                    .filter { it.length >= MIN_FUZZY_LENGTH }
                    .map { similarity(observed, it) }
                    .maxOrNull()
                    ?: return@mapNotNull null
                candidate.itemId.takeIf { score >= FUZZY_REVIEW_THRESHOLD }?.let { it to score }
            }
            .groupBy({ it.first }, { it.second })
            .map { (itemId, scores) -> itemId to scores.maxOrNull()!! }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })

        return if (fuzzy.isEmpty()) {
            AssetMatch.Unknown
        } else {
            AssetMatch.Review(fuzzy.map { it.first })
        }
    }

    fun isTradeRepublicCandidate(item: InvestmentItem): Boolean =
        isSupportedSecurityType(item.type) &&
            (item.tradeRepublicName.isNotBlank() || item.name.isNotBlank() || item.ticker.isNotBlank())

    fun isTradeRepublicCandidate(item: CustomInvestment): Boolean =
        isSupportedSecurityType(item.type) && (item.name.isNotBlank() || item.ticker.isNotBlank())

    private data class Candidate(
        val itemId: String,
        val exactAliases: List<String>,
        val fuzzyAliases: List<String>
    )

    private fun isSupportedSecurityType(type: String): Boolean = when (type.trim().lowercase(Locale.ROOT)) {
        "aktie", "stock", "etf" -> true
        else -> false
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / maxOf(left.length, right.length).toDouble()
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    substitution
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}
