package com.driveguard.mobile.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class DriveGuardBackendClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun upsertMobileDevice(
        baseUrl: String,
        device: EdgeDevicePayload,
    ): EdgeDeviceResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("platform", device.platform)
            .put("display_name", device.displayName)
            .put("manufacturer", device.manufacturer)
            .put("model", device.model)
            .put("os_version", device.osVersion)
            .put("app_version", device.appVersion)
            .toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        requestJson(
            url = "${baseUrl.normalizeBaseUrl()}/mobile/devices/${device.id}",
            method = "PUT",
            body = body,
        ) { json ->
            EdgeDeviceResponse(
                id = json.getString("id"),
                platform = json.getString("platform"),
                displayName = json.optString("display_name").ifBlank { null },
                manufacturer = json.optString("manufacturer").ifBlank { null },
                model = json.optString("model").ifBlank { null },
                osVersion = json.optString("os_version").ifBlank { null },
                appVersion = json.optString("app_version").ifBlank { null },
            )
        }
    }

    suspend fun upsertDeviceSession(
        baseUrl: String,
        payload: EdgeDeviceSessionPayload,
    ): EdgeDeviceSessionResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("device_id", payload.deviceId)
            .put("source_origin", payload.sourceOrigin)
            .put("backend_url", payload.backendUrl)
            .put("status", payload.status)
            .put("started_at", payload.startedAtIso)
            .put("ended_at", payload.endedAtIso)
            .toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        requestJson(
            url = "${baseUrl.normalizeBaseUrl()}/mobile/device-sessions/${payload.id}",
            method = "PUT",
            body = body,
        ) { json ->
            EdgeDeviceSessionResponse(
                id = json.getString("id"),
                deviceId = json.getString("device_id"),
                status = json.getString("status"),
                sourceOrigin = json.getString("source_origin"),
                startedAtIso = json.getString("started_at"),
                endedAtIso = json.optString("ended_at").ifBlank { null },
            )
        }
    }

    suspend fun upsertEdgeEvent(
        baseUrl: String,
        payload: EdgeEventPayload,
    ): EdgeEventResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("device_session_id", payload.deviceSessionId)
            .put("uploaded_video_id", payload.uploadedVideoId)
            .put("analysis_job_id", payload.analysisJobId)
            .put("analysis_session_id", payload.analysisSessionId)
            .put("source_origin", payload.sourceOrigin)
            .put("suspected_event_type", payload.suspectedEventType)
            .put("suspected_event_title", payload.suspectedEventTitle)
            .put("local_confidence", payload.localConfidence)
            .put("local_priority_score", payload.localPriorityScore)
            .put("triggered_at", payload.triggeredAtIso)
            .put("capture_started_at", payload.captureStartedAtIso)
            .put("capture_ended_at", payload.captureEndedAtIso)
            .put("clip_file_name", payload.clipFileName)
            .put("edge_signals", JSONObject(payload.edgeSignals))
            .toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        requestJson(
            url = "${baseUrl.normalizeBaseUrl()}/mobile/edge-events/${payload.id}",
            method = "PUT",
            body = body,
        ) { json ->
            EdgeEventResponse(
                id = json.getString("id"),
                deviceSessionId = json.getString("device_session_id"),
                uploadedVideoId = json.optString("uploaded_video_id").ifBlank { null },
                analysisJobId = json.optString("analysis_job_id").ifBlank { null },
                analysisSessionId = json.optString("analysis_session_id").ifBlank { null },
                serverConfirmationStatus = json.optString("server_confirmation_status").ifBlank { "pending" },
                serverConfirmedEventTypes = buildList {
                    val array = json.optJSONArray("server_confirmed_event_types") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        add(array.optString(index))
                    }
                },
            )
        }
    }

    suspend fun uploadVideo(
        baseUrl: String,
        clipFile: File,
        sourceOrigin: String = "android_upload",
        onProgress: (Int) -> Unit,
    ): UploadedVideoPayload = withContext(Dispatchers.IO) {
        if (!clipFile.exists()) {
            throw IOException("Local clip file does not exist anymore.")
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source_origin", sourceOrigin)
            .addFormDataPart(
                name = "file",
                filename = clipFile.name,
                body = ProgressRequestBody(
                    delegate = clipFile.asRequestBody("video/mp4".toMediaTypeOrNull()),
                    onProgress = onProgress,
                ),
            )
            .build()

        val request = Request.Builder()
            .url("${baseUrl.normalizeBaseUrl()}/videos")
            .post(multipartBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.requireSuccessBody()
            onProgress(100)
            val json = JSONObject(body)
            UploadedVideoPayload(
                id = json.getString("id"),
                originalFilename = json.getString("original_filename"),
                storedPath = json.getString("stored_path"),
                sourceOrigin = json.getString("source_origin"),
                sizeBytes = json.optLong("size_bytes", clipFile.length()),
            )
        }
    }

    suspend fun createAnalysisJob(
        baseUrl: String,
        uploadedVideoId: String,
        configPath: String = "config.toml",
    ): AnalysisJobPayload = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("source_type", "video")
            .put("source_origin", "android_upload")
            .put("uploaded_video_ids", JSONArray().put(uploadedVideoId))
            .put("config_path", configPath)
            .toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        requestJson(
            url = "${baseUrl.normalizeBaseUrl()}/analysis-jobs",
            method = "POST",
            body = body,
        ) { json -> AnalysisJobPayload.fromJson(json) }
    }

    suspend fun fetchJob(
        baseUrl: String,
        jobId: String,
    ): AnalysisJobPayload = withContext(Dispatchers.IO) {
        requestJson(
            url = "${baseUrl.normalizeBaseUrl()}/analysis-jobs/$jobId",
            method = "GET",
            body = null,
        ) { json -> AnalysisJobPayload.fromJson(json) }
    }

    private fun <T> requestJson(
        url: String,
        method: String,
        body: RequestBody?,
        parse: (JSONObject) -> T,
    ): T {
        val requestBuilder = Request.Builder().url(url)
        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(body ?: EMPTY_JSON_BODY)
            "PUT" -> requestBuilder.put(body ?: EMPTY_JSON_BODY)
            else -> error("Unsupported HTTP method: $method")
        }
        if (body != null) {
            requestBuilder.addHeader("Content-Type", "application/json")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            return parse(JSONObject(response.requireSuccessBody()))
        }
    }
}

data class EdgeDevicePayload(
    val id: String,
    val platform: String = "android",
    val displayName: String?,
    val manufacturer: String?,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?,
)

data class EdgeDeviceResponse(
    val id: String,
    val platform: String,
    val displayName: String?,
    val manufacturer: String?,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?,
)

data class EdgeDeviceSessionPayload(
    val id: String,
    val deviceId: String,
    val status: String,
    val startedAtIso: String,
    val endedAtIso: String?,
    val backendUrl: String,
    val sourceOrigin: String = "android_upload",
)

data class EdgeDeviceSessionResponse(
    val id: String,
    val deviceId: String,
    val status: String,
    val sourceOrigin: String,
    val startedAtIso: String,
    val endedAtIso: String?,
)

data class EdgeEventPayload(
    val id: String,
    val deviceSessionId: String,
    val suspectedEventType: String,
    val suspectedEventTitle: String?,
    val localConfidence: Double,
    val localPriorityScore: Int,
    val triggeredAtIso: String,
    val captureStartedAtIso: String?,
    val captureEndedAtIso: String?,
    val clipFileName: String?,
    val edgeSignals: Map<String, String>,
    val uploadedVideoId: String? = null,
    val analysisJobId: String? = null,
    val analysisSessionId: String? = null,
    val sourceOrigin: String = "android_upload",
)

data class EdgeEventResponse(
    val id: String,
    val deviceSessionId: String,
    val uploadedVideoId: String?,
    val analysisJobId: String?,
    val analysisSessionId: String?,
    val serverConfirmationStatus: String,
    val serverConfirmedEventTypes: List<String>,
)

data class UploadedVideoPayload(
    val id: String,
    val originalFilename: String,
    val storedPath: String,
    val sourceOrigin: String,
    val sizeBytes: Long,
)

data class AnalysisJobPayload(
    val id: String,
    val status: String,
    val sourceOrigin: String,
    val progressPercent: Float,
    val progressPhase: String,
    val progressMessage: String?,
    val errorMessage: String?,
    val sessionIds: List<String>,
) {
    companion object {
        fun fromJson(json: JSONObject): AnalysisJobPayload {
            val sessionsJson = json.optJSONArray("sessions")
            val sessionIds = buildList {
                if (sessionsJson != null) {
                    for (index in 0 until sessionsJson.length()) {
                        val item = sessionsJson.optJSONObject(index) ?: continue
                        add(item.optString("id"))
                    }
                }
            }.filter { it.isNotBlank() }

            return AnalysisJobPayload(
                id = json.getString("id"),
                status = json.getString("status"),
                sourceOrigin = json.optString("source_origin"),
                progressPercent = json.optDouble("progress_percent", 0.0).toFloat(),
                progressPhase = json.optString("progress_phase"),
                progressMessage = json.optString("progress_message").ifBlank { null },
                errorMessage = json.optString("error_message").ifBlank { null },
                sessionIds = sessionIds,
            )
        }
    }
}

private fun String.normalizeBaseUrl(): String = trim().trimEnd('/')

private fun Response.requireSuccessBody(): String {
    val bodyString = body?.string().orEmpty()
    if (isSuccessful) {
        return bodyString
    }

    val detail = try {
        JSONObject(bodyString).optString("detail").ifBlank { bodyString }
    } catch (_: Exception) {
        bodyString
    }
    throw IOException(detail.ifBlank { "Request failed with status $code." })
}

private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength() = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val contentLength = contentLength()
        val buffer = Buffer()
        delegate.writeTo(buffer)
        val source = buffer.inputStream().source()
        var totalRead = 0L
        var lastPercent = -1

        source.use { bufferedSource ->
            while (true) {
                val read = bufferedSource.read(sink.buffer, 8_192)
                if (read == -1L) {
                    break
                }
                totalRead += read
                sink.flush()

                if (contentLength > 0) {
                    val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            }
        }
    }
}

private val EMPTY_JSON_BODY = "{}".toRequestBody("application/json".toMediaTypeOrNull())
