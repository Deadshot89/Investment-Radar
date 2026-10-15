package de.tobias.investmentradar

// Task 5 RED contract: importer/OCR types intentionally do not exist yet.
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeRepublicScreenshotImporterTest {
    @Test
    fun `import reports ordered progress for every selected image`() = runBlocking {
        val images = listOf(
            ScreenshotImageRef("one", "content://screenshots/one"),
            ScreenshotImageRef("two", "content://screenshots/two"),
            ScreenshotImageRef("three", "content://screenshots/three")
        )
        val recognizer = FakeRecognizer(
            mapOf(
                "one" to tradeRepublicBlocks("Apple", "100,00 €"),
                "two" to tradeRepublicBlocks("Microsoft", "200,00 €"),
                "three" to tradeRepublicBlocks("Alphabet", "300,00 €")
            )
        )
        val progress = mutableListOf<Pair<Int, Int>>()

        val batch = TradeRepublicScreenshotImporter.import(
            images = images,
            recognizer = recognizer,
            onProgress = { completed, total -> progress += completed to total }
        )

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(listOf("one", "two", "three"), recognizer.calls)
        assertEquals(3, batch.images.size)
        assertTrue(batch.images.all { it.errorMessage == null })
    }

    @Test
    fun `one unreadable image does not cancel successful images`() = runBlocking {
        val images = listOf(
            ScreenshotImageRef("good-1", "content://screenshots/good-1"),
            ScreenshotImageRef("broken", "content://screenshots/broken"),
            ScreenshotImageRef("good-2", "content://screenshots/good-2")
        )
        val recognizer = FakeRecognizer(
            responses = mapOf(
                "good-1" to tradeRepublicBlocks("Apple", "101,00 €"),
                "good-2" to tradeRepublicBlocks("Meta Platforms", "202,00 €")
            ),
            failures = setOf("broken")
        )

        val batch = TradeRepublicScreenshotImporter.import(images, recognizer) { _, _ -> }

        assertEquals(3, batch.images.size)
        assertEquals(listOf("good-1", "broken", "good-2"), recognizer.calls)
        assertTrue(batch.images.first { it.image.sourceId == "good-1" }.parseResult?.recognizedAsTradeRepublic == true)
        assertTrue(batch.images.first { it.image.sourceId == "broken" }.errorMessage?.isNotBlank() == true)
        assertTrue(batch.images.first { it.image.sourceId == "good-2" }.parseResult?.recognizedAsTradeRepublic == true)
        assertEquals(2, batch.successfulImages.size)
        assertEquals(1, batch.failedImages.size)
    }

    @Test
    fun `non Trade Republic image remains a successful recognition with empty observations`() = runBlocking {
        val image = ScreenshotImageRef("other", "content://screenshots/other")
        val recognizer = FakeRecognizer(
            mapOf(
                "other" to listOf(RecognizedTextBlock("Urlaubsfoto", 0, 0, 100, 20))
            )
        )

        val batch = TradeRepublicScreenshotImporter.import(listOf(image), recognizer) { _, _ -> }
        val result = batch.images.single()

        assertEquals(null, result.errorMessage)
        assertFalse(result.parseResult?.recognizedAsTradeRepublic ?: true)
        assertTrue(result.parseResult?.observations?.isEmpty() == true)
    }

    @Test
    fun `empty selection produces empty batch and no progress callbacks`() = runBlocking {
        val recognizer = FakeRecognizer(emptyMap())
        val progress = mutableListOf<Pair<Int, Int>>()

        val batch = TradeRepublicScreenshotImporter.import(emptyList(), recognizer) { completed, total ->
            progress += completed to total
        }

        assertTrue(batch.images.isEmpty())
        assertTrue(batch.successfulImages.isEmpty())
        assertTrue(batch.failedImages.isEmpty())
        assertTrue(progress.isEmpty())
        assertTrue(recognizer.calls.isEmpty())
    }

    private class FakeRecognizer(
        private val responses: Map<String, List<RecognizedTextBlock>>,
        private val failures: Set<String> = emptySet()
    ) : LocalScreenshotTextRecognizer {
        val calls = mutableListOf<String>()

        override suspend fun recognize(image: ScreenshotImageRef): List<RecognizedTextBlock> {
            calls += image.sourceId
            if (image.sourceId in failures) error("Bild nicht lesbar")
            return responses[image.sourceId].orEmpty()
        }
    }

    private fun tradeRepublicBlocks(name: String, value: String): List<RecognizedTextBlock> = listOf(
        RecognizedTextBlock("Portfolio", 0, 0, 100, 20),
        RecognizedTextBlock(name, 0, 100, 300, 130),
        RecognizedTextBlock(value, 400, 100, 520, 130),
        RecognizedTextBlock("+1,00 €", 400, 140, 520, 170),
        RecognizedTextBlock("Seit Kauf", 0, 200, 100, 220)
    )
}
