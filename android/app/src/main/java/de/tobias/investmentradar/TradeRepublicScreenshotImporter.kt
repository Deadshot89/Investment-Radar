package de.tobias.investmentradar

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ScreenshotImageRef(
    val sourceId: String,
    val uriString: String
)

interface LocalScreenshotTextRecognizer {
    suspend fun recognize(image: ScreenshotImageRef): List<RecognizedTextBlock>
}

data class ScreenshotImportImageResult(
    val image: ScreenshotImageRef,
    val parseResult: TradeRepublicParseResult? = null,
    val errorMessage: String? = null
)

data class ScreenshotImportBatch(
    val images: List<ScreenshotImportImageResult>
) {
    val successfulImages: List<ScreenshotImportImageResult>
        get() = images.filter { it.errorMessage == null && it.parseResult != null }

    val failedImages: List<ScreenshotImportImageResult>
        get() = images.filter { it.errorMessage != null }

    val observations: List<TradeRepublicScreenshotObservation>
        get() = successfulImages.flatMap { it.parseResult?.observations.orEmpty() }
}

object TradeRepublicScreenshotImporter {
    suspend fun import(
        images: List<ScreenshotImageRef>,
        recognizer: LocalScreenshotTextRecognizer,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): ScreenshotImportBatch {
        if (images.isEmpty()) return ScreenshotImportBatch(emptyList())

        val total = images.size
        val results = images.mapIndexed { index, image ->
            val result = try {
                val blocks = recognizer.recognize(image)
                ScreenshotImportImageResult(
                    image = image,
                    parseResult = TradeRepublicScreenshotParser.parse(
                        blocks = blocks,
                        sourceIndex = index
                    )
                )
            } catch (error: Exception) {
                ScreenshotImportImageResult(
                    image = image,
                    errorMessage = error.message?.takeIf { it.isNotBlank() }
                        ?: "Screenshot konnte nicht gelesen werden"
                )
            }
            onProgress(index + 1, total)
            result
        }
        return ScreenshotImportBatch(results)
    }
}

class MlKitLocalScreenshotTextRecognizer(
    context: Context
) : LocalScreenshotTextRecognizer {
    private val appContext = context.applicationContext
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(image: ScreenshotImageRef): List<RecognizedTextBlock> {
        val inputImage = InputImage.fromFilePath(appContext, Uri.parse(image.uriString))
        return suspendCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { recognized ->
                    val blocks = recognized.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { line ->
                            val box = line.boundingBox ?: return@mapNotNull null
                            RecognizedTextBlock(
                                text = line.text,
                                left = box.left,
                                top = box.top,
                                right = box.right,
                                bottom = box.bottom
                            )
                        }
                    continuation.resume(blocks)
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }
    }
}
