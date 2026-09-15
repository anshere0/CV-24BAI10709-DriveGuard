package com.driveguard.mobile.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.driveguard.mobile.data.local.LocalCaptureRepository
import com.driveguard.mobile.data.remote.DriveGuardBackendClient
import com.driveguard.mobile.data.remote.syncEdgeContextForClip
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class PendingClipUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val repository = LocalCaptureRepository.getInstance(appContext)
    private val backendClient = DriveGuardBackendClient()

    override suspend fun doWork(): Result {
        val pendingClips = repository.listPendingClips()
        if (pendingClips.isEmpty()) {
            return Result.success()
        }

        pendingClips.forEach { clip ->
            val file = File(clip.filePath)
            if (!file.exists()) {
                repository.markClipFailed(
                    clipId = clip.id,
                    backendMessage = "Local clip file is missing. It cannot be uploaded anymore.",
                )
                return@forEach
            }

            try {
                repository.markClipUploading(clip.id)
                val uploadedVideo = backendClient.uploadVideo(
                    baseUrl = clip.backendUrl,
                    clipFile = file,
                    sourceOrigin = clip.sourceOrigin,
                    onProgress = {},
                )
                repository.markClipUploaded(
                    clipId = clip.id,
                    uploadedVideoId = uploadedVideo.id,
                )
                syncEdgeContextForClip(
                    appContext = applicationContext,
                    backendClient = backendClient,
                    localCaptureRepository = repository,
                    clip = clip,
                    uploadedVideoId = uploadedVideo.id,
                )

                val job = backendClient.createAnalysisJob(
                    baseUrl = clip.backendUrl,
                    uploadedVideoId = uploadedVideo.id,
                )
                syncEdgeContextForClip(
                    appContext = applicationContext,
                    backendClient = backendClient,
                    localCaptureRepository = repository,
                    clip = clip,
                    uploadedVideoId = uploadedVideo.id,
                    analysisJobId = job.id,
                )
                repository.markJobCreated(
                    clipId = clip.id,
                    backendJobId = job.id,
                    backendMessage = "Backend job created automatically after network recovery.",
                )
            } catch (exception: IOException) {
                repository.markClipFailed(
                    clipId = clip.id,
                    backendMessage = exception.message ?: "Upload will retry when network becomes available again.",
                )
                return Result.retry()
            } catch (exception: Exception) {
                repository.markClipFailed(
                    clipId = clip.id,
                    backendMessage = exception.message ?: "Automatic upload failed.",
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "driveguard-pending-clip-upload"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<PendingClipUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
