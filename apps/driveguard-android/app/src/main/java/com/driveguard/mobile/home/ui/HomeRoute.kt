package com.driveguard.mobile.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveguard.mobile.app.theme.Copper60
import com.driveguard.mobile.app.theme.Ink70
import com.driveguard.mobile.app.theme.Sand10
import com.driveguard.mobile.app.theme.Sand20
import com.driveguard.mobile.settings.data.BackendSettings
import com.driveguard.mobile.settings.data.BackendSettingsRepository

@Composable
fun HomeRoute(
    repository: BackendSettingsRepository,
    onOpenDriveSession: () -> Unit,
    onOpenRecentEvents: () -> Unit,
) {
    val settings by repository.settings.collectAsState(initial = BackendSettings())
    val edgeDeviceLabel = settings.edgeDeviceId.ifBlank { "Generated on first backend sync" }

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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "DriveGuard Android",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Demo-ready edge capture app for driver monitoring. The phone now runs local face and object signals, raises useful alerts, preserves relevant clips, and hands sessions off to the backend dashboard.",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink70,
            )

            HomeHeroCard(
                backendUrl = settings.backendUrl,
                edgeDeviceLabel = edgeDeviceLabel,
            )

            HomeStageCard(
                title = "Demo flow",
                body = "1. Open Drive and start a session.\n2. Let the phone watch face + object risk cues locally.\n3. Keep only useful clips around suspicious moments.\n4. Review recent edge events on device or in the web dashboard after upload.",
            )

            HomeStageCard(
                title = "What is live right now",
                body = "Camera preview, MediaPipe face signals, LiteRT YOLOv8n object detection, local rule fusion, rolling clip buffer, Android edge payloads, and dashboard-visible mobile sessions.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenDriveSession,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Open drive demo")
                }
                OutlinedButton(
                    onClick = onOpenRecentEvents,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Recent events")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeHeroCard(
    backendUrl: String,
    edgeDeviceLabel: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edge pipeline ready",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "This build is oriented around one demonstrable product path: detect locally on Android, keep only useful evidence, then sync structured edge context into the backend and dashboard.",
                style = MaterialTheme.typography.bodyLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Pill(text = "Face signals")
                Pill(text = "LiteRT YOLOv8n")
                Pill(text = "Useful clips")
                Pill(text = "Edge sessions")
                Pill(text = "Dashboard handoff")
            }
            Text(
                text = "Current backend target",
                style = MaterialTheme.typography.labelLarge,
                color = Copper60,
            )
            Text(
                text = backendUrl,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Edge device id: $edgeDeviceLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
        }
    }
}

@Composable
private fun HomeStageCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink70,
            )
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
