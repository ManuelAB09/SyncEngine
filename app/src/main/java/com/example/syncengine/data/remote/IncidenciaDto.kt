package com.example.syncengine.data.remote

import android.annotation.SuppressLint
import com.example.syncengine.data.local.IncidenciaEntity
import com.example.syncengine.data.local.SyncStatus
import com.example.syncengine.util.DateUtils
import kotlinx.serialization.Serializable

/**
 * DTO que representa una incidencia tal como viene/va de Supabase.
 * Los campos coinciden con las columnas de la tabla "incidencias" en PostgreSQL.
 */
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class IncidenciaDto(
    val id: String,
    val usuario_id: String,
    val titulo: String,
    val descripcion: String? = null,
    val estado: String = "pendiente",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val foto_url: String? = null,          // URL pública en Supabase Storage
    val version: Int = 1,
    val creado_en: String? = null,
    val actualizado_en: String? = null,
    val borrado_en: String? = null
)

// ─── Mappers ─────────────────────────────────────────────────────

/**
 * Convierte un DTO remoto a entidad local Room.
 */
fun IncidenciaDto.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): IncidenciaEntity {
    return IncidenciaEntity(
        id = id,
        usuario_id = usuario_id,
        titulo = titulo,
        descripcion = descripcion ?: "",
        estado = estado,
        latitud = latitud,
        longitud = longitud,
        foto_path = null,           // Se descargará aparte si hace falta
        foto_url = foto_url,
        version = version,
        creado_en = creado_en?.let { DateUtils.parseIsoToEpochMillis(it) } ?: System.currentTimeMillis(),
        actualizado_en = actualizado_en?.let { DateUtils.parseIsoToEpochMillis(it) } ?: System.currentTimeMillis(),
        borrado_en = borrado_en?.let { DateUtils.parseIsoToEpochMillis(it) },
        sync_status = syncStatus
    )
}

/**
 * Convierte una entidad local Room a DTO para enviar a Supabase.
 */
fun IncidenciaEntity.toDto(): IncidenciaDto {
    return IncidenciaDto(
        id = id,
        usuario_id = usuario_id,
        titulo = titulo,
        descripcion = descripcion,
        estado = estado,
        latitud = latitud,
        longitud = longitud,
        foto_url = foto_url,       // Se rellena tras subir la foto a Storage
        version = version,
        creado_en = DateUtils.epochMillisToIso(creado_en),
        actualizado_en = DateUtils.epochMillisToIso(actualizado_en),
        borrado_en = borrado_en?.let { DateUtils.epochMillisToIso(it) }
    )
}

