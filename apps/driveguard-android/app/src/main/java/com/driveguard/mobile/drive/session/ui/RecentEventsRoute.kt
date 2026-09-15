package com.driveguard.mobile.drive.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveguard.mobile.app.theme.Copper60
import com.driveguard.mobile.app.theme.Ink70
import com.driveguard.mobile.app.theme.Sand10
import com.driveguard.mobile.app.theme.Sand20
import com.driveguard.mobile.data.local.LocalCaptureRepository
import com.driveguard.mobile.data.local.LocalClipRecordEntity
import com.driveguard.mobile.data.local.LocalRiskEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecentEventsRoute() {
    val localCaptureRepository = LocalCaptureRepository.fromCurrentContext()
    val recentEvents = localCaptureRepository.rememberRecentRiskEvents()
    val recentClips = localCaptureRepository.rememberRecentClips()
    val recentSessions = localCaptureRepository.rememberRecentSessions()
    val clipsById = remember(recentClips) { recentClips.associateBy { it.id } }
    val clipReadyCount = recentEvents.count { it.clipRecordId != null }
    val pendingUploads = recentClips.count { clip ->
        clip.uploadState in setOf("pending", "uploading", "sent", "processing")
    }
    val completedUploads = recentClips.count { clip ->
        clip.uploadState == "completed" || clip.backendSessionId != null
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Sand10,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Sand10, Sand20),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Recent edge events",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "This screen keeps the mobile demo readable: what was suspected locally, which useful clip was linked, and what is already ready for backend review.",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink70,
            )

            RecentEventsSummaryCard(
                totalEvents = recentEvents.size,
                totalSessions = recentSessions.size,
                clipReadyCount = clipReadyCount,
                pendingUploads = pendingUploads,
                completedUploads = completedUploads,
            )

            if (recentEvents.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No recent events yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Start a drive session and let the local rules trigger. Useful clips and edge events will appear here automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink70,
                        )
                    }
                }
            } else {
                recentEvents.take(12).forEach { event ->
                    RecentEventCard(
                        event = event,
                        clip = event.clipRecordId?.let(clipsById::get),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentEventsSummaryCard(
    totalEvents: Int,
    totalSessions: Int,
    clipReadyCount: Int,
    pendingUploads: Int,
    completedUploads: Int,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Device summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryPill(label = "Events", value = totalEvents.toString())
                SummaryPill(label = "Sessions", value = totalSessions.toString())
                SummaryPill(label = "Clips ready", value = clipReadyCount.toString())
                SummaryPill(label = "Pending upload", value = pendingUploads.toString())
                SummaryPill(label = "Dashboard ready", value = completedUploads.toString())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentEventCard(
    event: LocalRiskEventEntity,
    clip: LocalClipRecordEntity?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatEventTimestamp(event.triggeredAtEpochMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink70,
                    )
                }
                Text(
                    text = "p${event.priorityScore}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Copper60,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EventPill(text = event.severity.replaceFirstChar { it.uppercase() })
                EventPill(text = "${event.localConfidencePercent}% local")
                EventPill(text = humanizeStatus(event.status))
                clip?.let {
                    EventPill(text = humanizeStatus(it.uploadState))
                }
            }

            Text(
                text = event.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )

            if (clip != null) {
                Text(
                    text = "Linked clip: ${clip.fileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        append("Upload state: ${humanizeStatus(clip.uploadState)}")
                        clip.backendSessionId?.let { sessionId ->
                            append(" | Web session: ${sessionId.take(8)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink70,
                )
                if (!clip.backendMessage.isNullOrBlank()) {
                    Text(
                        text = clip.backendMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink70,
                    )
                }
            } else {
                Text(
                    text = "No clip linked yet. The rolling buffer may still be capturing or the event may have failed clip extraction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink70,
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
) {
    Surface(
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EventPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun humanizeStatus(status: String): String {
    return status
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
}

private fun formatEventTimestamp(epochMs: Long): String {
    return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
}
