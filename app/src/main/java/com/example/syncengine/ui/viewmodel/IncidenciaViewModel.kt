@file:OptIn(
    kotlinx.serialization.InternalSerializationApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package com.example.syncengine.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syncengine.SyncEngineApp
import com.example.syncengine.data.local.IncidenciaEntity
import com.example.syncengine.data.local.PendingConflictEntity
import com.example.syncengine.data.remote.SupabaseNetwork
import com.example.syncengine.data.repository.IncidenciaRepository
import com.example.syncengine.sync.SyncEngine
import com.example.syncengine.sync.SyncResult
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel principal de la app.
 * Expone el estado de la UI y delega operaciones al repositorio y al motor de sync.
 */
class IncidenciaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as SyncEngineApp).database
    private val repository = IncidenciaRepository(db.incidenciaDao(), db.conflictDao())
    private val syncEngine = SyncEngine(db.incidenciaDao(), db.conflictDao(), application)

    // ─── Estado de la UI ────────────────────────────────────────

    /** ID del usuario actual (para control de permisos en la UI) */
    val currentUserId: String?
        get() = SupabaseNetwork.client.auth.currentUserOrNull()?.id

    /** Lista de incidencias activas (Flow → StateFlow para Compose) */
    val incidencias: StateFlow<List<IncidenciaEntity>> = repository.getAllIncidencias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Incidencias en conflicto */
    val conflicts: StateFlow<List<IncidenciaEntity>> = repository.getConflictIncidencias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cantidad de conflictos (para badge) */
    val conflictCount: StateFlow<Int> = repository.getConflictCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Todos los datos remotos de conflictos */
    val conflictRemoteData: StateFlow<List<PendingConflictEntity>> = repository.getAllConflicts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cantidad de cambios pendientes de sincronizar */
    val pendingCount: StateFlow<Int> = repository.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Estado de sincronización */
    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    /** Incidencia seleccionada para editar */
    private val _selectedIncidencia = MutableStateFlow<IncidenciaEntity?>(null)
    val selectedIncidencia: StateFlow<IncidenciaEntity?> = _selectedIncidencia.asStateFlow()

    /** Timestamp de la última sincronización */
    private val _lastSyncTimestamp = MutableStateFlow(syncEngine.getLastSyncTimestamp())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    // ─── Acciones ───────────────────────────────────────────────

    fun createIncidencia(titulo: String, descripcion: String, latitud: Double?, longitud: Double?, fotoPath: String? = null) {
        viewModelScope.launch {
            val userId = SupabaseNetwork.client.auth.currentUserOrNull()?.id ?: return@launch
            repository.createIncidencia(titulo, descripcion, latitud, longitud, userId, fotoPath)
        }
    }

    fun updateIncidencia(titulo: String, descripcion: String, estado: String, fotoPath: String? = null) {
        viewModelScope.launch {
            val existing = _selectedIncidencia.value ?: return@launch
            repository.updateIncidencia(existing, titulo, descripcion, estado, fotoPath)
            _selectedIncidencia.value = null
        }
    }

    fun deleteIncidencia(incidencia: IncidenciaEntity) {
        viewModelScope.launch {
            repository.deleteIncidencia(incidencia)
        }
    }

    fun selectIncidencia(incidencia: IncidenciaEntity) {
        _selectedIncidencia.value = incidencia
    }

    fun clearSelection() {
        _selectedIncidencia.value = null
    }

    /**
     * Ejecuta la sincronización manual (el usuario pulsa el botón de sync).
     */
    fun syncNow() {
        viewModelScope.launch {
            _syncState.value = SyncUiState.Syncing
            try {
                val result = syncEngine.sync()
                _lastSyncTimestamp.value = syncEngine.getLastSyncTimestamp()
                _syncState.value = SyncUiState.Done(result)
            } catch (e: Exception) {
                _syncState.value = SyncUiState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncUiState.Idle
    }

    // ─── Resolución de conflictos ───────────────────────────────

    fun resolveConflictKeepLocal(incidenciaId: String) {
        viewModelScope.launch {
            repository.resolveConflictKeepLocal(incidenciaId)
        }
    }

    fun resolveConflictKeepRemote(incidenciaId: String) {
        viewModelScope.launch {
            repository.resolveConflictKeepRemote(incidenciaId)
        }
    }

    /** Cierra la sesión de Supabase Auth. */
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                SupabaseNetwork.client.auth.signOut()
            } catch (_: Exception) { /* ignorar errores de red al cerrar */ }
            onLoggedOut()
        }
    }
}

/**
 * Estados posibles de la sincronización en la UI.
 */
sealed class SyncUiState {
    data object Idle : SyncUiState()
    data object Syncing : SyncUiState()
    data class Done(val result: SyncResult) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}
