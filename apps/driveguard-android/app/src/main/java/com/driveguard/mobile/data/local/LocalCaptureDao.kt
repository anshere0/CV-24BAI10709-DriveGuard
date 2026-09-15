package com.driveguard.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalCaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LocalDriveSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: LocalClipRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskEvent(event: LocalRiskEventEntity)

    @Query(
        """
        UPDATE local_drive_sessions
        SET status = :status, endedAtEpochMs = :endedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :sessionId
        """,
    )
    suspend fun finishSession(
        sessionId: String,
        status: String,
        endedAtEpochMs: Long,
        updatedAtEpochMs: Long,
    )

    @Query(
        """
        UPDATE local_clip_records
        SET uploadState = :uploadState,
            uploadedVideoId = :uploadedVideoId,
            backendJobId = :backendJobId,
            backendSessionId = :backendSessionId,
            backendMessage = :backendMessage,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :clipId
        """,
    )
    suspend fun updateClipUploadState(
        clipId: Long,
        uploadState: String,
        uploadedVideoId: String?,
        backendJobId: String?,
        backendSessionId: String?,
        backendMessage: String?,
        updatedAtEpochMs: Long,
    )

    @Query(
        """
        SELECT * FROM local_drive_sessions
        ORDER BY updatedAtEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentSessions(limit: Int = 5): Flow<List<LocalDriveSessionEntity>>

    @Query(
        """
        SELECT * FROM local_clip_records
        ORDER BY updatedAtEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentClips(limit: Int = 8): Flow<List<LocalClipRecordEntity>>

    @Query(
        """
        SELECT * FROM local_risk_events
        ORDER BY priorityScore DESC, triggeredAtEpochMs DESC
        LIMIT :limit
        """,
    )
    fun observeRecentRiskEvents(limit: Int = 8): Flow<List<LocalRiskEventEntity>>

    @Query(
        """
        SELECT * FROM local_clip_records
        WHERE uploadState IN ('pending', 'failed')
        ORDER BY priorityScore DESC, createdAtEpochMs ASC
        LIMIT :limit
        """,
    )
    suspend fun listPendingClips(limit: Int = 10): List<LocalClipRecordEntity>

    @Query("SELECT * FROM local_clip_records WHERE id = :clipId LIMIT 1")
    suspend fun getClipById(clipId: Long): LocalClipRecordEntity?

    @Query("SELECT * FROM local_risk_events WHERE id = :eventId LIMIT 1")
    suspend fun getRiskEventById(eventId: String): LocalRiskEventEntity?

    @Query("SELECT * FROM local_drive_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): LocalDriveSessionEntity?

    @Query(
        """
        UPDATE local_risk_events
        SET status = :status,
            clipRecordId = :clipRecordId,
            detail = :detail,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :eventId
        """,
    )
    suspend fun updateRiskEvent(
        eventId: String,
        status: String,
        clipRecordId: Long?,
        detail: String,
        updatedAtEpochMs: Long,
    )
}
