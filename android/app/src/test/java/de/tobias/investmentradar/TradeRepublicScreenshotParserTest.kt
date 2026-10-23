package de.tobias.investmentradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicScreenshotParserTest {
    private fun block(text: String, top: Int, left: Int = 20, right: Int = 500, bottom: Int = top + 22) =
        RecognizedTextBlock(text = text, left = left, top = top, right = right, bottom = bottom)

    @Test
    fun localizedNumbersPreserveGermanMagnitudeAndSign() {
        assertEquals(1217.07, TradeRepublicScreenshotParser.parseLocalizedNumber("1.217,07 €")!!, 0.000001)
        assertEquals(34.40, TradeRepublicScreenshotParser.parseLocalizedNumber("+34,40 €")!!, 0.000001)
        assertEquals(-3.71, TradeRepublicScreenshotParser.parseLocalizedNumber("-3,71 €")!!, 0.000001)
        assertEquals(4.53, TradeRepublicScreenshotParser.parseLocalizedNumber("+4,53 %")!!, 0.000001)
        assertEquals(-2.17, TradeRepublicScreenshotParser.parseLocalizedNumber("−2,17 %")!!, 0.000001)
        assertEquals(1234.0, TradeRepublicScreenshotParser.parseLocalizedNumber("1.234 €")!!, 0.000001)
        assertEquals(12345678.0, TradeRepublicScreenshotParser.parseLocalizedNumber("12.345.678 €")!!, 0.000001)
        assertEquals(-1500.0, TradeRepublicScreenshotParser.parseLocalizedNumber("-1.500 €")!!, 0.000001)
        assertEquals(0.125, TradeRepublicScreenshotParser.parseLocalizedNumber("0.125")!!, 0.000001)
        assertEquals(12.5, TradeRepublicScreenshotParser.parseLocalizedNumber("12.5")!!, 0.000001)
        assertNull(TradeRepublicScreenshotParser.parseLocalizedNumber("-- €"))
        assertNull(TradeRepublicScreenshotParser.parseLocalizedNumber("nicht lesbar"))
    }

    @Test
    fun parsesSingleTradeRepublicPositionAndDerivesSnapshotCostBasis() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Portfolio", 10),
                block("Seit Kauf", 40),
                block("Meta Platforms", 100),
                block("(A)", 122),
                block("794,83 €", 145, left = 600, right = 760),
                block("+34,40 €", 170, left = 600, right = 760),
                block("+4,52 %", 192, left = 600, right = 760)
            ),
            sourceIndex = 0
        )

        assertTrue(result.recognizedAsTradeRepublic)
        assertEquals(1, result.observations.size)
        val position = result.observations.single()
        assertEquals("Meta Platforms", position.name)
        assertEquals(794.83, position.valueEur!!, 0.001)
        assertEquals(34.40, position.profitLossEur!!, 0.001)
        assertEquals(4.52, position.profitLossPct!!, 0.001)
        assertEquals(760.43, position.derivedCostBasisEur!!, 0.001)
        assertEquals(0, position.sourceIndex)
    }

    @Test
    fun parsesMultipleRowsWithoutLeakingValuesAcrossGeometry() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Seit Kauf", 20),
                block("Apple", 100),
                block("68,42 €", 130, left = 610, right = 760),
                block("+7,42 €", 154, left = 610, right = 760),
                block("Samsung (GDR)", 210),
                block("59,91 €", 240, left = 610, right = 760),
                block("-3,71 €", 264, left = 610, right = 760),
                block("-5,83 %", 286, left = 610, right = 760)
            ),
            sourceIndex = 3
        )

        assertEquals(2, result.observations.size)
        val apple = result.observations.first { it.name == "Apple" }
        val samsung = result.observations.first { it.name == "Samsung (GDR)" }
        assertEquals(68.42, apple.valueEur!!, 0.001)
        assertEquals(7.42, apple.profitLossEur!!, 0.001)
        assertEquals(61.00, apple.derivedCostBasisEur!!, 0.001)
        assertEquals(59.91, samsung.valueEur!!, 0.001)
        assertEquals(-3.71, samsung.profitLossEur!!, 0.001)
        assertEquals(-5.83, samsung.profitLossPct!!, 0.001)
        assertEquals(63.62, samsung.derivedCostBasisEur!!, 0.001)
        assertEquals(3, samsung.sourceIndex)
    }

    @Test
    fun parsesExplicitSharesOnlyWhenTheyAreActuallyVisible() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Portfolio", 10),
                block("Microsoft", 100),
                block("45,87 €", 130, left = 600, right = 760),
                block("0,125 Stück", 155),
                block("+7,04 €", 180, left = 600, right = 760)
            ),
            sourceIndex = 1
        )

        assertEquals(0.125, result.observations.single().shares!!, 0.000001)
    }

    @Test
    fun clippedRowWithoutCurrentValueIsNotInvented() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Seit Kauf", 10),
                block("Apple", 100),
                block("+7,42 €", 130, left = 600, right = 760),
                block("Microsoft", 200),
                block("45,87 €", 230, left = 600, right = 760)
            ),
            sourceIndex = 0
        )

        assertTrue(result.observations.none { it.name == "Apple" })
        assertEquals(45.87, result.observations.single { it.name == "Microsoft" }.valueEur!!, 0.001)
    }

    @Test
    fun contentWithoutTradeRepublicStructureIsRejected() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Restaurant Rechnung", 20),
                block("Pizza", 100),
                block("18,50 €", 130)
            ),
            sourceIndex = 0
        )

        assertFalse(result.recognizedAsTradeRepublic)
        assertTrue(result.observations.isEmpty())
    }

    @Test
    fun acceptsScrolledPortfolioWithoutVisibleHeaderWhenRowsContainPerformance() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Apple", 100, left = 20, right = 250),
                block("68,42 €", 130, left = 610, right = 760),
                block("+7,42 €", 154, left = 610, right = 760),
                block("+12,16 %", 177, left = 610, right = 760),
                block("Samsung (GDR)", 230, left = 20, right = 250),
                block("59,91 €", 260, left = 610, right = 760),
                block("-3,71 €", 283, left = 610, right = 760),
                block("-5,83 %", 306, left = 610, right = 760)
            ),
            sourceIndex = 2
        )
        assertTrue(result.recognizedAsTradeRepublic)
        assertEquals(listOf("Apple", "Samsung (GDR)"), result.observations.map { it.name })
    }

    @Test
    fun acceptsSingleScrolledPositionOnlyWithStrongPerformanceEvidence() {
        val trusted = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Microsoft", 100, right = 300),
                block("45,87 €", 130, left = 610, right = 760),
                block("+7,04 €", 154, left = 610, right = 760),
                block("+18,13 %", 177, left = 610, right = 760)
            ),
            sourceIndex = 0
        )
        assertTrue(trusted.recognizedAsTradeRepublic)
        assertEquals("Microsoft", trusted.observations.single().name)

        val untrusted = TradeRepublicScreenshotParser.parse(
            blocks = listOf(block("Pizza", 100), block("18,50 €", 130, left = 610)),
            sourceIndex = 0
        )
        assertFalse(untrusted.recognizedAsTradeRepublic)
        assertTrue(untrusted.observations.isEmpty())
    }

    @Test
    fun recognizesInvestmentsHeaderAndEurCurrency() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Investments", 20),
                block("Alphabet", 100),
                block("1.217,07 EUR", 130, left = 610)
            ),
            sourceIndex = 0
        )
        assertTrue(result.recognizedAsTradeRepublic)
        assertEquals(1217.07, result.observations.single().valueEur!!, 0.001)
    }

    @Test
    fun keepsWrappedEtfNameTogetherInsteadOfMislabelingItsValue() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Portfolio", 10),
                block("iShares Core MSCI World", 100, left = 20, right = 340),
                block("USD (Acc)", 126, left = 20, right = 220),
                block("1.217,07 €", 160, left = 610, right = 760),
                block("+34,40 €", 183, left = 610, right = 760),
                block("Microsoft", 260, left = 20, right = 300),
                block("45,87 €", 290, left = 610, right = 760)
            ),
            sourceIndex = 0
        )
        assertTrue(result.recognizedAsTradeRepublic)
        assertEquals(2, result.observations.size)
        assertEquals("iShares Core MSCI World USD (Acc)", result.observations[0].name)
        assertEquals(1217.07, result.observations[0].valueEur!!, 0.001)
        assertEquals("Microsoft", result.observations[1].name)
        assertEquals(45.87, result.observations[1].valueEur!!, 0.001)
    }


    @Test
    fun pairsCurrencyInSeparateOcrBlocksWithoutLosingPortfolioValues() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Portfolio", 20),
                block("Apple", 100, left = 20, right = 250),
                block("68,42", 130, left = 610, right = 704),
                block("€", 132, left = 710, right = 730),
                block("+7,42", 158, left = 610, right = 704),
                block("EUR", 157, left = 711, right = 755),
                block("+12,16 %", 185, left = 610, right = 760)
            ),
            sourceIndex = 0
        )

        assertTrue(result.recognizedAsTradeRepublic)
        val apple = result.observations.single()
        assertEquals("Apple", apple.name)
        assertEquals(68.42, apple.valueEur!!, 0.001)
        assertEquals(7.42, apple.profitLossEur!!, 0.001)
        assertEquals(61.00, apple.derivedCostBasisEur!!, 0.001)
    }

    @Test
    fun detachedCurrencyFromAnotherLineDoesNotInventCurrentValue() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Portfolio", 20),
                block("Apple", 100, left = 20, right = 250),
                block("68,42", 130, left = 610, right = 700),
                block("€", 185, left = 705, right = 725),
                block("-4,00 %", 205, left = 610, right = 760)
            ),
            sourceIndex = 0
        )

        assertFalse(result.recognizedAsTradeRepublic)
        assertTrue(result.observations.isEmpty())
    }

    @Test
    fun splitsNameAndAmountWhenOcrMergesThemIntoOneLine() {
        val result = TradeRepublicScreenshotParser.parse(
            blocks = listOf(
                block("Portfolio", 10),
                block("Meta Platforms 794,83 €", 100, left = 20, right = 760),
                block("+34,40 €", 135, left = 610, right = 760),
                block("+4,52 %", 157, left = 610, right = 760)
            ),
            sourceIndex = 0
        )
        assertTrue(result.recognizedAsTradeRepublic)
        assertEquals("Meta Platforms", result.observations.single().name)
        assertEquals(794.83, result.observations.single().valueEur!!, 0.001)
    }
}
