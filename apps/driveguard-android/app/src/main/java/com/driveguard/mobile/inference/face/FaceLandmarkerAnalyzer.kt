package com.driveguard.mobile.inference.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.driveguard.mobile.inference.objects.RiskObjectDetector
import com.driveguard.mobile.inference.objects.RiskObjectSnapshot
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.roundToInt

class FaceLandmarkerAnalyzer(
    context: Context,
    private val onSignalsReady: (FaceSignalSnapshot) -> Unit,
    private val onObjectsReady: (RiskObjectSnapshot) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {
    private val appContext = context.applicationContext
    private val faceLandmarker: FaceLandmarker? = runCatching {
        FaceLandmarker.createFromOptions(
            appContext,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET_NAME)
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build(),
        )
    }.onFailure { error ->
        onError(error.message ?: "Face Landmarker could not be initialized.")
    }.getOrNull()
    private val riskObjectDetector: RiskObjectDetector? = runCatching {
        RiskObjectDetector(appContext)
    }.onFailure { error ->
        onError(error.message ?: "Risk object detector could not be initialized.")
    }.getOrNull()

    private var lastFaceAnalyzedAtMs = 0L
    private var lastObjectAnalyzedAtMs = 0L
    private var missingFrameStreak = 0

    override fun analyze(imageProxy: ImageProxy) {
        val faceLandmarker = faceLandmarker
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        val shouldRunFace = now - lastFaceAnalyzedAtMs >= FACE_ANALYSIS_INTERVAL_MS
        val shouldRunObjects = now - lastObjectAnalyzedAtMs >= OBJECT_ANALYSIS_INTERVAL_MS
        if (!shouldRunFace && !shouldRunObjects) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap().rotate(imageProxy.imageInfo.rotationDegrees)
            if (shouldRunFace) {
                lastFaceAnalyzedAtMs = now
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = faceLandmarker.detect(mpImage)
                onSignalsReady(result.toSnapshot())
            }
            if (shouldRunObjects) {
                lastObjectAnalyzedAtMs = now
                val objectSnapshot = riskObjectDetector?.detect(bitmap) ?: RiskObjectSnapshot(
                    statusLabel = "Risk object detector is unavailable on this device.",
                )
                onObjectsReady(objectSnapshot)
            }
        } catch (error: Exception) {
            onError(error.message ?: "Face analysis failed on the current frame.")
        } finally {
            imageProxy.close()
        }
    }

    override fun close() {
        faceLandmarker?.close()
        riskObjectDetector?.close()
    }

    private fun FaceLandmarkerResult?.toSnapshot(): FaceSignalSnapshot {
        val landmarks = this?.faceLandmarks()?.let { faces ->
            if (faces.isNotEmpty()) faces[0] else null
        }
        val blendshapes = this?.faceBlendshapes()
            ?.orElse(emptyList())
            ?.let { classifications ->
                if (classifications.isNotEmpty()) classifications[0] else emptyList()
            }
            ?: emptyList()
        if (landmarks.isNullOrEmpty()) {
            missingFrameStreak += 1
            return FaceSignalSnapshot(
                facePresent = false,
                headOrientationLabel = "Face missing",
                gazeDirectionLabel = "Unknown",
                statusLabel = if (missingFrameStreak > 3) {
                    "Driver face not detected with enough confidence."
                } else {
                    "Searching for a stable face."
                },
            )
        }

        missingFrameStreak = 0
        val eyeClosure = ((blendshapeScore(blendshapes, "eyeBlinkLeft") + blendshapeScore(blendshapes, "eyeBlinkRight")) / 2f)
            .coerceIn(0f, 1f)
        val yawn = blendshapeScore(blendshapes, "jawOpen").coerceIn(0f, 1f)
        val headTurn = estimateHeadTurn(landmarks).coerceIn(-100f, 100f)
        val headPitch = estimateHeadPitch(landmarks).coerceIn(-100f, 100f)

        val headLabel = when {
            headTurn > 24f -> "Looking right"
            headTurn < -24f -> "Looking left"
            abs(headPitch) > 28f && headPitch > 0f -> "Head down"
            abs(headPitch) > 24f && headPitch < 0f -> "Head up"
            abs(headTurn) > 10f -> "Off-center"
            else -> "Centered"
        }
        val gazeDown = headPitch > 22f
        val gazeLabel = when {
            gazeDown -> "Downward"
            headPitch < -18f -> "Upward"
            else -> "Forward"
        }
        val status = when {
            eyeClosure > 0.78f && headPitch > 22f -> "Eyes appear closed and the head is nodding down."
            eyeClosure > 0.65f -> "Eyes appear closed for this frame."
            yawn > 0.55f -> "Mouth opening suggests a yawn."
            gazeDown -> "Head pose suggests gaze is moving downward."
            abs(headTurn) > 24f -> "Head orientation is clearly off-center."
            else -> "Face landmarks look stable."
        }

        return FaceSignalSnapshot(
            facePresent = true,
            eyeClosurePercent = (eyeClosure * 100f).roundToInt(),
            yawnPercent = (yawn * 100f).roundToInt(),
            headTurnPercent = headTurn.roundToInt(),
            headPitchPercent = headPitch.roundToInt(),
            headOrientationLabel = headLabel,
            gazeDirectionLabel = gazeLabel,
            gazeDown = gazeDown,
            headNodDetected = headPitch > 22f,
            statusLabel = status,
        )
    }

    private fun blendshapeScore(
        blendshapes: List<Category>,
        key: String,
    ): Float {
        return blendshapes.firstOrNull { category ->
            category.categoryName() == key
        }?.score() ?: 0f
    }

    private fun estimateHeadTurn(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.size <= 263) {
            return 0f
        }
        val leftEyeOuterX = landmarks[33].x()
        val rightEyeOuterX = landmarks[263].x()
        val noseX = landmarks[1].x()
        val eyeCenter = (leftEyeOuterX + rightEyeOuterX) / 2f
        val eyeDistance = (rightEyeOuterX - leftEyeOuterX).coerceAtLeast(0.0001f)
        return ((noseX - eyeCenter) / eyeDistance) * 100f
    }

    private fun estimateHeadPitch(landmarks: List<NormalizedLandmark>): Float {
        if (landmarks.size <= 263) {
            return 0f
        }
        val leftEyeY = landmarks[33].y()
        val rightEyeY = landmarks[263].y()
        val noseY = landmarks[1].y()
        val mouthY = landmarks[13].y()
        val eyeCenterY = (leftEyeY + rightEyeY) / 2f
        val faceHeight = (mouthY - eyeCenterY).coerceAtLeast(0.0001f)
        val neutralRatio = 0.52f
        val ratio = (noseY - eyeCenterY) / faceHeight
        return ((ratio - neutralRatio) / 0.22f) * 100f
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, outputStream)
        val imageBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) {
            return this
        }
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    companion object {
        private const val MODEL_ASSET_NAME = "face_landmarker.task"
        private const val FACE_ANALYSIS_INTERVAL_MS = 250L
        private const val OBJECT_ANALYSIS_INTERVAL_MS = 750L
    }
}
