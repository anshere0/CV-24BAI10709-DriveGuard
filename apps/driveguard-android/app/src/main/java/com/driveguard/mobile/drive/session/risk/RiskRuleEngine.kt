package com.driveguard.mobile.drive.session.risk

import com.driveguard.mobile.inference.face.FaceSignalSnapshot
import com.driveguard.mobile.inference.objects.RiskObjectSnapshot
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

enum class RiskEventType(
    val wireName: String,
    val displayTitle: String,
) {
    DROWSINESS_SUSPECTED(
        wireName = "drowsiness_suspected",
        displayTitle = "Drowsiness suspected",
    ),
    DISTRACTION_SUSPECTED(
        wireName = "distraction_suspected",
        displayTitle = "Distraction suspected",
    ),
    FACE_MISSING(
        wireName = "face_missing",
        displayTitle = "Face missing",
    ),
    PHONE_USAGE_SUSPECTED(
        wireName = "phone_usage_suspected",
        displayTitle = "Phone usage suspected",
    ),
    SEVERE_DROWSINESS_SUSPECTED(
        wireName = "severe_drowsiness_suspected",
        displayTitle = "Severe drowsiness suspected",
    ),
    OBJECT_DISTRACTION_SUSPECTED(
        wireName = "object_distraction_suspected",
        displayTitle = "Object distraction suspected",
    ),
}

data class RiskAlertState(
    val type: RiskEventType,
    val title: String,
    val detail: String,
    val severityLabel: String,
    val priorityScore: Int,
    val activeSinceEpochMs: Long,
)

data class RiskEventTrigger(
    val id: String,
    val type: RiskEventType,
    val title: String,
    val detail: String,
    val severityLabel: String,
    val priorityScore: Int,
    val localConfidencePercent: Int,
    val edgeSignals: Map<String, String>,
    val triggeredAtEpochMs: Long,
)

data class RiskRuleEvaluation(
    val activeAlerts: List<RiskAlertState>,
    val newEvents: List<RiskEventTrigger>,
    val localSuspicionScore: Int,
    val prioritySummary: String,
)

class RiskRuleEngine {
    private val drowsinessTracker = RuleTracker()
    private val distractionTracker = RuleTracker()
    private val faceMissingTracker = RuleTracker()
    private val phoneUsageTracker = RuleTracker()
    private val severeDrowsinessTracker = RuleTracker()
    private val objectDistractionTracker = RuleTracker()

    fun evaluate(
        snapshot: FaceSignalSnapshot,
        objects: RiskObjectSnapshot,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RiskRuleEvaluation {
        val activeAlerts = mutableListOf<RiskAlertState>()
        val newEvents = mutableListOf<RiskEventTrigger>()
        val edgeSignals = buildEdgeSignals(snapshot, objects)

        evaluateRule(
            tracker = faceMissingTracker,
            nowEpochMs = nowEpochMs,
            conditionMet = !snapshot.facePresent,
            minDurationMs = FACE_MISSING_MIN_DURATION_MS,
            cooldownMs = FACE_MISSING_COOLDOWN_MS,
            type = RiskEventType.FACE_MISSING,
            severityLabel = "high",
            priorityScore = 55,
            localConfidencePercent = 55,
            edgeSignals = { edgeSignals },
            detail = { "Driver face has been missing from the frame for at least 2 seconds." },
        ).appendTo(activeAlerts, newEvents)

        evaluateRule(
            tracker = drowsinessTracker,
            nowEpochMs = nowEpochMs,
            conditionMet = snapshot.facePresent &&
                (
                    snapshot.eyeClosurePercent >= DROWSINESS_EYE_CLOSURE_THRESHOLD ||
                        snapshot.yawnPercent >= DROWSINESS_YAWN_THRESHOLD
                    ),
            minDurationMs = DROWSINESS_MIN_DURATION_MS,
            cooldownMs = DROWSINESS_COOLDOWN_MS,
            type = RiskEventType.DROWSINESS_SUSPECTED,
            severityLabel = "medium",
            priorityScore = 48,
            localConfidencePercent = 68,
            edgeSignals = { edgeSignals },
            detail = {
                when {
                    snapshot.eyeClosurePercent >= DROWSINESS_EYE_CLOSURE_THRESHOLD &&
                        snapshot.yawnPercent >= DROWSINESS_YAWN_THRESHOLD ->
                        "Eye closure and yawn intensity stayed above the drowsiness threshold."

                    snapshot.eyeClosurePercent >= DROWSINESS_EYE_CLOSURE_THRESHOLD ->
                        "Eye closure stayed above the local drowsiness threshold."

                    else ->
                        "Yawn intensity stayed above the local drowsiness threshold."
                }
            },
        ).appendTo(activeAlerts, newEvents)

        evaluateRule(
            tracker = distractionTracker,
            nowEpochMs = nowEpochMs,
            conditionMet = snapshot.facePresent && abs(snapshot.headTurnPercent) >= DISTRACTION_HEAD_TURN_THRESHOLD,
            minDurationMs = DISTRACTION_MIN_DURATION_MS,
            cooldownMs = DISTRACTION_COOLDOWN_MS,
            type = RiskEventType.DISTRACTION_SUSPECTED,
            severityLabel = "medium",
            priorityScore = 40,
            localConfidencePercent = 62,
            edgeSignals = { edgeSignals },
            detail = {
                "Head orientation stayed off-center beyond the distraction threshold."
            },
        ).appendTo(activeAlerts, newEvents)

        evaluateRule(
            tracker = phoneUsageTracker,
            nowEpochMs = nowEpochMs,
            conditionMet = snapshot.facePresent && objects.hasPhone && snapshot.gazeDown,
            minDurationMs = PHONE_USAGE_MIN_DURATION_MS,
            cooldownMs = PHONE_USAGE_COOLDOWN_MS,
            type = RiskEventType.PHONE_USAGE_SUSPECTED,
            severityLabel = "high",
            priorityScore = 82,
            localConfidencePercent = 82,
            edgeSignals = { edgeSignals },
            detail = {
                "A phone is visible while the driver gaze proxy points downward."
            },
        ).appendTo(activeAlerts, newEvents)

        evaluateRule(
            tracker = severeDrowsinessTracker,
            nowEpochMs = nowEpochMs,
            conditionMet = snapshot.facePresent &&
                snapshot.eyeClosurePercent >= SEVERE_DROWSINESS_EYE_THRESHOLD &&
                snapshot.headNodDetected,
            minDurationMs = SEVERE_DROWSINESS_MIN_DURATION_MS,
            cooldownMs = SEVERE_DROWSINESS_COOLDOWN_MS,
            type = RiskEventType.SEVERE_DROWSINESS_SUSPECTED,
            severityLabel = "critical",
            priorityScore = 90,
            localConfidencePercent = 90,
            edgeSignals = { edgeSignals },
            detail = {
                "Eyes remained closed while the head pose suggested a nodding movement."
            },
        ).appendTo(activeAlerts, newEvents)

        evaluateRule(
            tracker = objectDistractionTracker,
            nowEpochMs = nowEpochMs,
            conditionMet = snapshot.facePresent && objects.hasRiskObject && abs(snapshot.headTurnPercent) >= OBJECT_DISTRACTION_HEAD_TURN_THRESHOLD,
            minDurationMs = OBJECT_DISTRACTION_MIN_DURATION_MS,
            cooldownMs = OBJECT_DISTRACTION_COOLDOWN_MS,
            type = RiskEventType.OBJECT_DISTRACTION_SUSPECTED,
            severityLabel = "high",
            priorityScore = 72,
            localConfidencePercent = 74,
            edgeSignals = { edgeSignals },
            detail = {
                "A risk object is present while distraction stays active."
            },
        ).appendTo(activeAlerts, newEvents)

        val suspicionScore = computeSuspicionScore(
            activeAlerts = activeAlerts,
            snapshot = snapshot,
            objects = objects,
        )
        val prioritySummary = activeAlerts
            .maxByOrNull { alert -> alert.priorityScore }
            ?.title
            ?: if (objects.hasRiskObject) "Risk object present" else "Monitoring"

        return RiskRuleEvaluation(
            activeAlerts = activeAlerts.sortedByDescending { alert -> alert.priorityScore },
            newEvents = newEvents.sortedByDescending { event -> event.priorityScore },
            localSuspicionScore = suspicionScore,
            prioritySummary = prioritySummary,
        )
    }

    fun reset() {
        drowsinessTracker.reset()
        distractionTracker.reset()
        faceMissingTracker.reset()
        phoneUsageTracker.reset()
        severeDrowsinessTracker.reset()
        objectDistractionTracker.reset()
    }

    private fun computeSuspicionScore(
        activeAlerts: List<RiskAlertState>,
        snapshot: FaceSignalSnapshot,
        objects: RiskObjectSnapshot,
    ): Int {
        var score = activeAlerts.maxOfOrNull { alert -> alert.priorityScore } ?: 0
        if (objects.hasPhone) score = max(score, 25)
        if (objects.hasDrink) score = max(score, 15)
        if (snapshot.gazeDown) score = max(score, 20)
        if (snapshot.headNodDetected) score = max(score, 28)
        if (activeAlerts.size > 1) {
            score = (score + 8).coerceAtMost(100)
        }
        return score.coerceIn(0, 100)
    }

    private fun buildEdgeSignals(
        snapshot: FaceSignalSnapshot,
        objects: RiskObjectSnapshot,
    ): Map<String, String> {
        return mapOf(
            "face_present" to snapshot.facePresent.toString(),
            "eye_closure_percent" to snapshot.eyeClosurePercent.toString(),
            "yawn_percent" to snapshot.yawnPercent.toString(),
            "head_turn_percent" to snapshot.headTurnPercent.toString(),
            "head_pitch_percent" to snapshot.headPitchPercent.toString(),
            "gaze_direction" to snapshot.gazeDirectionLabel,
            "gaze_down" to snapshot.gazeDown.toString(),
            "head_nod_detected" to snapshot.headNodDetected.toString(),
            "phone_detected" to objects.hasPhone.toString(),
            "drink_detected" to objects.hasDrink.toString(),
            "risk_object_detected" to objects.hasRiskObject.toString(),
        )
    }

    private fun evaluateRule(
        tracker: RuleTracker,
        nowEpochMs: Long,
        conditionMet: Boolean,
        minDurationMs: Long,
        cooldownMs: Long,
        type: RiskEventType,
        severityLabel: String,
        priorityScore: Int,
        localConfidencePercent: Int,
        edgeSignals: () -> Map<String, String>,
        detail: () -> String,
    ): RuleOutcome {
        if (!conditionMet) {
            tracker.conditionSinceEpochMs = null
            tracker.activeSinceEpochMs = null
            return RuleOutcome()
        }

        val conditionSince = tracker.conditionSinceEpochMs ?: nowEpochMs.also {
            tracker.conditionSinceEpochMs = it
        }
        val activeSince = tracker.activeSinceEpochMs
        val newEvent = if (
            activeSince == null &&
            nowEpochMs - conditionSince >= minDurationMs &&
            nowEpochMs - tracker.lastTriggeredAtEpochMs >= cooldownMs
        ) {
            tracker.activeSinceEpochMs = conditionSince
            tracker.lastTriggeredAtEpochMs = nowEpochMs
            RiskEventTrigger(
                id = UUID.randomUUID().toString(),
                type = type,
                title = type.displayTitle,
                detail = detail(),
                severityLabel = severityLabel,
                priorityScore = priorityScore,
                localConfidencePercent = localConfidencePercent,
                edgeSignals = edgeSignals(),
                triggeredAtEpochMs = nowEpochMs,
            )
        } else {
            null
        }

        val effectiveActiveSince = tracker.activeSinceEpochMs
        val activeAlert = if (effectiveActiveSince != null) {
            RiskAlertState(
                type = type,
                title = type.displayTitle,
                detail = detail(),
                severityLabel = severityLabel,
                priorityScore = priorityScore,
                activeSinceEpochMs = effectiveActiveSince,
            )
        } else {
            null
        }

        return RuleOutcome(
            activeAlert = activeAlert,
            newEvent = newEvent,
        )
    }

    private data class RuleTracker(
        var conditionSinceEpochMs: Long? = null,
        var activeSinceEpochMs: Long? = null,
        var lastTriggeredAtEpochMs: Long = 0L,
    ) {
        fun reset() {
            conditionSinceEpochMs = null
            activeSinceEpochMs = null
            lastTriggeredAtEpochMs = 0L
        }
    }

    private data class RuleOutcome(
        val activeAlert: RiskAlertState? = null,
        val newEvent: RiskEventTrigger? = null,
    ) {
        fun appendTo(
            activeAlerts: MutableList<RiskAlertState>,
            newEvents: MutableList<RiskEventTrigger>,
        ) {
            activeAlert?.let(activeAlerts::add)
            newEvent?.let(newEvents::add)
        }
    }

    companion object {
        private const val DROWSINESS_EYE_CLOSURE_THRESHOLD = 72
        private const val DROWSINESS_YAWN_THRESHOLD = 68
        private const val DISTRACTION_HEAD_TURN_THRESHOLD = 28
        private const val SEVERE_DROWSINESS_EYE_THRESHOLD = 78
        private const val OBJECT_DISTRACTION_HEAD_TURN_THRESHOLD = 20

        private const val DROWSINESS_MIN_DURATION_MS = 2_000L
        private const val DISTRACTION_MIN_DURATION_MS = 1_500L
        private const val FACE_MISSING_MIN_DURATION_MS = 2_000L
        private const val PHONE_USAGE_MIN_DURATION_MS = 1_200L
        private const val SEVERE_DROWSINESS_MIN_DURATION_MS = 1_600L
        private const val OBJECT_DISTRACTION_MIN_DURATION_MS = 1_200L

        private const val DROWSINESS_COOLDOWN_MS = 9_000L
        private const val DISTRACTION_COOLDOWN_MS = 7_000L
        private const val FACE_MISSING_COOLDOWN_MS = 8_000L
        private const val PHONE_USAGE_COOLDOWN_MS = 8_000L
        private const val SEVERE_DROWSINESS_COOLDOWN_MS = 10_000L
        private const val OBJECT_DISTRACTION_COOLDOWN_MS = 8_000L
    }
}
