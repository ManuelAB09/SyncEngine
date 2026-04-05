package com.example.syncengine.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Almacena la "foto" remota cuando se detecta un conflicto.
 * Así el usuario puede comparar su versión local con la del servidor.
 */
@Entity(tableName = "pending_conflicts")
data class PendingConflictEntity(
    @PrimaryKey val incidencia_id: String,
    val remote_titulo: String,
    val remote_descripcion: String,
    val remote_estado: String,
    val remote_latitud: Double?,
    val remote_longitud: Double?,
    val remote_foto_url: String?,
    val remote_google_maps_url: String?,
    val remote_version: Int,
    val remote_creado_en: Long,
    val remote_actualizado_en: Long,
    val remote_borrado_en: Long?
)

@Dao
interface ConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: PendingConflictEntity)

    @Query("SELECT * FROM pending_conflicts")
    fun getAll(): Flow<List<PendingConflictEntity>>

    @Query("SELECT * FROM pending_conflicts WHERE incidencia_id = :id")
    suspend fun getById(id: String): PendingConflictEntity?

    @Query("DELETE FROM pending_conflicts WHERE incidencia_id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM pending_conflicts")
    fun getConflictCount(): Flow<Int>
}

