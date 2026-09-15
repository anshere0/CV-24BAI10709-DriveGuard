package com.driveguard.mobile.data.remote

import android.content.Context
import android.os.Build
import com.driveguard.mobile.data.local.LocalCaptureRepository
import com.driveguard.mobile.data.local.LocalClipRecordEntity
import com.driveguard.mobile.data.local.LocalDriveSessionEntity
import com.driveguard.mobile.data.local.LocalRiskEventEntity
import com.driveguard.mobile.settings.data.BackendSettingsRepository
import java.time.Instant
import org.json.JSONObject

suspend fun syncEdgeContextForClip(
    appContext: Context,
    backendClient: DriveGuardBackendClient,
    localCaptureRepository: LocalCaptureRepository,
    clip: LocalClipRecordEntity,
    uploadedVideoId: String? = null,
    analysisJobId: String? = null,
): EdgeEventResponse? {
    val sessionId = clip.sessionId ?: return null
    val localSession = localCaptureRepository.getSessionById(sessionId) ?: return null
    val deviceId = BackendSettingsRepository.getInstance(appContext).getOrCreateEdgeDeviceId()

    backendClient.upsertMobileDevice(
        baseUrl = clip.backendUrl,
        device = buildEdgeDevicePayload(appContext, deviceId),
    )
    backendClient.upsertDeviceSession(
        baseUrl = clip.backendUrl,
        payload = buildDeviceSessionPayload(
            deviceId = deviceId,
            localSession = localSession,
        ),
    )

    val localRiskEvent = clip.triggerEventId
        ?.let { eventId -> localCaptureRepository.getRiskEventById(eventId) }
        ?: return null

    return backendClient.upsertEdgeEvent(
        baseUrl = clip.backendUrl,
        payload = buildEdgeEventPayload(
            clip = clip,
            localRiskEvent = localRiskEvent,
            uploadedVideoId = uploadedVideoId,
            analysisJobId = analysisJobId,
        ),
    )
}

private fun buildEdgeDevicePayload(appContext: Context, deviceId: String): EdgeDevicePayload {
    val versionName = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull()
    return EdgeDevicePayload(
        id = deviceId,
        platform = "android",
        displayName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .ifBlank { Build.DEVICE },
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        osVersion = "Android ${Build.VERSION.RELEASE}",
        appVersion = versionName,
    )
}

private fun buildDeviceSessionPayload(
    deviceId: String,
    localSession: LocalDriveSessionEntity,
): EdgeDeviceSessionPayload {
    return EdgeDeviceSessionPayload(
        id = localSession.id,
        deviceId = deviceId,
        status = when (localSession.status) {
            "active" -> "active"
            "stopped" -> "stopped"
            else -> "completed"
        },
        startedAtIso = localSession.startedAtEpochMs.toIsoString(),
        endedAtIso = localSession.endedAtEpochMs?.toIsoString(),
        backendUrl = localSession.backendUrl,
        sourceOrigin = "android_upload",
    )
}

private fun buildEdgeEventPayload(
    clip: LocalClipRecordEntity,
    localRiskEvent: LocalRiskEventEntity,
    uploadedVideoId: String?,
    analysisJobId: String?,
): EdgeEventPayload {
    return EdgeEventPayload(
        id = localRiskEvent.id,
        deviceSessionId = requireNotNull(clip.sessionId),
        suspectedEventType = localRiskEvent.eventType,
        suspectedEventTitle = localRiskEvent.title,
        localConfidence = (localRiskEvent.localConfidencePercent.coerceIn(0, 100) / 100.0),
        localPriorityScore = localRiskEvent.priorityScore.coerceIn(0, 100),
        triggeredAtIso = localRiskEvent.triggeredAtEpochMs.toIsoString(),
        captureStartedAtIso = clip.captureStartedAtEpochMs?.toIsoString(),
        captureEndedAtIso = clip.captureEndedAtEpochMs?.toIsoString(),
        clipFileName = clip.fileName,
        edgeSignals = localRiskEvent.edgeSignalsJson.toEdgeSignalMap(),
        uploadedVideoId = uploadedVideoId,
        analysisJobId = analysisJobId,
        analysisSessionId = clip.backendSessionId,
        sourceOrigin = "android_upload",
    )
}

private fun Long.toIsoString(): String = Instant.ofEpochMilli(this).toString()

private fun String.toEdgeSignalMap(): Map<String, String> {
    if (isBlank()) {
        return emptyMap()
    }

    val json = runCatching { JSONObject(this) }.getOrNull() ?: return emptyMap()
    return buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, json.opt(key)?.toString().orEmpty())
        }
    }
}
