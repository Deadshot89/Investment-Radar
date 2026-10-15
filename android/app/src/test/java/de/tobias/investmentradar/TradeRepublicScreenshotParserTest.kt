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
}
