package com.driveguard.mobile.settings.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

class BackendSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<BackendSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            BackendSettings(
                backendUrl = preferences[BACKEND_URL_KEY]?.trim().orEmpty()
                    .ifBlank { DEFAULT_BACKEND_URL },
                edgeDeviceId = preferences[EDGE_DEVICE_ID_KEY].orEmpty(),
            )
        }

    suspend fun saveBackendUrl(url: String) {
        val normalized = url.trim().ifBlank { DEFAULT_BACKEND_URL }
        dataStore.edit { preferences ->
            preferences[BACKEND_URL_KEY] = normalized
        }
    }

    suspend fun getOrCreateEdgeDeviceId(): String {
        val current = settings.map { it.edgeDeviceId }.first().trim()
        if (current.isNotBlank()) {
            return current
        }

        val generated = UUID.randomUUID().toString()
        dataStore.edit { preferences ->
            preferences[EDGE_DEVICE_ID_KEY] = generated
        }
        return generated
    }

    @Composable
    fun rememberBackendUrl(): String {
        return settings.collectAsState(initial = BackendSettings()).value.backendUrl
    }

    companion object {
        private const val STORE_NAME = "driveguard_settings.preferences_pb"
        private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
        private val EDGE_DEVICE_ID_KEY = stringPreferencesKey("edge_device_id")

        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000"

        @Volatile
        private var instance: BackendSettingsRepository? = null

        fun getInstance(context: Context): BackendSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: BackendSettingsRepository(
                    dataStore = PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                        produceFile = { context.preferencesDataStoreFile(STORE_NAME) },
                    ),
                ).also { instance = it }
            }
        }

        @Composable
        fun fromCurrentContext(): BackendSettingsRepository {
            val appContext = LocalContext.current.applicationContext
            return remember(appContext) {
                getInstance(appContext)
            }
        }
    }
}
