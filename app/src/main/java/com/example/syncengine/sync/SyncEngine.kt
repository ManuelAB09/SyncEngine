@file:OptIn(
    InternalSerializationApi::class,
    ExperimentalSerializationApi::class
)

package com.example.syncengine.sync

import android.content.Context
import android.util.Log
import com.example.syncengine.data.local.ConflictDao
import com.example.syncengine.data.local.IncidenciaDao
import com.example.syncengine.data.local.IncidenciaEntity
import com.example.syncengine.data.local.PendingConflictEntity
import com.example.syncengine.data.local.SyncStatus
import com.example.syncengine.data.remote.IncidenciaDto
import com.example.syncengine.data.remote.SupabaseNetwork
import com.example.syncengine.data.remote.toDto
import com.example.syncengine.data.remote.toEntity
import com.example.syncengine.util.DateUtils
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Payload ligero para soft-delete: envía SOLO los campos necesarios.
 * Al no tener valores por defecto, todos los campos se serializan SIEMPRE
 * (inmune al encodeDefaults=false de Supabase-kt).
 */
@Serializable
data class SoftDeletePayload(
    val borrado_en: String,
    val actualizado_en: String
)

/**
 * Motor de sincronización bidireccional.
 *
 * Flujo:
 * 1. PUSH → Sube cambios locales pendientes al servidor,
 *    pero PRIMERO comprueba si el servidor cambió (detección de conflictos por VERSIÓN).
 * 2. PULL → Descarga cambios del servidor desde la última sincronización.
 *
 * Se ejecuta PUSH antes de PULL para minimizar conflictos.
 */
class SyncEngine(
    private val incidenciaDao: IncidenciaDao,
    private val conflictDao: ConflictDao,
    private val context: Context
) {
    companion object {
        private const val TAG = "SyncEngine"
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }

    private val client = SupabaseNetwork.client

    /**
     * Ejecuta un ciclo completo de sincronización: PUSH luego PULL.
     */
    suspend fun sync(): SyncResult {
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        val errors = mutableListOf<String>()

        try {
            val pushResult = push()
            pushed = pushResult.first
            conflicts += pushResult.second
            Log.d(TAG, "PUSH completado: $pushed subidos, ${pushResult.second} conflictos")
        } catch (e: Exception) {
            Log.e(TAG, "Error en PUSH", e)
            errors.add("PUSH: ${classifyError(e)}")
        }

        try {
            val pullResult = pull()
            pulled = pullResult.first
            conflicts += pullResult.second
            Log.d(TAG, "PULL completado: $pulled descargados, ${pullResult.second} conflictos")
        } catch (e: Exception) {
            Log.e(TAG, "Error en PULL", e)
            errors.add("PULL: ${classifyError(e)}")
        }

        return SyncResult(pushed, pulled, conflicts, errors)
    }

    // ─── PUSH: Subir cambios locales al servidor ────────────────

    /**
     * Sube todos los registros con sync_status pendiente.
     * ANTES de subir un UPDATE, consulta el servidor para
     * comprobar que nadie más modificó el registro (detección de conflictos por checksum).
     *
     * Retorna (registros subidos, conflictos detectados).
     */
    private suspend fun push(): Pair<Int, Int> {
        val pending = incidenciaDao.getPendingSyncIncidencias()
        var count = 0
        var conflicts = 0

        // Limpiar registros LOCAL_ONLY (creados y borrados sin llegar al server)
        incidenciaDao.deleteLocalOnlyItems()

        for (entity in pending) {
            try {
                when (entity.sync_status) {
                    SyncStatus.PENDING_INSERT -> {
                        val entityWithUrl = uploadPhotoIfNeeded(entity)
                        val dto = entityWithUrl.toDto()
                        client.from("incidencias").upsert(dto)
                        // Tras subir, guardar checksum del estado sincronizado
                        val checksum = computeDtoChecksum(dto)
                        incidenciaDao.insertOrUpdate(
                            entityWithUrl.copy(
                                sync_status = SyncStatus.SYNCED,
                                synced_checksum = checksum
                            )
                        )
                        count++
                    }

                    SyncStatus.PENDING_UPDATE -> {
                        val serverConflict = checkServerConflict(entity)
                        if (serverConflict != null) {
                            markAsConflict(entity, serverConflict)
                            conflicts++
                            Log.w(TAG, "Conflicto detectado en PUSH para ${entity.id}")
                        } else {
                            val entityWithUrl = uploadPhotoIfNeeded(entity)
                            // Incrementar versión SOLO al subir
                            val newVersion = entityWithUrl.version + 1
                            val dtoToSend = entityWithUrl.copy(version = newVersion).toDto()
                            client.from("incidencias").update(dtoToSend) {
                                filter { eq("id", entity.id) }
                            }
                            val checksum = computeDtoChecksum(dtoToSend)
                            incidenciaDao.insertOrUpdate(
                                entityWithUrl.copy(
                                    version = newVersion,
                                    sync_status = SyncStatus.SYNCED,
                                    synced_checksum = checksum
                                )
                            )
                            count++
                        }
                    }

                    SyncStatus.PENDING_DELETE -> {
                        val payload = SoftDeletePayload(
                            borrado_en = DateUtils.epochMillisToIso(
                                entity.borrado_en ?: System.currentTimeMillis()
                            ),
                            actualizado_en = DateUtils.epochMillisToIso(entity.actualizado_en)
                        )
                        Log.d(TAG, "Enviando soft-delete de ${entity.id}, borrado_en=${payload.borrado_en}")
                        client.from("incidencias").update(payload) {
                            filter { eq("id", entity.id) }
                        }
                        incidenciaDao.deleteById(entity.id)
                        count++
                        Log.d(TAG, "Soft-delete sincronizado y eliminado localmente: ${entity.id}")
                    }

                    SyncStatus.LOCAL_ONLY -> {
                        // Fue creado y borrado sin llegar al server → ya limpiado arriba
                    }

                    else -> { /* SYNCED o CONFLICT → no hacer nada en PUSH */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error subiendo incidencia ${entity.id}", e)
            }
        }
        return Pair(count, conflicts)
    }

    // ─── Subida de fotos a Supabase Storage ─────────────────────

    /**
     * Si la entidad tiene una foto local sin URL remota, la sube a Supabase Storage
     * y devuelve la entidad actualizada con la foto_url.
     */
    private suspend fun uploadPhotoIfNeeded(entity: IncidenciaEntity): IncidenciaEntity {
        val localPath = entity.foto_path ?: return entity
        if (entity.foto_url != null) return entity // Ya tiene URL, no re-subir

        val file = File(localPath)
        if (!file.exists()) return entity

        return try {
            val storagePath = "incidencias/${entity.id}.jpg"
            val bucket = client.storage.from("incidencia-fotos")
            bucket.upload(storagePath, file.readBytes(), upsert = true)
            val publicUrl = bucket.publicUrl(storagePath)

            Log.d(TAG, "Foto subida: $publicUrl")
            val updated = entity.copy(foto_url = publicUrl)
            incidenciaDao.insertOrUpdate(updated.copy(sync_status = entity.sync_status))
            updated
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo foto para ${entity.id}", e)
            entity // Continuar sin la foto
        }
    }

    // ─── Detección de conflictos ─────────────────────────────────

    /**
     * Calcula el checksum de un DTO del servidor para comparar con el almacenado.
     */
    private fun computeDtoChecksum(dto: IncidenciaDto): String {
        return IncidenciaEntity.computeChecksum(
            titulo = dto.titulo,
            descripcion = dto.descripcion,
            estado = dto.estado,
            latitud = dto.latitud,
            longitud = dto.longitud,
            foto_url = dto.foto_url,
            version = dto.version,
            google_maps_url = dto.google_maps_url
        )
    }

    /**
     * Consulta el servidor para ver si un registro fue modificado
     * desde nuestra última sincronización.
     *
     * Estrategia de detección (de más fiable a menos):
     * 1. CHECKSUM: compara hash del contenido del servidor con el hash
     *    guardado en el último sync. Detecta CUALQUIER cambio, incluso
     *    ediciones directas en Supabase sin trigger.
     * 2. VERSIÓN: si no hay checksum guardado (entidad migrada), compara
     *    la versión del servidor con la local.
     *
     * Retorna el DTO del servidor si hay conflicto, o null si es seguro subir.
     */
    private suspend fun checkServerConflict(
        localEntity: IncidenciaEntity
    ): IncidenciaDto? {
        val serverRecords: List<IncidenciaDto> = client.from("incidencias")
            .select { filter { eq("id", localEntity.id) } }
            .decodeList()

        val serverDto = serverRecords.firstOrNull()
            ?: return null // No existe en servidor → no hay conflicto

        // 1) Detección por CHECKSUM (preferida)
        val storedChecksum = localEntity.synced_checksum
        if (storedChecksum.isNotEmpty()) {
            val serverChecksum = computeDtoChecksum(serverDto)
            if (serverChecksum != storedChecksum) {
                Log.d(TAG, "Conflicto por checksum: stored=$storedChecksum server=$serverChecksum")
                return serverDto
            }
            return null // Checksums iguales → servidor no cambió → seguro subir
        }

        // 2) Fallback por VERSIÓN (para entidades migradas sin checksum)
        return if (serverDto.version != localEntity.version) {
            Log.d(TAG, "Conflicto por versión: local v${localEntity.version} vs server v${serverDto.version}")
            serverDto
        } else {
            null
        }
    }

    /**
     * Marca un registro local como CONFLICT y guarda la versión
     * remota para que el usuario pueda compararlas.
     */
    private suspend fun markAsConflict(
        localEntity: IncidenciaEntity,
        serverDto: IncidenciaDto
    ) {
        incidenciaDao.updateSyncStatus(localEntity.id, SyncStatus.CONFLICT)

        val remoteEntity = serverDto.toEntity()
        conflictDao.insert(
            PendingConflictEntity(
                incidencia_id = serverDto.id,
                remote_titulo = remoteEntity.titulo,
                remote_descripcion = remoteEntity.descripcion,
                remote_estado = remoteEntity.estado,
                remote_latitud = remoteEntity.latitud,
                remote_longitud = remoteEntity.longitud,
                remote_foto_url = remoteEntity.foto_url,
                remote_google_maps_url = remoteEntity.google_maps_url,
                remote_version = remoteEntity.version,
                remote_creado_en = remoteEntity.creado_en,
                remote_actualizado_en = remoteEntity.actualizado_en,
                remote_borrado_en = remoteEntity.borrado_en
            )
        )
    }

    // ─── PULL: Descargar cambios del servidor ───────────────────

    /**
     * Descarga registros modificados desde la última sincronización, o todos si es la primera vez.
     * Retorna (registros descargados, conflictos detectados).
     */
    private suspend fun pull(): Pair<Int, Int> {
        val lastSync = getLastSyncTimestamp()
        var pulled = 0
        var conflicts = 0

        val userId = client.auth.currentUserOrNull()?.id ?: return Pair(0, 0)

        val remoteChanges: List<IncidenciaDto> = if (lastSync > 0) {
            val isoTimestamp = DateUtils.epochMillisToIso(lastSync)
            client.from("incidencias")
                .select {
                    filter {
                        gte("actualizado_en", isoTimestamp)
                        eq("usuario_id", userId)
                    }
                }
                .decodeList()
        } else {
            client.from("incidencias")
                .select {
                    filter {
                        eq("usuario_id", userId)
                    }
                }
                .decodeList()
        }

        for (dto in remoteChanges) {
            // Ignorar registros borrados en el servidor
            if (dto.borrado_en != null) {
                // Si existía localmente, eliminarlo también
                val local = incidenciaDao.getById(dto.id)
                if (local != null) {
                    incidenciaDao.deleteById(dto.id)
                    Log.d(TAG, "PULL: eliminado localmente ${dto.id} (borrado en servidor)")
                }
                pulled++
                continue
            }

            val local = incidenciaDao.getById(dto.id)
            val serverChecksum = computeDtoChecksum(dto)

            if (local == null) {
                incidenciaDao.insertOrUpdate(
                    dto.toEntity(SyncStatus.SYNCED).copy(synced_checksum = serverChecksum)
                )
                pulled++
            } else if (local.sync_status == SyncStatus.SYNCED) {
                // Conservar foto_path local si existe
                val merged = dto.toEntity(SyncStatus.SYNCED).copy(
                    foto_path = local.foto_path,
                    synced_checksum = serverChecksum
                )
                incidenciaDao.insertOrUpdate(merged)
                pulled++
            } else if (local.sync_status == SyncStatus.CONFLICT) {
                Log.d(TAG, "PULL: ${dto.id} ya es CONFLICT, saltando")
            } else {
                // Local tiene cambios pendientes Y el servidor también cambió
                // Comparar checksum del servidor con el que guardamos al sincronizar
                val storedChecksum = local.synced_checksum
                val serverChanged = if (storedChecksum.isNotEmpty()) {
                    serverChecksum != storedChecksum
                } else {
                    // Fallback: comparar versiones si no hay checksum
                    dto.version != local.version
                }

                if (serverChanged) {
                    markAsConflict(local, dto)
                    conflicts++
                    Log.d(TAG, "PULL: conflicto detectado para ${dto.id}")
                } else {
                    Log.d(TAG, "PULL: ${dto.id} tiene cambios locales pendientes pero el servidor no cambió, saltando")
                }
            }
        }

        saveLastSyncTimestamp(System.currentTimeMillis())
        return Pair(pulled, conflicts)
    }

    // ─── Almacenamiento del timestamp de última sync ────────────

    fun getLastSyncTimestamp(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    private fun saveLastSyncTimestamp(timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    // ─── Clasificación de errores ────────────────────────────────

    /**
     * Clasifica una excepción en un mensaje legible para el usuario.
     */
    private fun classifyError(e: Exception): String {
        val cause = e.cause ?: e
        return when {
            cause is UnknownHostException ||
            cause is ConnectException ||
            e is UnknownHostException ||
            e is ConnectException ->
                "Sin conexión a internet"

            cause is SocketTimeoutException ||
            e is SocketTimeoutException ->
                "Tiempo de espera agotado (servidor no responde)"

            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
            e.message?.contains("No address associated", ignoreCase = true) == true ||
            e.message?.contains("Network is unreachable", ignoreCase = true) == true ||
            e.message?.contains("failed to connect", ignoreCase = true) == true ->
                "Sin conexión a internet"

            e.message?.contains("timeout", ignoreCase = true) == true ->
                "Tiempo de espera agotado"

            e.message?.contains("401", ignoreCase = true) == true ||
            e.message?.contains("Unauthorized", ignoreCase = true) == true ->
                "Sesión expirada, vuelve a iniciar sesión"

            e.message?.contains("403", ignoreCase = true) == true ||
            e.message?.contains("Forbidden", ignoreCase = true) == true ->
                "Sin permisos para realizar esta operación"

            else -> e.message ?: "Error desconocido"
        }
    }
}

/**
 * Resultado de un ciclo de sincronización.
 */
data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val errors: List<String>
) {
    val isSuccess get() = errors.isEmpty()

    /** True si todos los errores son por falta de conexión */
    val isNetworkError: Boolean
        get() = errors.isNotEmpty() && errors.all { it.contains("Sin conexión") }

    /** True si algún error es por timeout */
    val isTimeoutError: Boolean
        get() = errors.any { it.contains("Tiempo de espera") }
}
