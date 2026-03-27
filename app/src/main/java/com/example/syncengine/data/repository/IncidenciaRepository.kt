package com.example.syncengine.data.repository

import com.example.syncengine.data.local.ConflictDao
import com.example.syncengine.data.local.IncidenciaDao
import com.example.syncengine.data.local.IncidenciaEntity
import com.example.syncengine.data.local.PendingConflictEntity
import com.example.syncengine.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repositorio offline-first.
 * REGLA PRINCIPAL: la UI SIEMPRE lee de Room (local). Nunca de la red directamente.
 * Las escrituras van primero a Room con un sync_status pendiente,
 * y el SyncEngine se encarga de sincronizar con Supabase.
 */
class IncidenciaRepository(
    private val incidenciaDao: IncidenciaDao,
    private val conflictDao: ConflictDao
) {
    // ─── LECTURAS (siempre locales) ─────────────────────────────

    /**
     * Lista de incidencias activas (no borradas), ordenadas por fecha.
     * Devuelve un Flow que se actualiza automáticamente.
     */
    fun getAllIncidencias(): Flow<List<IncidenciaEntity>> =
        incidenciaDao.getAllIncidencias()

    /**
     * Incidencias con estado CONFLICT.
     */
    fun getConflictIncidencias(): Flow<List<IncidenciaEntity>> =
        incidenciaDao.getConflictIncidencias()

    /**
     * Cantidad de conflictos pendientes (para badge en UI).
     */
    fun getConflictCount(): Flow<Int> =
        conflictDao.getConflictCount()

    /**
     * Obtiene una incidencia por su ID.
     */
    suspend fun getById(id: String): IncidenciaEntity? =
        incidenciaDao.getById(id)

    /**
     * Obtiene los datos remotos de un conflicto.
     */
    suspend fun getConflictRemoteData(incidenciaId: String): PendingConflictEntity? =
        conflictDao.getById(incidenciaId)

    /**
     * Todos los conflictos pendientes como Flow.
     */
    fun getAllConflicts(): Flow<List<PendingConflictEntity>> =
        conflictDao.getAll()

    /**
     * Cantidad de incidencias pendientes de sincronizar.
     */
    fun getPendingCount(): Flow<Int> =
        incidenciaDao.getPendingCount()

    // ─── ESCRITURAS (locales con sync_status pendiente) ─────────

    /**
     * Crea una nueva incidencia local. Se marca como PENDING_INSERT
     * para que el SyncEngine la suba al servidor.
     */
    suspend fun createIncidencia(
        titulo: String,
        descripcion: String,
        latitud: Double?,
        longitud: Double?,
        usuarioId: String,
        fotoPath: String? = null
    ) {
        val now = System.currentTimeMillis()
        val entity = IncidenciaEntity(
            id = UUID.randomUUID().toString(),
            usuario_id = usuarioId,
            titulo = titulo,
            descripcion = descripcion,
            estado = "pendiente",
            latitud = latitud,
            longitud = longitud,
            foto_path = fotoPath,
            foto_url = null,
            version = 1,
            creado_en = now,
            actualizado_en = now,
            borrado_en = null,
            sync_status = SyncStatus.PENDING_INSERT
        )
        incidenciaDao.insertOrUpdate(entity)
    }

    /**
     * Actualiza una incidencia existente. Se marca como PENDING_UPDATE.
     * Incrementa la versión local para el control de concurrencia optimista.
     */
    suspend fun updateIncidencia(
        existing: IncidenciaEntity,
        titulo: String,
        descripcion: String,
        estado: String,
        fotoPath: String? = null
    ) {
        val updated = existing.copy(
            titulo = titulo,
            descripcion = descripcion,
            estado = estado,
            foto_path = fotoPath ?: existing.foto_path,
            actualizado_en = System.currentTimeMillis(),
            // NO incrementar version localmente: se mantiene la versión del servidor
            // para poder detectar conflictos comparando con la versión remota.
            // La versión se incrementa solo al hacer PUSH exitoso.
            sync_status = when (existing.sync_status) {
                SyncStatus.PENDING_INSERT -> SyncStatus.PENDING_INSERT // Aún no se subió
                else -> SyncStatus.PENDING_UPDATE
            }
        )
        incidenciaDao.insertOrUpdate(updated)
    }

    /**
     * Soft-delete: marca borrado_en y pone sync_status PENDING_DELETE.
     */
    suspend fun deleteIncidencia(existing: IncidenciaEntity) {
        val now = System.currentTimeMillis()
        val deleted = existing.copy(
            borrado_en = now,
            actualizado_en = now,
            sync_status = when (existing.sync_status) {
                SyncStatus.PENDING_INSERT -> SyncStatus.LOCAL_ONLY // Nunca llegó al server, borrar local
                else -> SyncStatus.PENDING_DELETE
            }
        )
        incidenciaDao.insertOrUpdate(deleted)
    }

    // ─── RESOLUCIÓN DE CONFLICTOS ───────────────────────────────

    /**
     * El usuario elige QUEDARSE CON SU VERSIÓN LOCAL.
     * Se marca como PENDING_UPDATE para que el SyncEngine la suba.
     * Actualizamos el checksum con los datos REMOTOS actuales para que
     * el próximo checkServerConflict no re-detecte el mismo conflicto.
     */
    suspend fun resolveConflictKeepLocal(incidenciaId: String) {
        val remote = conflictDao.getById(incidenciaId)
        val local = incidenciaDao.getById(incidenciaId)
        if (local != null && remote != null) {
            // El checksum debe reflejar el estado ACTUAL del servidor
            // (que es lo que almacena PendingConflictEntity)
            val serverChecksum = IncidenciaEntity.computeChecksum(
                titulo = remote.remote_titulo,
                descripcion = remote.remote_descripcion,
                estado = remote.remote_estado,
                latitud = remote.remote_latitud,
                longitud = remote.remote_longitud,
                foto_url = remote.remote_foto_url,
                version = remote.remote_version
            )
            val resolved = local.copy(
                version = remote.remote_version,
                sync_status = SyncStatus.PENDING_UPDATE,
                synced_checksum = serverChecksum
            )
            incidenciaDao.insertOrUpdate(resolved)
        } else if (local != null) {
            // Sin datos remotos (raro), simplemente marcar como pending
            incidenciaDao.insertOrUpdate(local.copy(sync_status = SyncStatus.PENDING_UPDATE))
        }
        conflictDao.delete(incidenciaId)
    }

    /**
     * El usuario elige QUEDARSE CON LA VERSIÓN REMOTA.
     * Se reemplaza la entidad local con los datos del servidor y se marca SYNCED.
     */
    suspend fun resolveConflictKeepRemote(incidenciaId: String) {
        val remote = conflictDao.getById(incidenciaId) ?: return
        val local = incidenciaDao.getById(incidenciaId) ?: return

        val remoteChecksum = IncidenciaEntity.computeChecksum(
            titulo = remote.remote_titulo,
            descripcion = remote.remote_descripcion,
            estado = remote.remote_estado,
            latitud = remote.remote_latitud,
            longitud = remote.remote_longitud,
            foto_url = remote.remote_foto_url,
            version = remote.remote_version
        )

        val resolved = local.copy(
            titulo = remote.remote_titulo,
            descripcion = remote.remote_descripcion ?: "",
            estado = remote.remote_estado,
            latitud = remote.remote_latitud,
            longitud = remote.remote_longitud,
            foto_url = remote.remote_foto_url,
            version = remote.remote_version,
            creado_en = remote.remote_creado_en,
            actualizado_en = remote.remote_actualizado_en,
            borrado_en = remote.remote_borrado_en,
            sync_status = SyncStatus.SYNCED,
            synced_checksum = remoteChecksum
        )
        incidenciaDao.insertOrUpdate(resolved)
        conflictDao.delete(incidenciaId)
    }
}

