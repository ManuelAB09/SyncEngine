package com.example.syncengine.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

enum class SyncStatus {
    LOCAL_ONLY, PENDING_INSERT, PENDING_UPDATE, PENDING_DELETE, SYNCED, CONFLICT
}

@Entity(tableName = "incidencias_locales")
data class IncidenciaEntity(
    @PrimaryKey val id: String,
    val usuario_id: String,
    val titulo: String,
    val descripcion: String,
    val estado: String,
    val latitud: Double?,
    val longitud: Double?,
    val foto_path: String?,       // Ruta local del fichero de imagen
    val foto_url: String?,        // URL pública en Supabase Storage
    val version: Int,
    val creado_en: Long,
    val actualizado_en: Long,
    val borrado_en: Long?,
    val sync_status: SyncStatus,
    /**
     * Hash MD5 del contenido del servidor en el momento del último sync.
     * Se usa para detectar si el servidor cambió (incluso sin trigger).
     * NO se modifica durante ediciones locales.
     */
    @ColumnInfo(defaultValue = "")
    val synced_checksum: String = ""
) {
    companion object {
        /**
         * Calcula un checksum MD5 a partir de los campos de contenido.
         * Sirve tanto para datos locales como remotos (DTO).
         */
        fun computeChecksum(
            titulo: String,
            descripcion: String?,
            estado: String,
            latitud: Double?,
            longitud: Double?,
            foto_url: String?,
            version: Int
        ): String {
            val raw = "$titulo|${descripcion.orEmpty()}|$estado|$latitud|$longitud|$foto_url|$version"
            val md5 = MessageDigest.getInstance("MD5")
            return md5.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

