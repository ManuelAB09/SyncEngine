package com.example.syncengine.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidenciaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(incidencia: IncidenciaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incidencias: List<IncidenciaEntity>)

    @Query("SELECT * FROM incidencias_locales WHERE borrado_en IS NULL ORDER BY actualizado_en DESC")
    fun getAllIncidencias(): Flow<List<IncidenciaEntity>>

    @Query("SELECT * FROM incidencias_locales WHERE id = :id")
    suspend fun getById(id: String): IncidenciaEntity?

    @Query("SELECT * FROM incidencias_locales WHERE sync_status != 'SYNCED'")
    suspend fun getPendingSyncIncidencias(): List<IncidenciaEntity>

    @Query("SELECT * FROM incidencias_locales WHERE sync_status = 'CONFLICT'")
    fun getConflictIncidencias(): Flow<List<IncidenciaEntity>>

    @Query("UPDATE incidencias_locales SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT MAX(actualizado_en) FROM incidencias_locales WHERE sync_status = 'SYNCED'")
    suspend fun getLastSyncedTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM incidencias_locales WHERE sync_status != 'SYNCED' AND sync_status != 'CONFLICT'")
    fun getPendingCount(): Flow<Int>

    @Query("DELETE FROM incidencias_locales WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM incidencias_locales WHERE sync_status = 'LOCAL_ONLY'")
    suspend fun deleteLocalOnlyItems()
}

