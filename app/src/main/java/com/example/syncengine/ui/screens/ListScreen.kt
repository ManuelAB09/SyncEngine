package com.example.syncengine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.syncengine.data.local.IncidenciaEntity
import com.example.syncengine.data.local.SyncStatus
import com.example.syncengine.ui.viewmodel.IncidenciaViewModel
import com.example.syncengine.ui.viewmodel.SyncUiState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    viewModel: IncidenciaViewModel,
    onNavigateToForm: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToConflicts: () -> Unit,
    onLogout: () -> Unit
) {
    val incidencias by viewModel.incidencias.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val conflictCount by viewModel.conflictCount.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val lastSync by viewModel.lastSyncTimestamp.collectAsState()
    val currentUserId = viewModel.currentUserId
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar resultado de sync en snackbar
    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncUiState.Done -> {
                val r = state.result
                val msg = if (r.isSuccess) {
                    buildString {
                        append("Sincronización completada")
                        if (r.pushed > 0) append(" · ↑ ${r.pushed} subidos")
                        if (r.pulled > 0) append(" · ↓ ${r.pulled} descargados")
                        if (r.conflicts > 0) append(" · ⚠ ${r.conflicts} conflictos")
                        if (r.pushed == 0 && r.pulled == 0 && r.conflicts == 0)
                            append(" · Todo al día ✓")
                    }
                } else if (r.isNetworkError) {
                    "📡 Sin conexión a internet. Tus cambios se guardan en local y se sincronizarán cuando haya conexión."
                } else if (r.isTimeoutError) {
                    "⏱ El servidor no respondió a tiempo. Inténtalo de nuevo más tarde."
                } else {
                    buildString {
                        append("❌ Error de sincronización")
                        r.errors.forEach { error ->
                            append("\n• $error")
                        }
                    }
                }
                snackbarHostState.showSnackbar(msg)
                viewModel.clearSyncState()
            }
            is SyncUiState.Error -> {
                snackbarHostState.showSnackbar("❌ Error inesperado: ${state.message}")
                viewModel.clearSyncState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Incidencias") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // Botón de conflictos con badge
                    if (conflictCount > 0) {
                        IconButton(onClick = onNavigateToConflicts) {
                            BadgedBox(badge = {
                                Badge { Text("$conflictCount") }
                            }) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Conflictos",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Botón de sync manual con badge de pendientes
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        enabled = syncState !is SyncUiState.Syncing
                    ) {
                        if (syncState is SyncUiState.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            if (pendingCount > 0) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    ) { Text("$pendingCount") }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                                }
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                            }
                        }
                    }

                    // Botón de cerrar sesión
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar sesión"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.clearSelection()
                onNavigateToForm()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Nueva incidencia")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Barra de progreso durante sync ──
            AnimatedVisibility(
                visible = syncState is SyncUiState.Syncing,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ── Banner de estado de sincronización ──
            SyncStatusBanner(
                pendingCount = pendingCount,
                conflictCount = conflictCount,
                lastSync = lastSync,
                isSyncing = syncState is SyncUiState.Syncing,
                onSyncClick = { viewModel.syncNow() },
                onConflictsClick = onNavigateToConflicts
            )

            // ── Contenido principal ──
            if (incidencias.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "📋",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No hay incidencias",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Pulsa + para crear la primera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // ── Leyenda de iconos ──
                SyncLegend(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)
                ) {
                    items(
                        items = incidencias,
                        key = { it.id }
                    ) { incidencia ->
                        val isOwner = currentUserId != null && incidencia.usuario_id == currentUserId
                        if (isOwner) {
                            SwipeToDeleteItem(
                                incidencia = incidencia,
                                onDelete = { viewModel.deleteIncidencia(incidencia) },
                                onClick = {
                                    viewModel.selectIncidencia(incidencia)
                                    onNavigateToEdit(incidencia.id)
                                }
                            )
                        } else {
                            // Incidencia ajena: solo ver, sin editar ni borrar
                            IncidenciaCard(
                                incidencia = incidencia,
                                isReadOnly = true,
                                onClick = { /* no navegar a edición */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Banner de estado de sincronización ──────────────────────────

@Composable
private fun SyncStatusBanner(
    pendingCount: Int,
    conflictCount: Int,
    lastSync: Long,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onConflictsClick: () -> Unit
) {
    // Banner de conflictos (rojo, llamativo)
    AnimatedVisibility(
        visible = conflictCount > 0,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable(onClick = onConflictsClick),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠️", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$conflictCount conflicto${if (conflictCount > 1) "s" else ""} sin resolver",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Toca para comparar y elegir qué versión conservar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    // Banner de cambios pendientes (azul/naranja)
    AnimatedVisibility(
        visible = pendingCount > 0 && conflictCount == 0,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("☁️", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$pendingCount cambio${if (pendingCount > 1) "s" else ""} sin sincronizar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "Pulsa Sincronizar para subir tus cambios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSyncClick,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Sync", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // Info de última sync (sutil, en gris)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (lastSync > 0)
                "Última sync: ${formatTimestamp(lastSync)}"
            else
                "Nunca sincronizado",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (pendingCount == 0 && conflictCount == 0) {
            Text(
                "✓ Todo sincronizado",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Leyenda de iconos de sync ──────────────────────────────────

@Composable
private fun SyncLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendItem(color = Color(0xFF4CAF50), label = "✓ Sync")
        LegendItem(color = Color(0xFF2196F3), label = "↑ Nuevo")
        LegendItem(color = Color(0xFFFF9800), label = "↑ Editado")
        LegendItem(color = Color(0xFFF44336), label = "✕ Borrado")
        LegendItem(color = Color(0xFFE91E63), label = "⚠ Conflicto")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ─── Componentes de tarjeta ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(
    incidencia: IncidenciaEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Eliminar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        content = {
            IncidenciaCard(incidencia = incidencia, onClick = onClick)
        }
    )
}

@Composable
private fun IncidenciaCard(
    incidencia: IncidenciaEntity,
    isReadOnly: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Borde especial para indicar estado de sync
    val cardColor = when {
        isReadOnly -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        incidencia.sync_status == SyncStatus.CONFLICT ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        incidencia.sync_status in listOf(
            SyncStatus.PENDING_INSERT, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE
        ) -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }

    // Determinar imagen a mostrar: local path > url remota
    val photoSource: Any? = when {
        incidencia.foto_path != null && File(incidencia.foto_path).exists() -> File(incidencia.foto_path)
        incidencia.foto_url != null -> incidencia.foto_url
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // ── Miniatura de foto (si existe) ──
            if (photoSource != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoSource)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // ── Contenido textual ──
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = incidencia.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isReadOnly) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF9E9E9E).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "👁 Solo lectura",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF616161),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        SyncStatusChip(incidencia.sync_status)
                    }
                }

                if (!incidencia.descripcion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = incidencia.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EstadoChip(incidencia.estado)
                    Text(
                        text = formatTimestamp(incidencia.actualizado_en),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Indicador de estado de sync mejorado: chip con color + texto legible.
 */
@Composable
private fun SyncStatusChip(status: SyncStatus) {
    val (bgColor, textColor, label) = when (status) {
        SyncStatus.SYNCED -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.15f), Color(0xFF2E7D32), "✓ Sync"
        )
        SyncStatus.PENDING_INSERT -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.15f), Color(0xFF1565C0), "↑ Nuevo"
        )
        SyncStatus.PENDING_UPDATE -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.15f), Color(0xFFE65100), "↑ Editado"
        )
        SyncStatus.PENDING_DELETE -> Triple(
            Color(0xFFF44336).copy(alpha = 0.15f), Color(0xFFC62828), "✕ Borrado"
        )
        SyncStatus.CONFLICT -> Triple(
            Color(0xFFE91E63).copy(alpha = 0.15f), Color(0xFFC2185B), "⚠ Conflicto"
        )
        SyncStatus.LOCAL_ONLY -> Triple(
            Color(0xFF9E9E9E).copy(alpha = 0.15f), Color(0xFF616161), "● Local"
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EstadoChip(estado: String) {
    val color = when (estado.lowercase()) {
        "pendiente" -> MaterialTheme.colorScheme.tertiary
        "en_progreso", "en progreso" -> MaterialTheme.colorScheme.primary
        "resuelto", "resuelta" -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.outline
    }

    Text(
        text = estado.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

private fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
