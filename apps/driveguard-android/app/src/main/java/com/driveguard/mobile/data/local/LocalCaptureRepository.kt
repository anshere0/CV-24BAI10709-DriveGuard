package com.driveguard.mobile.data.local

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class LocalCaptureRepository(
    private val dao: LocalCaptureDao,
) {
    val recentSessions: Flow<List<LocalDriveSessionEntity>> = dao.observeRecentSessions()
    val recentClips: Flow<List<LocalClipRecordEntity>> = dao.observeRecentClips()
    val recentRiskEvents: Flow<List<LocalRiskEventEntity>> = dao.observeRecentRiskEvents()

    suspend fun listPendingClips(limit: Int = 10): List<LocalClipRecordEntity> = dao.listPendingClips(limit)
    suspend fun getClipById(clipId: Long): LocalClipRecordEntity? = dao.getClipById(clipId)
    suspend fun getRiskEventById(eventId: String): LocalRiskEventEntity? = dao.getRiskEventById(eventId)
    suspend fun getSessionById(sessionId: String): LocalDriveSessionEntity? = dao.getSessionById(sessionId)

    suspend fun startSession(backendUrl: String): String {
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        dao.insertSession(
            LocalDriveSessionEntity(
                id = sessionId,
                backendUrl = backendUrl,
                status = "active",
                startedAtEpochMs = now,
                endedAtEpochMs = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        return sessionId
    }

    suspend fun stopSession(sessionId: String) {
        val now = System.currentTimeMillis()
        dao.finishSession(
            sessionId = sessionId,
            status = "stopped",
            endedAtEpochMs = now,
            updatedAtEpochMs = now,
        )
    }

    suspend fun saveFinalizedClip(
        sessionId: String?,
        backendUrl: String,
        clipFile: File,
        sizeBytes: Long,
        sourceOrigin: String = "android_upload",
        triggerEventId: String? = null,
        triggerEventType: String? = null,
        priorityScore: Int = 0,
        captureStrategy: String = "manual_capture",
        captureStartedAtEpochMs: Long? = null,
        captureEndedAtEpochMs: Long? = null,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertClip(
            LocalClipRecordEntity(
                sessionId = sessionId,
                backendUrl = backendUrl,
                filePath = clipFile.absolutePath,
                fileName = clipFile.name,
                sizeBytes = sizeBytes,
                localState = "finalized",
                uploadState = "pending",
                sourceOrigin = sourceOrigin,
                triggerEventId = triggerEventId,
                triggerEventType = triggerEventType,
                priorityScore = priorityScore,
                captureStrategy = captureStrategy,
                captureStartedAtEpochMs = captureStartedAtEpochMs,
                captureEndedAtEpochMs = captureEndedAtEpochMs,
                uploadedVideoId = null,
                backendJobId = null,
                backendSessionId = null,
                backendMessage = "Clip stored locally and waiting for upload.",
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    suspend fun markClipUploading(clipId: Long) {
        updateClipState(
            clipId = clipId,
            uploadState = "uploading",
            backendMessage = "Uploading clip to backend.",
        )
    }

    suspend fun markClipUploaded(
        clipId: Long,
        uploadedVideoId: String,
    ) {
        updateClipState(
            clipId = clipId,
            uploadState = "sent",
            uploadedVideoId = uploadedVideoId,
            backendMessage = "Clip uploaded. Analysis job is being created.",
        )
    }

    suspend fun markJobCreated(
        clipId: Long,
        backendJobId: String,
        backendMessage: String?,
    ) {
        updateClipState(
            clipId = clipId,
            uploadState = "sent",
            backendJobId = backendJobId,
            backendMessage = backendMessage ?: "Backend job created.",
        )
    }

    suspend fun markJobProgress(
        clipId: Long,
        uploadState: String,
        backendJobId: String?,
        backendSessionId: String?,
        backendMessage: String?,
    ) {
        updateClipState(
            clipId = clipId,
            uploadState = uploadState,
            backendJobId = backendJobId,
            backendSessionId = backendSessionId,
            backendMessage = backendMessage,
        )
    }

    suspend fun markClipFailed(
        clipId: Long,
        backendMessage: String,
    ) {
        updateClipState(
            clipId = clipId,
            uploadState = "failed",
            backendMessage = backendMessage,
        )
    }

    suspend fun saveRiskEvent(
        sessionId: String?,
        eventId: String,
        eventType: String,
        title: String,
        detail: String,
        severity: String,
        priorityScore: Int,
        localConfidencePercent: Int,
        edgeSignalsJson: String,
        triggeredAtEpochMs: Long,
    ) {
        val now = System.currentTimeMillis()
        dao.insertRiskEvent(
            LocalRiskEventEntity(
                id = eventId,
                sessionId = sessionId,
                eventType = eventType,
                title = title,
                detail = detail,
                severity = severity,
                priorityScore = priorityScore,
                localConfidencePercent = localConfidencePercent,
                edgeSignalsJson = edgeSignalsJson,
                status = "triggered",
                clipRecordId = null,
                triggeredAtEpochMs = triggeredAtEpochMs,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    suspend fun markRiskEventClipCaptured(
        eventId: String,
        clipRecordId: Long,
        detail: String,
    ) {
        dao.updateRiskEvent(
            eventId = eventId,
            status = "clip_ready",
            clipRecordId = clipRecordId,
            detail = detail,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    suspend fun markRiskEventCaptureFailed(
        eventId: String,
        detail: String,
    ) {
        dao.updateRiskEvent(
            eventId = eventId,
            status = "capture_failed",
            clipRecordId = null,
            detail = detail,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    @Composable
    fun rememberRecentSessions(): List<LocalDriveSessionEntity> {
        return recentSessions.collectAsState(initial = emptyList()).value
    }

    @Composable
    fun rememberRecentClips(): List<LocalClipRecordEntity> {
        return recentClips.collectAsState(initial = emptyList()).value
    }

    @Composable
    fun rememberRecentRiskEvents(): List<LocalRiskEventEntity> {
        return recentRiskEvents.collectAsState(initial = emptyList()).value
    }

    private suspend fun updateClipState(
        clipId: Long,
        uploadState: String,
        uploadedVideoId: String? = null,
        backendJobId: String? = null,
        backendSessionId: String? = null,
        backendMessage: String? = null,
    ) {
        dao.updateClipUploadState(
            clipId = clipId,
            uploadState = uploadState,
            uploadedVideoId = uploadedVideoId,
            backendJobId = backendJobId,
            backendSessionId = backendSessionId,
            backendMessage = backendMessage,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    companion object {
        @Volatile
        private var instance: LocalCaptureRepository? = null

        fun getInstance(context: Context): LocalCaptureRepository {
            return instance ?: synchronized(this) {
                instance ?: LocalCaptureRepository(
                    dao = DriveGuardLocalDatabase.getInstance(context).localCaptureDao(),
                ).also { instance = it }
            }
        }

        @Composable
        fun fromCurrentContext(): LocalCaptureRepository {
            val appContext = LocalContext.current.applicationContext
            return remember(appContext) {
                getInstance(appContext)
            }
        }
    }
}
