package com.driveguard.mobile.inference.objects

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RiskObjectDetector(
    context: Context,
) : Closeable {
    private val interpreter = Interpreter(
        mapModelFile(context.applicationContext, MODEL_ASSET_NAME),
        Interpreter.Options().apply {
            setNumThreads(4)
        },
    )
    private val inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputBuffer = Array(1) { Array(OUTPUT_CHANNELS) { FloatArray(OUTPUT_BOXES) } }

    fun detect(bitmap: Bitmap): RiskObjectSnapshot {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        fillInputBuffer(scaledBitmap)
        interpreter.run(inputBuffer, outputBuffer)
        val detections = decodeDetections(bitmap.width, bitmap.height)
        return RiskObjectSnapshot(
            detections = detections,
            statusLabel = when {
                detections.isEmpty() -> "No risk object detected in the current frame."
                else -> {
                    val labels = detections.joinToString { detection ->
                        "${detection.label} ${detection.confidencePercent}%"
                    }
                    "Risk objects detected: $labels"
                }
            },
        )
    }

    override fun close() {
        interpreter.close()
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        pixels.forEach { pixel ->
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((pixel and 0xFF) / 255f)
        }
        inputBuffer.rewind()
    }

    private fun decodeDetections(
        originalWidth: Int,
        originalHeight: Int,
    ): List<RiskObjectDetection> {
        val candidates = mutableListOf<RiskObjectDetection>()
        val scaleX = originalWidth / INPUT_SIZE.toFloat()
        val scaleY = originalHeight / INPUT_SIZE.toFloat()

        for (boxIndex in 0 until OUTPUT_BOXES) {
            val classScores = relevantClassScores(boxIndex)
            val bestClass = classScores.maxByOrNull { entry -> entry.value } ?: continue
            if (bestClass.value < CONFIDENCE_THRESHOLD) {
                continue
            }

            val centerX = outputBuffer[0][0][boxIndex]
            val centerY = outputBuffer[0][1][boxIndex]
            val width = outputBuffer[0][2][boxIndex]
            val height = outputBuffer[0][3][boxIndex]
            val left = ((centerX - width / 2f) * scaleX).coerceIn(0f, originalWidth.toFloat())
            val top = ((centerY - height / 2f) * scaleY).coerceIn(0f, originalHeight.toFloat())
            val right = ((centerX + width / 2f) * scaleX).coerceIn(0f, originalWidth.toFloat())
            val bottom = ((centerY + height / 2f) * scaleY).coerceIn(0f, originalHeight.toFloat())

            val (category, label) = classIdToCategory(bestClass.key)
            candidates += RiskObjectDetection(
                category = category,
                label = label,
                confidencePercent = (bestClass.value * 100f).roundToInt(),
                left = left,
                top = top,
                right = right,
                bottom = bottom,
            )
        }

        return nonMaximumSuppression(candidates)
    }

    private fun relevantClassScores(boxIndex: Int): Map<Int, Float> {
        return mapOf(
            COCO_CLASS_BOTTLE to outputBuffer[0][4 + COCO_CLASS_BOTTLE][boxIndex],
            COCO_CLASS_CUP to outputBuffer[0][4 + COCO_CLASS_CUP][boxIndex],
            COCO_CLASS_CELL_PHONE to outputBuffer[0][4 + COCO_CLASS_CELL_PHONE][boxIndex],
        )
    }

    private fun classIdToCategory(classId: Int): Pair<RiskObjectCategory, String> {
        return when (classId) {
            COCO_CLASS_CELL_PHONE -> RiskObjectCategory.PHONE to "Phone"
            COCO_CLASS_BOTTLE -> RiskObjectCategory.DRINK to "Bottle"
            COCO_CLASS_CUP -> RiskObjectCategory.DRINK to "Cup"
            else -> RiskObjectCategory.DRINK to "Object"
        }
    }

    private fun nonMaximumSuppression(
        detections: List<RiskObjectDetection>,
    ): List<RiskObjectDetection> {
        val selected = mutableListOf<RiskObjectDetection>()
        val sorted = detections.sortedByDescending { detection -> detection.confidencePercent }

        sorted.forEach { candidate ->
            val overlaps = selected.any { kept ->
                kept.category == candidate.category && intersectionOverUnion(kept, candidate) > IOU_THRESHOLD
            }
            if (!overlaps) {
                selected += candidate
            }
        }
        return selected.take(MAX_DETECTIONS)
    }

    private fun intersectionOverUnion(
        first: RiskObjectDetection,
        second: RiskObjectDetection,
    ): Float {
        val interLeft = max(first.left, second.left)
        val interTop = max(first.top, second.top)
        val interRight = min(first.right, second.right)
        val interBottom = min(first.bottom, second.bottom)
        if (interRight <= interLeft || interBottom <= interTop) {
            return 0f
        }
        val intersection = (interRight - interLeft) * (interBottom - interTop)
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        return intersection / (firstArea + secondArea - intersection).coerceAtLeast(1f)
    }

    companion object {
        private const val MODEL_ASSET_NAME = "yolov8n_float16.tflite"
        private const val INPUT_SIZE = 320
        private const val OUTPUT_CHANNELS = 84
        private const val OUTPUT_BOXES = 2100
        private const val CONFIDENCE_THRESHOLD = 0.35f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 6

        private const val COCO_CLASS_BOTTLE = 39
        private const val COCO_CLASS_CUP = 41
        private const val COCO_CLASS_CELL_PHONE = 67

        private fun mapModelFile(
            context: Context,
            assetName: String,
        ): ByteBuffer {
            context.assets.openFd(assetName).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).channel.use { fileChannel ->
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fileDescriptor.startOffset,
                        fileDescriptor.declaredLength,
                    )
                }
            }
        }
    }
}
