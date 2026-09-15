package com.driveguard.mobile.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_clip_records",
    foreignKeys = [
        ForeignKey(
            entity = LocalDriveSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["filePath"], unique = true),
    ],
)
data class LocalClipRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String?,
    val backendUrl: String,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val localState: String,
    val uploadState: String,
    val sourceOrigin: String,
    val triggerEventId: String?,
    val triggerEventType: String?,
    val priorityScore: Int,
    val captureStrategy: String,
    val captureStartedAtEpochMs: Long?,
    val captureEndedAtEpochMs: Long?,
    val uploadedVideoId: String?,
    val backendJobId: String?,
    val backendSessionId: String?,
    val backendMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
