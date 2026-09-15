package com.driveguard.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_drive_sessions")
data class LocalDriveSessionEntity(
    @PrimaryKey val id: String,
    val backendUrl: String,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
