package com.driveguard.mobile.drive.session.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.driveguard.mobile.app.theme.Copper60
import com.driveguard.mobile.app.theme.Ink70
import com.driveguard.mobile.app.theme.Sand10
import com.driveguard.mobile.app.theme.Sand20
import com.driveguard.mobile.camera.CameraPreviewPane
import com.driveguard.mobile.camera.DriveSessionCameraController
import com.driveguard.mobile.camera.LocalRecordingState
import com.driveguard.mobile.data.local.LocalCaptureRepository
import com.driveguard.mobile.data.local.LocalClipRecordEntity
import com.driveguard.mobile.data.local.LocalDriveSessionEntity
import com.driveguard.mobile.data.local.LocalRiskEventEntity
import com.driveguard.mobile.data.remote.AnalysisJobPayload
import com.driveguard.mobile.data.remote.DriveGuardBackendClient
import com.driveguard.mobile.data.remote.syncEdgeContextForClip
import com.driveguard.mobile.inference.face.FaceLandmarkerAnalyzer
import com.driveguard.mobile.inference.face.FaceSignalSnapshot
import com.driveguard.mobile.inference.objects.RiskObjectSnapshot
import com.driveguard.mobile.drive.session.risk.RiskAlertState
import com.driveguard.mobile.drive.session.risk.RiskEventTrigger
import com.driveguard.mobile.drive.session.risk.RiskRuleEngine
import com.driveguard.mobile.settings.data.BackendSettingsRepository
import com.driveguard.mobile.workers.PendingClipUploadWorker
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun DriveSessionRoute() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { DriveSessionCameraController() }
    val backendSettingsRepository = BackendSettingsRepository.fromCurrentContext()
    val localCaptureRepository = LocalCaptureRepository.fromCurrentContext()
    val backendUrl = backendSettingsRepository.rememberBackendUrl()
    val recentLocalSessions = localCaptureRepository.rememberRecentSessions()
    val recentLocalClips = localCaptureRepository.rememberRecentClips()
    val recentRiskEvents = localCaptureRepository.rememberRecentRiskEvents()
    val backendClient = remember { DriveGuardBackendClient() }
    val riskRuleEngine = remember { RiskRuleEngine() }
    val coroutineScope = rememberCoroutineScope()
    var faceSignals by remember { mutableStateOf(FaceSignalSnapshot()) }
    var objectSnapshot by remember { mutableStateOf(RiskObjectSnapshot()) }
    var faceAnalyzerError by remember { mutableStateOf<String?>(null) }
    var activeRiskAlerts by remember { mutableStateOf<List<RiskAlertState>>(emptyList()) }
    var localSuspicionScore by rememberSaveable { mutableStateOf(0) }
    var riskPrioritySummary by rememberSaveable { mutableStateOf("Monitoring") }
    var bufferStateLabel by rememberSaveable { mutableStateOf("Idle") }
    var bufferDetail by rememberSaveable {
        mutableStateOf("Start a drive session to arm the rolling pre-trigger capture buffer.")
    }
    var latestRiskEventTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var currentBufferWindow by remember { mutableStateOf<RollingCaptureWindow?>(null) }
    var bufferStopJob by remember { mutableStateOf<Job?>(null) }
    var cameraReady by remember { mutableStateOf(false) }

    var cameraGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var audioGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO)) }
    var sessionRunning by remember { mutableStateOf(false) }
    var cameraStatus by remember { mutableStateOf("Camera not ready yet.") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraWarning by remember { mutableStateOf<String?>(null) }
    var latestClipName by rememberSaveable { mutableStateOf<String?>(null) }
    var latestClipPath by rememberSaveable { mutableStateOf<String?>(null) }
    var latestClipSizeBytes by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordingStateLabel by rememberSaveable { mutableStateOf("Buffer idle") }
    var recordingDetail by rememberSaveable {
        mutableStateOf("Useful clips are created automatically when a local alert is confirmed.")
    }
    var currentLocalSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeLocalClipRecordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var uploadStateLabel by rememberSaveable { mutableStateOf("Idle") }
    var uploadDetail by rememberSaveable {
        mutableStateOf("Only event-triggered clips are kept locally and sent into the backend workflow.")
    }
    var uploadProgressPercent by rememberSaveable { mutableStateOf(0) }
    var uploadedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var createdJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var backendJobStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var backendJobPhase by rememberSaveable { mutableStateOf<String?>(null) }
    var backendJobProgressPercent by rememberSaveable { mutableStateOf(0) }
    var backendJobMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var backendSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val faceAnalyzer = remember(context) {
        FaceLandmarkerAnalyzer(
            context = context,
            onSignalsReady = { snapshot ->
                faceSignals = snapshot
                faceAnalyzerError = null
                if (!sessionRunning) {
                    activeRiskAlerts = emptyList()
                    localSuspicionScore = 0
                    riskPrioritySummary = "Monitoring"
                    riskRuleEngine.reset()
                } else {
                    val evaluation = riskRuleEngine.evaluate(snapshot, objectSnapshot)
                    activeRiskAlerts = evaluation.activeAlerts
                    localSuspicionScore = evaluation.localSuspicionScore
                    riskPrioritySummary = evaluation.prioritySummary
                    evaluation.newEvents.forEach { event ->
                        latestRiskEventTitle = event.title
                        uploadStateLabel = "Useful clip pending"
                        uploadDetail = "${event.title} fired locally. Preserving a useful clip around the event."
                        coroutineScope.launch {
                            localCaptureRepository.saveRiskEvent(
                                sessionId = currentLocalSessionId,
                                eventId = event.id,
                                eventType = event.type.wireName,
                                title = event.title,
                                detail = event.detail,
                                severity = event.severityLabel,
                                priorityScore = event.priorityScore,
                                localConfidencePercent = event.localConfidencePercent,
                                edgeSignalsJson = JSONObject(event.edgeSignals).toString(),
                                triggeredAtEpochMs = event.triggeredAtEpochMs,
                            )
                        }

                        val activeWindow = currentBufferWindow
                        if (activeWindow == null) {
                            startRollingCaptureWindow(
                                context = context,
                                cameraController = cameraController,
                                audioGranted = audioGranted,
                                triggerEvent = event,
                                currentWindowProvider = { currentBufferWindow },
                                onWindowUpdated = { window -> currentBufferWindow = window },
                                onStopJobUpdated = { job -> bufferStopJob = job },
                                coroutineScope = coroutineScope,
                                onBufferState = { label, detail ->
                                    bufferStateLabel = label
                                    bufferDetail = detail
                                },
                                onRecordingState = { label, detail ->
                                    recordingStateLabel = label
                                    recordingDetail = detail
                                },
                                onClipFinalized = { file, sizeBytes, captureWindow ->
                                    latestClipName = file.name
                                    latestClipPath = file.absolutePath
                                    latestClipSizeBytes = sizeBytes
                                    uploadStateLabel = "Useful clip ready"
                                    uploadDetail = "An event-triggered clip is ready and queued for backend handoff."
                                    coroutineScope.launch {
                                        val clipId = localCaptureRepository.saveFinalizedClip(
                                            sessionId = currentLocalSessionId,
                                            backendUrl = backendUrl,
                                            clipFile = file,
                                            sizeBytes = sizeBytes,
                                            sourceOrigin = "android_upload",
                                            triggerEventId = captureWindow.triggerEventId,
                                            triggerEventType = captureWindow.triggerEventType,
                                            priorityScore = captureWindow.priorityScore,
                                            captureStrategy = captureWindow.captureStrategy,
                                            captureStartedAtEpochMs = captureWindow.startedAtEpochMs,
                                            captureEndedAtEpochMs = System.currentTimeMillis(),
                                        )
                                        activeLocalClipRecordId = clipId
                                        captureWindow.triggerEventId?.let { eventId ->
                                            localCaptureRepository.markRiskEventClipCaptured(
                                                eventId = eventId,
                                                clipRecordId = clipId,
                                                detail = "Useful clip ${file.name} captured from the rolling buffer.",
                                            )
                                        }
                                        PendingClipUploadWorker.enqueue(context)
                                    }
                                },
                                onEventCaptureFailed = { eventId, message ->
                                    coroutineScope.launch {
                                        localCaptureRepository.markRiskEventCaptureFailed(
                                            eventId = eventId,
                                            detail = message,
                                        )
                                    }
                                },
                            )
                        } else if (!activeWindow.keepClip) {
                            currentBufferWindow = activeWindow.withTrigger(event)
                            bufferStateLabel = "Event locked"
                            bufferDetail = "Buffer window locked by ${event.title.lowercase()}. Capturing post-trigger footage now."
                            recordingStateLabel = "Capturing alert clip"
                            recordingDetail = "Preserving a useful clip with pre-trigger context for ${event.title.lowercase()}."

                            val elapsedMs = (event.triggeredAtEpochMs - activeWindow.startedAtEpochMs).coerceAtLeast(0L)
                            val stopDelayMs = max(
                                POST_TRIGGER_CAPTURE_MS,
                                MIN_USEFUL_CLIP_DURATION_MS - elapsedMs,
                            )
                            bufferStopJob?.cancel()
                            bufferStopJob = coroutineScope.launch {
                                delay(stopDelayMs)
                                cameraController.stopRecording()
                            }
                        } else {
                            bufferDetail = "Another local alert fired while the current useful clip was already being preserved."
                        }
                    }
                }
            },
            onObjectsReady = { snapshot ->
                objectSnapshot = snapshot
                if (sessionRunning) {
                    val evaluation = riskRuleEngine.evaluate(faceSignals, snapshot)
                    activeRiskAlerts = evaluation.activeAlerts
                    localSuspicionScore = evaluation.localSuspicionScore
                    riskPrioritySummary = evaluation.prioritySummary
                }
            },
            onError = { message ->
                faceAnalyzerError = message
            },
        )
    }

    LaunchedEffect(sessionRunning, cameraReady, cameraError, currentBufferWindow) {
        if (!sessionRunning) {
            return@LaunchedEffect
        }

        if (cameraReady && cameraError == null && currentBufferWindow == null && !cameraController.isRecording()) {
            startRollingCaptureWindow(
                context = context,
                cameraController = cameraController,
                audioGranted = audioGranted,
                triggerEvent = null,
                currentWindowProvider = { currentBufferWindow },
                onWindowUpdated = { window -> currentBufferWindow = window },
                onStopJobUpdated = { job -> bufferStopJob = job },
                coroutineScope = coroutineScope,
                onBufferState = { label, detail ->
                    bufferStateLabel = label
                    bufferDetail = detail
                },
                onRecordingState = { label, detail ->
                    recordingStateLabel = label
                    recordingDetail = detail
                },
                onClipFinalized = { file, sizeBytes, captureWindow ->
                    latestClipName = file.name
                    latestClipPath = file.absolutePath
                    latestClipSizeBytes = sizeBytes
                    uploadStateLabel = "Useful clip ready"
                    uploadDetail = "An event-triggered clip is ready and queued for backend handoff."
                    coroutineScope.launch {
                        val clipId = localCaptureRepository.saveFinalizedClip(
                            sessionId = currentLocalSessionId,
                            backendUrl = backendUrl,
                            clipFile = file,
                            sizeBytes = sizeBytes,
                            sourceOrigin = "android_upload",
                            triggerEventId = captureWindow.triggerEventId,
                            triggerEventType = captureWindow.triggerEventType,
                            priorityScore = captureWindow.priorityScore,
                            captureStrategy = captureWindow.captureStrategy,
                            captureStartedAtEpochMs = captureWindow.startedAtEpochMs,
                            captureEndedAtEpochMs = System.currentTimeMillis(),
                        )
                        activeLocalClipRecordId = clipId
                        captureWindow.triggerEventId?.let { eventId ->
                            localCaptureRepository.markRiskEventClipCaptured(
                                eventId = eventId,
                                clipRecordId = clipId,
                                detail = "Useful clip ${file.name} captured from the rolling buffer.",
                            )
                        }
                        PendingClipUploadWorker.enqueue(context)
                    }
                },
                onEventCaptureFailed = { eventId, message ->
                    coroutineScope.launch {
                        localCaptureRepository.markRiskEventCaptureFailed(
                            eventId = eventId,
                            detail = message,
                        )
                    }
                },
            )
        }
    }

    LaunchedEffect(createdJobId, backendUrl) {
        val jobId = createdJobId ?: return@LaunchedEffect
        while (true) {
            val snapshotResult = runCatching {
                backendClient.fetchJob(
                    baseUrl = backendUrl,
                    jobId = jobId,
                )
            }

            if (snapshotResult.isFailure) {
                val error = snapshotResult.exceptionOrNull()
                backendJobMessage = error?.message ?: "Could not refresh backend job status."
                activeLocalClipRecordId?.let { clipId ->
                    localCaptureRepository.markClipFailed(
                        clipId = clipId,
                        backendMessage = backendJobMessage ?: "Could not refresh backend job status.",
                    )
                }
                break
            }
            val snapshot = snapshotResult.getOrThrow()

            applyJobSnapshot(
                snapshot = snapshot,
                onStatus = { status ->
                    backendJobStatus = status
                },
                onPhase = { phase ->
                    backendJobPhase = phase
                },
                onProgress = { progress ->
                    backendJobProgressPercent = progress
                },
                onMessage = { message ->
                    backendJobMessage = message
                },
                onSessionId = { sessionId ->
                    backendSessionId = sessionId
                },
            )

            if (snapshot.status in TERMINAL_JOB_STATUSES) {
                uploadStateLabel = if (snapshot.status == "completed") "Completed" else "Finished"
                uploadDetail = if (snapshot.status == "completed") {
                    "Backend analysis finished. You can now inspect this job in the web dashboard."
                } else {
                    snapshot.errorMessage ?: snapshot.progressMessage ?: "Backend job finished with status ${snapshot.status}."
                }
                activeLocalClipRecordId?.let { clipId ->
                    localCaptureRepository.markJobProgress(
                        clipId = clipId,
                        uploadState = if (snapshot.status == "completed") "completed" else snapshot.status,
                        backendJobId = snapshot.id,
                        backendSessionId = snapshot.sessionIds.firstOrNull(),
                        backendMessage = uploadDetail,
                    )
                }
                break
            }

            activeLocalClipRecordId?.let { clipId ->
                localCaptureRepository.markJobProgress(
                    clipId = clipId,
                    uploadState = "processing",
                    backendJobId = snapshot.id,
                    backendSessionId = snapshot.sessionIds.firstOrNull(),
                    backendMessage = snapshot.errorMessage ?: snapshot.progressMessage,
                )
            }

            delay(2_000)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        cameraGranted = permissions[Manifest.permission.CAMERA] == true
        audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        if (!cameraGranted) {
            cameraError = "Camera permission is required to preview the driver feed."
            cameraWarning = null
            sessionRunning = false
        } else if (!audioGranted) {
            cameraError = null
            cameraWarning = "Microphone permission is still missing. Preview works, and recording will use audio once access is granted."
        } else {
            cameraError = null
            cameraWarning = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = context.hasPermission(Manifest.permission.CAMERA)
                audioGranted = context.hasPermission(Manifest.permission.RECORD_AUDIO)
                cameraError = if (cameraGranted) null else "Camera permission is required to preview the driver feed."
                cameraWarning = if (cameraGranted && !audioGranted) {
                    "Microphone permission is still missing. Preview works, and recording will use audio once access is granted."
                } else {
                    null
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val networkStatus = rememberNetworkStatus()
    val cameraIndicator = when {
        cameraError != null -> SessionStatusIndicator(
            label = "Camera",
            value = "Issue",
            detail = cameraError ?: "Camera unavailable.",
            tone = StatusTone.Critical,
        )

        !cameraGranted -> SessionStatusIndicator(
            label = "Camera",
            value = "Blocked",
            detail = "Camera permission is still missing.",
            tone = StatusTone.Warning,
        )

        cameraReady && sessionRunning -> SessionStatusIndicator(
            label = "Camera",
            value = "Live",
            detail = "Front camera preview is actively driving the session.",
            tone = StatusTone.Success,
        )

        cameraReady -> SessionStatusIndicator(
            label = "Camera",
            value = "Ready",
            detail = "Preview pipeline is armed for the next session.",
            tone = StatusTone.Success,
        )

        else -> SessionStatusIndicator(
            label = "Camera",
            value = "Starting",
            detail = "Preparing the preview pipeline.",
            tone = StatusTone.Neutral,
        )
    }
    val aiIndicator = when {
        faceAnalyzerError != null -> SessionStatusIndicator(
            label = "AI",
            value = "Attention",
            detail = faceAnalyzerError ?: "Inference warning.",
            tone = StatusTone.Warning,
        )

        !sessionRunning -> SessionStatusIndicator(
            label = "AI",
            value = "Standby",
            detail = "Local face and object rules wake up during a drive session.",
            tone = StatusTone.Neutral,
        )

        cameraReady -> SessionStatusIndicator(
            label = "AI",
            value = "Live",
            detail = "Face landmarks, objects, and local rules are being evaluated on-device.",
            tone = StatusTone.Success,
        )

        else -> SessionStatusIndicator(
            label = "AI",
            value = "Waiting",
            detail = "Inference will start once the camera feed is ready.",
            tone = StatusTone.Neutral,
        )
    }
    val networkIndicator = if (networkStatus.isConnected) {
        SessionStatusIndicator(
            label = "Network",
            value = networkStatus.label,
            detail = networkStatus.detail,
            tone = StatusTone.Success,
        )
    } else {
        SessionStatusIndicator(
            label = "Network",
            value = networkStatus.label,
            detail = "Local monitoring still works offline. Upload resumes when connectivity returns.",
            tone = StatusTone.Warning,
        )
    }
    val uploadIndicator = when {
        uploadStateLabel == "Error" -> SessionStatusIndicator(
            label = "Upload",
            value = "Blocked",
            detail = uploadDetail,
            tone = StatusTone.Critical,
        )

        uploadStateLabel in setOf("Uploading", "Creating job", "Job created") ||
            backendJobStatus in setOf("queued", "running", "processing") -> SessionStatusIndicator(
            label = "Upload",
            value = "Syncing",
            detail = uploadDetail,
            tone = StatusTone.Success,
        )

        uploadStateLabel in setOf("Completed", "Finished") || backendSessionId != null -> SessionStatusIndicator(
            label = "Upload",
            value = "Ready",
            detail = "This clip is already linked to backend review data.",
            tone = StatusTone.Success,
        )

        latestClipName != null -> SessionStatusIndicator(
            label = "Upload",
            value = "Queued",
            detail = "A useful clip is stored locally and ready for backend handoff.",
            tone = StatusTone.Warning,
        )

        else -> SessionStatusIndicator(
            label = "Upload",
            value = "Idle",
            detail = "Nothing is waiting for upload yet.",
            tone = StatusTone.Neutral,
        )
    }
    val sessionHeadline = when {
        sessionRunning && latestRiskEventTitle != null ->
            "$latestRiskEventTitle triggered locally and the useful clip flow is active."

        sessionRunning ->
            "Live monitoring is active. The phone is watching behavior, objects, and event-triggered clip capture."

        else ->
            "Ready for demo. Start a drive session to arm the camera, local AI, and rolling useful clip buffer."
    }
    val sessionNotices = buildList {
        if (!cameraGranted) {
            add(
                SessionNotice(
                    title = "Camera permission needed",
                    body = "Grant camera access to turn the phone into the live driver preview surface.",
                    tone = StatusTone.Warning,
                ),
            )
        }
        cameraError?.let { error ->
            add(
                SessionNotice(
                    title = "Camera issue",
                    body = error,
                    tone = StatusTone.Critical,
                ),
            )
        }
        cameraWarning?.let { warning ->
            add(
                SessionNotice(
                    title = "Microphone still missing",
                    body = warning,
                    tone = StatusTone.Warning,
                ),
            )
        }
        faceAnalyzerError?.let { warning ->
            add(
                SessionNotice(
                    title = "Local AI warning",
                    body = warning,
                    tone = StatusTone.Warning,
                ),
            )
        }
        if (!networkStatus.isConnected) {
            add(
                SessionNotice(
                    title = "Offline mode",
                    body = "Local event detection and useful clip capture still work. Backend upload waits until connectivity returns.",
                    tone = StatusTone.Neutral,
                ),
            )
        }
        if (uploadStateLabel == "Error") {
            add(
                SessionNotice(
                    title = "Upload needs attention",
                    body = uploadDetail,
                    tone = StatusTone.Critical,
                ),
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Sand10,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Sand10, Sand20),
                    ),
                ),
        ) {
            val previewHeight = when {
                maxHeight < 720.dp -> 220.dp
                maxHeight < 840.dp -> 260.dp
                else -> 320.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Drive Session",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Demo-ready session view for live camera monitoring, local AI, useful clip capture, and backend handoff.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink70,
                )

                SessionOverviewCard(
                    summary = sessionHeadline,
                    cameraStatus = cameraStatus,
                    currentLocalSessionId = currentLocalSessionId,
                    localSuspicionScore = localSuspicionScore,
                    prioritySummary = riskPrioritySummary,
                    latestRiskEventTitle = latestRiskEventTitle,
                    recentEventCount = recentRiskEvents.size,
                    cameraIndicator = cameraIndicator,
                    aiIndicator = aiIndicator,
                    networkIndicator = networkIndicator,
                    uploadIndicator = uploadIndicator,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Live preview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Front camera feed used for on-device AI, rolling clip capture, and event verification.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink70,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewHeight)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(24.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                !cameraGranted -> {
                                    PreviewPlaceholder(
                                        title = "Camera access needed",
                                        body = "Grant camera permission to turn the phone into the live driver preview surface.",
                                    )
                                }

                                cameraError != null -> {
                                    PreviewPlaceholder(
                                        title = "Camera unavailable",
                                        body = cameraError ?: "The device camera could not be opened.",
                                    )
                                }

                                else -> {
                                    CameraPreviewPane(
                                        modifier = Modifier.fillMaxSize(),
                                        controller = cameraController,
                                        preferredLensFacing = CameraSelector.LENS_FACING_FRONT,
                                        frameAnalyzer = faceAnalyzer,
                                        onCameraReady = { label ->
                                            cameraStatus = label
                                            cameraError = null
                                            cameraReady = true
                                            cameraWarning = if (audioGranted) null else {
                                                "Microphone permission is still missing for future recording."
                                            }
                                        },
                                        onCameraError = { message ->
                                            cameraError = message
                                            cameraReady = false
                                            cameraWarning = null
                                            sessionRunning = false
                                            currentBufferWindow = null
                                            bufferStopJob?.cancel()
                                            bufferStopJob = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                sessionNotices.forEach { notice ->
                    SessionNoticeCard(notice = notice)
                }

                PermissionStatusCard(
                    cameraGranted = cameraGranted,
                    audioGranted = audioGranted,
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                            ),
                        )
                    },
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SessionHeader(
                            sessionRunning = sessionRunning,
                            cameraStatus = cameraStatus,
                        )

                        SessionControls(
                            sessionRunning = sessionRunning,
                            canStartSession = cameraGranted && cameraError == null,
                            onStartSession = {
                                sessionRunning = true
                                cameraStatus = "Live drive session is active."
                                bufferStateLabel = "Arming"
                                bufferDetail = "Preparing the rolling pre-trigger buffer for useful clip capture."
                                recordingStateLabel = "Buffer arming"
                                recordingDetail = "Waiting for the camera pipeline to arm the rolling buffer."
                                latestRiskEventTitle = null
                                riskRuleEngine.reset()
                                activeRiskAlerts = emptyList()
                                localSuspicionScore = 0
                                riskPrioritySummary = "Monitoring"
                                objectSnapshot = RiskObjectSnapshot()
                                coroutineScope.launch {
                                    currentLocalSessionId = localCaptureRepository.startSession(backendUrl)
                                }
                            },
                            onStopSession = {
                                sessionRunning = false
                                bufferStopJob?.cancel()
                                bufferStopJob = null
                                cameraController.stopRecording()
                                currentBufferWindow = null
                                cameraReady = false
                                riskRuleEngine.reset()
                                activeRiskAlerts = emptyList()
                                localSuspicionScore = 0
                                riskPrioritySummary = "Monitoring"
                                objectSnapshot = RiskObjectSnapshot()
                                bufferStateLabel = "Idle"
                                bufferDetail = "Drive session stopped. No rolling buffer is active."
                                recordingStateLabel = "Buffer idle"
                                recordingDetail = "Useful clips are created automatically when a local alert is confirmed."
                                cameraStatus = if (cameraGranted) {
                                    "Camera preview is armed and waiting for the next session."
                                } else {
                                    "Camera not ready yet."
                                }
                                currentLocalSessionId?.let { sessionId ->
                                    coroutineScope.launch {
                                        localCaptureRepository.stopSession(sessionId)
                                        currentLocalSessionId = null
                                    }
                                }
                            },
                        )

                        RiskAlertsCard(
                            activeAlerts = activeRiskAlerts,
                            recentEvents = recentRiskEvents,
                            localSuspicionScore = localSuspicionScore,
                            prioritySummary = riskPrioritySummary,
                        )

                        BufferStatusCard(
                            bufferStateLabel = bufferStateLabel,
                            bufferDetail = bufferDetail,
                            latestRiskEventTitle = latestRiskEventTitle,
                        )

                        RecordingStatusCard(
                            stateLabel = recordingStateLabel,
                            detail = recordingDetail,
                            clipName = latestClipName,
                            clipSizeBytes = latestClipSizeBytes,
                        )

                        BackendUploadCard(
                            backendUrl = backendUrl,
                            uploadStateLabel = uploadStateLabel,
                            uploadDetail = uploadDetail,
                            uploadProgressPercent = uploadProgressPercent,
                            uploadedVideoId = uploadedVideoId,
                            jobId = createdJobId,
                            backendJobStatus = backendJobStatus,
                            backendJobPhase = backendJobPhase,
                            backendJobProgressPercent = backendJobProgressPercent,
                            backendJobMessage = backendJobMessage,
                            backendSessionId = backendSessionId,
                            canUpload = latestClipPath != null && recordingStateLabel != "Recording",
                            onUpload = {
                                val clipPath = latestClipPath
                                if (clipPath == null) {
                                    uploadStateLabel = "Error"
                                    uploadDetail = "No finalized clip is available for upload yet."
                                    return@BackendUploadCard
                                }

                                val clipFile = File(clipPath)
                                if (!clipFile.exists()) {
                                    uploadStateLabel = "Error"
                                    uploadDetail = "The local clip file is no longer available."
                                    return@BackendUploadCard
                                }

                                coroutineScope.launch {
                                    uploadStateLabel = "Uploading"
                                    uploadDetail = "Sending the local clip to the backend storage endpoint."
                                    uploadProgressPercent = 0
                                    uploadedVideoId = null
                                    createdJobId = null
                                    backendJobStatus = null
                                    backendJobPhase = null
                                    backendJobProgressPercent = 0
                                    backendJobMessage = null
                                    backendSessionId = null
                                    activeLocalClipRecordId?.let { clipId ->
                                        localCaptureRepository.markClipUploading(clipId)
                                    }

                                    runCatching {
                                        val uploadedVideo = backendClient.uploadVideo(
                                            baseUrl = backendUrl,
                                            clipFile = clipFile,
                                            sourceOrigin = "android_upload",
                                            onProgress = { percent ->
                                                uploadProgressPercent = percent
                                            },
                                        )
                                        uploadedVideoId = uploadedVideo.id
                                        uploadStateLabel = "Creating job"
                                        uploadDetail = "Clip stored on the backend. Creating the analysis job now."
                                        activeLocalClipRecordId?.let { clipId ->
                                            localCaptureRepository.markClipUploaded(
                                                clipId = clipId,
                                                uploadedVideoId = uploadedVideo.id,
                                            )
                                            localCaptureRepository.getClipById(clipId)?.let { clipRecord ->
                                                syncEdgeContextForClip(
                                                    appContext = context.applicationContext,
                                                    backendClient = backendClient,
                                                    localCaptureRepository = localCaptureRepository,
                                                    clip = clipRecord,
                                                    uploadedVideoId = uploadedVideo.id,
                                                )
                                            }
                                        }

                                        val job = backendClient.createAnalysisJob(
                                            baseUrl = backendUrl,
                                            uploadedVideoId = uploadedVideo.id,
                                        )
                                        createdJobId = job.id
                                        activeLocalClipRecordId?.let { clipId ->
                                            localCaptureRepository.markJobCreated(
                                                clipId = clipId,
                                                backendJobId = job.id,
                                                backendMessage = "Backend job created and queued for analysis.",
                                            )
                                            localCaptureRepository.getClipById(clipId)?.let { clipRecord ->
                                                syncEdgeContextForClip(
                                                    appContext = context.applicationContext,
                                                    backendClient = backendClient,
                                                    localCaptureRepository = localCaptureRepository,
                                                    clip = clipRecord,
                                                    uploadedVideoId = uploadedVideo.id,
                                                    analysisJobId = job.id,
                                                )
                                            }
                                        }
                                        applyJobSnapshot(
                                            snapshot = job,
                                            onStatus = { status ->
                                                backendJobStatus = status
                                            },
                                            onPhase = { phase ->
                                                backendJobPhase = phase
                                            },
                                            onProgress = { progress ->
                                                backendJobProgressPercent = progress
                                            },
                                            onMessage = { message ->
                                                backendJobMessage = message
                                            },
                                            onSessionId = { sessionId ->
                                                backendSessionId = sessionId
                                            },
                                        )
                                        uploadStateLabel = "Job created"
                                        uploadDetail = "Backend job created successfully. The web dashboard can now pick it up."
                                    }.onFailure { error ->
                                        uploadStateLabel = "Error"
                                        uploadDetail = error.message ?: "Could not upload the clip to the backend."
                                        activeLocalClipRecordId?.let { clipId ->
                                            localCaptureRepository.markClipFailed(
                                                clipId = clipId,
                                                backendMessage = uploadDetail,
                                            )
                                        }
                                        PendingClipUploadWorker.enqueue(context)
                                    }
                                }
                            },
                            onRefreshJob = {
                                val jobId = createdJobId ?: return@BackendUploadCard
                                coroutineScope.launch {
                                    uploadDetail = "Refreshing backend job status."
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            backendClient.fetchJob(
                                                baseUrl = backendUrl,
                                                jobId = jobId,
                                            )
                                        }
                                    }.onSuccess { snapshot ->
                                        applyJobSnapshot(
                                            snapshot = snapshot,
                                            onStatus = { status ->
                                                backendJobStatus = status
                                            },
                                            onPhase = { phase ->
                                                backendJobPhase = phase
                                            },
                                            onProgress = { progress ->
                                                backendJobProgressPercent = progress
                                            },
                                            onMessage = { message ->
                                                backendJobMessage = message
                                            },
                                            onSessionId = { sessionId ->
                                                backendSessionId = sessionId
                                            },
                                        )
                                        uploadDetail = "Backend job status refreshed."
                                    }.onFailure { error ->
                                        uploadDetail = error.message ?: "Could not refresh backend job status."
                                    }
                                }
                            },
                        )

                        LocalQueueSummaryCard(
                            currentLocalSessionId = currentLocalSessionId,
                            recentSessions = recentLocalSessions,
                            recentClips = recentLocalClips,
                        )

                        FaceSignalsCard(
                            snapshot = faceSignals,
                            analyzerError = faceAnalyzerError,
                        )

                        RiskObjectsCard(
                            objectSnapshot = objectSnapshot,
                        )
                    }
                }
            }
        }
    }
}

private enum class StatusTone {
    Neutral,
    Success,
    Warning,
    Critical,
}

private data class SessionStatusIndicator(
    val label: String,
    val value: String,
    val detail: String,
    val tone: StatusTone,
)

private data class SessionNotice(
    val title: String,
    val body: String,
    val tone: StatusTone,
)

@Composable
private fun SessionOverviewCard(
    summary: String,
    cameraStatus: String,
    currentLocalSessionId: String?,
    localSuspicionScore: Int,
    prioritySummary: String,
    latestRiskEventTitle: String?,
    recentEventCount: Int,
    cameraIndicator: SessionStatusIndicator,
    aiIndicator: SessionStatusIndicator,
    networkIndicator: SessionStatusIndicator,
    uploadIndicator: SessionStatusIndicator,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Session overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = cameraStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusIndicatorCard(
                    indicator = cameraIndicator,
                    modifier = Modifier.weight(1f),
                )
                StatusIndicatorCard(
                    indicator = aiIndicator,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusIndicatorCard(
                    indicator = networkIndicator,
                    modifier = Modifier.weight(1f),
                )
                StatusIndicatorCard(
                    indicator = uploadIndicator,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryMetricCard(
                    label = "Suspicion",
                    value = "$localSuspicionScore / 100",
                    modifier = Modifier.weight(1f),
                )
                SummaryMetricCard(
                    label = "Recent events",
                    value = recentEventCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Current focus: $prioritySummary",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Local session: ${currentLocalSessionId?.take(8) ?: "Not started yet"}",
                style = MaterialTheme.typography.bodySmall,
                color = Ink70,
            )
            latestRiskEventTitle?.let { title ->
                Text(
                    text = "Latest trigger: $title",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink70,
                )
            }
        }
    }
}

@Composable
private fun StatusIndicatorCard(
    indicator: SessionStatusIndicator,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (indicator.tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        StatusTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        StatusTone.Critical -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        StatusTone.Neutral -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    val accentColor = when (indicator.tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.tertiary
        StatusTone.Warning -> MaterialTheme.colorScheme.secondary
        StatusTone.Critical -> Copper60
        StatusTone.Neutral -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = indicator.label,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
            )
            Text(
                text = indicator.value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = indicator.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Ink70,
            )
        }
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Ink70,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SessionNoticeCard(
    notice: SessionNotice,
) {
    val containerColor = when (notice.tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        StatusTone.Warning -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
        StatusTone.Critical -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        StatusTone.Neutral -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    val accentColor = when (notice.tone) {
        StatusTone.Success -> MaterialTheme.colorScheme.tertiary
        StatusTone.Warning -> MaterialTheme.colorScheme.secondary
        StatusTone.Critical -> Copper60
        StatusTone.Neutral -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
            )
            Text(
                text = notice.body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(
    cameraGranted: Boolean,
    audioGranted: Boolean,
    onRequestPermissions: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Camera access: ${permissionLabel(cameraGranted)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Microphone access: ${permissionLabel(audioGranted)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!cameraGranted || !audioGranted) {
                Button(onClick = onRequestPermissions) {
                    Text("Grant access")
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(
    sessionRunning: Boolean,
    cameraStatus: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (sessionRunning) "Drive session live" else "Drive session controls",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = cameraStatus,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink70,
        )
    }
}

@Composable
private fun SessionControls(
    sessionRunning: Boolean,
    canStartSession: Boolean,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onStartSession,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            enabled = canStartSession && !sessionRunning,
        ) {
            Text("Start demo session")
        }
        OutlinedButton(
            onClick = onStopSession,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            enabled = sessionRunning,
        ) {
            Text("Stop session")
        }
    }
}

@Composable
private fun RecordingStatusCard(
    stateLabel: String,
    detail: String,
    clipName: String?,
    clipSizeBytes: Long?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Local recording",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "State: $stateLabel",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (clipName != null) {
                Text(
                    text = "Latest clip: $clipName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            if (clipSizeBytes != null) {
                Text(
                    text = "Clip size: ${formatBytes(clipSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
        }
    }
}

@Composable
private fun BufferStatusCard(
    bufferStateLabel: String,
    bufferDetail: String,
    latestRiskEventTitle: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Rolling clip buffer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "State: $bufferStateLabel",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = bufferDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (latestRiskEventTitle != null) {
                Text(
                    text = "Latest trigger: $latestRiskEventTitle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
        }
    }
}

@Composable
private fun FaceSignalsCard(
    snapshot: FaceSignalSnapshot,
    analyzerError: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Face diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Face present: ${if (snapshot.facePresent) "Yes" else "No"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Eye closure: ${snapshot.eyeClosurePercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Yawn proxy: ${snapshot.yawnPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Head orientation: ${snapshot.headOrientationLabel} (${snapshot.headTurnPercent}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Head pitch: ${snapshot.headPitchPercent}% | Gaze: ${snapshot.gazeDirectionLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Head nod detected: ${if (snapshot.headNodDetected) "Yes" else "No"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = snapshot.statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (analyzerError != null) {
                Text(
                    text = analyzerError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Copper60,
                )
            }
        }
    }
}

@Composable
private fun RiskObjectsCard(
    objectSnapshot: RiskObjectSnapshot,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Object detections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = objectSnapshot.statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (objectSnapshot.detections.isEmpty()) {
                Text(
                    text = "No phone or drink detected right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            } else {
                objectSnapshot.detections.take(4).forEach { detection ->
                    Text(
                        text = "${detection.label} | ${detection.confidencePercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskAlertsCard(
    activeAlerts: List<RiskAlertState>,
    recentEvents: List<LocalRiskEventEntity>,
    localSuspicionScore: Int,
    prioritySummary: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Local alerts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Suspicion score: $localSuspicionScore | Focus: $prioritySummary",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (activeAlerts.isEmpty()) {
                Text(
                    text = "No confirmed local alert is active right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            } else {
                activeAlerts.forEach { alert ->
                    Text(
                        text = "${alert.title} (${alert.severityLabel})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = alert.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                }
            }
            if (recentEvents.isNotEmpty()) {
                Text(
                    text = "Recent confirmations",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                recentEvents.take(4).forEach { event ->
                    val clipLabel = if (event.clipRecordId != null) "clip linked" else event.status
                    Text(
                        text = "${event.title} | p${event.priorityScore} | $clipLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalQueueCard(
    currentLocalSessionId: String?,
    recentSessions: List<LocalDriveSessionEntity>,
    recentClips: List<LocalClipRecordEntity>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Recent edge activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Current session: ${currentLocalSessionId ?: "No active local session"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Saved sessions: ${recentSessions.size}  •  Saved clips: ${recentClips.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (recentClips.isEmpty()) {
                Text(
                    text = "No local clips stored yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            } else {
                recentClips.take(4).forEach { clip ->
                    Text(
                        text = "${clip.fileName}  •  ${clip.uploadState}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                    if (!clip.backendMessage.isNullOrBlank()) {
                        Text(
                            text = clip.backendMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink70,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalQueueSummaryCard(
    currentLocalSessionId: String?,
    recentSessions: List<LocalDriveSessionEntity>,
    recentClips: List<LocalClipRecordEntity>,
) {
    val pendingUploads = recentClips.count { clip ->
        clip.uploadState in setOf("pending", "uploading", "sent", "processing")
    }
    val completedUploads = recentClips.count { clip ->
        clip.uploadState == "completed" || clip.backendSessionId != null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Recent edge activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Current session: ${currentLocalSessionId ?: "No active local session"}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Saved sessions: ${recentSessions.size} | Useful clips: ${recentClips.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Pending upload: $pendingUploads | Dashboard ready: $completedUploads",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (recentClips.isEmpty()) {
                Text(
                    text = "No local clips stored yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            } else {
                recentClips.take(4).forEach { clip ->
                    val clipTrigger = clip.triggerEventType ?: clip.captureStrategy
                    Text(
                        text = "${clip.fileName} | ${clipTrigger} | ${clip.uploadState}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                    if (!clip.backendMessage.isNullOrBlank()) {
                        Text(
                            text = clip.backendMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink70,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendUploadCard(
    backendUrl: String,
    uploadStateLabel: String,
    uploadDetail: String,
    uploadProgressPercent: Int,
    uploadedVideoId: String?,
    jobId: String?,
    backendJobStatus: String?,
    backendJobPhase: String?,
    backendJobProgressPercent: Int,
    backendJobMessage: String?,
    backendSessionId: String?,
    canUpload: Boolean,
    onUpload: () -> Unit,
    onRefreshJob: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Backend handoff",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Target: $backendUrl",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            Text(
                text = "Upload state: $uploadStateLabel",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = uploadDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
            if (uploadStateLabel in setOf("Uploading", "Creating job", "Job created", "Completed", "Finished")) {
                LinearProgressIndicator(
                    progress = { (uploadProgressPercent.coerceIn(0, 100) / 100f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Clip upload: $uploadProgressPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink70,
                )
            }
            if (uploadedVideoId != null) {
                Text(
                    text = "Uploaded video id: $uploadedVideoId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            if (jobId != null) {
                Text(
                    text = "Backend job id: $jobId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            if (backendJobStatus != null) {
                Text(
                    text = "Job status: $backendJobStatus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            if (backendJobPhase != null) {
                Text(
                    text = "Job phase: $backendJobPhase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            if (jobId != null) {
                LinearProgressIndicator(
                    progress = { backendJobProgressPercent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Backend analysis: $backendJobProgressPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink70,
                )
            }
            if (backendJobMessage != null) {
                Text(
                    text = backendJobMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            if (backendSessionId != null) {
                Text(
                    text = "Session id ready for web review: $backendSessionId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink70,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = canUpload && uploadStateLabel != "Uploading" && uploadStateLabel != "Creating job",
                ) {
                    Text("Send latest clip")
                }
                OutlinedButton(
                    onClick = onRefreshJob,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    enabled = jobId != null,
                ) {
                    Text("Refresh backend")
                }
            }
        }
    }
}

@Composable
private fun PreviewPlaceholder(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink70,
        )
    }
}

private fun startRollingCaptureWindow(
    context: android.content.Context,
    cameraController: DriveSessionCameraController,
    audioGranted: Boolean,
    triggerEvent: RiskEventTrigger?,
    currentWindowProvider: () -> RollingCaptureWindow?,
    onWindowUpdated: (RollingCaptureWindow?) -> Unit,
    onStopJobUpdated: (Job?) -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onBufferState: (String, String) -> Unit,
    onRecordingState: (String, String) -> Unit,
    onClipFinalized: (File, Long, RollingCaptureWindow) -> Unit,
    onEventCaptureFailed: (String, String) -> Unit,
) {
    if (cameraController.isRecording()) {
        return
    }

    val clipFile = buildBufferedClipFile(context)
    val captureWindow = RollingCaptureWindow(
        file = clipFile,
        startedAtEpochMs = System.currentTimeMillis(),
        triggerEventId = triggerEvent?.id,
        triggerEventType = triggerEvent?.type?.wireName,
        priorityScore = triggerEvent?.priorityScore ?: 0,
        keepClip = triggerEvent != null,
    )
    onWindowUpdated(captureWindow)

    val bufferLabel = if (triggerEvent == null) "Rolling buffer active" else "Event capture active"
    val bufferDetail = if (triggerEvent == null) {
        "Capturing a short rolling window that will be discarded unless a local alert fires."
    } else {
        "A local alert fired before the rolling buffer was active. Capturing the post-trigger clip now."
    }
    onBufferState(bufferLabel, bufferDetail)

    val recordingLabel = if (triggerEvent == null) "Buffering" else "Capturing alert clip"
    val recordingDetail = if (triggerEvent == null) {
        "Rolling pre-trigger video is active and rotating automatically."
    } else {
        "Recording a useful clip directly from the local alert trigger."
    }
    onRecordingState(recordingLabel, recordingDetail)

    cameraController.startRecording(
        context = context,
        audioEnabled = audioGranted,
        outputFile = clipFile,
    ) { state ->
        when (state) {
            is LocalRecordingState.Ready -> {
                onRecordingState("Buffer ready", "Camera is armed for rolling buffer capture.")
            }

            is LocalRecordingState.Recording -> {
                onRecordingState(recordingLabel, recordingDetail)
            }

            is LocalRecordingState.Finalized -> {
                onStopJobUpdated(null)
                val finalizedWindow = currentWindowProvider() ?: captureWindow
                onWindowUpdated(null)
                if (!finalizedWindow.keepClip) {
                    state.file.delete()
                    onBufferState(
                        "Rotating",
                        "An untriggered rolling window was discarded to keep storage usage low.",
                    )
                    onRecordingState(
                        "Buffering",
                        "Waiting for the next rolling window to arm automatically.",
                    )
                    return@startRecording
                }

                onBufferState(
                    "Useful clip ready",
                    "A useful clip was extracted from the rolling buffer and linked to its local alert.",
                )
                onRecordingState(
                    "Finalized",
                    "Useful event clip saved locally and ready for backend handoff.",
                )
                onClipFinalized(state.file, state.bytes, finalizedWindow)
            }

            is LocalRecordingState.Error -> {
                onStopJobUpdated(null)
                val failedWindow = currentWindowProvider() ?: captureWindow
                onWindowUpdated(null)
                onBufferState("Error", state.message)
                onRecordingState("Error", state.message)
                failedWindow.triggerEventId?.let { eventId ->
                    onEventCaptureFailed(eventId, state.message)
                }
            }
        }
    }

    val stopDelayMs = if (triggerEvent == null) {
        ROLLING_BUFFER_WINDOW_MS
    } else {
        POST_TRIGGER_CAPTURE_MS
    }
    val stopJob = coroutineScope.launch {
        delay(stopDelayMs)
        cameraController.stopRecording()
    }
    onStopJobUpdated(stopJob)
}

private fun permissionLabel(granted: Boolean): String {
    return if (granted) "Granted" else "Missing"
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = max(bytes, 0L)
    val megabytes = safeBytes / (1024f * 1024f)
    return String.format("%.2f MB", megabytes)
}

private fun android.content.Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun applyJobSnapshot(
    snapshot: AnalysisJobPayload,
    onStatus: (String) -> Unit,
    onPhase: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onMessage: (String?) -> Unit,
    onSessionId: (String?) -> Unit,
) {
    onStatus(snapshot.status)
    onPhase(snapshot.progressPhase)
    onProgress(snapshot.progressPercent.toInt().coerceIn(0, 100))
    onMessage(snapshot.errorMessage ?: snapshot.progressMessage)
    onSessionId(snapshot.sessionIds.firstOrNull())
}

private fun buildBufferedClipFile(context: android.content.Context): File {
    val outputDirectory = File(context.cacheDir, "driveguard-clips").apply { mkdirs() }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.US)
        .format(java.util.Date())
    return File(outputDirectory, "driveguard_event_$stamp.mp4")
}

private data class RollingCaptureWindow(
    val file: File,
    val startedAtEpochMs: Long,
    val triggerEventId: String?,
    val triggerEventType: String?,
    val priorityScore: Int,
    val keepClip: Boolean,
    val captureStrategy: String = "rolling_event_buffer_v1",
) {
    fun withTrigger(triggerEvent: RiskEventTrigger): RollingCaptureWindow {
        return copy(
            triggerEventId = triggerEvent.id,
            triggerEventType = triggerEvent.type.wireName,
            priorityScore = triggerEvent.priorityScore,
            keepClip = true,
        )
    }
}

private val TERMINAL_JOB_STATUSES = setOf("completed", "failed", "canceled")
private const val ROLLING_BUFFER_WINDOW_MS = 12_000L
private const val POST_TRIGGER_CAPTURE_MS = 6_000L
private const val MIN_USEFUL_CLIP_DURATION_MS = 8_000L
