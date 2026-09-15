package com.driveguard.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_risk_events",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["clipRecordId"]),
        Index(value = ["triggeredAtEpochMs"]),
    ],
)
data class LocalRiskEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String?,
    val eventType: String,
    val title: String,
    val detail: String,
    val severity: String,
    val priorityScore: Int,
    val localConfidencePercent: Int,
    val edgeSignalsJson: String,
    val status: String,
    val clipRecordId: Long?,
    val triggeredAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
