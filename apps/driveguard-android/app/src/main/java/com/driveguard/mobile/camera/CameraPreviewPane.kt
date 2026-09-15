package com.driveguard.mobile.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val CAMERA_PREVIEW_TAG = "DriveGuardCameraPreview"

@Composable
fun CameraPreviewPane(
    modifier: Modifier = Modifier,
    controller: DriveSessionCameraController,
    preferredLensFacing: Int = CameraSelector.LENS_FACING_FRONT,
    frameAnalyzer: ImageAnalysis.Analyzer? = null,
    onCameraReady: (String) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraProviderFuture = remember(context) {
        ProcessCameraProvider.getInstance(context)
    }
    val currentOnCameraReady = rememberUpdatedState(onCameraReady)
    val currentOnCameraError = rememberUpdatedState(onCameraError)
    val boundProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val analysisExecutor = remember(frameAnalyzer) {
        frameAnalyzer?.let { Executors.newSingleThreadExecutor() }
    }

    DisposableEffect(lifecycleOwner, preferredLensFacing, frameAnalyzer) {
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val selector = resolveCameraSelector(cameraProvider, preferredLensFacing)
                cameraProvider.unbindAll()

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val videoCapture = controller.buildVideoCapture()
                val useCases = mutableListOf(preview, videoCapture)

                if (frameAnalyzer != null && analysisExecutor != null) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }
                    useCases += imageAnalysis
                }

                cameraProvider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                boundProviderState.value = cameraProvider
                currentOnCameraReady.value(cameraLabelFor(selector))
            } catch (exception: Exception) {
                Log.e(CAMERA_PREVIEW_TAG, "Unable to bind camera preview", exception)
                currentOnCameraError.value(
                    exception.message ?: "Unable to connect to the device camera.",
                )
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            boundProviderState.value?.unbindAll()
            boundProviderState.value = null
            analysisExecutor?.shutdown()
            if (frameAnalyzer is Closeable) {
                frameAnalyzer.close()
            }
            controller.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}

private fun resolveCameraSelector(
    cameraProvider: ProcessCameraProvider,
    preferredLensFacing: Int,
): CameraSelector {
    val preferredSelector = CameraSelector.Builder()
        .requireLensFacing(preferredLensFacing)
        .build()
    val fallbackLensFacing = if (preferredLensFacing == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.LENS_FACING_BACK
    } else {
        CameraSelector.LENS_FACING_FRONT
    }
    val fallbackSelector = CameraSelector.Builder()
        .requireLensFacing(fallbackLensFacing)
        .build()

    return when {
        cameraProvider.hasCamera(preferredSelector) -> preferredSelector
        cameraProvider.hasCamera(fallbackSelector) -> fallbackSelector
        else -> throw IllegalStateException("No compatible camera was found on this device.")
    }
}

private fun cameraLabelFor(cameraSelector: CameraSelector): String {
    val lensFacing = cameraSelector.lensFacing
    return when (lensFacing) {
        CameraSelector.LENS_FACING_FRONT -> "Front cabin camera"
        CameraSelector.LENS_FACING_BACK -> "Rear fallback camera"
        else -> "Camera ready"
    }
}
