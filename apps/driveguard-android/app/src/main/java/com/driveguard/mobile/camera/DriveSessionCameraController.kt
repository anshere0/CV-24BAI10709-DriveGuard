package com.driveguard.mobile.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RECORDING_TAG = "DriveGuardRecording"

@Stable
class DriveSessionCameraController {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    fun buildVideoCapture(): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.FHD,
                    androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()

        return VideoCapture.withOutput(recorder).also {
            videoCapture = it
        }
    }

    fun isRecording(): Boolean = activeRecording != null

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        audioEnabled: Boolean,
        outputFile: File? = null,
        onStateChanged: (LocalRecordingState) -> Unit,
    ) {
        val capture = videoCapture
        if (capture == null) {
            onStateChanged(LocalRecordingState.Error("Camera capture is not armed yet."))
            return
        }

        if (activeRecording != null) {
            onStateChanged(LocalRecordingState.Error("A recording is already in progress."))
            return
        }

        val outputDirectory = File(context.cacheDir, "driveguard-clips").apply { mkdirs() }
        val clipFile = outputFile ?: File(outputDirectory, buildClipFileName())
        val fileOutputOptions = FileOutputOptions.Builder(clipFile).build()

        val pendingRecording = capture.output
            .prepareRecording(context, fileOutputOptions)
            .applyAudioIfEnabled(audioEnabled)

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    onStateChanged(LocalRecordingState.Recording(clipFile))
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (event.hasError()) {
                        Log.e(
                            RECORDING_TAG,
                            "Recording finalize error: ${event.error}",
                        )
                        onStateChanged(
                            LocalRecordingState.Error(
                                event.cause?.message
                                    ?: "Recording failed with error code ${event.error}.",
                            ),
                        )
                    } else {
                        onStateChanged(
                            LocalRecordingState.Finalized(
                                file = clipFile,
                                bytes = clipFile.length(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    fun release() {
        activeRecording?.close()
        activeRecording = null
        videoCapture = null
    }
}

private fun PendingRecording.applyAudioIfEnabled(audioEnabled: Boolean): PendingRecording {
    return if (audioEnabled) withAudioEnabled() else this
}

private fun buildClipFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "driveguard_clip_$stamp.mp4"
}

@Immutable
sealed interface LocalRecordingState {
    data object Ready : LocalRecordingState
    data class Recording(val file: File) : LocalRecordingState
    data class Finalized(val file: File, val bytes: Long) : LocalRecordingState
    data class Error(val message: String) : LocalRecordingState
}
