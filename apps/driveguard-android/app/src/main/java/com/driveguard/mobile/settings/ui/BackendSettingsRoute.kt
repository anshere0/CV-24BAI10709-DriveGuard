package com.driveguard.mobile.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driveguard.mobile.app.theme.Ink70
import com.driveguard.mobile.app.theme.Sand10
import com.driveguard.mobile.settings.data.BackendSettings
import com.driveguard.mobile.settings.data.BackendSettingsRepository
import kotlinx.coroutines.launch

@Composable
fun BackendSettingsRoute(
    repository: BackendSettingsRepository,
) {
    val coroutineScope = rememberCoroutineScope()
    val settings by repository.settings.collectAsState(initial = BackendSettings())
    var input by rememberSaveable { mutableStateOf("") }
    var statusMessage by rememberSaveable {
        mutableStateOf("Use the emulator loopback for local testing or your LAN IP for a real phone.")
    }

    LaunchedEffect(settings.backendUrl) {
        if (input.isBlank() || input == BackendSettingsRepository.DEFAULT_BACKEND_URL || input == settings.backendUrl) {
            input = settings.backendUrl
        }
    }

    LaunchedEffect(settings.edgeDeviceId) {
        if (settings.edgeDeviceId.isBlank()) {
            repository.getOrCreateEdgeDeviceId()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Sand10,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edge settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "This screen controls how the Android edge client identifies itself and where useful clips, sessions, and edge events are sent.",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink70,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Android edge identity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Edge device id",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink70,
                    )
                    Text(
                        text = settings.edgeDeviceId.ifBlank { "Creating device id..." },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "This id lets the backend understand Android as an edge client instead of a raw video uploader.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Backend target",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Backend base URL") },
                        supportingText = {
                            Text("Examples: http://10.0.2.2:8000 or http://192.168.1.24:8000")
                        },
                        singleLine = true,
                    )

                    Button(
                        onClick = {
                            val normalized = input.trim().removeSuffix("/")
                            val persistedUrl = normalized.ifBlank { BackendSettingsRepository.DEFAULT_BACKEND_URL }
                            input = persistedUrl
                            statusMessage = "Backend target saved for the next drive session."
                            coroutineScope.launch {
                                repository.saveBackendUrl(persistedUrl)
                            }
                        },
                    ) {
                        Text("Save backend URL")
                    }

                    TextButton(
                        onClick = {
                            input = BackendSettingsRepository.DEFAULT_BACKEND_URL
                            statusMessage = "Emulator default restored."
                            coroutineScope.launch {
                                repository.saveBackendUrl(BackendSettingsRepository.DEFAULT_BACKEND_URL)
                            }
                        },
                    ) {
                        Text("Reset to emulator default")
                    }

                    Text(
                        text = "Current saved target: ${settings.backendUrl}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink70,
                    )
                }
            }
        }
    }
}
